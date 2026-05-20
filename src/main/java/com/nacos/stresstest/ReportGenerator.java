package com.nacos.stresstest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Generates an HTML report with embedded Chart.js visualizations.
 */
public class ReportGenerator {

    private static final Logger logger = LoggerFactory.getLogger(ReportGenerator.class);

    private final StressTestConfig config;
    private final MetricsCollector metrics;

    public ReportGenerator(StressTestConfig config, MetricsCollector metrics) {
        this.config = config;
        this.metrics = metrics;
    }

    /**
     * Generate the HTML report and save to the configured output file.
     */
    public void generate() throws IOException {
        String outputPath = config.getOutputFile();
        logger.info("Generating report: {}", outputPath);

        List<MetricsCollector.TimelineDataPoint> timeline = metrics.getTimelineData();

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n");
        html.append("  <meta charset=\"UTF-8\">\n");
        html.append("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        html.append("  <title>Nacos Stress Test Report</title>\n");
        html.append("  <script src=\"https://cdn.jsdelivr.net/npm/chart.js\"></script>\n");
        html.append("  <style>\n");
        html.append(getStyles());
        html.append("  </style>\n");
        html.append("</head>\n<body>\n");

        // Header
        html.append("  <div class=\"container\">\n");
        html.append("    <h1>Nacos Stress Test Report</h1>\n");
        html.append("    <p class=\"timestamp\">Generated: ")
                .append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                .append("</p>\n\n");

        // Test Configuration Summary
        html.append(generateConfigSummary());

        // Results Summary Table
        html.append(generateResultsSummary());

        // Charts
        html.append("    <h2>Performance Charts</h2>\n");
        html.append("    <div class=\"chart-container\"><canvas id=\"tpsChart\"></canvas></div>\n");
        html.append("    <div class=\"chart-container\"><canvas id=\"latencyChart\"></canvas></div>\n");
        html.append("    <div class=\"chart-container\"><canvas id=\"errorChart\"></canvas></div>\n");

        html.append("  </div>\n\n");

        // JavaScript for charts
        html.append("  <script>\n");
        html.append(generateChartScript(timeline));
        html.append("  </script>\n");

        html.append("</body>\n</html>\n");

        try (FileWriter writer = new FileWriter(outputPath)) {
            writer.write(html.toString());
        }

        logger.info("Report saved to: {}", outputPath);
    }

    private String getStyles() {
        return "    body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; margin: 0; padding: 20px; background: #f5f5f5; }\n" +
                "    .container { max-width: 1200px; margin: 0 auto; background: white; padding: 30px; border-radius: 8px; box-shadow: 0 2px 10px rgba(0,0,0,0.1); }\n" +
                "    h1 { color: #333; border-bottom: 2px solid #007bff; padding-bottom: 10px; }\n" +
                "    h2 { color: #555; margin-top: 30px; }\n" +
                "    .timestamp { color: #888; font-size: 0.9em; }\n" +
                "    table { width: 100%; border-collapse: collapse; margin: 15px 0; }\n" +
                "    th, td { padding: 10px 12px; text-align: left; border-bottom: 1px solid #ddd; }\n" +
                "    th { background: #f8f9fa; font-weight: 600; color: #555; }\n" +
                "    tr:hover { background: #f8f9fa; }\n" +
                "    .chart-container { margin: 20px 0; padding: 15px; background: #fafafa; border-radius: 6px; }\n" +
                "    .config-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }\n" +
                "    .config-item { padding: 8px; background: #f8f9fa; border-radius: 4px; }\n" +
                "    .config-label { font-weight: 600; color: #555; }\n" +
                "    .config-value { color: #333; }\n" +
                "    .metric-good { color: #28a745; }\n" +
                "    .metric-warn { color: #ffc107; }\n" +
                "    .metric-bad { color: #dc3545; }\n";
    }

    private String generateConfigSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("    <h2>Test Configuration</h2>\n");
        sb.append("    <div class=\"config-grid\">\n");
        sb.append(configItem("Server Address", config.getServerAddr()));
        sb.append(configItem("Namespace", config.getNamespace()));
        sb.append(configItem("Group", config.getGroup()));
        sb.append(configItem("Total Configs", String.valueOf(config.getTotalConfigs())));
        sb.append(configItem("Concurrent Threads", String.valueOf(config.getConcurrentThreads())));
        sb.append(configItem("Duration", config.getDurationSeconds() + "s"));
        sb.append(configItem("Read Ratio", config.getReadRatio() + "%"));
        sb.append(configItem("Write Ratio", (100 - config.getReadRatio()) + "%"));
        sb.append(configItem("Warmup", config.getWarmupSeconds() + "s"));
        sb.append(configItem("Content Size", config.getConfigContentSize() + " bytes"));
        sb.append(configItem("Ramp-up", config.getRampUpSeconds() + "s"));
        sb.append(configItem("Report Interval", config.getReportIntervalSeconds() + "s"));
        sb.append("    </div>\n\n");
        return sb.toString();
    }

