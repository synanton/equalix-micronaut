package org.synanton.equalix.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import io.micronaut.test.support.TestPropertyProvider;
import org.testcontainers.containers.PostgreSQLContainer;
import org.synanton.equalix.domain.model.CmsErrorStatistics;
import org.synanton.equalix.domain.port.in.TaskCompletionPort;
import org.synanton.equalix.domain.port.in.TaskIngestionPort;
import org.synanton.equalix.domain.port.out.CMSProviderPort;
import org.synanton.equalix.domain.service.CmsErrorRecorder;
import org.synanton.equalix.domain.service.DispatcherService;
import org.synanton.equalix.domain.service.PriorityCalculatorService;

/**
 * EQX-2: the error recorder compares the live sketch with in-flight tasks and publishes Prometheus metrics.
 *
 * <p>Drift is injected as a phantom +3 on an idle key, applied outside any transaction, as a crash between the
 * database commit and the sketch update would leave it.
 */
class CmsErrorRecorderIntegrationTest extends BaseIntegrationTest implements TestPropertyProvider {

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



    private static final byte[] PAYLOAD = "cms".getBytes();

    @Inject
    @Client("/")
    private HttpClient httpClient;

    @Inject
    private TaskIngestionPort taskIngestion;

    @Inject
    private TaskCompletionPort taskCompletion;

    @Inject
    private PriorityCalculatorService priorityCalculatorService;

    @Inject
    private DispatcherService dispatcherService;

    @Inject
    private CmsErrorRecorder cmsErrorRecorder;

    @Inject
    private CMSProviderPort cms;

    @Test
    void shouldSampleEstimationErrorAndPublishPrometheusMetrics() {
        String prefix = "cms-" + UUID.randomUUID().toString().substring(0, 8) + "-";
        String busyKey = prefix + "busy";
        String idleKey = prefix + "idle";
        for (int index = 0; index < 3; index++) {
            taskIngestion.createTask(busyKey, BigDecimal.ONE, PAYLOAD, false, null, null, false);
        }
        UUID idleTask = taskIngestion.createTask(idleKey, BigDecimal.ONE, PAYLOAD, false, null, null, false).getId();
        List<UUID> sent = new ArrayList<>();
        doAnswer(invocation -> sent.add(invocation.getArgument(0)))
            .when(remoteExecutor).send(any(), any(), any());
        priorityCalculatorService.run();
        dispatcherService.dispatch();
        taskCompletion.completeTask(idleTask, true, null, null);
        cms.add(idleKey, 3);

        CmsErrorStatistics statistics = cmsErrorRecorder.sample();

        // Busy key: 3 in flight and estimated 3. Idle key: 0 in flight but estimated 3.
        assertThat(sent).hasSize(4);
        assertThat(List.of(statistics.count(), statistics.min(), statistics.max()))
            .containsExactly(2L, 0L, 3L);
        String metrics = httpClient.toBlocking().retrieve(
            HttpRequest.GET("/prometheus").header("X-API-Key", "test-api-key"));
        assertThat(metrics)
            .contains("equalix_cms_estimation_error_count{direction=\"over\"}")
            .contains("equalix_cms_estimation_error_count{direction=\"exact\"}")
            .contains("equalix_cms_estimation_error_magnitude{quantile=\"0.99\"}");
    }
}
