package org.synanton.equalix.adapter.out.cms;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.synanton.equalix.adapter.out.database.SessionCallbacks;
import org.synanton.equalix.domain.port.out.CMSProviderPort;

/**
 * Makes CMS updates follow the outcome of the surrounding database transaction.
 *
 * <p>Inside a transaction, {@link #add} only buffers the delta. The buffered net deltas are applied after commit
 * and discarded on rollback, so a rolled-back dispatch no longer leaves a phantom +1 and a rolled-back completion
 * that is retried no longer subtracts twice (invariants §20, EQX-2). Outside a transaction, deltas apply
 * immediately.
 *
 * <p>Reads and {@link #rebuild} go straight to the sketch; a transaction does not see its own buffered deltas,
 * which no caller relies on.
 *
 * <p>Micronaut port: the Spring oracle hooks {@code TransactionSynchronizationManager}; Micronaut 4 exposes no
 * equivalent manager, so the buffer lives on the current Hibernate session instead — one
 * {@code AfterTransactionCompletionProcess} per session applies the net deltas on successful commit and drops
 * them on rollback. Buffers are keyed by session identity in a {@link WeakHashMap} so a {@code REQUIRES_NEW}
 * inner transaction (separate session) keeps a separate buffer, same as the oracle.
 */
@Slf4j
public class TransactionAwareCmsProvider implements CMSProviderPort {

    private final CMSProviderPort delegate;
    private final SessionFactory sessionFactory;

    private final Map<Session, Map<String, Long>> buffers =
        Collections.synchronizedMap(new WeakHashMap<>());

    public TransactionAwareCmsProvider(CMSProviderPort delegate, SessionFactory sessionFactory) {
        this.delegate = delegate;
        this.sessionFactory = sessionFactory;
    }

    @Override
    public void add(String key, long delta) {
        if (delta == 0) {
            return;
        }
        Session session = currentSession();
        if (session == null || !isTransactionActive(session)) {
            delegate.add(key, delta);
            return;
        }
        pendingDeltas(session).merge(key, delta, Long::sum);
    }

    @Override
    public long estimateCount(String key) {
        return delegate.estimateCount(key);
    }

    @Override
    public void rebuild(Map<String, Integer> snapshot) {
        delegate.rebuild(snapshot);
    }

    @Override
    public long totalInFlight() {
        return delegate.totalInFlight();
    }

    private Session currentSession() {
        try {
            return sessionFactory.getCurrentSession();
        } catch (HibernateException ex) {
            return null;
        }
    }

    private boolean isTransactionActive(Session session) {
        try {
            return session.getTransaction().isActive();
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private Map<String, Long> pendingDeltas(Session session) {
        synchronized (buffers) {
            Map<String, Long> existing = buffers.get(session);
            if (existing != null) {
                return existing;
            }
            Map<String, Long> deltas = new LinkedHashMap<>();
            buffers.put(session, deltas);
            SessionCallbacks.register(session, () -> {
                Map<String, Long> pending;
                synchronized (buffers) {
                    pending = buffers.remove(session);
                }
                if (pending != null) {
                    apply(pending);
                }
            }, () -> {
                synchronized (buffers) {
                    buffers.remove(session);
                }
            });
            return deltas;
        }
    }

    private void apply(Map<String, Long> deltas) {
        deltas.forEach((key, delta) -> {
            if (delta == 0) {
                return;
            }
            try {
                delegate.add(key, delta);
            } catch (RuntimeException exception) {
                // The transaction is already committed; the watchdog repairs the sketch.
                log.warn("CMS update after commit failed for key='{}' delta={}: {}", key, delta,
                    exception.getMessage());
            }
        });
    }
}
