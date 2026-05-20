package com.nacos.stresstest;

import org.apache.commons.cli.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entry point for the Nacos stress testing application.
 * Parses CLI arguments, configures the test, and orchestrates execution.
 */
public class NacosStressTestMain {

    private static final Logger logger = LoggerFactory.getLogger(NacosStressTestMain.class);

    public static void main(String[] args) {
        StressTestConfig config = parseArgs(args);
        if (config == null) {
            System.exit(1);
            return;
        }

        logger.info("=== Nacos Stress Test ===");
        logger.info("Configuration: {}", config);

        MetricsCollector metrics = new MetricsCollector();
        StressTestRunner runner = new StressTestRunner(config, metrics);

        // Register shutdown hook for graceful termination
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutdown signal received");
            runner.shutdown();
        }, "shutdown-hook"));

        try {
            // Initialize connection
            runner.initialize();

            // Pre-populate configs
            runner.setupConfigs();

            // Run the stress test
            runner.run();

            // Generate report
            logger.info("=== Test Complete ===");
            logger.info("Total operations: {}", metrics.getTotalOps());
            logger.info("Total errors: {}", metrics.getTotalErrors());

            ReportGenerator reportGenerator = new ReportGenerator(config, metrics);
            reportGenerator.generate();

            logger.info("Stress test completed successfully");

        } catch (Exception e) {
            logger.error("Stress test failed: {}", e.getMessage(), e);
            runner.shutdown();
            System.exit(1);
        }
    }

    /**
     * Parse command-line arguments into a StressTestConfig.
     */
    private static StressTestConfig parseArgs(String[] args) {
        Options options = buildOptions();
        CommandLineParser parser = new DefaultParser();

        try {
            CommandLine cmd = parser.parse(options, args);

            if (cmd.hasOption("h")) {
                printHelp(options);
                System.exit(0);
                return null;
            }

            StressTestConfig config = new StressTestConfig();

            if (cmd.hasOption("s")) {
                config.setServerAddr(cmd.getOptionValue("s"));
            }
            if (cmd.hasOption("n")) {
                config.setNamespace(cmd.getOptionValue("n"));
            }
            if (cmd.hasOption("u")) {
                config.setUsername(cmd.getOptionValue("u"));
            }
            if (cmd.hasOption("p")) {
                config.setPassword(cmd.getOptionValue("p"));
            }
            if (cmd.hasOption("g")) {
                config.setGroup(cmd.getOptionValue("g"));
            }
            if (cmd.hasOption("c")) {
                config.setTotalConfigs(Integer.parseInt(cmd.getOptionValue("c")));
            }
            if (cmd.hasOption("t")) {
                config.setConcurrentThreads(Integer.parseInt(cmd.getOptionValue("t")));
            }
            if (cmd.hasOption("d")) {
                config.setDurationSeconds(Integer.parseInt(cmd.getOptionValue("d")));
            }
            if (cmd.hasOption("r")) {
                int ratio = Integer.parseInt(cmd.getOptionValue("r"));
                if (ratio < 0 || ratio > 100) {
                    logger.error("Read ratio must be between 0 and 100");
                    return null;
                }
                config.setReadRatio(ratio);
            }
            if (cmd.hasOption("w")) {
                config.setWarmupSeconds(Integer.parseInt(cmd.getOptionValue("w")));
            }
            if (cmd.hasOption("content-size")) {
                config.setConfigContentSize(Integer.parseInt(cmd.getOptionValue("content-size")));
            }
            if (cmd.hasOption("ramp-up")) {
                config.setRampUpSeconds(Integer.parseInt(cmd.getOptionValue("ramp-up")));
            }
            if (cmd.hasOption("report-interval")) {
                config.setReportIntervalSeconds(Integer.parseInt(cmd.getOptionValue("report-interval")));
            }
            if (cmd.hasOption("o")) {
                config.setOutputFile(cmd.getOptionValue("o"));
            }

            return config;

        } catch (ParseException e) {
            logger.error("Failed to parse arguments: {}", e.getMessage());
            printHelp(options);
            return null;
        } catch (NumberFormatException e) {
            logger.error("Invalid number format: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Build CLI options.
     */
    private static Options buildOptions() {
        Options options = new Options();

        options.addOption(Option.builder("s").longOpt("server").hasArg()
                .desc("Nacos server address (default: 127.0.0.1:8848)").build());
        options.addOption(Option.builder("n").longOpt("namespace").hasArg()
                .desc("Namespace (default: public)").build());
        options.addOption(Option.builder("u").longOpt("username").hasArg()
                .desc("Username (default: nacos)").build());
        options.addOption(Option.builder("p").longOpt("password").hasArg()
                .desc("Password (default: nacos)").build());
        options.addOption(Option.builder("g").longOpt("group").hasArg()
                .desc("Config group (default: STRESS_TEST_GROUP)").build());
        options.addOption(Option.builder("c").longOpt("configs").hasArg()
                .desc("Number of config items (default: 100)").build());
        options.addOption(Option.builder("t").longOpt("threads").hasArg()
                .desc("Concurrent threads (default: 10)").build());
        options.addOption(Option.builder("d").longOpt("duration").hasArg()
                .desc("Test duration in seconds (default: 60)").build());
        options.addOption(Option.builder("r").longOpt("read-ratio").hasArg()
                .desc("Read ratio percentage 0-100 (default: 70)").build());
        options.addOption(Option.builder("w").longOpt("warmup").hasArg()
                .desc("Warmup seconds (default: 5)").build());
        options.addOption(Option.builder().longOpt("content-size").hasArg()
                .desc("Config content size in bytes (default: 1024)").build());
        options.addOption(Option.builder().longOpt("ramp-up").hasArg()
                .desc("Ramp-up time in seconds (default: 0)").build());
        options.addOption(Option.builder().longOpt("report-interval").hasArg()
                .desc("Report interval in seconds (default: 5)").build());
        options.addOption(Option.builder("o").longOpt("output").hasArg()
                .desc("Output report file path (default: stress-test-report.html)").build());
        options.addOption(Option.builder("h").longOpt("help")
                .desc("Print this help message").build());

        return options;
    }

    /**
     * Print help message.
     */
    private static void printHelp(Options options) {
        HelpFormatter formatter = new HelpFormatter();
        formatter.setWidth(100);
        formatter.printHelp("nacos-stress-test", "\nNacos Configuration Service Stress Testing Tool\n\n", options,
                "\nExample: nacos-stress-test -s 192.168.1.100:8848 -t 20 -d 120 -r 80\n", true);
    }
}
