package org.synanton.equalix.domain.service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import jakarta.inject.Singleton;
import jakarta.inject.Inject;
import io.micronaut.transaction.annotation.Transactional;
import org.synanton.equalix.domain.model.TaskStatus;
import org.synanton.equalix.domain.port.out.TaskRepositoryPort;

/** Records that the remote executor accepted a dispatched task. */
@Slf4j
@Singleton
public class DispatchAckService {

    @Inject
    public DispatchAckService(TaskRepositoryPort taskRepository, Clock clock) {
        this.taskRepository = taskRepository;
        this.clock = clock;
    }

    private final TaskRepositoryPort taskRepository;
    private final Clock clock;

    @Transactional
    public void markCommitted(UUID taskId) {
        taskRepository.findById(taskId).ifPresent(task -> {
            if (task.getStatus() != TaskStatus.DISPATCHED) {
                return;
            }
            Instant now = Instant.now(clock);
            task.setStatus(TaskStatus.COMMITTED);
            taskRepository.save(task);
            log.debug("Task {} marked COMMITTED", taskId);
        });
    }
}
