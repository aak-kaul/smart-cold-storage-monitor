package edu.northeastern.coldstorage.data;

import edu.northeastern.coldstorage.model.SensorReading;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads cold storage readings from the Kaggle "IoT Based Cold Storage Monitoring" CSV format.
 *
 * <p>Columns are found by name, so their order does not matter. Required columns:
 * {@code id, room_id, noted_date, temp, humidity}. Rows that cannot be parsed are
 * skipped and counted instead of crashing the program.
 */
public class ColdStorageDataReader {

    /** Timestamp format used by the dataset, e.g. "2024-01-01 23:00:00". */
    public static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final String[] REQUIRED_COLUMNS = {"id", "room_id", "noted_date", "temp", "humidity"};

    private int skippedRowCount;

    /**
     * Reads a CSV bundled in src/main/resources.
     *
     * @param resourceName name of the file on the classpath, e.g. "IoT.csv"
     * @return the parsed readings in file order
     * @throws IOException if the resource is missing or cannot be read
     */
    public List<SensorReading> readFromResource(String resourceName) throws IOException {
        String path = resourceName.startsWith("/") ? resourceName : "/" + resourceName;
        InputStream in = ColdStorageDataReader.class.getResourceAsStream(path);
        if (in == null) {
            throw new IOException("Dataset not found on classpath: " + resourceName
                    + " (put it in src/main/resources)");
        }
        try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            return read(reader);
        }
    }

    /**
     * Reads a CSV file from disk.
     *
     * @param file path to the CSV file
     * @return the parsed readings in file order
     * @throws IOException if the file cannot be read
     */
    public List<SensorReading> readFromFile(Path file) throws IOException {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return read(reader);
        }
    }

    /**
     * Reads CSV data from any source. The first line must be the header.
     *
     * @param source the CSV text
     * @return the parsed readings in file order
     * @throws IOException              if reading fails
     * @throws IllegalArgumentException if the header is empty or missing a required column
     */
    public List<SensorReading> read(Reader source) throws IOException {
        skippedRowCount = 0;
        BufferedReader reader = source instanceof BufferedReader
                ? (BufferedReader) source : new BufferedReader(source);

        String headerLine = reader.readLine();
        if (headerLine == null || headerLine.isBlank()) {
            throw new IllegalArgumentException("CSV is empty: no header row");
        }
        Map<String, Integer> columns = indexColumns(headerLine);

        List<SensorReading> readings = new ArrayList<>();
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.isBlank()) {
                continue;
            }
            try {
                readings.add(parseRow(line.split(",", -1), columns));
            } catch (RuntimeException e) {
                // Bad or incomplete row: skip it and keep going.
                skippedRowCount++;
            }
        }
        return readings;
    }

    /** Returns how many rows were skipped as invalid during the last read. */
    public int getSkippedRowCount() {
        return skippedRowCount;
    }

    private Map<String, Integer> indexColumns(String headerLine) {
        String[] names = headerLine.replace("\uFEFF", "").split(",", -1);
        Map<String, Integer> columns = new HashMap<>();
        for (int i = 0; i < names.length; i++) {
            columns.put(names[i].trim().toLowerCase(), i);
        }
        for (String required : REQUIRED_COLUMNS) {
            if (!columns.containsKey(required)) {
                throw new IllegalArgumentException("CSV is missing required column: " + required);
            }
        }
        return columns;
    }

    private SensorReading parseRow(String[] fields, Map<String, Integer> columns) {
        int id = Integer.parseInt(field(fields, columns, "id"));
        String roomId = field(fields, columns, "room_id");
        LocalDateTime timestamp = parseTimestamp(field(fields, columns, "noted_date"));
        double temp = Double.parseDouble(field(fields, columns, "temp"));
        double humidity = Double.parseDouble(field(fields, columns, "humidity"));
        return new SensorReading(id, roomId, timestamp, temp, humidity);
    }

    private String field(String[] fields, Map<String, Integer> columns, String name) {
        int index = columns.get(name);
        if (index >= fields.length) {
            throw new IllegalArgumentException("Row is missing column " + name);
        }
        return fields[index].trim();
    }

    private LocalDateTime parseTimestamp(String value) {
        try {
            return LocalDateTime.parse(value, TIMESTAMP_FORMAT);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Bad timestamp: " + value, e);
        }
    }
}