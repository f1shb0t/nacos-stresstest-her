package com.nacos.stresstest;

import org.HdrHistogram.ConcurrentHistogram;
import org.HdrHistogram.Histogram;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe metrics collector using HdrHistogram for latency tracking.
 */
public class MetricsCollector {

    private static final Logger logger = LoggerFactory.getLogger(MetricsCollector.class);

    // Histograms for latency (in microseconds, max 60 seconds)
    private final Histogram readHistogram;
    private final Histogram writeHistogram;

    // Counters
    private final AtomicLong readSuccessCount = new AtomicLong(0);
    private final AtomicLong readFailureCount = new AtomicLong(0);
    private final AtomicLong writeSuccessCount = new AtomicLong(0);
    private final AtomicLong writeFailureCount = new AtomicLong(0);

    // Snapshot tracking for periodic reports
    private volatile long lastSnapshotTime;
    private volatile long lastReadSuccess;
    private volatile long lastWriteSuccess;
    private volatile long lastReadFailure;
    private volatile long lastWriteFailure;

    // Timeline data points
    private final List<TimelineDataPoint> timelineData = Collections.synchronizedList(new ArrayList<>());

    private final long startTime;

    public MetricsCollector() {
        // Track latencies from 1 microsecond to 60 seconds with 3 significant digits
        this.readHistogram = new ConcurrentHistogram(1L, 60_000_000L, 3);
        this.writeHistogram = new ConcurrentHistogram(1L, 60_000_000L, 3);
        this.startTime = System.currentTimeMillis();
        this.lastSnapshotTime = startTime;
    }

    /**
     * Record a successful read operation.
     * @param latencyMicros latency in microseconds
     */
    public void recordReadSuccess(long latencyMicros) {
        readSuccessCount.incrementAndGet();
        readHistogram.recordValue(Math.min(latencyMicros, 60_000_000L));
    }

    /**
     * Record a failed read operation.
     */
    public void recordReadFailure() {
        readFailureCount.incrementAndGet();
    }

    /**
     * Record a successful write operation.
     * @param latencyMicros latency in microseconds
     */
    public void recordWriteSuccess(long latencyMicros) {
        writeSuccessCount.incrementAndGet();
        writeHistogram.recordValue(Math.min(latencyMicros, 60_000_000L));
    }

    /**
     * Record a failed write operation.
     */
    public void recordWriteFailure() {
        writeFailureCount.incrementAndGet();
    }

    /**
     * Take a periodic snapshot and record timeline data point.
     */
    public void takeSnapshot() {
        long now = System.currentTimeMillis();
        long elapsed = now - lastSnapshotTime;
        if (elapsed <= 0) return;

        double elapsedSeconds = elapsed / 1000.0;

        long currentReadSuccess = readSuccessCount.get();
        long currentWriteSuccess = writeSuccessCount.get();
        long currentReadFailure = readFailureCount.get();
        long currentWriteFailure = writeFailureCount.get();

        long deltaReadSuccess = currentReadSuccess - lastReadSuccess;
        long deltaWriteSuccess = currentWriteSuccess - lastWriteSuccess;
        long deltaReadFailure = currentReadFailure - lastReadFailure;
        long deltaWriteFailure = currentWriteFailure - lastWriteFailure;

        double readTps = deltaReadSuccess / elapsedSeconds;
        double writeTps = deltaWriteSuccess / elapsedSeconds;

        long totalDeltaOps = deltaReadSuccess + deltaWriteSuccess + deltaReadFailure + deltaWriteFailure;
        long totalDeltaErrors = deltaReadFailure + deltaWriteFailure;
        double errorRate = totalDeltaOps > 0 ? (double) totalDeltaErrors / totalDeltaOps * 100.0 : 0.0;

        // Get current percentiles from histograms
        long readP99 = readHistogram.getTotalCount() > 0 ? readHistogram.getValueAtPercentile(99.0) : 0;
        long writeP99 = writeHistogram.getTotalCount() > 0 ? writeHistogram.getValueAtPercentile(99.0) : 0;

        double timestampSeconds = (now - startTime) / 1000.0;

        TimelineDataPoint dp = new TimelineDataPoint(
                timestampSeconds, readTps, writeTps, readP99, writeP99, errorRate
        );
        timelineData.add(dp);

        logger.info("Snapshot [t={:.1f}s]: readTPS={:.1f}, writeTPS={:.1f}, readP99={}us, writeP99={}us, errorRate={:.2f}%",
                timestampSeconds, readTps, writeTps, readP99, writeP99, errorRate);

        // Update last values
        lastSnapshotTime = now;
        lastReadSuccess = currentReadSuccess;
        lastWriteSuccess = currentWriteSuccess;
        lastReadFailure = currentReadFailure;
        lastWriteFailure = currentWriteFailure;
    }

