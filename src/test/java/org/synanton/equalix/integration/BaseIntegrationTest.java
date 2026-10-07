package org.synanton.equalix.integration;

import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.test.annotation.MockBean;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;
import org.synanton.equalix.adapter.out.database.ClientCountsJpaRepository;
import org.synanton.equalix.adapter.out.database.ClientSequenceStateJpaRepository;
import org.synanton.equalix.adapter.out.database.ClientVirtualTimeJpaRepository;
import org.synanton.equalix.adapter.out.database.HierarchyNodeJpaRepository;
import org.synanton.equalix.adapter.out.database.SchedulerVirtualClockJpaRepository;
import org.synanton.equalix.adapter.out.database.TaskJpaRepository;
import org.synanton.equalix.domain.port.out.RemoteExecutorPort;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Micronaut port of the oracle's {@code BaseIntegrationTest}: full application context
 * against real PostgreSQL (one shared container), stubbed executor, scheduling off, jobs
 * driven explicitly so assertions stay deterministic.
 *
 * <p>Wiring note: the container starts in a static initializer and publishes its coordinates
 * as system properties, which Micronaut picks up with the highest precedence. This is
 * deliberate — {@code TestPropertyProvider} on a shared abstract base is not picked up
 * by Micronaut Test, and per-class providers would restart PostgreSQL per test class.
 */
@MicronautTest(transactional = false)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class BaseIntegrationTest {

    /**
     * Shared PostgreSQL, started on first use. The {@code getProperties()} provider below
     * runs during Micronaut's extension setup (before any test instance exists under the
     * default lifecycle, hence {@code PER_CLASS} above), so the container cannot rely on
     * extension ordering — whoever asks first starts it.
     */
    static final class TestPostgres {

        private static PostgreSQLContainer<?> container;

        static synchronized PostgreSQLContainer<?> container() {
            if (container == null) {
                container = new PostgreSQLContainer<>("postgres:16-alpine");
                container.start();
            }
            return container;
        }
    }

    /** Common test properties (same shape as the oracle's test application.yml). */
    static Map<String, String> integrationProperties() {
        return Map.ofEntries(
            Map.entry("app.scheduling.enabled", "false"),
            Map.entry("app.security.api-key", "test-api-key"),
            Map.entry("app.executor.base-url", "http://localhost:9999"),
            Map.entry("app.executor.connect-timeout-ms", "1000"),
            Map.entry("app.executor.read-timeout-ms", "1000"),
            Map.entry("app.queue.max-tasks-in-process", "100"),
            Map.entry("app.queue.max-per-client-quota", "10"),
            Map.entry("app.queue.priority-calc-interval", "100"),
            Map.entry("app.queue.dispatcher-interval", "50"),
            Map.entry("app.queue.worker-poll-size", "10"),
            Map.entry("app.queue.max-queued-time-ms", "60000"),
            Map.entry("app.queue.task-timeout-ms", "300000"),
            Map.entry("app.queue.max-payload-bytes", "1048576"),
            Map.entry("app.queue.fairness-mode", "flat"),
            Map.entry("app.queue.virtual-time.quantum", "1000"),
            Map.entry("app.queue.aging.policy", "none"),
            Map.entry("app.queue.aging.lambda", "1000"),
            Map.entry("app.queue.aging.gamma", "2.0"),
            Map.entry("app.queue.aging.candidate-pool-size", "200"),
            Map.entry("app.queue.cms.mode", "local"),
            Map.entry("app.queue.cms.width", "1024"),
            Map.entry("app.queue.cms.depth", "3"),
            Map.entry("app.queue.cms.error-sampling.enabled", "false"),
            Map.entry("app.queue.cms.error-sampling.interval-ms", "1000"),
            Map.entry("app.queue.sequential.enabled", "true"),
            Map.entry("app.queue.sequential.client-block-timeout-ms", "10000"),
            Map.entry("app.queue.sequential.dispatcher-interval", "50"),
            Map.entry("app.queue.sequential.block-recovery-interval", "1000"),
            Map.entry("app.queue.sequential.result-passthrough-interval", "1000"),
            Map.entry("app.adaptive-rps.enabled", "false"),
            Map.entry("app.adaptive-rps.initial-rps", "10"),
            Map.entry("app.adaptive-rps.min-rps", "1"),
            Map.entry("app.adaptive-rps.max-rps", "100"),
            Map.entry("app.adaptive-rps.target-latency-ms", "200"),
            Map.entry("app.adaptive-rps.error-threshold", "0.05"),
            Map.entry("app.adaptive-rps.latency-threshold", "0.2"),
            Map.entry("app.adaptive-rps.window-size", "100"),
            Map.entry("app.adaptive-rps.min-samples", "10"),
            Map.entry("app.adaptive-rps.emergency-factor", "0.5"),
            Map.entry("app.adaptive-rps.decrease-factor", "0.9"),
            Map.entry("app.adaptive-rps.increase-factor", "1.05"),
            Map.entry("app.adaptive-rps.increase-error-threshold", "0.01"),
            Map.entry("app.adaptive-rps.adjustment-interval-ms", "2000"),
            Map.entry("app.adaptive-rps.latency-ema-alpha", "0.7"),
            Map.entry("app.adaptive-rps.direction-change-confirmations", "3"),
            Map.entry("app.hierarchical.separator", "/"),
            Map.entry("app.hierarchical.metrics-depth", "1"),
            Map.entry("app.watchdog.interval-minutes", "60"),
            Map.entry("app.watchdog.drift-metric-max-keys", "100"));
    }

    @MockBean(RemoteExecutorPort.class)
    RemoteExecutorPort remoteExecutor() {
        return Mockito.mock(RemoteExecutorPort.class);
    }

    @Inject
    protected RemoteExecutorPort remoteExecutor;

    @Inject
    protected TaskJpaRepository taskJpaRepository;

    @Inject
    protected ClientCountsJpaRepository clientCountsJpaRepository;

    @Inject
    protected ClientSequenceStateJpaRepository sequenceStateJpaRepository;

    @Inject
    protected ClientVirtualTimeJpaRepository clientVirtualTimeJpaRepository;

    @Inject
    protected SchedulerVirtualClockJpaRepository schedulerVirtualClockJpaRepository;

    @Inject
    protected HierarchyNodeJpaRepository hierarchyNodeJpaRepository;

    /** Application clock; frozen at context start and reset before each test unless a test advances it. */
    @Inject
    protected AdjustableClock clock;

    @BeforeEach
    void cleanUp() {
        clock.reset();
        Mockito.reset(remoteExecutor);
        clientCountsJpaRepository.deleteAllInBatch();
        sequenceStateJpaRepository.deleteAllInBatch();
        clientVirtualTimeJpaRepository.deleteAllInBatch();
        schedulerVirtualClockJpaRepository.deleteAllInBatch();
        hierarchyNodeJpaRepository.deleteAllInBatch();
        taskJpaRepository.deleteAllInBatch();
    }

    /**
     * Starts at real time so that SQL comparing application timestamps with database {@code now()} (starvation,
     * timeouts) behaves as in production.
     */
    @Factory
    static class ClockTestFactory {

        private final AdjustableClock clock =
            new AdjustableClock(Instant.now().truncatedTo(ChronoUnit.MILLIS));

        @Singleton
        @Replaces(Clock.class)
        AdjustableClock adjustableClock() {
            return clock;
        }
    }
}
