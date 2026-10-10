package org.synanton.equalix.adapter.in.schedule;

import io.micronaut.context.annotation.Requires;
import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.inject.Singleton;
import jakarta.inject.Inject;
import org.synanton.equalix.domain.service.DispatcherService;

@Singleton
@Requires(property = "app.scheduling.enabled", value = "true", defaultValue = "true")
public class DispatcherScheduler {

    @Inject
    public DispatcherScheduler(DispatcherService dispatcherService) {
        this.dispatcherService = dispatcherService;
    }

    private final DispatcherService dispatcherService;

    @Scheduled(fixedDelay = "${app.queue.dispatcher-interval:50}ms")
    public void run() {
        TransientRetry.run("dispatcher", dispatcherService::dispatch);
    }
}
