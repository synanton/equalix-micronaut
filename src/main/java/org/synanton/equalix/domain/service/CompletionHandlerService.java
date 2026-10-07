package org.synanton.equalix.domain.service;

import java.time.Clock;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import jakarta.inject.Singleton;
import jakarta.inject.Inject;
import io.micronaut.transaction.annotation.Transactional;
import org.synanton.equalix.domain.model.Task;
import org.synanton.equalix.domain.model.TaskStatus;
import org.synanton.equalix.domain.port.out.CMSProviderPort;
import org.synanton.equalix.domain.port.out.ClientCountsRepositoryPort;
import org.synanton.equalix.domain.port.out.PerformanceMonitorPort;
import org.synanton.equalix.domain.port.out.TaskRepositoryPort;

/** Handles completion of standard (non-sequential) tasks. */
@Slf4j
@Singleton
public class CompletionHandlerService {

    @Inject
    public CompletionHandlerService(TaskRepositoryPort taskRepository, CMSProviderPort cms, ClientCountsRepositoryPort clientCounts, PerformanceMonitorPort performanceMonitor, Clock clock) {
        this.taskRepository = taskRepository;
        this.cms = cms;
        this.clientCounts = clientCounts;
        this.performanceMonitor = performanceMonitor;
        this.clock = clock;
    }

    private final TaskRepositoryPort taskRepository;
    private final CMSProviderPort cms;
    private final ClientCountsRepositoryPort clientCounts;
    private final PerformanceMonitorPort performanceMonitor;
    private final Clock clock;

    @Transactional
    public void handle(Task task, boolean success, @Nullable byte[] result, @Nullable String error) {
        if (task.getStatus().isTerminal()) {
            log.debug("Ignoring duplicate completion for terminal task {}", task.getId());
            return;
        }
        if (!task.getStatus().isInFlight()) {
            throw new IllegalArgumentException("Task is not in-flight: " + task.getId());
        }

        Instant now = Instant.now(clock);
        long durationMs = task.getUpdatedAt() != null
            ? now.toEpochMilli() - task.getUpdatedAt().toEpochMilli()
            : 0L;

        TaskStatus finalStatus = success ? TaskStatus.SUCCEEDED : TaskStatus.FAILED;

        task.setStatus(finalStatus)
            .setResult(result)
            .setLastError(error)
            .setCompletedAt(now);

        taskRepository.save(task);
        cms.add(task.getFairnessKey(), -1);
        clientCounts.decrementInFlight(task.getFairnessKey());
        performanceMonitor.recordCompletion(task.getFairnessKey(), durationMs, success);

        log.debug("Task {} completed with status {}", task.getId(), finalStatus);
    }
}
