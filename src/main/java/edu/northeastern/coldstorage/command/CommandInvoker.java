package edu.northeastern.coldstorage.command;

import edu.northeastern.coldstorage.logging.AuditLogger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Runs operator commands and records each one in the audit log (Command pattern "invoker").
 *
 * <p>Both the desktop GUI and the web dashboard send their button clicks here, so every
 * operator action is logged the same way (NIST SP 800-53 AU-2: auditable events).
 */
public class CommandInvoker {

    private final AuditLogger auditLogger;
    private final List<String> history = new ArrayList<>();

    /**
     * Creates an invoker.
     *
     * @param auditLogger where operator actions are recorded
     */
    public CommandInvoker(AuditLogger auditLogger) {
        this.auditLogger = Objects.requireNonNull(auditLogger, "auditLogger must not be null");
    }

    /**
     * Executes a command and audits it.
     *
     * @param operator who performed the action, e.g. "desktop" or "web"
     * @param command  the action to perform
     * @return the command's result message
     */
    public synchronized String execute(String operator, OperatorCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        String result = command.execute();
        String entry = command.describe() + " by " + operator + ": " + result;
        auditLogger.log(AuditLogger.OPERATOR_ACTION, entry);
        history.add(entry);
        return result;
    }

    /** Returns every executed action, oldest first. */
    public synchronized List<String> getHistory() {
        return Collections.unmodifiableList(new ArrayList<>(history));
    }
}
