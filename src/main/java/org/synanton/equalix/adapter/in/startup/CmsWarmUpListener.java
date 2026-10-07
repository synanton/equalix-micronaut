package org.synanton.equalix.adapter.in.startup;

import io.micronaut.context.event.StartupEvent;
import io.micronaut.runtime.event.annotation.EventListener;
import jakarta.inject.Singleton;
import jakarta.inject.Inject;
import org.synanton.equalix.domain.service.WatchdogService;

/**
 * Rebuilds the CMS from in-flight tasks at startup. Without this, a restarted instance's local sketch is empty
 * and underestimates every key until the first watchdog run. A new Redis layout version also starts empty.
 */
@Singleton
public class CmsWarmUpListener {

    @Inject
    public CmsWarmUpListener(WatchdogService watchdogService) {
        this.watchdogService = watchdogService;
    }

    private final WatchdogService watchdogService;

    @EventListener
    public void onApplicationReady(StartupEvent event) {
        watchdogService.warmUpCms();
    }
}
