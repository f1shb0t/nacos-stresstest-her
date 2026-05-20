package com.nacos.stresstest;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.exception.NacosException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Core stress test executor that simulates thousands of independent clients hitting Nacos.
 * 
 * Uses a pool of ConfigService instances (simulating independent JVMs), Zipf-distributed
 * read patterns for hot configs, think time between operations, and staggered startup.
 */
public class StressTestRunner {

    private static final Logger logger = LoggerFactory.getLogger(StressTestRunner.class);

    private final StressTestConfig config;
    private final MetricsCollector metrics;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean warmupComplete = new AtomicBoolean(false);

    /** Pool of independent ConfigService instances simulating N independent machines */
    private final List<ConfigService> clientPool = new ArrayList<>();

    private ExecutorService workerPool;
    private ScheduledExecutorService schedulerPool;

    /** Pre-computed Zipf CDF for read config selection */
    private double[] zipfCdf;

    public StressTestRunner(StressTestConfig config, MetricsCollector metrics) {
        this.config = config;
        this.metrics = metrics;
    }

    /**
     * Initialize the ConfigService pool - creates clientPoolSize independent connections.
     * Each instance simulates an independent JVM/machine connecting to Nacos.
     */
    public void initialize() throws NacosException {
        int poolSize = config.getClientPoolSize();
        logger.info("Creating ConfigService pool with {} independent clients to {}", poolSize, config.getServerAddr());

        for (int i = 0; i < poolSize; i++) {
            Properties properties = new Properties();
            properties.setProperty("serverAddr", config.getServerAddr());
            properties.setProperty("namespace", config.getNamespace());
            properties.setProperty("username", config.getUsername());
            properties.setProperty("password", config.getPassword());
            // Randomized connection ID to simulate independent JVMs
            properties.setProperty("clientWorkerMaxThreadCount", "4");
            properties.setProperty("clusterName", "stress-client-" + i + "-" + UUID.randomUUID().toString().substring(0, 8));

            try {
                ConfigService client = NacosFactory.createConfigService(properties);
                clientPool.add(client);
                if ((i + 1) % 10 == 0) {
                    logger.info("Created {}/{} ConfigService instances", i + 1, poolSize);
                }
            } catch (NacosException e) {
                logger.error("Failed to create ConfigService instance {}: {}", i, e.getMessage());
                throw e;
            }
        }

        logger.info("Successfully created {} ConfigService instances", clientPool.size());

        // Pre-compute Zipf distribution CDF for read operations
        buildZipfCdf(config.getTotalConfigs());
    }

    /**
     * Pre-populate config items in Nacos for the stress test.
     * Uses the first client in the pool for setup operations.
     */
    public void setup() throws NacosException {
        if (clientPool.isEmpty()) {
            throw new IllegalStateException("Must call initialize() before setup()");
        }

        ConfigService setupClient = clientPool.get(0);
        logger.info("Pre-populating {} config items...", config.getTotalConfigs());
        String content = generateRandomContent(config.getConfigContentSize());

        int batchSize = 50;
        for (int i = 0; i < config.getTotalConfigs(); i++) {
            String dataId = config.getDataIdPrefix() + i;
            boolean success = setupClient.publishConfig(dataId, config.getGroup(), content);
            if (!success) {
                logger.warn("Failed to publish initial config: {}", dataId);
            }
            if ((i + 1) % batchSize == 0) {
                logger.info("Published {}/{} configs", i + 1, config.getTotalConfigs());
            }
        }
        logger.info("Config pre-population complete");
    }

    /**
     * Backward-compatible alias for setup().
     */
    public void setupConfigs() throws NacosException {
        setup();
    }

