package edu.northeastern.coldstorage.logging;

import edu.northeastern.coldstorage.controller.AlertListener;
import edu.northeastern.coldstorage.model.Alert;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The system's single, tamper-evident audit log (Singleton pattern).
 *
 * <p>Every entry is linked to the previous one with a SHA-256 hash.
 * It also listens for alerts (Observer pattern), so every unsafe reading
 * is recorded automatically.
 */
public final class AuditLogger implements AlertListener {

    /** Event type for an unsafe reading. */
    public static final String TEMPERATURE_VIOLATION = "TEMPERATURE_VIOLATION";
    /** Event type for system lifecycle events (start, stop, data loaded). */
    public static final String SYSTEM = "SYSTEM";

    private static AuditLogger instance;

    private final List<AuditEntry> entries = new ArrayList<>();
    private BufferedWriter fileWriter;

    private AuditLogger() {
        // Private: use getInstance().
    }

    /** Returns the one shared logger, creating it on first use. */
    public static synchronized AuditLogger getInstance() {
        if (instance == null) {
            instance = new AuditLogger();
        }
        return instance;
    }

    /**
     * Also writes entries to a file from now on (the file is overwritten).
     *
     * @param file the log file, e.g. logs/audit.log
     * @throws UncheckedIOException if the file cannot be created
     */
    public synchronized void writeTo(Path file) {
        close();
        try {
            Path parent = file.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            fileWriter = Files.newBufferedWriter(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not open audit log " + file, e);
        }
    }

    /**
     * Appends an event to the log.
     *
     * @param eventType the kind of event, e.g. {@link #TEMPERATURE_VIOLATION}
     * @param details   a description of what happened
     * @return the new entry
     */
    public synchronized AuditEntry log(String eventType, String details) {
        String previousHash = entries.isEmpty()
                ? AuditEntry.GENESIS_HASH : entries.get(entries.size() - 1).getHash();
        AuditEntry entry = new AuditEntry(entries.size() + 1L, LocalDateTime.now(),
                eventType, details, previousHash);
        entries.add(entry);
        writeLine(entry);
        return entry;
    }

    /** Records every alert it is notified about. */
    @Override
    public void onAlert(Alert alert) {
        log(TEMPERATURE_VIOLATION, alert.getMessage());
    }

    /** Returns a read-only view of all entries, oldest first. */
    public synchronized List<AuditEntry> getEntries() {
        return Collections.unmodifiableList(new ArrayList<>(entries));
    }

    /** Returns true if no entry in this log has been altered. */
    public synchronized boolean verifyIntegrity() {
        return verifyChain(entries);
    }

    /**
     * Checks a sequence of entries: every hash must match its content and
     * every entry must point to the hash of the one before it.
     *
     * @param chain entries, oldest first
     * @return true if the chain is intact
     */
    public static boolean verifyChain(List<AuditEntry> chain) {
        String expectedPrevious = AuditEntry.GENESIS_HASH;
        for (AuditEntry entry : chain) {
            if (!entry.getPreviousHash().equals(expectedPrevious) || !entry.hasValidHash()) {
                return false;
            }
            expectedPrevious = entry.getHash();
        }
        return true;
    }

    /** Flushes and closes the log file, if one is open. */
    public synchronized void close() {
        if (fileWriter != null) {
            try {
                fileWriter.close();
            } catch (IOException e) {
                throw new UncheckedIOException("Could not close audit log", e);
            } finally {
                fileWriter = null;
            }
        }
    }

    /** Removes all entries and closes the file. Intended for tests only. */
    synchronized void reset() {
        close();
        entries.clear();
    }

    private void writeLine(AuditEntry entry) {
        if (fileWriter == null) {
            return;
        }
        try {
            fileWriter.write(entry.toString());
            fileWriter.newLine();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write audit log", e);
        }
    }
}