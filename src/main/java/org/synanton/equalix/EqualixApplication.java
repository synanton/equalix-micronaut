package org.synanton.equalix;

import io.micronaut.context.event.StartupEvent;
import io.micronaut.runtime.Micronaut;
import io.micronaut.runtime.event.annotation.EventListener;
import jakarta.inject.Singleton;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EqualixApplication {

    private static final Logger log = LoggerFactory.getLogger(EqualixApplication.class);

    public static void main(String[] args) {
        // Startup milestone for the differential matrix's
        // runtime-characterization row (informational, never gated):
        // main-entry here, context-built below at StartupEvent.
        // The harness records spawn/ready/first-dispatch; these lines give
        // the internal phase breakdown when a cell needs drill-down.
        log.info("startup milestone phase=main-entry at={} nanoTime={}",
                Instant.now(), System.nanoTime());
        Micronaut.run(EqualixApplication.class, args);
    }

    @Singleton
    static class StartupMilestones {
        @EventListener
        public void onReady(StartupEvent event) {
            log.info("startup milestone phase=context-ready at={} nanoTime={}",
                    Instant.now(), System.nanoTime());
        }
    }
}
