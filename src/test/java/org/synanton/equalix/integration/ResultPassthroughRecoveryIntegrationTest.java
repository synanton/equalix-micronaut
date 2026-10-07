package org.synanton.equalix.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import jakarta.inject.Inject;
import java.util.Base64;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import io.micronaut.test.support.TestPropertyProvider;
import org.testcontainers.containers.PostgreSQLContainer;
import org.synanton.equalix.domain.service.PriorityCalculatorService;
import org.synanton.equalix.domain.service.ResultPassthroughRecoveryService;
import org.synanton.equalix.domain.service.SequentialDispatcherService;

class ResultPassthroughRecoveryIntegrationTest extends BaseIntegrationTest implements TestPropertyProvider {

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



    private static final String API_KEY = "test-api-key";

    @Inject
    @Client("/")
    private HttpClient httpClient;

    @Inject
    private ObjectMapper objectMapper;

    @Inject
    private PriorityCalculatorService priorityCalculatorService;

    @Inject
    private SequentialDispatcherService sequentialDispatcherService;

    @Inject
    private ResultPassthroughRecoveryService resultPassthroughRecoveryService;

    @Test
    void shouldFailDependentTaskWhenPredecessorFailed() throws Exception {
        String fairnessKey = "passthrough-client-" + UUID.randomUUID();
        UUID predecessorId = createSequentialTask(fairnessKey, 1, null);
        UUID dependentId = createSequentialTask(fairnessKey, 2, predecessorId);

        priorityCalculatorService.run();
        sequentialDispatcherService.dispatch();
        int completeStatus = httpClient.toBlocking().exchange(
                HttpRequest.POST("/api/v1/tasks/" + predecessorId + "/complete",
                        """
                        {"success": false, "error": "executor crashed"}
                        """)
                    .header("X-API-Key", API_KEY)
                    .contentType(MediaType.APPLICATION_JSON),
                String.class).getStatus().getCode();
        assertThat(completeStatus).isLessThan(300);

        resultPassthroughRecoveryService.recover();

        String body = httpClient.toBlocking().retrieve(
            HttpRequest.GET("/api/v1/tasks/" + dependentId).header("X-API-Key", API_KEY));
        JsonNode node = objectMapper.readTree(body);
        assertThat(node.get("status").asText()).isEqualTo("FAILED");
        assertThat(node.get("lastError").asText()).isEqualTo("Dependency failed: executor crashed");
    }

    private UUID createSequentialTask(String fairnessKey, long sequenceNumber, @Nullable UUID dependsOnTaskId) {
        String payload = Base64.getEncoder().encodeToString("passthrough".getBytes());
        String dependency = dependsOnTaskId == null ? "null" : "\"" + dependsOnTaskId + "\"";
        String body = """
            {
                "fairnessKey": "%s",
                "weight": 1.0,
                "payload": "%s",
                "sequential": true,
                "sequenceNumber": %d,
                "dependsOnTaskId": %s,
                "requiresPreviousResult": %s
            }
            """.formatted(fairnessKey, payload, sequenceNumber, dependency, dependsOnTaskId != null);

        String response = httpClient.toBlocking().exchange(
                HttpRequest.POST("/api/v1/tasks", body)
                    .header("X-API-Key", API_KEY)
                    .contentType(MediaType.APPLICATION_JSON),
                String.class).getBody().orElseThrow();
        return UUID.fromString(response.replace("\"", ""));
    }
}
