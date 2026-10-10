package org.synanton.equalix.adapter.in.schedule;

import io.micronaut.context.annotation.Requires;
import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.inject.Singleton;
import jakarta.inject.Inject;
import org.synanton.equalix.domain.service.SequentialDispatcherService;

@Singleton
@Requires(property = "app.scheduling.enabled", value = "true", defaultValue = "true")
@Requires(property = "app.queue.sequential.enabled", value = "true", defaultValue = "true")
public class SequentialDispatcherScheduler {

    @Inject
    public SequentialDispatcherScheduler(SequentialDispatcherService sequentialDispatcherService) {
        this.sequentialDispatcherService = sequentialDispatcherService;
    }

    private final SequentialDispatcherService sequentialDispatcherService;

    @Scheduled(fixedDelay = "${app.queue.sequential.dispatcher-interval:50}ms")
    public void run() {
        TransientRetry.run("sequentialDispatcher", sequentialDispatcherService::dispatch);
    }
}
