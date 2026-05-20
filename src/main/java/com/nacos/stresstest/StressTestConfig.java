package com.nacos.stresstest;

/**
 * Configuration POJO holding all stress test parameters.
 */
public class StressTestConfig {

    private String serverAddr = "127.0.0.1:8848";
    private String namespace = "public";
    private String username = "nacos";
    private String password = "nacos";
    private String group = "STRESS_TEST_GROUP";
    private String dataIdPrefix = "stress-test-config-";
    private int totalConfigs = 100;
    private int concurrentThreads = 10;
    private int durationSeconds = 60;
    private int readRatio = 70;
    private int warmupSeconds = 5;
    private int configContentSize = 1024;
    private int rampUpSeconds = 0;
    private int reportIntervalSeconds = 5;
    private String outputFile = "stress-test-report.html";

    // --- New parameters ---
    private int clientPoolSize = 50;
    private long thinkTimeMs = 100;
    private long maxThinkTimeMs = 500;
    private boolean jitterEnabled = true;
    private boolean uniqueNamespacePerClient = false;
    private int connectionTimeoutMs = 5000;
    private int requestTimeoutMs = 3000;

    public String getServerAddr() {
        return serverAddr;
    }

    public void setServerAddr(String serverAddr) {
        this.serverAddr = serverAddr;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    public String getDataIdPrefix() {
        return dataIdPrefix;
    }

    public void setDataIdPrefix(String dataIdPrefix) {
        this.dataIdPrefix = dataIdPrefix;
    }

    public int getTotalConfigs() {
        return totalConfigs;
    }

    public void setTotalConfigs(int totalConfigs) {
        this.totalConfigs = totalConfigs;
    }

    public int getConcurrentThreads() {
        return concurrentThreads;
    }

    public void setConcurrentThreads(int concurrentThreads) {
        this.concurrentThreads = concurrentThreads;
    }

    public int getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(int durationSeconds) {
        this.durationSeconds = durationSeconds;
    }

    public int getReadRatio() {
        return readRatio;
    }

    public void setReadRatio(int readRatio) {
        this.readRatio = readRatio;
    }

    public int getWarmupSeconds() {
        return warmupSeconds;
    }

    public void setWarmupSeconds(int warmupSeconds) {
        this.warmupSeconds = warmupSeconds;
    }

    public int getConfigContentSize() {
        return configContentSize;
    }

    public void setConfigContentSize(int configContentSize) {
        this.configContentSize = configContentSize;
    }

    public int getRampUpSeconds() {
        return rampUpSeconds;
    }

    public void setRampUpSeconds(int rampUpSeconds) {
        this.rampUpSeconds = rampUpSeconds;
    }

    public int getReportIntervalSeconds() {
        return reportIntervalSeconds;
    }

    public void setReportIntervalSeconds(int reportIntervalSeconds) {
        this.reportIntervalSeconds = reportIntervalSeconds;
    }

    public int getClientPoolSize() {
        return clientPoolSize;
    }

    public void setClientPoolSize(int clientPoolSize) {
        this.clientPoolSize = clientPoolSize;
    }

    public long getThinkTimeMs() {
        return thinkTimeMs;
    }

    public void setThinkTimeMs(long thinkTimeMs) {
        this.thinkTimeMs = thinkTimeMs;
    }

    public long getMaxThinkTimeMs() {
        return maxThinkTimeMs;
    }

    public void setMaxThinkTimeMs(long maxThinkTimeMs) {
        this.maxThinkTimeMs = maxThinkTimeMs;
    }

    public boolean isJitterEnabled() {
        return jitterEnabled;
    }

    public void setJitterEnabled(boolean jitterEnabled) {
        this.jitterEnabled = jitterEnabled;
    }

    public boolean isUniqueNamespacePerClient() {
        return uniqueNamespacePerClient;
    }

    public void setUniqueNamespacePerClient(boolean uniqueNamespacePerClient) {
        this.uniqueNamespacePerClient = uniqueNamespacePerClient;
    }

    public int getConnectionTimeoutMs() {
        return connectionTimeoutMs;
    }

    public void setConnectionTimeoutMs(int connectionTimeoutMs) {
        this.connectionTimeoutMs = connectionTimeoutMs;
    }

    public int getRequestTimeoutMs() {
        return requestTimeoutMs;
    }

    public void setRequestTimeoutMs(int requestTimeoutMs) {
        this.requestTimeoutMs = requestTimeoutMs;
    }

    public String getOutputFile() {
        return outputFile;
    }

    public void setOutputFile(String outputFile) {
        this.outputFile = outputFile;
    }

    @Override
    public String toString() {
        return "StressTestConfig{" +
                "serverAddr='" + serverAddr + '\'' +
                ", namespace='" + namespace + '\'' +
                ", username='" + username + '\'' +
                ", group='" + group + '\'' +
                ", dataIdPrefix='" + dataIdPrefix + '\'' +
                ", totalConfigs=" + totalConfigs +
                ", concurrentThreads=" + concurrentThreads +
                ", durationSeconds=" + durationSeconds +
                ", readRatio=" + readRatio +
                ", warmupSeconds=" + warmupSeconds +
                ", configContentSize=" + configContentSize +
                ", rampUpSeconds=" + rampUpSeconds +
                ", reportIntervalSeconds=" + reportIntervalSeconds +
                ", outputFile='" + outputFile + '\'' +
                ", clientPoolSize=" + clientPoolSize +
                ", thinkTimeMs=" + thinkTimeMs +
                ", maxThinkTimeMs=" + maxThinkTimeMs +
                ", jitterEnabled=" + jitterEnabled +
                ", uniqueNamespacePerClient=" + uniqueNamespacePerClient +
                ", connectionTimeoutMs=" + connectionTimeoutMs +
                ", requestTimeoutMs=" + requestTimeoutMs +
                '}';
    }
}