    private String configItem(String label, String value) {
        return "      <div class=\"config-item\"><span class=\"config-label\">" + label +
                ":</span> <span class=\"config-value\">" + value + "</span></div>\n";
    }

    private String generateResultsSummary() {
        long totalOps = metrics.getTotalOps();
        long totalErrors = metrics.getTotalErrors();
        double overallErrorRate = totalOps > 0 ? (double) totalErrors / totalOps * 100.0 : 0;

        StringBuilder sb = new StringBuilder();
        sb.append("    <h2>Results Summary</h2>\n");
        sb.append("    <table>\n");
        sb.append("      <tr><th>Metric</th><th>Read</th><th>Write</th><th>Total</th></tr>\n");
        sb.append(String.format("      <tr><td>Success Count</td><td>%,d</td><td>%,d</td><td>%,d</td></tr>\n",
                metrics.getReadSuccessCount(), metrics.getWriteSuccessCount(),
                metrics.getReadSuccessCount() + metrics.getWriteSuccessCount()));
        sb.append(String.format("      <tr><td>Failure Count</td><td>%,d</td><td>%,d</td><td>%,d</td></tr>\n",
                metrics.getReadFailureCount(), metrics.getWriteFailureCount(), totalErrors));
        sb.append(String.format("      <tr><td>Error Rate</td><td colspan=\"3\">%.2f%%</td></tr>\n", overallErrorRate));
        sb.append("    </table>\n\n");

        // Latency table
        sb.append("    <h3>Latency Summary (milliseconds)</h3>\n");
        sb.append("    <table>\n");
        sb.append("      <tr><th>Percentile</th><th>Read (ms)</th><th>Write (ms)</th></tr>\n");
        sb.append(String.format("      <tr><td>Average</td><td>%.2f</td><td>%.2f</td></tr>\n",
                metrics.getReadMeanLatencyMicros() / 1000.0, metrics.getWriteMeanLatencyMicros() / 1000.0));
        sb.append(String.format("      <tr><td>P50</td><td>%.2f</td><td>%.2f</td></tr>\n",
                metrics.getReadP50Micros() / 1000.0, metrics.getWriteP50Micros() / 1000.0));
        sb.append(String.format("      <tr><td>P90</td><td>%.2f</td><td>%.2f</td></tr>\n",
                metrics.getReadP90Micros() / 1000.0, metrics.getWriteP90Micros() / 1000.0));
        sb.append(String.format("      <tr><td>P95</td><td>%.2f</td><td>%.2f</td></tr>\n",
                metrics.getReadP95Micros() / 1000.0, metrics.getWriteP95Micros() / 1000.0));
        sb.append(String.format("      <tr><td>P99</td><td>%.2f</td><td>%.2f</td></tr>\n",
                metrics.getReadP99Micros() / 1000.0, metrics.getWriteP99Micros() / 1000.0));
        sb.append(String.format("      <tr><td>Max</td><td>%.2f</td><td>%.2f</td></tr>\n",
                metrics.getReadMaxMicros() / 1000.0, metrics.getWriteMaxMicros() / 1000.0));
        sb.append("    </table>\n\n");

        return sb.toString();
    }

