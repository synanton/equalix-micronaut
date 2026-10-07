package org.synanton.equalix.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import jakarta.inject.Inject;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import io.micronaut.test.support.TestPropertyProvider;
import org.testcontainers.containers.PostgreSQLContainer;
import org.synanton.equalix.adapter.out.database.entity.TaskEntity;
import org.synanton.equalix.domain.model.TaskStatus;
import org.synanton.equalix.domain.service.DispatcherService;
import org.synanton.equalix.domain.service.PriorityCalculatorService;

class VirtualTimeIntegrationTest extends BaseIntegrationTest implements TestPropertyProvider {

    @Override
    public java.util.Map<String, String> getProperties() {
        PostgreSQLContainer<?> pg = TestPostgres.container();
        java.util.Map<String, String> props = new java.util.HashMap<>(integrationProperties());
        props.put("datasources.default.url", pg.getJdbcUrl());
        props.put("datasources.default.username", pg.getUsername());
        props.put("datasources.default.password", pg.getPassword());
        props.put("datasources.default.driver-class-name", "org.postgresql.Driver");
        return props;
    }



    @Inject
    @Client("/")
    private HttpClient httpClient;

    @Inject
    private PriorityCalculatorService priorityCalculatorService;

    @Inject
    private DispatcherService dispatcherService;

    @Test
    void shouldPersistWeightedVirtualTimeThroughQueueingAndDispatch() {
        String fairnessKey = "vt-client-" + UUID.randomUUID();
        int taskCount = 4;
        for (int taskIndex = 0; taskIndex < taskCount; taskIndex++) {
            createTask(fairnessKey, "2.0");
        }

        priorityCalculatorService.run();
        dispatcherService.dispatch();

        // quantum 1000 / weight 2 = 500 virtual units per task, starting from V = 0.
        List<TaskEntity> dispatched = taskJpaRepository.findByFairnessKeyOrderByCreatedAtAsc(fairnessKey);
        assertThat(dispatched)
            .extracting(TaskEntity::getStatus, TaskEntity::getVirtualFinish)
            .containsExactly(
                tuple(TaskStatus.DISPATCHED, 500.0),
                tuple(TaskStatus.DISPATCHED, 1000.0),
                tuple(TaskStatus.DISPATCHED, 1500.0),
                tuple(TaskStatus.DISPATCHED, 2000.0));
        assertThat(clientVirtualTimeJpaRepository.findById(fairnessKey))
            .get()
            .extracting(entity -> List.of(entity.getVirtualTime(), entity.getVirtualFinish()))
            .isEqualTo(List.of(2000.0, 2000.0));
        assertThat(schedulerVirtualClockJpaRepository.findSystemVirtualTime()).isEqualTo(2000.0);
    }

    private void createTask(String fairnessKey, String weight) {
        String payload = Base64.getEncoder().encodeToString("vt-payload".getBytes());
        String body = """
            {
                "fairnessKey": "%s",
                "weight": %s,
                "payload": "%s",
                "sequential": false,
                "requiresPreviousResult": false
            }
            """.formatted(fairnessKey, weight, payload);

        int status = httpClient.toBlocking().exchange(
                HttpRequest.POST("/api/v1/tasks", body)
                    .header("X-API-Key", "test-api-key")
                    .contentType(MediaType.APPLICATION_JSON),
                String.class).getStatus().getCode();
        assertThat(status).isEqualTo(201);
    }
}
