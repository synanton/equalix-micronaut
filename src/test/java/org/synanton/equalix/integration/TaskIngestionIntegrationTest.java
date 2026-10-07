package org.synanton.equalix.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.util.Base64;
import java.util.UUID;
import io.micronaut.test.support.TestPropertyProvider;
import org.testcontainers.containers.PostgreSQLContainer;
import org.junit.jupiter.api.Test;
import org.synanton.equalix.domain.model.TaskStatus;

class TaskIngestionIntegrationTest extends BaseIntegrationTest implements TestPropertyProvider {

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

    @Test
    void shouldCreateTaskViaRestAndFindItInDatabase() throws Exception {
        byte[] payload = "test-payload".getBytes();
        String payloadBase64 = Base64.getEncoder().encodeToString(payload);
        String requestBody = """
            {
                "fairnessKey": "integration-client",
                "weight": 1.5,
                "payload": "%s",
                "sequential": false,
                "requiresPreviousResult": false
            }
            """.formatted(payloadBase64);

        HttpResponse<String> response = httpClient.toBlocking().exchange(
            HttpRequest.POST("/api/v1/tasks", requestBody)
                .header("X-API-Key", "test-api-key")
                .contentType(MediaType.APPLICATION_JSON),
            String.class);
        assertThat(response.getStatus().getCode()).isEqualTo(201);

        UUID taskId = UUID.fromString(response.getBody().orElseThrow().replace("\"", ""));

        assertThat(taskJpaRepository.findById(taskId))
            .isPresent()
            .get()
            .satisfies(task -> {
                assertThat(task.getFairnessKey()).isEqualTo("integration-client");
                assertThat(task.getWeight()).isEqualTo(new BigDecimal("1.5000"));
                assertThat(task.getStatus()).isEqualTo(TaskStatus.RECEIVED);
                assertThat(task.isSequential()).isFalse();
                assertThat(task.getPayload()).isEqualTo(payload);
                assertThat(task.getCreatedAt()).isNotNull();
            });
    }

    @Test
    void shouldRejectTaskWithBlankFairnessKey() {
        String payloadBase64 = Base64.getEncoder().encodeToString("test".getBytes());
        String requestBody = """
            {
                "fairnessKey": "",
                "weight": 1.0,
                "payload": "%s",
                "sequential": false,
                "requiresPreviousResult": false
            }
            """.formatted(payloadBase64);

        HttpClientResponseException thrown = assertThrows(HttpClientResponseException.class,
            () -> httpClient.toBlocking().exchange(
                HttpRequest.POST("/api/v1/tasks", requestBody)
                    .header("X-API-Key", "test-api-key")
                    .contentType(MediaType.APPLICATION_JSON),
                String.class));
        assertThat(thrown.getStatus().getCode()).isEqualTo(400);
    }
}
