package org.synanton.equalix.adapter.in.schedule;

import io.micronaut.context.annotation.Requires;
import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.inject.Singleton;
import jakarta.inject.Inject;
import org.synanton.equalix.domain.service.ResultPassthroughRecoveryService;

@Singleton
@Requires(property = "app.scheduling.enabled", value = "true", defaultValue = "true")
@Requires(property = "app.queue.sequential.enabled", value = "true", defaultValue = "true")
public class ResultPassthroughRecoveryScheduler {

    @Inject
    public ResultPassthroughRecoveryScheduler(ResultPassthroughRecoveryService resultPassthroughRecoveryService) {
        this.resultPassthroughRecoveryService = resultPassthroughRecoveryService;
    }

    private final ResultPassthroughRecoveryService resultPassthroughRecoveryService;

    @Scheduled(fixedDelay = "${app.queue.sequential.result-passthrough-interval:60000}ms")
    public void run() {
        resultPassthroughRecoveryService.recover();
    }
}
