package org.synanton.equalix.config.properties;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.context.annotation.ConfigurationProperties;

/** Configuration for the sequential execution extension. */
@ConfigurationProperties("app.queue.sequential")
@Introspected
public class SequentialProperties {

    private boolean enabled;
    private long clientBlockTimeoutMs;
    private long dispatcherInterval;
    private long blockRecoveryInterval;
    private long resultPassthroughInterval;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public long getClientBlockTimeoutMs() {
        return clientBlockTimeoutMs;
    }

    public void setClientBlockTimeoutMs(long clientBlockTimeoutMs) {
        this.clientBlockTimeoutMs = clientBlockTimeoutMs;
    }

    public long getDispatcherInterval() {
        return dispatcherInterval;
    }

    public void setDispatcherInterval(long dispatcherInterval) {
        this.dispatcherInterval = dispatcherInterval;
    }

    public long getBlockRecoveryInterval() {
        return blockRecoveryInterval;
    }

    public void setBlockRecoveryInterval(long blockRecoveryInterval) {
        this.blockRecoveryInterval = blockRecoveryInterval;
    }

    public long getResultPassthroughInterval() {
        return resultPassthroughInterval;
    }

    public void setResultPassthroughInterval(long resultPassthroughInterval) {
        this.resultPassthroughInterval = resultPassthroughInterval;
    }
}
