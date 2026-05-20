package com.nacos.stresstest;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.exception.NacosException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;
import java.util.Random;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Core stress test executor that manages threads and operations against Nacos ConfigService.
 */
public class StressTestRunner {

    private static final Logger logger = LoggerFactory.getLogger(StressTestRunner.class);

    private final StressTestConfig config;
    private final MetricsCollector metrics;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean warmupComplete = new AtomicBoolean(false);

    private ConfigService configService;
    private ExecutorService workerPool;
    private ScheduledExecutorService schedulerPool;

    public StressTestRunner(StressTestConfig config, MetricsCollector metrics) {
        this.config = config;
        this.metrics = metrics;
    }

    /**
     * Initialize the Nacos ConfigService connection.
     */
    public void initialize() throws NacosException {
        Properties properties = new Properties();
        properties.setProperty("serverAddr", config.getServerAddr());
        properties.setProperty("namespace", config.getNamespace());
        properties.setProperty("username", config.getUsername());
        properties.setProperty("password", config.getPassword());

        logger.info("Connecting to Nacos server at {}", config.getServerAddr());
        configService = NacosFactory.createConfigService(properties);
        logger.info("Successfully connected to Nacos server");
    }

    /**
     * Pre-populate config items in Nacos for the stress test.
     */
    public void setupConfigs() throws NacosException {
        logger.info("Pre-populating {} config items...", config.getTotalConfigs());
        String content = generateRandomContent(config.getConfigContentSize());

        int batchSize = 50;
        for (int i = 0; i < config.getTotalConfigs(); i++) {
            String dataId = config.getDataIdPrefix() + i;
            boolean success = configService.publishConfig(dataId, config.getGroup(), content);
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
     * Run the stress test.
     */
    public void run() throws InterruptedException {
        running.set(true);

        workerPool = Executors.newFixedThreadPool(config.getConcurrentThreads(),
                new ThreadFactory() {
                    private int count = 0;
                    @Override
                    public Thread newThread(Runnable r) {
                        Thread t = new Thread(r, "stress-worker-" + (count++));
                        t.setDaemon(true);
                        return t;
                    }
                });

        schedulerPool = Executors.newScheduledThreadPool(2, r -> {
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
     * Start worker threads with optional ramp-up.
     */
    private void startWorkers() {
        int threads = config.getConcurrentThreads();
        int rampUpMs = config.getRampUpSeconds() * 1000;

        if (rampUpMs > 0 && threads > 1) {
            int delayPerThread = rampUpMs / threads;
            logger.info("Ramping up {} threads over {} seconds", threads, config.getRampUpSeconds());
            for (int i = 0; i < threads; i++) {
                final int threadIndex = i;
                schedulerPool.schedule(() -> {
                    if (running.get()) {
                        workerPool.submit(new WorkerTask(threadIndex));
                    }
                }, (long) i * delayPerThread, TimeUnit.MILLISECONDS);
            }
        } else {
            for (int i = 0; i < threads; i++) {
                workerPool.submit(new WorkerTask(i));
            }
        }
    }

    /**
     * Gracefully shutdown the test.
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
        if (configService != null) {
            try {
                configService.shutDown();
            } catch (NacosException e) {
                logger.warn("Error shutting down ConfigService: {}", e.getMessage());
            }
        }
        logger.info("Stress test shutdown complete");
    }

    /**
     * Check if the test is still running.
     */
    public boolean isRunning() {
        return running.get();
    }

    /**
     * Generate random alphanumeric content of specified size.
     */
    private String generateRandomContent(int size) {
        Random random = new Random();
        StringBuilder sb = new StringBuilder(size);
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        for (int i = 0; i < size; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

    /**
     * Worker task that performs read/write operations in a loop.
     */
    private class WorkerTask implements Runnable {
        private final int threadIndex;
        private final Random random;

        WorkerTask(int threadIndex) {
            this.threadIndex = threadIndex;
            this.random = new Random(threadIndex * 31L + System.nanoTime());
        }

        @Override
        public void run() {
            logger.debug("Worker thread {} started", threadIndex);
            while (running.get() && !Thread.currentThread().isInterrupted()) {
                try {
                    // Randomly pick a dataId
                    int configIndex = random.nextInt(config.getTotalConfigs());
                    String dataId = config.getDataIdPrefix() + configIndex;

                    // Decide read or write based on readRatio
                    boolean isRead = random.nextInt(100) < config.getReadRatio();

                    if (isRead) {
                        performRead(dataId);
                    } else {
                        performWrite(dataId);
                    }
                } catch (Exception e) {
                    if (running.get()) {
                        logger.debug("Worker {} error: {}", threadIndex, e.getMessage());
                    }
                }
            }
            logger.debug("Worker thread {} stopped", threadIndex);
        }

        private void performRead(String dataId) {
            long startNanos = System.nanoTime();
            try {
                String result = configService.getConfig(dataId, config.getGroup(), 5000);
                long latencyMicros = (System.nanoTime() - startNanos) / 1000;
                if (result != null) {
                    metrics.recordReadSuccess(latencyMicros);
                } else {
                    metrics.recordReadFailure();
                }
            } catch (NacosException e) {
                metrics.recordReadFailure();
                logger.debug("Read failed for {}: {}", dataId, e.getMessage());
            }
        }

        private void performWrite(String dataId) {
            long startNanos = System.nanoTime();
            try {
                String content = generateRandomContent(config.getConfigContentSize());
                boolean success = configService.publishConfig(dataId, config.getGroup(), content);
                long latencyMicros = (System.nanoTime() - startNanos) / 1000;
                if (success) {
                    metrics.recordWriteSuccess(latencyMicros);
                } else {
                    metrics.recordWriteFailure();
                }
            } catch (NacosException e) {
                metrics.recordWriteFailure();
                logger.debug("Write failed for {}: {}", dataId, e.getMessage());
            }
        }
    }
}
