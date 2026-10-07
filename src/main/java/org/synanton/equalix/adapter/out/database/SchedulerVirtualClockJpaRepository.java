package org.synanton.equalix.adapter.out.database;

import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.repository.GenericRepository;
import org.synanton.equalix.adapter.out.database.entity.SchedulerVirtualClockEntity;

@Repository
public interface SchedulerVirtualClockJpaRepository extends GenericRepository<SchedulerVirtualClockEntity, Short> {

    @Query(value = "SELECT COALESCE(MAX(virtual_time), 0) FROM scheduler_virtual_clock WHERE id = 1",
        nativeQuery = true)
    double findSystemVirtualTime();

    @Query(value = """
        INSERT INTO scheduler_virtual_clock (id, virtual_time, updated_at)
        VALUES (1, :finishTag, now())
        ON CONFLICT (id)
        DO UPDATE SET virtual_time = GREATEST(scheduler_virtual_clock.virtual_time, :finishTag),
                      updated_at = now()
        """, nativeQuery = true)
    void advance(double finishTag);

    @Query("DELETE FROM SchedulerVirtualClockEntity c")
    void deleteAllInBatch();
}
