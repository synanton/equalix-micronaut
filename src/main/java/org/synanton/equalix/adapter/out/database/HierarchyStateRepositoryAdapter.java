package org.synanton.equalix.adapter.out.database;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;
import jakarta.inject.Singleton;
import jakarta.inject.Inject;
import org.synanton.equalix.domain.model.HierarchyNodeState;
import org.synanton.equalix.domain.port.out.HierarchyStateRepositoryPort;

@Singleton
public class HierarchyStateRepositoryAdapter implements HierarchyStateRepositoryPort {

    @Inject
    public HierarchyStateRepositoryAdapter(HierarchyNodeJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    private final HierarchyNodeJpaRepository jpaRepository;

    @Override
    public Map<String, HierarchyNodeState> findByKeys(Collection<String> nodeKeys) {
        return jpaRepository.findAllById(nodeKeys).stream()
            .collect(Collectors.toMap(
                entity -> entity.getNodeKey(),
                entity -> new HierarchyNodeState(
                    entity.getNodeKey(), entity.getVirtualTime(), entity.getChildrenVirtualTime())));
    }

    @Override
    public void chargeVirtualTime(String nodeKey, double floor, double delta) {
        jpaRepository.chargeVirtualTime(nodeKey, floor, delta);
    }

    @Override
    public void raiseChildrenFloor(String nodeKey, double floor) {
        jpaRepository.raiseChildrenFloor(nodeKey, floor);
    }
}
