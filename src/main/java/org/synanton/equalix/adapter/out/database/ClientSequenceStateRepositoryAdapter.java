package org.synanton.equalix.adapter.out.database;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import jakarta.inject.Singleton;
import jakarta.inject.Inject;
import org.hibernate.HibernateException;
import org.hibernate.SessionFactory;
import org.synanton.equalix.adapter.out.database.entity.ClientSequenceStateEntity;
import org.synanton.equalix.domain.model.ClientSequenceState;
import org.synanton.equalix.domain.port.out.ClientSequenceStateRepositoryPort;

@Singleton
public class ClientSequenceStateRepositoryAdapter implements ClientSequenceStateRepositoryPort {

    @Inject
    public ClientSequenceStateRepositoryAdapter(ClientSequenceStateJpaRepository jpaRepository, Clock clock,
        SessionFactory sessionFactory) {
        this.jpaRepository = jpaRepository;
        this.clock = clock;
        this.sessionFactory = sessionFactory;
    }

    private final ClientSequenceStateJpaRepository jpaRepository;
    private final Clock clock;
    private final SessionFactory sessionFactory;

    private ClientSequenceStateEntity mergedSave(ClientSequenceStateEntity entity) {
        // Same merge-instead-of-persist rationale as TaskRepositoryAdapter.save.
        try {
            return sessionFactory.getCurrentSession().merge(entity);
        } catch (HibernateException ex) {
            return jpaRepository.save(entity);
        }
    }

    @Override
    public ClientSequenceState findOrCreate(String fairnessKey) {
        return jpaRepository.findById(fairnessKey)
            .map(this::toDomain)
            .orElseGet(() -> {
                ClientSequenceStateEntity entity = new ClientSequenceStateEntity()
                    .setFairnessKey(fairnessKey)
                    .setLastCompletedSequence(0L)
                    .setLastDispatchedSequence(0L)
                    .setBlocked(false);
                return toDomain(mergedSave(entity));
            });
    }

    @Override
    public Optional<ClientSequenceState> findByFairnessKey(String fairnessKey) {
        return jpaRepository.findById(fairnessKey).map(this::toDomain);
    }

    @Override
    public ClientSequenceState save(ClientSequenceState state) {
        return toDomain(mergedSave(toEntity(state)));
    }

    @Override
    public List<ClientSequenceState> findReadyClients() {
        return jpaRepository.findReadyClients().stream().map(this::toDomain).toList();
    }

    @Override
    public List<ClientSequenceState> findBlockedClients() {
        return jpaRepository.findByIsBlockedTrue().stream().map(this::toDomain).toList();
    }

    private ClientSequenceState toDomain(ClientSequenceStateEntity entity) {
        return new ClientSequenceState()
            .setFairnessKey(entity.getFairnessKey())
            .setLastCompletedSequence(entity.getLastCompletedSequence())
            .setLastDispatchedSequence(entity.getLastDispatchedSequence())
            .setCurrentExecutingTaskId(entity.getCurrentExecutingTaskId())
            .setBlocked(entity.isBlocked())
            .setBlockedAt(entity.getBlockedAt())
            .setUpdatedAt(entity.getUpdatedAt());
    }

    private ClientSequenceStateEntity toEntity(ClientSequenceState state) {
        return new ClientSequenceStateEntity()
            .setFairnessKey(state.getFairnessKey())
            .setLastCompletedSequence(state.getLastCompletedSequence())
            .setLastDispatchedSequence(state.getLastDispatchedSequence())
            .setCurrentExecutingTaskId(state.getCurrentExecutingTaskId())
            .setBlocked(state.isBlocked())
            .setBlockedAt(state.getBlockedAt())
            .setUpdatedAt(state.getUpdatedAt());
    }
}
