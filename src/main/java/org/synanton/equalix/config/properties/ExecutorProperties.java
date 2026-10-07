package org.synanton.equalix.config.properties;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.context.annotation.ConfigurationProperties;

/** HTTP remote executor connection settings. */
@ConfigurationProperties("app.executor")
@Introspected
public class ExecutorProperties {

    private String baseUrl;
    private int connectTimeoutMs;
    private int readTimeoutMs;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(int connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    public int getReadTimeoutMs() {
        return readTimeoutMs;
    }

    public void setReadTimeoutMs(int readTimeoutMs) {
        this.readTimeoutMs = readTimeoutMs;
    }
}
