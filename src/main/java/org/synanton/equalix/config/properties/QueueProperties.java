package org.synanton.equalix.config.properties;

import io.micronaut.core.annotation.Introspected;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.synanton.equalix.domain.model.AgingPolicy;
import org.synanton.equalix.domain.model.FairnessMode;
import io.micronaut.context.annotation.ConfigurationProperties;

import io.micronaut.validation.Validated;

/** Root configuration for queue scheduling behaviour. */
@Validated
@ConfigurationProperties("app.queue")
@Introspected
public class QueueProperties {

    private int maxTasksInProcess;
    private int maxPerClientQuota;
    private long priorityCalcInterval;
    private long dispatcherInterval;
    private int workerPollSize;
    private long maxQueuedTimeMs;
    private long taskTimeoutMs;
    private int maxPayloadBytes;

    /** {@code flat} (default) or {@code hierarchical}; see {@link HierarchicalProperties}. */
    private FairnessMode fairnessMode;

    private CmsProperties cms = new CmsProperties();

    private VirtualTimeProperties virtualTime = new VirtualTimeProperties();

    @Valid
    private AgingProperties aging = new AgingProperties();

    public int getMaxTasksInProcess() {
        return maxTasksInProcess;
    }

    public void setMaxTasksInProcess(int maxTasksInProcess) {
        this.maxTasksInProcess = maxTasksInProcess;
    }

    public int getMaxPerClientQuota() {
        return maxPerClientQuota;
    }

    public void setMaxPerClientQuota(int maxPerClientQuota) {
        this.maxPerClientQuota = maxPerClientQuota;
    }

    public long getPriorityCalcInterval() {
        return priorityCalcInterval;
    }

    public void setPriorityCalcInterval(long priorityCalcInterval) {
        this.priorityCalcInterval = priorityCalcInterval;
    }

    public long getDispatcherInterval() {
        return dispatcherInterval;
    }

    public void setDispatcherInterval(long dispatcherInterval) {
        this.dispatcherInterval = dispatcherInterval;
    }

    public int getWorkerPollSize() {
        return workerPollSize;
    }

    public void setWorkerPollSize(int workerPollSize) {
        this.workerPollSize = workerPollSize;
    }

    public long getMaxQueuedTimeMs() {
        return maxQueuedTimeMs;
    }

    public void setMaxQueuedTimeMs(long maxQueuedTimeMs) {
        this.maxQueuedTimeMs = maxQueuedTimeMs;
    }

    public long getTaskTimeoutMs() {
        return taskTimeoutMs;
    }

    public void setTaskTimeoutMs(long taskTimeoutMs) {
        this.taskTimeoutMs = taskTimeoutMs;
    }

    public int getMaxPayloadBytes() {
        return maxPayloadBytes;
    }

    public void setMaxPayloadBytes(int maxPayloadBytes) {
        this.maxPayloadBytes = maxPayloadBytes;
    }

    public FairnessMode getFairnessMode() {
        return fairnessMode;
    }

    public void setFairnessMode(FairnessMode fairnessMode) {
        this.fairnessMode = fairnessMode;
    }

    public CmsProperties getCms() {
        return cms;
    }

    public void setCms(CmsProperties cms) {
        this.cms = cms;
    }

    public VirtualTimeProperties getVirtualTime() {
        return virtualTime;
    }

    public void setVirtualTime(VirtualTimeProperties virtualTime) {
        this.virtualTime = virtualTime;
    }

    public AgingProperties getAging() {
        return aging;
    }

    public void setAging(AgingProperties aging) {
        this.aging = aging;
    }

    /** Configuration for the Count-Min Sketch (local or Redis-backed). */
    @ConfigurationProperties("cms")
    @Introspected
    public static class CmsProperties {

        /** Number of columns in the CMS matrix; larger values reduce error magnitude (ε = 2/width). */
        private int width;

        /** Number of hash rows; larger values reduce error probability (δ = (1/2)^depth). */
        private int depth;

        /**
         * CMS adapter mode: {@code local} (default, in-memory sketch) or
         * {@code redis} (distributed, shared across all instances).
         */
        private String mode = "local";

