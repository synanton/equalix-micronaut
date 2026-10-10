package org.synanton.equalix.adapter.out.database;

import org.hibernate.engine.spi.SessionImplementor;
import org.hibernate.Session;

/**
 * Registers commit/rollback callbacks on a Hibernate session's action queue (Hibernate 6 SPI).
 *
 * <p>Shared by the transaction-aware CMS buffer and the after-commit port: both need
 * "run this only if the surrounding transaction commits" without a Spring-style
 * synchronization manager, which Micronaut 4 does not provide.
 */
public final class SessionCallbacks {
    private SessionCallbacks() {
    }

    public static void register(Session session, Runnable onCommit, Runnable onRollback) {
        ((SessionImplementor) session).getActionQueue().registerProcess((success, completionSession) -> {
            if (success) {
                onCommit.run();
            } else {
                onRollback.run();
            }
        });
    }
}
