package org.synanton.equalix.adapter.in.schedule;

import io.micronaut.context.annotation.Requires;
import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.inject.Singleton;
import jakarta.inject.Inject;
import org.synanton.equalix.domain.service.WatchdogService;

@Singleton
@Requires(property = "app.scheduling.enabled", value = "true", defaultValue = "true")
public class WatchdogScheduler {

    @Inject
    public WatchdogScheduler(WatchdogService watchdogService) {
        this.watchdogService = watchdogService;
    }

    private final WatchdogService watchdogService;

    @Scheduled(fixedRate = "${app.watchdog.interval-minutes:5}m")
    public void run() {
        watchdogService.reconcile();
    }
}
