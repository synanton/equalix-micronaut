package org.synanton.equalix.integration;

import static org.assertj.core.api.Assertions.assertThat;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import io.micronaut.test.support.TestPropertyProvider;
import org.testcontainers.containers.PostgreSQLContainer;
import org.synanton.equalix.domain.port.in.TaskCompletionPort;
import org.synanton.equalix.domain.port.in.TaskIngestionPort;
import org.synanton.equalix.domain.port.out.CMSProviderPort;
import org.synanton.equalix.domain.service.DispatcherService;
import org.synanton.equalix.domain.service.PriorityCalculatorService;
import org.synanton.equalix.domain.service.WatchdogService;

/**
 * EQX-5: the watchdog publishes {@code equalix.cms.estimation.drift{fairnessKey}} before rebuilding the sketch,
 * and removes the series once the key stops drifting.
 *
 * <p>Drift is injected as a phantom +3 on an idle key, applied outside any transaction, as a crash between the
 * database commit and the sketch update would leave it. The rebuild after the measurement clears it.
 */
class CmsDriftMetricIntegrationTest extends BaseIntegrationTest implements TestPropertyProvider {

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



    private static final byte[] PAYLOAD = "drift".getBytes();

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
    private WatchdogService watchdogService;

    @Inject
    private CMSProviderPort cms;

    @Test
    void shouldPublishDriftPerKeyAndRemoveItWhenDriftClears() {
        String prefix = "drift-" + UUID.randomUUID().toString().substring(0, 8) + "-";
        String busyKey = prefix + "busy";
        String idleKey = prefix + "idle";
        for (int index = 0; index < 3; index++) {
            taskIngestion.createTask(busyKey, BigDecimal.ONE, PAYLOAD, false, null, null, false);
        }
        UUID idleTask = taskIngestion.createTask(idleKey, BigDecimal.ONE, PAYLOAD, false, null, null, false).getId();
        priorityCalculatorService.run();
        dispatcherService.dispatch();
        taskCompletion.completeTask(idleTask, true, null, null);
        cms.add(idleKey, 3);

        watchdogService.reconcile();

        // The idle key has 0 tasks in flight but is estimated at 3; the busy key is exact.
        assertThat(scrape())
            .contains("equalix_cms_estimation_drift{fairnessKey=\"" + idleKey + "\",layer=\"key\"} 3.0")
            .doesNotContain("fairnessKey=\"" + busyKey + "\"")
            .contains("equalix_cms_estimation_drift_max 3.0")
            .contains("equalix_cms_estimation_drift_min 0.0")
            .contains("equalix_cms_estimation_drift_keys 1.0")
            .contains("equalix_cms_estimation_drift_keys_sampled 2.0")
            .contains("equalix_cms_estimation_drift_absolute 3.0")
            .contains("equalix_cms_estimation_drift_timestamp_seconds " + (double) clock.instant().getEpochSecond());

        // The first run rebuilt the sketch from the task table, so the next measurement finds no drift.
        watchdogService.reconcile();

        assertThat(scrape())
            .doesNotContain("fairnessKey=\"" + idleKey + "\"")
            .contains("equalix_cms_estimation_drift_keys 0.0");
    }

    private String scrape() {
        int status = httpClient.toBlocking().exchange(
                HttpRequest.GET("/prometheus").header("X-API-Key", "test-api-key"),
                String.class).getStatus().getCode();
        assertThat(status).isEqualTo(200);
        return httpClient.toBlocking().retrieve(
            HttpRequest.GET("/prometheus").header("X-API-Key", "test-api-key")
                .contentType(MediaType.TEXT_PLAIN));
    }
}
