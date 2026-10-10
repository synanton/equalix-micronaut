package org.synanton.equalix.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.test.support.TestPropertyProvider;
import org.testcontainers.containers.PostgreSQLContainer;
import org.synanton.equalix.domain.model.TaskStatus;
import org.synanton.equalix.domain.port.in.TaskCompletionPort;
import org.synanton.equalix.domain.port.in.TaskIngestionPort;
import org.synanton.equalix.domain.port.out.TaskRepositoryPort;
import org.synanton.equalix.domain.service.DispatchAckService;
import org.synanton.equalix.domain.service.DispatcherService;
import org.synanton.equalix.domain.service.PriorityCalculatorService;
import org.synanton.equalix.domain.service.SequentialDispatcherService;

/**
 * DB-load benchmark (Micronaut port of the oracle's {@code DbLoadBenchmarkTest}):
 * drives a fixed workload (single tenant, quota off, RPS control off) through the full
 * lifecycle and reports wall-clock time per phase. Used to compare statement counts/timings
 * before and after the write-path optimizations (bulk dispatch UPDATE, targeted
 * completion/ack UPDATEs, bulk promotion, batched counts).
 *
 * <p>Method: Testcontainers PostgreSQL, scheduling off, jobs driven explicitly. One
 * measured pass after the mock setup (no warmup iterations — JIT/DB-cache noise applies
 * equally to both sides of a before/after comparison on the same host). Timings print
 * to stdout; assertions cover correctness only, never performance.
 */
class DbLoadBenchmarkTest extends BaseIntegrationTest implements TestPropertyProvider {

    @Override
    public java.util.Map<String, String> getProperties() {
        PostgreSQLContainer<?> pg = TestPostgres.container();
        java.util.Map<String, String> props = new java.util.HashMap<>(integrationProperties());
        props.put("datasources.default.url", pg.getJdbcUrl());
        props.put("datasources.default.username", pg.getUsername());
        props.put("datasources.default.password", pg.getPassword());
        props.put("datasources.default.driver-class-name", "org.postgresql.Driver");
        props.put("app.queue.max-per-client-quota", "0");
        props.put("app.queue.max-tasks-in-process", "500");
        props.put("app.queue.worker-poll-size", "500");
        props.put("app.queue.max-queued-time-ms", "3153600000000");
        return props;
    }

    static final int TASKS = 400;
    private static final byte[] PAYLOAD = "benchmark".getBytes();

    /** Test DataSource with a statement counter (this test's context only). */
    @Factory
    static class CountingDataSourceFactory {
        @Singleton
        @Replaces(DataSource.class)
        @jakarta.inject.Named("default")
        DataSource countingDataSource() {
            PostgreSQLContainer<?> pg = TestPostgres.container();
            com.zaxxer.hikari.HikariConfig config = new com.zaxxer.hikari.HikariConfig();
            config.setJdbcUrl(pg.getJdbcUrl());
            config.setUsername(pg.getUsername());
            config.setPassword(pg.getPassword());
            config.setDriverClassName("org.postgresql.Driver");
            // Room for 4 concurrent dispatch transactions (multi-tenant scenario below).
            config.setMaximumPoolSize(10);
            config.setMinimumIdle(2);
            return CountingDataSource.wrap(new com.zaxxer.hikari.HikariDataSource(config));
        }
    }

    @Inject
    private TaskIngestionPort taskIngestion;

    @Inject
    private TaskCompletionPort taskCompletion;

    @Inject
    private TaskRepositoryPort taskRepository;

    @Inject
    private PriorityCalculatorService priorityCalculatorService;

    @Inject
    private DispatcherService dispatcherService;

    @Inject
    private DispatchAckService dispatchAckService;

    @Inject
    private SequentialDispatcherService sequentialDispatcherService;

