package org.synanton.equalix.config.properties;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.context.annotation.ConfigurationProperties;

/** API authentication settings. */
@ConfigurationProperties("app.security")
@Introspected
public class SecurityProperties {

    /** Shared secret expected in the X-API-Key header. Override via EQUALIX_API_KEY in production. */
    private String apiKey;

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }
}
