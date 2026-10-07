package org.synanton.equalix.adapter.out.database;

import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.repository.GenericRepository;
import java.util.Collection;
import java.util.List;
import org.synanton.equalix.adapter.out.database.entity.HierarchyNodeEntity;

/**
 * See {@link ClientCountsJpaRepository} for why this extends {@link GenericRepository}.
 * Same SQL as the Spring oracle.
 */
@Repository
public interface HierarchyNodeJpaRepository extends GenericRepository<HierarchyNodeEntity, String> {

    @Query("SELECT h FROM HierarchyNodeEntity h WHERE h.nodeKey IN :ids")
    List<HierarchyNodeEntity> findAllById(Collection<String> ids);

    @Query(value = """
        INSERT INTO hierarchy_node (node_key, virtual_time, children_virtual_time, updated_at)
        VALUES (:key, :floor + :delta, 0, now())
        ON CONFLICT (node_key)
        DO UPDATE SET virtual_time = GREATEST(hierarchy_node.virtual_time, :floor) + :delta,
                      updated_at = now()
        """, nativeQuery = true)
    void chargeVirtualTime(String key, double floor, double delta);

    @Query(value = """
        INSERT INTO hierarchy_node (node_key, virtual_time, children_virtual_time, updated_at)
        VALUES (:key, 0, :floor, now())
        ON CONFLICT (node_key)
        DO UPDATE SET children_virtual_time = GREATEST(hierarchy_node.children_virtual_time, :floor),
                      updated_at = now()
        """, nativeQuery = true)
    void raiseChildrenFloor(String key, double floor);

    @Query("DELETE FROM HierarchyNodeEntity h")
    void deleteAllInBatch();

    @Query("SELECT h FROM HierarchyNodeEntity h WHERE h.nodeKey = :id")
    java.util.Optional<HierarchyNodeEntity> findById(String id);
}
