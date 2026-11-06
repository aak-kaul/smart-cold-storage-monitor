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