        private RedisProperties redis = new RedisProperties();

        private ErrorSamplingProperties errorSampling = new ErrorSamplingProperties();

        @Introspected
        public static class RedisProperties {

            /** Redis hash key used to store the CMS matrix. */
            private String keyNamespace = "equalix:cms";

            /** Fall back to the local in-memory CMS if Redis is unreachable. */
            private boolean fallbackToLocal = true;

            public String getKeyNamespace() {
                return keyNamespace;
            }

            public void setKeyNamespace(String keyNamespace) {
                this.keyNamespace = keyNamespace;
            }

            public boolean isFallbackToLocal() {
                return fallbackToLocal;
            }

            public void setFallbackToLocal(boolean fallbackToLocal) {
                this.fallbackToLocal = fallbackToLocal;
            }
        }

        /** Periodic sampling of the CMS estimation error against the task table (EQX-2); meant for load tests. */
        @Introspected
        public static class ErrorSamplingProperties {

            private boolean enabled;

            /** Delay between samples; each sample runs one GROUP BY over in-flight tasks. */
            private long intervalMs;

            public boolean isEnabled() {
                return enabled;
            }

            public void setEnabled(boolean enabled) {
                this.enabled = enabled;
            }

            public long getIntervalMs() {
                return intervalMs;
            }

            public void setIntervalMs(long intervalMs) {
                this.intervalMs = intervalMs;
            }
        }

        public int getWidth() {
            return width;
        }

        public void setWidth(int width) {
            this.width = width;
        }

        public int getDepth() {
            return depth;
        }

        public void setDepth(int depth) {
            this.depth = depth;
        }

        public String getMode() {
            return mode;
        }

        public void setMode(String mode) {
            this.mode = mode;
        }

        public RedisProperties getRedis() {
            return redis;
        }

        public void setRedis(RedisProperties redis) {
            this.redis = redis;
        }

        public ErrorSamplingProperties getErrorSampling() {
            return errorSampling;
        }

        public void setErrorSampling(ErrorSamplingProperties errorSampling) {
            this.errorSampling = errorSampling;
        }
    }

    /** Configuration for persistent weighted virtual time (T_k). */
    @ConfigurationProperties("virtual-time")
    @Introspected
    public static class VirtualTimeProperties {

        /**
         * Virtual-time units charged for one unit-cost task at weight 1.0; a task advances its key by
         * {@code quantum * cost / weight}. Sized relative to the in-flight pressure term, which is expressed in
         * milliseconds ({@code 1000 / currentRps} per in-flight task).
         */
        private double quantum;

        public double getQuantum() {
            return quantum;
        }

        public void setQuantum(double quantum) {
            this.quantum = quantum;
        }
    }

    /** Configuration for anti-starvation aging A(W), evaluated by the dispatcher at selection time. */
    @ConfigurationProperties("aging")
    @Introspected
    public static class AgingProperties {

        /** Aging function: {@code none}, {@code linear}, {@code log} or {@code power}. */
        private AgingPolicy policy;

        /** Aging rate λ in priority units (one weight-1 task advances virtual time by {@code virtual-time.quantum}). */
        @PositiveOrZero
        private double lambda;

        /** Exponent γ for the {@code power} policy. */
        @Positive
        private double gamma;

        /**
         * Rows the dispatcher locks from each ordering (best base priority, oldest arrival) before re-ranking by aged
         * priority. Larger pools rank more exactly at the cost of locking more rows per tick.
         */
        @Positive
        private int candidatePoolSize;

        public AgingPolicy getPolicy() {
            return policy;
        }

        public void setPolicy(AgingPolicy policy) {
            this.policy = policy;
        }

        public double getLambda() {
            return lambda;
        }

        public void setLambda(double lambda) {
            this.lambda = lambda;
        }

        public double getGamma() {
            return gamma;
        }

        public void setGamma(double gamma) {
            this.gamma = gamma;
        }

        public int getCandidatePoolSize() {
            return candidatePoolSize;
        }

        public void setCandidatePoolSize(int candidatePoolSize) {
            this.candidatePoolSize = candidatePoolSize;
        }
    }
}
