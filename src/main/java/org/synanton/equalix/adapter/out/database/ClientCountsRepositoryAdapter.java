package org.synanton.equalix.adapter.out.database;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import jakarta.inject.Singleton;
import jakarta.inject.Inject;
import org.synanton.equalix.domain.model.ClientCounts;
import org.synanton.equalix.domain.port.out.ClientCountsRepositoryPort;

@Singleton
public class ClientCountsRepositoryAdapter implements ClientCountsRepositoryPort {

    @Inject
    public ClientCountsRepositoryAdapter(ClientCountsJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    private final ClientCountsJpaRepository jpaRepository;

    @Override
    public void incrementInFlight(String fairnessKey) {
        incrementInFlight(fairnessKey, 1);
    }

    @Override
    public void incrementInFlight(String fairnessKey, int delta) {
        jpaRepository.incrementInFlight(fairnessKey, delta);
    }

    @Override
    public void decrementInFlight(String fairnessKey) {
        jpaRepository.decrementInFlight(fairnessKey);
    }

    @Override
    public void upsertCount(String fairnessKey, int count) {
        jpaRepository.upsertCount(fairnessKey, count);
    }

    @Override
    public List<ClientCounts> findAll() {
        return jpaRepository.findAll().stream()
            .map(entity -> new ClientCounts()
                .setFairnessKey(entity.getFairnessKey())
                .setInFlightCount(entity.getInFlightCount())
                .setUpdatedAt(entity.getUpdatedAt()))
            .toList();
    }

    @Override
    public long totalInFlight() {
        return jpaRepository.totalInFlight();
    }

    @Override
    public Map<String, Integer> findAllAsMap() {
        return jpaRepository.findAll().stream()
            .collect(Collectors.toMap(
                entity -> entity.getFairnessKey(),
                entity -> entity.getInFlightCount()));
    }
}