    /**
     * Reset all metrics (used after warmup phase).
     */
    public void reset() {
        readHistogram.reset();
        writeHistogram.reset();
        readSuccessCount.set(0);
        readFailureCount.set(0);
        writeSuccessCount.set(0);
        writeFailureCount.set(0);
        timelineData.clear();
        lastSnapshotTime = System.currentTimeMillis();
        lastReadSuccess = 0;
        lastWriteSuccess = 0;
        lastReadFailure = 0;
        lastWriteFailure = 0;
        logger.info("Metrics reset (warmup phase complete)");
    }

    // Getters for report generation

    public long getReadSuccessCount() {
        return readSuccessCount.get();
    }

    public long getReadFailureCount() {
        return readFailureCount.get();
    }

    public long getWriteSuccessCount() {
        return writeSuccessCount.get();
    }

    public long getWriteFailureCount() {
        return writeFailureCount.get();
    }

    public long getTotalOps() {
        return readSuccessCount.get() + readFailureCount.get() +
                writeSuccessCount.get() + writeFailureCount.get();
    }

    public long getTotalErrors() {
        return readFailureCount.get() + writeFailureCount.get();
    }

    public double getReadMeanLatencyMicros() {
        return readHistogram.getTotalCount() > 0 ? readHistogram.getMean() : 0;
    }

    public long getReadP50Micros() {
        return readHistogram.getTotalCount() > 0 ? readHistogram.getValueAtPercentile(50.0) : 0;
    }

    public long getReadP90Micros() {
        return readHistogram.getTotalCount() > 0 ? readHistogram.getValueAtPercentile(90.0) : 0;
    }

    public long getReadP95Micros() {
        return readHistogram.getTotalCount() > 0 ? readHistogram.getValueAtPercentile(95.0) : 0;
    }

    public long getReadP99Micros() {
        return readHistogram.getTotalCount() > 0 ? readHistogram.getValueAtPercentile(99.0) : 0;
    }

    public long getReadMaxMicros() {
        return readHistogram.getTotalCount() > 0 ? readHistogram.getMaxValue() : 0;
    }

    public double getWriteMeanLatencyMicros() {
        return writeHistogram.getTotalCount() > 0 ? writeHistogram.getMean() : 0;
    }

    public long getWriteP50Micros() {
        return writeHistogram.getTotalCount() > 0 ? writeHistogram.getValueAtPercentile(50.0) : 0;
    }

    public long getWriteP90Micros() {
        return writeHistogram.getTotalCount() > 0 ? writeHistogram.getValueAtPercentile(90.0) : 0;
    }

    public long getWriteP95Micros() {
        return writeHistogram.getTotalCount() > 0 ? writeHistogram.getValueAtPercentile(95.0) : 0;
    }

    public long getWriteP99Micros() {
        return writeHistogram.getTotalCount() > 0 ? writeHistogram.getValueAtPercentile(99.0) : 0;
    }

    public long getWriteMaxMicros() {
        return writeHistogram.getTotalCount() > 0 ? writeHistogram.getMaxValue() : 0;
    }

    public List<TimelineDataPoint> getTimelineData() {
        return new ArrayList<>(timelineData);
    }

    public long getStartTime() {
        return startTime;
    }

    /**
     * Timeline data point for periodic snapshots.
     */
    public static class TimelineDataPoint {
        private final double timestampSeconds;
        private final double readTps;
        private final double writeTps;
        private final long readP99Micros;
        private final long writeP99Micros;
        private final double errorRate;

        public TimelineDataPoint(double timestampSeconds, double readTps, double writeTps,
                                 long readP99Micros, long writeP99Micros, double errorRate) {
            this.timestampSeconds = timestampSeconds;
            this.readTps = readTps;
            this.writeTps = writeTps;
            this.readP99Micros = readP99Micros;
            this.writeP99Micros = writeP99Micros;
            this.errorRate = errorRate;
        }

        public double getTimestampSeconds() { return timestampSeconds; }
        public double getReadTps() { return readTps; }
        public double getWriteTps() { return writeTps; }
        public long getReadP99Micros() { return readP99Micros; }
        public long getWriteP99Micros() { return writeP99Micros; }
        public double getErrorRate() { return errorRate; }
    }
}