    @Test
    void measureFullLifecycle() {
        String tenant = "bench-" + UUID.randomUUID().toString().substring(0, 8);
        List<UUID> sent = new ArrayList<>();
        doAnswer(invocation -> sent.add(invocation.getArgument(0)))
            .when(remoteExecutor).send(any(), any(), any());

        Timed ingest = timed(() -> {
            for (int i = 0; i < TASKS; i++) {
                taskIngestion.createTask(tenant, BigDecimal.ONE, PAYLOAD, false, null, null, false);
            }
        });
        long ingestStmts = ingest.stmts();

        Timed calc = timed(() -> {
            for (int i = 0; i < 10
                && taskRepository.findByStatus(TaskStatus.RECEIVED, TASKS).size() > 0; i++) {
                priorityCalculatorService.run();
            }
        });
        long calcStmts = calc.stmts();
        assertThat(taskRepository.findByStatus(TaskStatus.QUEUED, TASKS)).hasSize(TASKS);

        Timed dispatch = timed(() -> {
            for (int i = 0; i < 10 && sent.size() < TASKS; i++) {
                dispatcherService.dispatch();
            }
        });
        assertThat(sent).hasSize(TASKS);

        List<UUID> dispatched = new ArrayList<>(sent);
        Timed ack = timed(() -> dispatched.forEach(dispatchAckService::markCommitted));

        Timed complete = timed(() -> dispatched.forEach(
            id -> taskCompletion.completeTask(id, true, PAYLOAD, null)));
        assertThat(taskRepository.findByStatus(TaskStatus.SUCCEEDED, TASKS)).hasSize(TASKS);

        long totalMs = ingest.ms() + calc.ms() + dispatch.ms() + ack.ms() + complete.ms();
        long totalStmts = ingestStmts + calcStmts + dispatch.stmts() + ack.stmts() + complete.stmts();
        System.out.printf(
            "DBLOAD tasks=%d ingest=%dms/%dstmts (%.0f/s) calc=%dms/%dstmts (%.0f/s) "
                + "dispatch=%dms/%dstmts (%.0f/s) ack=%dms/%dstmts (%.0f/s) "
                + "complete=%dms/%dstmts (%.0f/s) total=%dms/%dstmts (%.0f/s)%n",
            TASKS,
            ingest.ms(), ingestStmts, rate(ingest.ms()),
            calc.ms(), calcStmts, rate(calc.ms()),
            dispatch.ms(), dispatch.stmts(), rate(dispatch.ms()),
            ack.ms(), ack.stmts(), rate(ack.ms()),
            complete.ms(), complete.stmts(), rate(complete.ms()),
            totalMs, totalStmts, rate(totalMs));
    }

    private record Timed(long ms, long stmts) {
    }

