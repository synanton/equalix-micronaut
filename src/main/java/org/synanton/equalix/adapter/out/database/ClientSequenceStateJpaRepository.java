package org.synanton.equalix.adapter.out.database;

import java.util.List;
import java.util.Optional;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.repository.GenericRepository;
import org.synanton.equalix.adapter.out.database.entity.ClientSequenceStateEntity;

@Repository
public interface ClientSequenceStateJpaRepository extends GenericRepository<ClientSequenceStateEntity, String> {

    <S extends ClientSequenceStateEntity> S save(S entity);

    @Query("SELECT c FROM ClientSequenceStateEntity c WHERE c.fairnessKey = :id")
    Optional<ClientSequenceStateEntity> findById(String id);

    @Query("SELECT c FROM ClientSequenceStateEntity c WHERE c.isBlocked = true")
    List<ClientSequenceStateEntity> findByIsBlockedTrue();

    /** Finds clients where no task is currently executing and not blocked, and a QUEUED sequential task exists. */
    @Query(value = """
        SELECT css.* FROM client_sequence_state css
        WHERE css.is_blocked = false
          AND css.current_executing_task_id IS NULL
          AND EXISTS (
            SELECT 1 FROM tasks t
            WHERE t.fairness_key = css.fairness_key
              AND t.status = 'QUEUED'
              AND t.is_sequential = true
          )
        """, nativeQuery = true)
    List<ClientSequenceStateEntity> findReadyClients();
}
