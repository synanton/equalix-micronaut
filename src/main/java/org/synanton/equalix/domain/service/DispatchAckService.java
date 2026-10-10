package org.synanton.equalix.domain.service;

import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import jakarta.inject.Singleton;
import jakarta.inject.Inject;
import io.micronaut.transaction.annotation.Transactional;
import org.synanton.equalix.domain.port.out.TaskRepositoryPort;

/** Records that the remote executor accepted a dispatched task. */
@Slf4j
@Singleton
public class DispatchAckService {

    @Inject
    public DispatchAckService(TaskRepositoryPort taskRepository) {
        this.taskRepository = taskRepository;
    }

    private final TaskRepositoryPort taskRepository;

    @Transactional
    public void markCommitted(UUID taskId) {
        // Single guarded UPDATE (no load-modify-save round-trip). A zero rowcount means the
        // task already moved on (completed, timed out, never dispatched) — the async ack
        // must never overwrite progress, so it is silently ignored, as before.
        if (taskRepository.markCommitted(taskId)) {
            log.debug("Task {} marked COMMITTED", taskId);
        }
    }
}
