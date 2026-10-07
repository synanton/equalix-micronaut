package org.synanton.equalix.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import io.micronaut.test.support.TestPropertyProvider;
import org.testcontainers.containers.PostgreSQLContainer;
import org.synanton.equalix.adapter.out.database.entity.TaskEntity;
import org.synanton.equalix.domain.model.TaskStatus;
import org.synanton.equalix.domain.port.in.TaskIngestionPort;
import org.synanton.equalix.domain.port.out.CMSProviderPort;
import org.synanton.equalix.domain.service.DispatcherService;
import org.synanton.equalix.domain.service.PriorityCalculatorService;

/** CMS updates follow the transaction outcome: applied on commit, discarded on rollback. */
class CmsTransactionConsistencyIntegrationTest extends BaseIntegrationTest implements TestPropertyProvider {

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



    private static final byte[] PAYLOAD = "tx".getBytes();

    @Inject
    private TaskIngestionPort taskIngestion;

    @Inject
    private PriorityCalculatorService priorityCalculatorService;

    @Inject
    private DispatcherService dispatcherService;

    @Inject
    private CMSProviderPort cms;

    @Test
    void shouldNotCountDispatchWhoseTransactionRolledBack() {
        String fairnessKey = "rollback-" + UUID.randomUUID();
        UUID taskId = taskIngestion.createTask(fairnessKey, BigDecimal.ONE, PAYLOAD, false, null, null, false).getId();
        priorityCalculatorService.run();
        doThrow(new IllegalStateException("executor unavailable"))
            .when(remoteExecutor).send(any(), any(), any());

        assertThatThrownBy(() -> dispatcherService.dispatch()).isInstanceOf(IllegalStateException.class);

        assertThat(taskJpaRepository.findById(taskId)).get()
            .extracting(TaskEntity::getStatus)
            .isEqualTo(TaskStatus.QUEUED);
        assertThat(cms.estimateCount(fairnessKey)).as("phantom in-flight after rollback").isZero();
    }

    @Test
    void shouldCountDispatchOnceTransactionCommits() {
        String fairnessKey = "commit-" + UUID.randomUUID();
        taskIngestion.createTask(fairnessKey, BigDecimal.ONE, PAYLOAD, false, null, null, false);
        priorityCalculatorService.run();

        dispatcherService.dispatch();

        assertThat(cms.estimateCount(fairnessKey)).isEqualTo(1);
    }
}
