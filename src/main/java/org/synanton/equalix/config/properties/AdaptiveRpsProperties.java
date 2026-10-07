package org.synanton.equalix.config.properties;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.context.annotation.ConfigurationProperties;

/** Configuration for the adaptive RPS controller. */
@ConfigurationProperties("app.adaptive-rps")
@Introspected
public class AdaptiveRpsProperties {

    private boolean enabled;
    private double initialRps;
    private double minRps;
    private double maxRps;
    private long targetLatencyMs;
    private double errorThreshold;
    private double latencyThreshold;
    private int windowSize;
    private int minSamples;
    private double emergencyFactor;
    private double decreaseFactor;
    private double increaseFactor;
    private double increaseErrorThreshold;

    /** EMA weight of the newest window mean in (0, 1]; 1 disables smoothing. */
    private double latencyEmaAlpha;

    /** Minimum time between two RPS adjustments; 0 adjusts on every completion (pre-EQX-6 behaviour). */
    private long adjustmentIntervalMs;

    /** Consecutive agreeing evaluations required to reverse direction; 1 reverses immediately. */
    private int directionChangeConfirmations;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public double getInitialRps() {
        return initialRps;
    }

    public void setInitialRps(double initialRps) {
        this.initialRps = initialRps;
    }

    public double getMinRps() {
        return minRps;
    }

    public void setMinRps(double minRps) {
        this.minRps = minRps;
    }

    public double getMaxRps() {
        return maxRps;
    }

    public void setMaxRps(double maxRps) {
        this.maxRps = maxRps;
    }

    public long getTargetLatencyMs() {
        return targetLatencyMs;
    }

    public void setTargetLatencyMs(long targetLatencyMs) {
        this.targetLatencyMs = targetLatencyMs;
    }

    public double getErrorThreshold() {
        return errorThreshold;
    }

    public void setErrorThreshold(double errorThreshold) {
        this.errorThreshold = errorThreshold;
    }

    public double getLatencyThreshold() {
        return latencyThreshold;
    }

    public void setLatencyThreshold(double latencyThreshold) {
        this.latencyThreshold = latencyThreshold;
    }

    public int getWindowSize() {
        return windowSize;
    }

    public void setWindowSize(int windowSize) {
        this.windowSize = windowSize;
    }

    public int getMinSamples() {
        return minSamples;
    }

    public void setMinSamples(int minSamples) {
        this.minSamples = minSamples;
    }

    public double getEmergencyFactor() {
        return emergencyFactor;
    }

    public void setEmergencyFactor(double emergencyFactor) {
        this.emergencyFactor = emergencyFactor;
    }

    public double getDecreaseFactor() {
        return decreaseFactor;
    }

    public void setDecreaseFactor(double decreaseFactor) {
        this.decreaseFactor = decreaseFactor;
    }

    public double getIncreaseFactor() {
        return increaseFactor;
    }

    public void setIncreaseFactor(double increaseFactor) {
        this.increaseFactor = increaseFactor;
    }

    public double getIncreaseErrorThreshold() {
        return increaseErrorThreshold;
    }

    public void setIncreaseErrorThreshold(double increaseErrorThreshold) {
        this.increaseErrorThreshold = increaseErrorThreshold;
    }

    public double getLatencyEmaAlpha() {
        return latencyEmaAlpha;
    }

    public void setLatencyEmaAlpha(double latencyEmaAlpha) {
        this.latencyEmaAlpha = latencyEmaAlpha;
    }

    public long getAdjustmentIntervalMs() {
        return adjustmentIntervalMs;
    }

    public void setAdjustmentIntervalMs(long adjustmentIntervalMs) {
        this.adjustmentIntervalMs = adjustmentIntervalMs;
    }

    public int getDirectionChangeConfirmations() {
        return directionChangeConfirmations;
    }

    public void setDirectionChangeConfirmations(int directionChangeConfirmations) {
        this.directionChangeConfirmations = directionChangeConfirmations;
    }
}
