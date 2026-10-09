package org.synanton.equalix.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import io.micronaut.test.support.TestPropertyProvider;
import org.testcontainers.containers.PostgreSQLContainer;
import org.synanton.equalix.domain.model.Task;
import org.synanton.equalix.domain.port.in.TaskIngestionPort;
import org.synanton.equalix.domain.service.DispatcherService;
import org.synanton.equalix.domain.service.PriorityCalculatorService;

/**
 * Starvation promotion is a hard backstop: a promoted task (priority 0) dispatches even when its
 * fairness key is at the per-key quota. Without the {@code OR priority <= 0} bypass in the
 * dispatch queries, a quota-blocked key's starved tasks would never dispatch.
 */
class StarvationBypassIntegrationTest extends BaseIntegrationTest implements TestPropertyProvider {

    @Override
    public java.util.Map<String, String> getProperties() {
        PostgreSQLContainer<?> pg = TestPostgres.container();
        java.util.Map<String, String> props = new java.util.HashMap<>(integrationProperties());
        props.put("datasources.default.url", pg.getJdbcUrl());
        props.put("datasources.default.username", pg.getUsername());
        props.put("datasources.default.password", pg.getPassword());
        props.put("datasources.default.driver-class-name", "org.postgresql.Driver");
        props.put("app.queue.max-per-client-quota", "1");
        props.put("app.queue.max-tasks-in-process", "5");
        props.put("app.queue.max-queued-time-ms", "1");
        return props;
    }

    private static final byte[] PAYLOAD = "bypass".getBytes();

    @Inject
    private TaskIngestionPort taskIngestion;

    @Inject
    private PriorityCalculatorService priorityCalculatorService;

    @Inject
    private DispatcherService dispatcherService;

    @Test
    void shouldDispatchPromotedTaskDespiteQuota() throws InterruptedException {
        String tenant = "tenant-" + UUID.randomUUID().toString().substring(0, 8);
        List<UUID> sent = new ArrayList<>();
        doAnswer(invocation -> sent.add(invocation.getArgument(0)))
            .when(remoteExecutor).send(any(), any(), any());

        // Fill the key's single quota slot; the task stays in flight (never completed).
        Task first = taskIngestion.createTask(tenant, BigDecimal.ONE, PAYLOAD, false, null, null, false);
        priorityCalculatorService.run();
        dispatcherService.dispatch();
        assertThat(sent).containsExactly(first.getId());

        // A second task queues, then ages past the 1 ms starvation deadline while the
        // test sleeps (100x margin; the promotion query reads the database clock).
        Task starved = taskIngestion.createTask(tenant, BigDecimal.ONE, PAYLOAD, false, null, null, false);
        priorityCalculatorService.run();
        Thread.sleep(100);

        dispatcherService.dispatch();
        // The promotion write and the locking select run in one transaction; a native
        // SELECT need not observe the unflushed promotion on every framework, so a
        // promoted task may dispatch one tick later. A second tick keeps the test
        // deterministic everywhere without weakening what it pins (without the
        // quota bypass the task would never dispatch, on any tick).
        dispatcherService.dispatch();

        assertThat(sent).containsExactly(first.getId(), starved.getId());
    }
}
