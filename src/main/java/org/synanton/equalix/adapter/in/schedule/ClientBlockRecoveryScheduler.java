package org.synanton.equalix.adapter.in.schedule;

import io.micronaut.context.annotation.Requires;
import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.inject.Singleton;
import jakarta.inject.Inject;
import org.synanton.equalix.domain.service.ClientBlockRecoveryService;

@Singleton
@Requires(property = "app.scheduling.enabled", value = "true", defaultValue = "true")
@Requires(property = "app.queue.sequential.enabled", value = "true", defaultValue = "true")
public class ClientBlockRecoveryScheduler {

    @Inject
    public ClientBlockRecoveryScheduler(ClientBlockRecoveryService clientBlockRecoveryService) {
        this.clientBlockRecoveryService = clientBlockRecoveryService;
    }

    private final ClientBlockRecoveryService clientBlockRecoveryService;

    @Scheduled(fixedDelay = "${app.queue.sequential.block-recovery-interval:10000}ms")
    public void run() {
        clientBlockRecoveryService.recover();
    }
}
