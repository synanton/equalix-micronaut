package org.synanton.equalix.adapter.in.schedule;

import io.micronaut.context.annotation.Requires;
import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.inject.Singleton;
import jakarta.inject.Inject;
import org.synanton.equalix.domain.service.PriorityCalculatorService;

@Singleton
@Requires(property = "app.scheduling.enabled", value = "true", defaultValue = "true")
public class PriorityCalculatorScheduler {

    @Inject
    public PriorityCalculatorScheduler(PriorityCalculatorService priorityCalculatorService) {
        this.priorityCalculatorService = priorityCalculatorService;
    }

    private final PriorityCalculatorService priorityCalculatorService;

    @Scheduled(fixedDelay = "${app.queue.priority-calc-interval:100}ms")
    public void run() {
        TransientRetry.run("priorityCalculator", priorityCalculatorService::run);
    }
}
