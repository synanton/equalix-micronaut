package org.synanton.equalix.domain.port.out;

/**
 * Defers work until the surrounding database transaction commits.
 *
 * <p>Dispatchers buffer executor sends behind this port so a rolled-back tick never sent
 * anything the database never dispatched. Outside a transaction the action runs
 * immediately (same fallback as the transaction-aware CMS buffer).
 */
public interface AfterCommitPort {

    void afterCommit(Runnable action);
}
