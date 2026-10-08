package org.synanton.equalix.adapter.out.database;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.List;
import org.hibernate.SessionFactory;
import org.synanton.equalix.adapter.out.database.entity.TaskEntity;

/**
 * Locking dispatch reads. Same SQL as the Spring oracle's {@code TaskJpaRepository} locking queries.
 *
 * <p>Micronaut port note: these stay as explicit Hibernate native queries instead of repository
 * {@code @Query} methods because Micronaut Data classifies any native query containing the
 * {@code FOR UPDATE} keyword as a mutation and refuses to run it as a select
 * ("Expecting a native mutation query"). The SQL, parameters, and {@code SKIP LOCKED} semantics
 * are unchanged; only the execution path differs.
 */
@Singleton
public class TaskLockingQueries {

    private final SessionFactory sessionFactory;

    @Inject
    public TaskLockingQueries(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    public List<TaskEntity> findAndLockDispatchable(int limit, Integer maxPerClient) {
        return sessionFactory.getCurrentSession()
            .createNativeQuery("""
                SELECT t.*
                FROM tasks t
                LEFT JOIN client_counts cc
                    ON t.fairness_key = cc.fairness_key
                WHERE t.status = 'QUEUED'
                  AND t.is_sequential = false
                  AND (
                        :maxPerClient IS NULL
                     OR cc.in_flight_count < :maxPerClient
                     OR cc.in_flight_count IS NULL
                     OR t.priority <= 0
                  )
                ORDER BY t.priority ASC NULLS LAST, t.created_at ASC, t.id ASC
                LIMIT :limit
                FOR UPDATE OF t SKIP LOCKED
                """, TaskEntity.class)
            .setParameter("limit", limit)
            .setParameter("maxPerClient", maxPerClient, Integer.class)
            .getResultList();
    }

    public List<TaskEntity> findAndLockOldestDispatchable(int limit, Integer maxPerClient) {
        return sessionFactory.getCurrentSession()
            .createNativeQuery("""
                SELECT t.*
                FROM tasks t
                LEFT JOIN client_counts cc
                    ON t.fairness_key = cc.fairness_key
                WHERE t.status = 'QUEUED'
                  AND t.is_sequential = false
                  AND (
                        :maxPerClient IS NULL
                     OR cc.in_flight_count < :maxPerClient
                     OR cc.in_flight_count IS NULL
                     OR t.priority <= 0
                  )
                ORDER BY t.created_at ASC, t.id ASC
                LIMIT :limit
                FOR UPDATE OF t SKIP LOCKED
                """, TaskEntity.class)
            .setParameter("limit", limit)
            .setParameter("maxPerClient", maxPerClient, Integer.class)
            .getResultList();
    }

    public List<Object[]> findQueuedLeaves() {
        return sessionFactory.getCurrentSession()
            .createNativeQuery("""
                SELECT t.fairness_key,
                       COUNT(*),
                       SUM(CASE WHEN t.priority <= 0 THEN 1 ELSE 0 END),
                       MAX(t.weight),
                       COALESCE(MAX(cc.in_flight_count), 0)
                FROM tasks t
                LEFT JOIN client_counts cc ON cc.fairness_key = t.fairness_key
                WHERE t.status = 'QUEUED'
                  AND t.is_sequential = false
                GROUP BY t.fairness_key
                """)
            .getResultList();
    }

    /**
     * {@code limitsJson} is a JSON array of {"key": ..., "limit": ...}; JSON avoids
     * driver-specific array binding (same convention as the oracle).
     */
    public List<TaskEntity> findAndLockQueuedHeads(String limitsJson) {
        return sessionFactory.getCurrentSession()
            .createNativeQuery("""
                SELECT q.*
                FROM ROWS FROM (jsonb_to_recordset(CAST(:limits AS jsonb)) AS (key text, "limit" int))
                     WITH ORDINALITY AS wanted(key, "limit", position)
                CROSS JOIN LATERAL (
                    SELECT t.*
                    FROM tasks t
                    WHERE t.fairness_key = wanted.key
                      AND t.status = 'QUEUED'
                      AND t.is_sequential = false
                    ORDER BY t.priority ASC NULLS LAST, t.created_at ASC, t.id ASC
                    LIMIT wanted."limit"
                    FOR UPDATE OF t SKIP LOCKED
                ) q
                ORDER BY wanted.position, q.priority ASC NULLS LAST, q.created_at ASC, q.id ASC
                """, TaskEntity.class)
            .setParameter("limits", limitsJson)
            .getResultList();
    }
}