    private String generateChartScript(List<MetricsCollector.TimelineDataPoint> timeline) {
        String labels = timeline.stream()
                .map(dp -> String.format("%.1f", dp.getTimestampSeconds()))
                .collect(Collectors.joining("','", "['", "']"));

        String readTpsData = timeline.stream()
                .map(dp -> String.format("%.1f", dp.getReadTps()))
                .collect(Collectors.joining(",", "[", "]"));

        String writeTpsData = timeline.stream()
                .map(dp -> String.format("%.1f", dp.getWriteTps()))
                .collect(Collectors.joining(",", "[", "]"));

        String readP99Data = timeline.stream()
                .map(dp -> String.format("%.2f", dp.getReadP99Micros() / 1000.0))
                .collect(Collectors.joining(",", "[", "]"));

        String writeP99Data = timeline.stream()
                .map(dp -> String.format("%.2f", dp.getWriteP99Micros() / 1000.0))
                .collect(Collectors.joining(",", "[", "]"));

        String errorRateData = timeline.stream()
                .map(dp -> String.format("%.2f", dp.getErrorRate()))
                .collect(Collectors.joining(",", "[", "]"));

        StringBuilder js = new StringBuilder();

        // TPS Chart
        js.append("    // TPS Over Time Chart\n");
        js.append("    new Chart(document.getElementById('tpsChart'), {\n");
        js.append("      type: 'line',\n");
        js.append("      data: {\n");
        js.append("        labels: ").append(labels).append(",\n");
        js.append("        datasets: [{\n");
        js.append("          label: 'Read TPS',\n");
        js.append("          data: ").append(readTpsData).append(",\n");
        js.append("          borderColor: '#007bff',\n");
        js.append("          backgroundColor: 'rgba(0,123,255,0.1)',\n");
        js.append("          fill: true,\n");
        js.append("          tension: 0.3\n");
        js.append("        }, {\n");
        js.append("          label: 'Write TPS',\n");
        js.append("          data: ").append(writeTpsData).append(",\n");
        js.append("          borderColor: '#28a745',\n");
        js.append("          backgroundColor: 'rgba(40,167,69,0.1)',\n");
        js.append("          fill: true,\n");
        js.append("          tension: 0.3\n");
        js.append("        }]\n");
        js.append("      },\n");
        js.append("      options: {\n");
        js.append("        responsive: true,\n");
        js.append("        plugins: { title: { display: true, text: 'Throughput (TPS) Over Time' } },\n");
        js.append("        scales: { x: { title: { display: true, text: 'Time (seconds)' } }, y: { title: { display: true, text: 'Operations/sec' }, beginAtZero: true } }\n");
        js.append("      }\n");
        js.append("    });\n\n");

        // Latency Chart
        js.append("    // Latency Over Time Chart\n");
        js.append("    new Chart(document.getElementById('latencyChart'), {\n");
        js.append("      type: 'line',\n");
        js.append("      data: {\n");
        js.append("        labels: ").append(labels).append(",\n");
        js.append("        datasets: [{\n");
        js.append("          label: 'Read P99 (ms)',\n");
        js.append("          data: ").append(readP99Data).append(",\n");
        js.append("          borderColor: '#007bff',\n");
        js.append("          tension: 0.3\n");
        js.append("        }, {\n");
        js.append("          label: 'Write P99 (ms)',\n");
        js.append("          data: ").append(writeP99Data).append(",\n");
        js.append("          borderColor: '#28a745',\n");
        js.append("          tension: 0.3\n");
        js.append("        }]\n");
        js.append("      },\n");
        js.append("      options: {\n");
        js.append("        responsive: true,\n");
        js.append("        plugins: { title: { display: true, text: 'P99 Latency Over Time' } },\n");
        js.append("        scales: { x: { title: { display: true, text: 'Time (seconds)' } }, y: { title: { display: true, text: 'Latency (ms)' }, beginAtZero: true } }\n");
        js.append("      }\n");
        js.append("    });\n\n");

        // Error Rate Chart
        js.append("    // Error Rate Over Time Chart\n");
        js.append("    new Chart(document.getElementById('errorChart'), {\n");
        js.append("      type: 'line',\n");
        js.append("      data: {\n");
        js.append("        labels: ").append(labels).append(",\n");
        js.append("        datasets: [{\n");
        js.append("          label: 'Error Rate (%)',\n");
        js.append("          data: ").append(errorRateData).append(",\n");
        js.append("          borderColor: '#dc3545',\n");
        js.append("          backgroundColor: 'rgba(220,53,69,0.1)',\n");
        js.append("          fill: true,\n");
        js.append("          tension: 0.3\n");
        js.append("        }]\n");
        js.append("      },\n");
        js.append("      options: {\n");
        js.append("        responsive: true,\n");
        js.append("        plugins: { title: { display: true, text: 'Error Rate Over Time' } },\n");
        js.append("        scales: { x: { title: { display: true, text: 'Time (seconds)' } }, y: { title: { display: true, text: 'Error Rate (%)' }, beginAtZero: true, max: 100 } }\n");
        js.append("      }\n");
        js.append("    });\n");

        return js.toString();
    }
}
