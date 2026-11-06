package edu.northeastern.coldstorage.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.northeastern.coldstorage.model.SensorReading;
import java.io.IOException;
import java.io.StringReader;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ColdStorageDataReaderTest {

    private static final int EXPECTED_HIGH_FREQUENCY_ROWS = 10075; // (Count - 1)
    private static final String HEADER =
            "id,room_id,noted_date,temp,humidity,anomaly,location,spoilage_risk\n";

    private ColdStorageDataReader reader;

    @BeforeEach
    void setUp() {
        reader = new ColdStorageDataReader();
    }

    @Test
    void parsesValidRows() throws IOException {
        String csv = HEADER
                + "24,Cold_Storage_C,2024-01-01 23:00:00,3.76,88.17,0,Outside,Moderate Risk\n"
                + "25,Cold_Storage_A,2024-01-02 00:00:00,4.17,79.71,0,Inside,Optimal\n";

        List<SensorReading> readings = reader.read(new StringReader(csv));

        assertEquals(2, readings.size());
        SensorReading first = readings.get(0);
        assertEquals(24, first.getId());
        assertEquals("Cold_Storage_C", first.getRoomId());
        assertEquals(LocalDateTime.of(2024, 1, 1, 23, 0), first.getTimestamp());
        assertEquals(3.76, first.getTemperature(), 1e-9);
        assertEquals(88.17, first.getHumidity(), 1e-9);
        assertEquals(0, reader.getSkippedRowCount());
    }

    @Test
    void skipsBadRowsWithoutCrashing() throws IOException {
        String csv = HEADER
                + "1,Cold_Storage_A,2024-01-01 00:00:00,3.0,80.0,0,Inside,Optimal\n"
                + "2,Cold_Storage_A,not-a-date,3.0,80.0,0,Inside,Optimal\n"
                + "3,Cold_Storage_A,2024-01-01 02:00:00,abc,80.0,0,Inside,Optimal\n"
                + "4,Cold_Storage_A\n"
                + "\n"
                + "5,Cold_Storage_B,2024-01-01 04:00:00,2.5,81.0,0,Inside,Optimal\n";

        List<SensorReading> readings = reader.read(new StringReader(csv));

        assertEquals(2, readings.size());
        assertEquals(3, reader.getSkippedRowCount());
    }

    @Test
    void columnOrderDoesNotMatter() throws IOException {
        String csv = "temp,humidity,room_id,id,noted_date\n"
                + "3.5,85.0,Cold_Storage_B,7,2024-03-01 10:00:00\n";

        SensorReading reading = reader.read(new StringReader(csv)).get(0);

        assertEquals("Cold_Storage_B", reading.getRoomId());
        assertEquals(3.5, reading.getTemperature(), 1e-9);
    }

    //BOM — невидимый символ \uFEFF
    @Test
    void headerWithByteOrderMarkIsRead() throws IOException {
        String csv = "\uFEFF" + "id,room_id,noted_date,temp,humidity\n"
                + "1,Cold_Storage_A,2024-01-01 00:00:00,3.0,80.0\n";

        List<SensorReading> readings = reader.read(new StringReader(csv));

        assertEquals(1, readings.size());
        assertEquals(1, readings.get(0).getId());
        assertEquals(0, reader.getSkippedRowCount());
    }

    @Test
    void valuesWithSurroundingSpacesAreTrimmed() throws IOException {
        String csv = "id, room_id, noted_date, temp, humidity\n"
                + " 7 , Cold_Storage_B , 2024-03-01 10:00:00 , 3.5 , 85.0 \n";

        List<SensorReading> readings = reader.read(new StringReader(csv));

        assertEquals(1, readings.size());
        SensorReading reading = readings.get(0);
        assertEquals(7, reading.getId());
        assertEquals("Cold_Storage_B", reading.getRoomId());
        assertEquals(LocalDateTime.of(2024, 3, 1, 10, 0), reading.getTimestamp());
        assertEquals(3.5, reading.getTemperature(), 1e-9);
        assertEquals(85.0, reading.getHumidity(), 1e-9);
        assertEquals(0, reader.getSkippedRowCount());
    }

    @Test
    void windowsLineEndingsAreHandled() throws IOException {
        String csv = "id,room_id,noted_date,temp,humidity\r\n"
                + "1,Cold_Storage_A,2024-01-01 00:00:00,3.0,80.0\r\n"
                + "2,Cold_Storage_B,2024-01-01 01:00:00,4.5,82.5\r\n";

        List<SensorReading> readings = reader.read(new StringReader(csv));

        assertEquals(2, readings.size());
        assertEquals(0, reader.getSkippedRowCount());
        assertEquals(80.0, readings.get(0).getHumidity(), 1e-9);
        assertEquals(82.5, readings.get(1).getHumidity(), 1e-9);
        assertEquals("Cold_Storage_B", readings.get(1).getRoomId());
    }


    /**
     * Checks that the reader supports the second dataset, cold_storage_high_frequency.csv.
     * It has different extra columns from IoT.csv, but the same five required columns
     * and the same timestamp format, so it must load completely.
     */
    @Test
    void highFrequencyDatasetLoads() throws IOException {
        List<SensorReading> readings = reader.readFromResource("cold_storage_high_frequency.csv");

        assertEquals(EXPECTED_HIGH_FREQUENCY_ROWS, readings.size());
        assertEquals(0, reader.getSkippedRowCount());

        SensorReading first = readings.get(0);
        assertEquals(6, first.getId());
        assertEquals(LocalDateTime.of(2024, 3, 1, 0, 5), first.getTimestamp());
        assertEquals(3.72, first.getTemperature(), 0.01);

        for (SensorReading reading : readings) {
            assertEquals("Cold_Storage_RealTime", reading.getRoomId());
        }
    }


    /**
     * The simulator in Phase 3 will replay this dataset as a live sensor, so the
     * readings must be in time order. This test checks that each reading is exactly
     * one minute after the previous one.
     */
    @Test
    void highFrequencyReadingsAreOneMinuteApart() throws IOException {
        List<SensorReading> readings = reader.readFromResource("cold_storage_high_frequency.csv");

        for (int i = 1; i < readings.size(); i++) {
            LocalDateTime expected = readings.get(i - 1).getTimestamp().plusMinutes(1);
            assertEquals(expected, readings.get(i).getTimestamp(),
                    "reading " + i + " is not one minute after the previous one");
        }
    }

    @Test
    void missingRequiredColumnIsRejected() {
        String csv = "id,room_id,noted_date,humidity\n1,A,2024-01-01 00:00:00,80\n";
        assertThrows(IllegalArgumentException.class, () -> reader.read(new StringReader(csv)));
    }

    @Test
    void emptyInputIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> reader.read(new StringReader("")));
    }

    @Test
    void bundledDatasetLoads() throws IOException {
        List<SensorReading> readings = reader.readFromResource("IoT.csv");
        assertTrue(readings.size() > 4000, "expected the full IoT.csv dataset");
        assertEquals(0, reader.getSkippedRowCount());
    }

    @Test
    void missingResourceGivesClearError() {
        assertThrows(IOException.class, () -> reader.readFromResource("does_not_exist.csv"));
    }
}