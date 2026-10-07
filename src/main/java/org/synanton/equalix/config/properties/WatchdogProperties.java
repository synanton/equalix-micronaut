package org.synanton.equalix.config.properties;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.context.annotation.ConfigurationProperties;

/** Configuration for the watchdog reconciliation job. */
@ConfigurationProperties("app.watchdog")
@Introspected
public class WatchdogProperties {

    private long intervalMinutes;

    /**
     * Maximum number of fairness keys exported as {@code equalix.cms.estimation.drift{fairnessKey}} per run,
     * largest {@code |drift|} first. Bounds Prometheus series cardinality; aggregates always cover every key.
     */
    private int driftMetricMaxKeys;

    public long getIntervalMinutes() {
        return intervalMinutes;
    }

    public void setIntervalMinutes(long intervalMinutes) {
        this.intervalMinutes = intervalMinutes;
    }

    public int getDriftMetricMaxKeys() {
        return driftMetricMaxKeys;
    }

    public void setDriftMetricMaxKeys(int driftMetricMaxKeys) {
        this.driftMetricMaxKeys = driftMetricMaxKeys;
    }
}
