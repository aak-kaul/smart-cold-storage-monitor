package edu.northeastern.coldstorage.logging;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Objects;

/**
 * One immutable, hash-linked entry in the audit log.
 *
 * <p>Each entry stores the hash of the entry before it. Changing any past entry
 * changes its hash and breaks the chain, which makes tampering detectable
 * (in line with NIST SP 800-53 AU-9, "Protection of Audit Information").
 */
public final class AuditEntry {

    /** Hash used as the "previous hash" of the very first entry. */
    public static final String GENESIS_HASH = "0".repeat(64);

    private final long sequence;
    private final LocalDateTime timestamp;
    private final String eventType;
    private final String details;
    private final String previousHash;
    private final String hash;

    /** Creates an entry and computes its hash. */
    AuditEntry(long sequence, LocalDateTime timestamp, String eventType, String details,
               String previousHash) {
        this(sequence, timestamp, eventType, details, previousHash,
                computeHash(sequence, timestamp, eventType, details, previousHash));
    }

    /** Creates an entry with a given hash (used when loading or when testing tampering). */
    AuditEntry(long sequence, LocalDateTime timestamp, String eventType, String details,
               String previousHash, String hash) {
        this.sequence = sequence;
        this.timestamp = Objects.requireNonNull(timestamp);
        this.eventType = Objects.requireNonNull(eventType);
        this.details = Objects.requireNonNull(details);
        this.previousHash = Objects.requireNonNull(previousHash);
        this.hash = Objects.requireNonNull(hash);
    }

    /** Returns true if this entry's stored hash matches its contents. */
    public boolean hasValidHash() {
        return hash.equals(computeHash(sequence, timestamp, eventType, details, previousHash));
    }

    static String computeHash(long sequence, LocalDateTime timestamp, String eventType,
                              String details, String previousHash) {
        String content = sequence + "|" + timestamp + "|" + eventType + "|" + details + "|" + previousHash;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    public long getSequence() {
        return sequence;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public String getEventType() {
        return eventType;
    }

    public String getDetails() {
        return details;
    }

    public String getPreviousHash() {
        return previousHash;
    }

    public String getHash() {
        return hash;
    }

    /** Formats the entry as one line of the log file. */
    @Override
    public String toString() {
        return String.format("#%d | %s | %s | %s | prev=%s | hash=%s",
                sequence, timestamp, eventType, details, previousHash.substring(0, 12),
                hash.substring(0, 12));
    }
}