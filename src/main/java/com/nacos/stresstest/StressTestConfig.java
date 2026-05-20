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
                '}';
    }
}
