package org.synanton.equalix.integration;

import static org.assertj.core.api.Assertions.assertThat;

import io.micronaut.context.BeanContext;
import io.micronaut.context.event.StartupEvent;
import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import io.micronaut.test.support.TestPropertyProvider;
import org.testcontainers.containers.PostgreSQLContainer;
import org.synanton.equalix.adapter.in.startup.CmsWarmUpListener;
import org.synanton.equalix.domain.port.in.TaskIngestionPort;
import org.synanton.equalix.domain.port.out.CMSProviderPort;
import org.synanton.equalix.domain.service.DispatcherService;
import org.synanton.equalix.domain.service.PriorityCalculatorService;

/** A restarted instance must not start with an empty sketch while tasks are still in flight. */
class CmsWarmUpIntegrationTest extends BaseIntegrationTest implements TestPropertyProvider {

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



    private static final byte[] PAYLOAD = "warm".getBytes();

    @Inject
    private TaskIngestionPort taskIngestion;

    @Inject
    private PriorityCalculatorService priorityCalculatorService;

    @Inject
    private DispatcherService dispatcherService;

    @Inject
    private CMSProviderPort cms;

    @Inject
    private CmsWarmUpListener cmsWarmUpListener;

    @Inject
    private BeanContext beanContext;

    @Test
    void shouldRebuildSketchFromInFlightTasksWhenApplicationIsReady() {
        String fairnessKey = "warm-" + UUID.randomUUID();
        for (int index = 0; index < 2; index++) {
            taskIngestion.createTask(fairnessKey, BigDecimal.ONE, PAYLOAD, false, null, null, false);
        }
        priorityCalculatorService.run();
        dispatcherService.dispatch();
        cms.rebuild(Map.of()); // what a freshly started instance holds

        cmsWarmUpListener.onApplicationReady(new StartupEvent(beanContext));

        assertThat(cms.estimateCount(fairnessKey)).isEqualTo(2);
    }
}