    /**
     * Multi-tenant concurrent workload (oracle mirror): 10 tenants × 40 tasks dispatched
     * by 4 competing dispatchers, plus a 10-key sequential leg. Reports per-phase
     * wall-clock and statements, per-task time-to-dispatch percentiles, and statements
     * per task.
     */
    @Test
    void measureMultiTenantConcurrent() throws Exception {
        int tenants = 10;
        int perTenant = 40;
        int total = tenants * perTenant;
        String runId = UUID.randomUUID().toString().substring(0, 8);
        List<String> keys = IntStream.range(0, tenants)
            .mapToObj(i -> "bench-mt-" + runId + "-" + i).toList();
        Map<UUID, Long> ingestNanos = new ConcurrentHashMap<>();
        Map<UUID, Long> sendNanos = new ConcurrentHashMap<>();
        List<UUID> sent = Collections.synchronizedList(new ArrayList<>());
        doAnswer(invocation -> {
            UUID id = invocation.getArgument(0);
            sendNanos.put(id, System.nanoTime());
            sent.add(id);
            return null;
        }).when(remoteExecutor).send(any(), any(), any());

        Timed ingest = timed(() -> keys.forEach(key -> {
            for (int i = 0; i < perTenant; i++) {
                UUID id = taskIngestion.createTask(key, BigDecimal.ONE, PAYLOAD, false, null, null,
                    false).getId();
                ingestNanos.put(id, System.nanoTime());
            }
        }));

        Timed calc = timed(() -> {
            for (int i = 0; i < 10
                && taskRepository.findByStatus(TaskStatus.RECEIVED, total).size() > 0; i++) {
                priorityCalculatorService.run();
            }
        });

        Timed dispatch = timed(() -> {
            ExecutorService pool = Executors.newFixedThreadPool(4);
            List<Callable<Void>> ticks = IntStream.range(0, 40)
                .mapToObj(i -> (Callable<Void>) () -> {
                    dispatcherService.dispatch();
                    return null;
                })
                .toList();
            try {
                for (java.util.concurrent.Future<Void> tick : pool.invokeAll(ticks)) {
                    tick.get(2, TimeUnit.MINUTES);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException e) {
                throw new IllegalStateException("dispatch tick failed under contention", e);
            } finally {
                pool.shutdown();
            }
            try {
                if (!pool.awaitTermination(2, TimeUnit.MINUTES)) {
                    throw new IllegalStateException("dispatch pool did not terminate");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        });
        assertThat(sent).hasSize(total);
        synchronized (sent) {
            assertThat(sent.stream().distinct().count()).isEqualTo(total);
        }

        long[] latencies;
        synchronized (sent) {
            latencies = sent.stream()
                .mapToLong(id -> (sendNanos.get(id) - ingestNanos.get(id)) / 1_000)
                .sorted().toArray();
        }
        double p50 = percentile(latencies, 50) / 1000.0;
        double p95 = percentile(latencies, 95) / 1000.0;
        double p99 = percentile(latencies, 99) / 1000.0;

        List<UUID> dispatched;
        synchronized (sent) {
            dispatched = new ArrayList<>(sent);
        }
        Timed ack = timed(() -> dispatched.forEach(dispatchAckService::markCommitted));

        Timed complete = timed(() -> dispatched.forEach(
            id -> taskCompletion.completeTask(id, true, PAYLOAD, null)));
        assertThat(taskRepository.findByStatus(TaskStatus.SUCCEEDED, total)).hasSize(total);

        // Sequential leg: one ordered task per key through the sequential dispatcher.
        List<UUID> seqIds = new ArrayList<>();
        Timed seqIngest = timed(() -> keys.forEach(key -> seqIds.add(taskIngestion
            .createTask(key, BigDecimal.ONE, PAYLOAD, true, 1L, null, false).getId())));
        Timed seqCalc = timed(() -> {
            for (int i = 0; i < 10
                && taskRepository.findByStatus(TaskStatus.RECEIVED, tenants).size() > 0; i++) {
                priorityCalculatorService.run();
            }
        });
        Timed seqDispatch = timed(sequentialDispatcherService::dispatch);
        int flatTotal = total + tenants;
        assertThat(sent).hasSize(flatTotal);
        Timed seqComplete = timed(() -> new ArrayList<>(seqIds).forEach(
            id -> taskCompletion.completeTask(id, true, PAYLOAD, null)));
        assertThat(taskRepository.findByStatus(TaskStatus.SUCCEEDED, flatTotal)).hasSize(flatTotal);

        long totalMs = ingest.ms() + calc.ms() + dispatch.ms() + ack.ms() + complete.ms()
            + seqIngest.ms() + seqCalc.ms() + seqDispatch.ms() + seqComplete.ms();
        long totalStmts = ingest.stmts() + calc.stmts() + dispatch.stmts() + ack.stmts()
            + complete.stmts() + seqIngest.stmts() + seqCalc.stmts() + seqDispatch.stmts()
            + seqComplete.stmts();
        System.out.printf(
            "DBLOAD-MT tenants=%d tasks=%d dispatch-threads=4 "
                + "ingest=%dms/%dstmts calc=%dms/%dstmts dispatch=%dms/%dstmts "
                + "ack=%dms/%dstmts complete=%dms/%dstmts "
                + "seq(ingest/calc/dispatch/complete)=%d/%d/%d/%dms %d/%d/%d/%dstmts "
                + "dispatch-latency-p50=%.1fms p95=%.1fms p99=%.1fms "
                + "total=%dms/%dstmts (%.1f stmts/task)%n",
            tenants, flatTotal,
            ingest.ms(), ingest.stmts(), calc.ms(), calc.stmts(), dispatch.ms(), dispatch.stmts(),
            ack.ms(), ack.stmts(), complete.ms(), complete.stmts(),
            seqIngest.ms(), seqCalc.ms(), seqDispatch.ms(), seqComplete.ms(),
            seqIngest.stmts(), seqCalc.stmts(), seqDispatch.stmts(), seqComplete.stmts(),
            p50, p95, p99, totalMs, totalStmts, (double) totalStmts / flatTotal);
    }

    private static long percentile(long[] sorted, int percent) {
        if (sorted.length == 0) {
            return 0;
        }
        return sorted[Math.min(sorted.length - 1, (int) Math.ceil(percent / 100.0 * sorted.length) - 1)];
    }

    private Timed timed(Runnable work) {
        CountingDataSource.reset();
        long start = System.nanoTime();
        work.run();
        return new Timed((System.nanoTime() - start) / 1_000_000, CountingDataSource.count());
    }

    private double rate(long ms) {
        return ms == 0 ? 0 : TASKS * 1000.0 / ms;
    }
}