    /**
     * Run the stress test with realistic simulation patterns.
     */
    public void run() throws InterruptedException {
        running.set(true);

        int threads = config.getConcurrentThreads();
        workerPool = Executors.newFixedThreadPool(threads, new ThreadFactory() {
            private final AtomicInteger count = new AtomicInteger(0);
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "stress-worker-" + count.getAndIncrement());
                t.setDaemon(true);
                return t;
            }
        });

        schedulerPool = Executors.newScheduledThreadPool(4, r -> {
            Thread t = new Thread(r, "stress-scheduler");
            t.setDaemon(true);
            return t;
        });

        // Schedule periodic metrics snapshots
        schedulerPool.scheduleAtFixedRate(() -> {
            if (warmupComplete.get()) {
                metrics.takeSnapshot();
            }
        }, config.getReportIntervalSeconds(), config.getReportIntervalSeconds(), TimeUnit.SECONDS);

        // Warmup phase
        if (config.getWarmupSeconds() > 0) {
            logger.info("Starting warmup phase ({} seconds)...", config.getWarmupSeconds());
            startWorkers();
            Thread.sleep(config.getWarmupSeconds() * 1000L);
            logger.info("Warmup complete, resetting metrics");
            metrics.reset();
        } else {
            startWorkers();
        }

        warmupComplete.set(true);
        logger.info("Starting measurement phase ({} seconds)...", config.getDurationSeconds());

        // Wait for test duration
        Thread.sleep(config.getDurationSeconds() * 1000L);

        // Take final snapshot
        metrics.takeSnapshot();

        // Shutdown
        shutdown();
    }

    /**
     * Start worker threads with staggered ramp-up.
     * Threads start with random delays to simulate machines coming online at different times.
     */
    private void startWorkers() {
        int threads = config.getConcurrentThreads();
        int rampUpMs = config.getRampUpSeconds() * 1000;
        int poolSize = clientPool.size();

        if (rampUpMs > 0 && threads > 1) {
            logger.info("Ramping up {} threads over {} seconds with staggered startup (pool size: {})",
                    threads, config.getRampUpSeconds(), poolSize);

            Random rampRandom = new Random();
            for (int i = 0; i < threads; i++) {
                final int threadIndex = i;
                // Staggered startup: base delay + random jitter within the ramp-up window
                long baseDelay = (long) i * rampUpMs / threads;
                long jitter = config.isJitterEnabled() ? rampRandom.nextInt(Math.max(1, rampUpMs / threads)) : 0;
                long delay = baseDelay + jitter;

                schedulerPool.schedule(() -> {
                    if (running.get()) {
                        workerPool.submit(new WorkerTask(threadIndex, clientPool.get(threadIndex % poolSize)));
                    }
                }, delay, TimeUnit.MILLISECONDS);
            }
        } else {
            logger.info("Starting {} threads immediately (pool size: {})", threads, poolSize);
            for (int i = 0; i < threads; i++) {
                workerPool.submit(new WorkerTask(i, clientPool.get(i % poolSize)));
            }
        }
    }

    /**
     * Gracefully shutdown the test - stops all threads and closes all ConfigService instances.
     */
    public void shutdown() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        logger.info("Shutting down stress test...");

        if (schedulerPool != null) {
            schedulerPool.shutdownNow();
        }
        if (workerPool != null) {
            workerPool.shutdownNow();
            try {
                workerPool.awaitTermination(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // Graceful shutdown of all ConfigService instances
        logger.info("Shutting down {} ConfigService instances...", clientPool.size());
        for (int i = 0; i < clientPool.size(); i++) {
            try {
                clientPool.get(i).shutDown();
            } catch (NacosException e) {
                logger.warn("Error shutting down ConfigService instance {}: {}", i, e.getMessage());
            }
        }
        clientPool.clear();

        logger.info("Stress test shutdown complete");
    }

    /**
     * Check if the test is still running.
     */
    public boolean isRunning() {
        return running.get();
    }

    /**
     * Build Zipf cumulative distribution function for realistic read patterns.
     * Implements 80/20 rule: top 20% of configs receive ~80% of reads.
     */
    private void buildZipfCdf(int numConfigs) {
        // Zipf exponent ~1.0 gives roughly 80/20 distribution
        double exponent = 1.0;
        double[] weights = new double[numConfigs];
        double totalWeight = 0.0;

        for (int i = 0; i < numConfigs; i++) {
            weights[i] = 1.0 / Math.pow(i + 1, exponent);
            totalWeight += weights[i];
        }

        zipfCdf = new double[numConfigs];
        double cumulative = 0.0;
        for (int i = 0; i < numConfigs; i++) {
            cumulative += weights[i] / totalWeight;
            zipfCdf[i] = cumulative;
        }
        zipfCdf[numConfigs - 1] = 1.0; // Ensure last element is exactly 1.0

        logger.info("Zipf distribution built: top 20% of configs ({}) will receive ~80% of reads",
                (int) (numConfigs * 0.2));
    }

    /**
     * Select a config index using Zipf distribution (hot configs get more reads).
     */
    private int selectZipfConfigIndex(Random random) {
        double u = random.nextDouble();
        // Binary search in CDF
        int low = 0, high = zipfCdf.length - 1;
        while (low < high) {
            int mid = (low + high) >>> 1;
            if (zipfCdf[mid] < u) {
                low = mid + 1;
            } else {
                high = mid;
            }
        }
        return low;
    }

    /**
     * Generate random alphanumeric content of specified size.
     */
    private String generateRandomContent(int size) {
        Random random = ThreadLocalRandom.current();
        StringBuilder sb = new StringBuilder(size);
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        for (int i = 0; i < size; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

    /**
     * Worker task that performs read/write operations against its assigned ConfigService.
     * Each worker is bound to a specific client from the pool (round-robin assignment).
     */
    private class WorkerTask implements Runnable {
        private final int threadIndex;
        private final ConfigService client;
        private final Random random;

        WorkerTask(int threadIndex, ConfigService client) {
            this.threadIndex = threadIndex;
            this.client = client;
            this.random = new Random(threadIndex * 31L + System.nanoTime());
        }

        @Override
        public void run() {
            logger.debug("Worker thread {} started (client pool index: {})", threadIndex, threadIndex % clientPool.size());

            while (running.get() && !Thread.currentThread().isInterrupted()) {
                try {
                    // Decide read or write based on readRatio
                    boolean isRead = random.nextInt(100) < config.getReadRatio();

                    if (isRead) {
                        // Zipf distribution for reads: hot configs get more traffic
                        int configIndex = selectZipfConfigIndex(random);
                        String dataId = config.getDataIdPrefix() + configIndex;
                        performRead(dataId);
                    } else {
                        // Uniform distribution for writes
                        int configIndex = random.nextInt(config.getTotalConfigs());
                        String dataId = config.getDataIdPrefix() + configIndex;
                        performWrite(dataId);
                    }

                    // Think time / jitter between operations
                    applyThinkTime();

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    // Independent failure isolation: log and continue
                    if (running.get()) {
                        logger.debug("Worker {} error (client {}): {}", threadIndex,
                                threadIndex % clientPool.size(), e.getMessage());
                    }
                }
            }
            logger.debug("Worker thread {} stopped", threadIndex);
        }

        private void performRead(String dataId) {
            long startNanos = System.nanoTime();
            try {
                String result = client.getConfig(dataId, config.getGroup(), config.getRequestTimeoutMs());
                long latencyMicros = (System.nanoTime() - startNanos) / 1000;
                if (result != null) {
                    metrics.recordReadSuccess(latencyMicros);
                } else {
                    metrics.recordReadFailure();
                }
            } catch (NacosException e) {
                metrics.recordReadFailure();
                logger.debug("Read failed for {} on client {}: {}", dataId,
                        threadIndex % clientPool.size(), e.getMessage());
            }
        }

        private void performWrite(String dataId) {
            long startNanos = System.nanoTime();
            try {
                String content = generateRandomContent(config.getConfigContentSize());
                boolean success = client.publishConfig(dataId, config.getGroup(), content);
                long latencyMicros = (System.nanoTime() - startNanos) / 1000;
                if (success) {
                    metrics.recordWriteSuccess(latencyMicros);
                } else {
                    metrics.recordWriteFailure();
                }
            } catch (NacosException e) {
                metrics.recordWriteFailure();
                logger.debug("Write failed for {} on client {}: {}", dataId,
                        threadIndex % clientPool.size(), e.getMessage());
            }
        }

        /**
         * Apply think time between operations to simulate realistic access patterns.
         * Sleeps for a random duration between thinkTimeMs and maxThinkTimeMs.
         */
        private void applyThinkTime() throws InterruptedException {
            long minThink = config.getThinkTimeMs();
            long maxThink = config.getMaxThinkTimeMs();

            if (maxThink <= 0 && minThink <= 0) {
                return; // No think time configured
            }

            long sleepMs;
            if (maxThink > minThink) {
                sleepMs = minThink + (long) (random.nextDouble() * (maxThink - minThink));
            } else {
                sleepMs = minThink;
            }

            if (sleepMs > 0) {
                Thread.sleep(sleepMs);
            }
        }
    }
}
