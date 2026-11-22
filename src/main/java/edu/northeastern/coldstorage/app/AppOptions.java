package edu.northeastern.coldstorage.app;

import edu.northeastern.coldstorage.ml.PredictionModel;
import edu.northeastern.coldstorage.sensor.SensorType;
import edu.northeastern.coldstorage.view.web.DashboardServer;
import java.util.Locale;
import java.util.Objects;

/**
 * Command-line options, for example {@code --mode=web --interval=50}.
 * Every option has a sensible default, so running with no options works.
 */
public final class AppOptions {

    /** How the program runs. */
    public enum Mode {
        /** Process the whole dataset at once and print a summary (fast). */
        BATCH,
        /** Replay the dataset through live sensor threads, printing alerts as they happen. */
        SIMULATE,
        /** Live simulation plus the web dashboard at http://localhost:8080. */
        WEB,
        /** Live simulation plus the desktop (Swing) dashboard window. */
        GUI
    }

    public static final String DEFAULT_DATA = "IoT.csv";
    public static final int DEFAULT_ALERTS_TO_PRINT = 15;
    /** Default time between two readings of the same live sensor. */
    public static final long DEFAULT_INTERVAL_MILLIS = 100;

    private Mode mode = Mode.BATCH;
    private String data = DEFAULT_DATA;
    private long intervalMillis = DEFAULT_INTERVAL_MILLIS;
    private SensorType sensorType = SensorType.CSV_REPLAY;
    private PredictionModel predictionModel = PredictionModel.MOVING_AVERAGE;
    private int port = DashboardServer.DEFAULT_PORT;
    private int alertsToPrint = DEFAULT_ALERTS_TO_PRINT;
    private boolean help;

    private AppOptions() {
    }

    /** Returns the default options (batch mode on IoT.csv). */
    public static AppOptions defaults() {
        return new AppOptions();
    }

    /**
     * Parses command-line arguments of the form {@code --name=value}.
     *
     * @throws IllegalArgumentException with a helpful message if an argument is wrong
     */
    public static AppOptions parse(String[] args) {
        Objects.requireNonNull(args, "args must not be null");
        AppOptions options = new AppOptions();
        for (String arg : args) {
            if (arg.equals("--help") || arg.equals("-h")) {
                options.help = true;
                continue;
            }
            if (!arg.startsWith("--") || !arg.contains("=")) {
                throw new IllegalArgumentException("Unrecognised argument: " + arg);
            }
            String name = arg.substring(2, arg.indexOf('='));
            String value = arg.substring(arg.indexOf('=') + 1).trim();
            if (value.isEmpty()) {
                throw new IllegalArgumentException("Missing value for --" + name);
            }
            switch (name) {
                case "mode":
                    options.mode = parseMode(value);
                    break;
                case "data":
                    options.data = value;
                    break;
                case "interval":
                    options.intervalMillis = parsePositive(name, value);
                    break;
                case "sensor":
                    options.sensorType = parseSensorType(value);
                    break;
                case "predictor":
                    options.predictionModel = PredictionModel.fromName(value);
                    break;
                case "port":
                    options.port = (int) parseRange(name, value, 1, 65535);
                    break;
                case "alerts":
                    options.alertsToPrint = (int) parseRange(name, value, 0, Integer.MAX_VALUE);
                    break;
                default:
                    throw new IllegalArgumentException("Unknown option: --" + name);
            }
        }
        return options;
    }

    /** Help text printed for {@code --help} or a wrong argument. */
    public static String usage() {
        return String.join(System.lineSeparator(),
                "Usage: Main [--mode=batch|simulate|web|gui] [--data=FILE] [--interval=MS]",
                "            [--sensor=csv-replay|spike] [--predictor=moving-average|linear]",
                "            [--port=PORT] [--alerts=N]",
                "",
                "  --mode       batch    : process the dataset at once and print a summary (default)",
                "               simulate : replay readings live through sensor threads",
                "               web      : live simulation + web dashboard (http://localhost:PORT)",
                "               gui      : live simulation + desktop dashboard window",
                "  --data       CSV file path, or a bundled dataset name (default IoT.csv;",
                "               also bundled: cold_storage_high_frequency.csv)",
                "  --interval   milliseconds between readings of each sensor (default "
                        + DEFAULT_INTERVAL_MILLIS + ")",
                "  --sensor     csv-replay: replay the data as recorded (default)",
                "               spike     : also add a +3.0 C spike to every 10th reading (alert demo)",
                "  --predictor  prediction model (default moving-average)",
                "  --port       web dashboard port (default " + DashboardServer.DEFAULT_PORT + ")",
                "  --alerts     how many alerts to print on the console (default " + DEFAULT_ALERTS_TO_PRINT + ")");
    }

    private static Mode parseMode(String value) {
        try {
            return Mode.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown mode: " + value + " (use batch, simulate, web or gui)", e);
        }
    }

    private static SensorType parseSensorType(String value) {
        switch (value.toLowerCase(Locale.ROOT)) {
            case "csv-replay":
            case "csv":
                return SensorType.CSV_REPLAY;
            case "spike":
                return SensorType.SPIKE;
            default:
                throw new IllegalArgumentException("Unknown sensor type: " + value + " (use csv-replay or spike)");
        }
    }

    private static long parsePositive(String name, String value) {
        return parseRange(name, value, 1, Long.MAX_VALUE);
    }

    private static long parseRange(String name, String value, long min, long max) {
        long number;
        try {
            number = Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("--" + name + " must be a whole number, got: " + value, e);
        }
        if (number < min || number > max) {
            throw new IllegalArgumentException("--" + name + " must be between " + min + " and " + max);
        }
        return number;
    }

    public Mode getMode() {
        return mode;
    }

    public String getData() {
        return data;
    }

    public long getIntervalMillis() {
        return intervalMillis;
    }

    public SensorType getSensorType() {
        return sensorType;
    }

    public PredictionModel getPredictionModel() {
        return predictionModel;
    }

    public int getPort() {
        return port;
    }

    public int getAlertsToPrint() {
        return alertsToPrint;
    }

    public boolean isHelp() {
        return help;
    }
}
