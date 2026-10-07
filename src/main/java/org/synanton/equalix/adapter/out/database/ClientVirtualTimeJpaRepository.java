package org.synanton.equalix.adapter.out.database;

import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.repository.GenericRepository;
import java.util.Optional;
import org.synanton.equalix.adapter.out.database.entity.ClientVirtualTimeEntity;

/**
 * See {@link ClientCountsJpaRepository} for why this extends {@link GenericRepository}.
 * Same SQL as the Spring oracle.
 */
@Repository
public interface ClientVirtualTimeJpaRepository extends GenericRepository<ClientVirtualTimeEntity, String> {

    @Query("SELECT c FROM ClientVirtualTimeEntity c WHERE c.fairnessKey = :id")
    Optional<ClientVirtualTimeEntity> findById(String id);

    /** Atomic read-modify-write so concurrent reservations for one key never receive the same tag. */
    @Query(value = """
        INSERT INTO client_virtual_time (fairness_key, virtual_time, virtual_finish, updated_at)
        VALUES (:key, :systemVirtualTime, :systemVirtualTime + :increment, now())
        ON CONFLICT (fairness_key)
        DO UPDATE SET virtual_finish = GREATEST(client_virtual_time.virtual_finish, :systemVirtualTime) + :increment,
                      updated_at = now()
        RETURNING virtual_finish
        """, nativeQuery = true)
    double reserveFinishTag(
        String key,
        double systemVirtualTime,
        double increment);

    @Query(value = """
        INSERT INTO client_virtual_time (fairness_key, virtual_time, virtual_finish, updated_at)
        VALUES (:key, :finishTag, :finishTag, now())
        ON CONFLICT (fairness_key)
        DO UPDATE SET virtual_time = GREATEST(client_virtual_time.virtual_time, :finishTag),
                      virtual_finish = GREATEST(client_virtual_time.virtual_finish, :finishTag),
                      updated_at = now()
        """, nativeQuery = true)
    void advanceVirtualTime(String key, double finishTag);

    @Query("DELETE FROM ClientVirtualTimeEntity c")
    void deleteAllInBatch();
}
