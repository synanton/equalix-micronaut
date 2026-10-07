package org.synanton.equalix.config;

import io.micronaut.context.annotation.Factory;
import jakarta.inject.Singleton;
import java.time.Clock;

/**
 * Framework beans. Micronaut registers {@code @ConfigurationProperties} classes automatically,
 * so no {@code EnableConfigurationProperties} equivalent is needed.
 */
@Factory
public class AppConfig {

    @Singleton
    public Clock clock() {
        return Clock.systemUTC();
    }
}
