package org.synanton.equalix.adapter.out.database;

import jakarta.inject.Singleton;
import jakarta.inject.Inject;
import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.synanton.equalix.domain.port.out.AfterCommitPort;

/**
 * {@link AfterCommitPort} over the current Hibernate session: registers the action on the
 * session's action queue (fires only on successful commit, dropped on rollback). With no
 * session or no active transaction, runs the action immediately. A throwing action
 * propagates out of the committing call, same as the oracle's synchronization hook.
 */
@Singleton
public class SessionAfterCommitAdapter implements AfterCommitPort {

    @Inject
    public SessionAfterCommitAdapter(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    private final SessionFactory sessionFactory;

    @Override
    public void afterCommit(Runnable action) {
        Session session;
        try {
            session = sessionFactory.getCurrentSession();
        } catch (HibernateException ex) {
            action.run();
            return;
        }
        boolean active;
        try {
            active = session.getTransaction().isActive();
        } catch (RuntimeException ex) {
            action.run();
            return;
        }
        if (!active) {
            action.run();
            return;
        }
        SessionCallbacks.register(session, action, () -> {
        });
    }
}
