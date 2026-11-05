package edu.northeastern.coldstorage.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.northeastern.coldstorage.model.Alert;
import edu.northeastern.coldstorage.model.ComplianceStatus;
import edu.northeastern.coldstorage.model.SensorReading;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AuditLoggerTest {

    private AuditLogger logger;

    @BeforeEach
    void setUp() {
        logger = AuditLogger.getInstance();
        logger.reset();
    }

    @AfterEach
    void tearDown() {
        logger.reset();
    }

    @Test
    void isASingleton() {
        assertSame(AuditLogger.getInstance(), AuditLogger.getInstance());
    }

    @Test
    void entriesAreHashLinked() {
        AuditEntry first = logger.log(AuditLogger.SYSTEM, "start");
        AuditEntry second = logger.log(AuditLogger.SYSTEM, "stop");

        assertEquals(AuditEntry.GENESIS_HASH, first.getPreviousHash());
        assertEquals(first.getHash(), second.getPreviousHash());
        assertTrue(logger.verifyIntegrity());
    }

    @Test
    void editingAnEntryIsDetected() {
        logger.log(AuditLogger.SYSTEM, "start");
        logger.log(AuditLogger.TEMPERATURE_VIOLATION, "Room A at 9.0 C");
        logger.log(AuditLogger.SYSTEM, "stop");

        List<AuditEntry> chain = new ArrayList<>(logger.getEntries());
        AuditEntry original = chain.get(1);
        AuditEntry tampered = new AuditEntry(original.getSequence(), original.getTimestamp(),
                original.getEventType(), "Room A at 3.0 C", original.getPreviousHash(), original.getHash());
        chain.set(1, tampered);

        assertFalse(AuditLogger.verifyChain(chain));
    }

    @Test
    void deletingAnEntryIsDetected() {
        logger.log(AuditLogger.SYSTEM, "a");
        logger.log(AuditLogger.SYSTEM, "b");
        logger.log(AuditLogger.SYSTEM, "c");

        List<AuditEntry> chain = new ArrayList<>(logger.getEntries());
        chain.remove(1);

        assertFalse(AuditLogger.verifyChain(chain));
    }

    @Test
    void alertsAreRecordedAsViolations() {
        SensorReading reading = new SensorReading(1, "Cold_Storage_A",
                LocalDateTime.of(2024, 1, 1, 0, 0), 9.0, 80.0);
        logger.onAlert(new Alert(reading, ComplianceStatus.NON_COMPLIANT));

        AuditEntry entry = logger.getEntries().get(0);
        assertEquals(AuditLogger.TEMPERATURE_VIOLATION, entry.getEventType());
        assertTrue(entry.getDetails().contains("Cold_Storage_A"));
    }

    @Test
    void writesEntriesToFile(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("audit.log");
        logger.writeTo(file);
        logger.log(AuditLogger.SYSTEM, "start");
        logger.log(AuditLogger.SYSTEM, "stop");
        logger.close();

        List<String> lines = Files.readAllLines(file);
        assertEquals(2, lines.size());
        assertTrue(lines.get(0).contains("start"));
    }
}