package edu.northeastern.coldstorage.command;

/**
 * An action an operator can take, wrapped as an object (Command pattern).
 *
 * <p>Turning each action into an object lets the {@link CommandInvoker} run every action
 * the same way and record it in the audit log, no matter which screen (desktop GUI or
 * web dashboard) the operator used.
 */
public interface OperatorCommand {

    /**
     * Performs the action.
     *
     * @return a short result message for the operator, e.g. "Acknowledged 3 alerts"
     */
    String execute();

    /** Describes the action for the audit log, e.g. "Silence alarm". */
    String describe();
}
