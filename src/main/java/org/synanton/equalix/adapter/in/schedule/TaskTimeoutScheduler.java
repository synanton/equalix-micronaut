package org.synanton.equalix.adapter.in.schedule;

import io.micronaut.context.annotation.Requires;
import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.inject.Singleton;
import jakarta.inject.Inject;
import org.synanton.equalix.domain.service.TaskTimeoutService;

@Singleton
@Requires(property = "app.scheduling.enabled", value = "true", defaultValue = "true")
public class TaskTimeoutScheduler {

    @Inject
    public TaskTimeoutScheduler(TaskTimeoutService taskTimeoutService) {
        this.taskTimeoutService = taskTimeoutService;
    }

    private final TaskTimeoutService taskTimeoutService;

    @Scheduled(fixedDelay = "${app.queue.dispatcher-interval:50}ms")
    public void run() {
        TransientRetry.run("taskTimeout", taskTimeoutService::expireTimedOutTasks);
    }
}
