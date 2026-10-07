package org.synanton.equalix.integration;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.synanton.equalix.domain.service.PriorityCalculatorService;

class SequentialExecutionIntegrationTest extends BaseIntegrationTest implements TestPropertyProvider {

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

    @Test
    void shouldIngestSequentialTasksWithReceivedStatus() {
        String payload = Base64.getEncoder().encodeToString("seq-payload".getBytes());
        String clientKey = "seq-client-" + UUID.randomUUID();

        for (int seqNum = 1; seqNum <= 3; seqNum++) {
            String body = """
                    {
                        "fairnessKey": "%s",
                        "weight": 1.0,
                        "payload": "%s",
                        "sequential": true,
                        "sequenceNumber": %d,
                        "requiresPreviousResult": false
                    }
                    """.formatted(clientKey, payload, seqNum);

            int status = httpClient.toBlocking().exchange(
                    HttpRequest.POST("/api/v1/tasks", body)
                        .header("X-API-Key", "test-api-key")
                        .contentType(MediaType.APPLICATION_JSON),
                    String.class).getStatus().getCode();
            assertThat(status).isEqualTo(201);
        }

        List<TaskEntity> tasks = taskJpaRepository.findByFairnessKeyOrderByCreatedAtAsc(clientKey);
        assertThat(tasks).hasSize(3);
        assertThat(tasks).allMatch(task -> task.getStatus() == TaskStatus.RECEIVED);
        assertThat(tasks).allMatch(TaskEntity::isSequential);
        assertThat(sequenceStateJpaRepository.findById(clientKey)).isPresent();
    }

    @Test
    void shouldTransitionTasksToQueuedAfterPriorityCalculation() {
        String payload = Base64.getEncoder().encodeToString("data".getBytes());
        String clientKey = "priority-client-" + UUID.randomUUID();

        String body = """
                {
                    "fairnessKey": "%s",
                    "weight": 1.0,
                    "payload": "%s",
                    "sequential": false,
                    "requiresPreviousResult": false
                }
                """.formatted(clientKey, payload);

        int status = httpClient.toBlocking().exchange(
                HttpRequest.POST("/api/v1/tasks", body)
                    .header("X-API-Key", "test-api-key")
                    .contentType(MediaType.APPLICATION_JSON),
                String.class).getStatus().getCode();
        assertThat(status).isEqualTo(201);

        priorityCalculatorService.run();

        List<TaskEntity> tasks = taskJpaRepository.findByFairnessKeyOrderByCreatedAtAsc(clientKey);
        assertThat(tasks).hasSize(1);
        assertThat(tasks.getFirst().getStatus()).isEqualTo(TaskStatus.QUEUED);
        assertThat(tasks.getFirst().getPriority()).isNotNull();
    }
}
