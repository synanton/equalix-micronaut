package org.synanton.equalix.adapter.out.database;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.repository.GenericRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.synanton.equalix.adapter.out.database.entity.TaskEntity;
import org.synanton.equalix.domain.model.TaskStatus;

/**
 * Micronaut Data repository. Same queries as the Spring oracle, spelled as explicit
 * {@code @Query} methods (see {@link ClientCountsJpaRepository} for why this extends
 * {@link GenericRepository} instead of {@code JpaRepository}).
 */
@Repository
public interface TaskJpaRepository extends GenericRepository<TaskEntity, UUID> {

    <S extends TaskEntity> S save(S entity);

    @Query("SELECT t FROM TaskEntity t WHERE t.id = :id")
    Optional<TaskEntity> findById(UUID id);

    @Query("SELECT t FROM TaskEntity t WHERE t.status = :status ORDER BY t.createdAt ASC")
    List<TaskEntity> findByStatusOrderByCreatedAtAsc(TaskStatus status, Pageable pageable);

    @Query("SELECT t FROM TaskEntity t WHERE t.fairnessKey = :fairnessKey ORDER BY t.createdAt ASC")
    List<TaskEntity> findByFairnessKeyOrderByCreatedAtAsc(String fairnessKey);

    @Query("SELECT t FROM TaskEntity t WHERE t.fairnessKey = :fairnessKey AND t.status = :status ORDER BY t.createdAt ASC")
    List<TaskEntity> findByFairnessKeyAndStatusOrderByCreatedAtAsc(String fairnessKey, TaskStatus status);

    @Query(value = """
        SELECT * FROM tasks
        WHERE status = 'QUEUED'
          AND is_sequential = false
          AND created_at < now() - (:olderThanMs || ' milliseconds')::interval
        ORDER BY created_at ASC
        LIMIT :limit
        """, nativeQuery = true)
    List<TaskEntity> findStarvedTasks(long olderThanMs, int limit);

    @Query("SELECT t FROM TaskEntity t WHERE t.fairnessKey = :fairnessKey AND t.sequenceNumber = :sequenceNumber AND t.status = :status")
    Optional<TaskEntity> findByFairnessKeyAndSequenceNumberAndStatus(
        String fairnessKey, Long sequenceNumber, TaskStatus status);

    // Status is bound as a parameter: an enum literal in JPQL renders as '...'::TaskStatus, which is not the
    // PostgreSQL type name (task_status).
    @Query("""
        SELECT t FROM TaskEntity t
        WHERE t.requiresPreviousResult = true
          AND t.previousResult IS NULL
          AND t.status = :status
          AND t.dependsOnTaskId IS NOT NULL
        """)
    List<TaskEntity> findTasksWaitingForPreviousResult(TaskStatus status);

    // updated_at is DB-assigned by trg_set_updated_at; no need to set it here.
    @Query("UPDATE TaskEntity t SET t.status = :newStatus WHERE t.id IN :ids")
    int updateStatusBatch(List<UUID> ids, TaskStatus newStatus);

    // Write-path optimizations (O1/O2): targeted UPDATEs replace load-modify-save
    // round-trips. Version is bumped inline so concurrent holders still fail fast
    // on stale state. Micronaut Data auto-detects modifying queries.

    /** Bulk dispatch transition for rows locked by the {@code TaskLockingQueries} readers. */
    @Query(value = """
        UPDATE tasks SET status = 'DISPATCHED', version = version + 1
        WHERE id IN :ids AND status = 'QUEUED'
        """, nativeQuery = true)
    int bulkMarkDispatched(List<UUID> ids);

    /** Single dispatch transition, attaching the predecessor result for sequential tasks. */
    @Query(value = """
        UPDATE tasks SET status = 'DISPATCHED', previous_result = :previousResult,
            version = version + 1
        WHERE id = :id AND status = 'QUEUED'
        """, nativeQuery = true)
    int markDispatched(UUID id, @Nullable byte[] previousResult);

    /** Queueing transition for the priority calculator (status, priority, finish tag). */
    @Query(value = """
        UPDATE tasks SET status = 'QUEUED', priority = :priority, virtual_finish = :virtualFinish,
            version = version + 1
        WHERE id = :id AND status = 'RECEIVED'
        """, nativeQuery = true)
    int markQueued(UUID id, long priority, @Nullable Double virtualFinish);

    /** Atomic terminal transition: status guard and version check run in the UPDATE. */
    @Query(value = """
        UPDATE tasks SET status = CAST(:status AS task_status), result = :result,
            last_error = :lastError, completed_at = :completedAt, version = version + 1
        WHERE id = :id AND status IN ('DISPATCHED', 'COMMITTED') AND version = :version
        """, nativeQuery = true)
    int completeTask(UUID id, long version, String status, @Nullable byte[] result,
        @Nullable String lastError, java.time.Instant completedAt);

    /** Executor-ack transition; silently skips tasks that already moved on. */
    @Query(value = """
        UPDATE tasks SET status = 'COMMITTED', version = version + 1
        WHERE id = :id AND status = 'DISPATCHED'
        """, nativeQuery = true)
    int markCommitted(UUID id);

    /** Timeout transition with the same in-UPDATE guards as {@link #completeTask}. */
    @Query(value = """
        UPDATE tasks SET status = 'TIMEOUT', last_error = :lastError, completed_at = :completedAt,
            version = version + 1
        WHERE id = :id AND status IN ('DISPATCHED', 'COMMITTED') AND version = :version
        """, nativeQuery = true)
    int markTimeout(UUID id, long version, String lastError, java.time.Instant completedAt);

    /** Bulk starvation promotion (priority → 0); rows already promoted are untouched. */
    @Query(value = """
        UPDATE tasks SET priority = 0, version = version + 1
        WHERE id IN (
            SELECT id FROM tasks
            WHERE status = 'QUEUED'
              AND is_sequential = false
              AND (priority IS NULL OR priority <> 0)
              AND created_at < now() - (:olderThanMs || ' milliseconds')::interval
            ORDER BY created_at ASC
            LIMIT :limit
        )
        """, nativeQuery = true)
    int bulkPromoteStarvedTasks(long olderThanMs, int limit);

    @Query("""
        SELECT t.fairnessKey, COUNT(t)
        FROM TaskEntity t
        WHERE t.status IN :statuses
        GROUP BY t.fairnessKey
        """)
    List<Object[]> countByStatusesGroupByFairnessKey(List<TaskStatus> statuses);

    @Query(value = """
        SELECT * FROM tasks
        WHERE status IN ('DISPATCHED', 'COMMITTED')
          AND updated_at < now() - (:olderThanMs || ' milliseconds')::interval
        ORDER BY updated_at ASC
        LIMIT :limit
        """, nativeQuery = true)
    List<TaskEntity> findTimedOutInFlight(long olderThanMs, int limit);

    @Query("DELETE FROM TaskEntity t")
    void deleteAllInBatch();

    @Query("SELECT t FROM TaskEntity t WHERE t.id IN :ids")
    List<TaskEntity> findAllById(java.util.Collection<java.util.UUID> ids);
}
