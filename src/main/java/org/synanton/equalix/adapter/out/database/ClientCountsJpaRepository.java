package org.synanton.equalix.adapter.out.database;

import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.repository.GenericRepository;
import java.util.List;
import org.synanton.equalix.adapter.out.database.entity.ClientCountsEntity;

@Repository
public interface ClientCountsJpaRepository extends GenericRepository<ClientCountsEntity, String> {

    @Query("SELECT c FROM ClientCountsEntity c")
    List<ClientCountsEntity> findAll();

    @Query(value = """
        INSERT INTO client_counts (fairness_key, in_flight_count, updated_at)
        VALUES (:key, 1, now())
        ON CONFLICT (fairness_key)
        DO UPDATE SET in_flight_count = GREATEST(0, client_counts.in_flight_count + 1), updated_at = now()
        """, nativeQuery = true)
    void incrementInFlight(String key);

    @Query(value = """
        UPDATE client_counts
        SET in_flight_count = GREATEST(0, in_flight_count - 1), updated_at = now()
        WHERE fairness_key = :key
        """, nativeQuery = true)
    void decrementInFlight(String key);

    @Query(value = """
        INSERT INTO client_counts (fairness_key, in_flight_count, updated_at)
        VALUES (:key, :count, now())
        ON CONFLICT (fairness_key)
        DO UPDATE SET in_flight_count = :count, updated_at = now()
        """, nativeQuery = true)
    void upsertCount(String key, int count);

    @Query("SELECT COALESCE(SUM(c.inFlightCount), 0) FROM ClientCountsEntity c")
    long totalInFlight();
}
