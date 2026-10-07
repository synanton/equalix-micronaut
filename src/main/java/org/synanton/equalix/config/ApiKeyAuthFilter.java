package org.synanton.equalix.config;

import io.micronaut.context.annotation.Factory;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.filter.HttpServerFilter;
import io.micronaut.http.filter.ServerFilterChain;
import jakarta.inject.Singleton;
import jakarta.inject.Inject;
import org.reactivestreams.Publisher;
import org.synanton.equalix.config.properties.SecurityProperties;
import reactor.core.publisher.Mono;

/**
 * API-key gate. Same contract as the Spring oracle: {@code X-API-Key} must equal
 * {@code app.security.api-key}, otherwise {@code 401}. Health/info stay open so the
 * container harness can probe readiness without credentials.
 */
@Singleton
@Filter("/**")
public class ApiKeyAuthFilter implements HttpServerFilter {

    @Inject
    public ApiKeyAuthFilter(SecurityProperties securityProperties) {
        this.securityProperties = securityProperties;
    }

    public static final String API_KEY_HEADER = "X-API-Key";

    private final SecurityProperties securityProperties;

    @Override
    public Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
        String path = request.getPath();
        if (path.equals("/health") || path.startsWith("/health/")
                || path.equals("/info") || path.startsWith("/info/")) {
            return chain.proceed(request);
        }
        String expected = securityProperties.getApiKey();
        String provided = request.getHeaders().get(API_KEY_HEADER);
        if (expected != null && !expected.isBlank() && expected.equals(provided)) {
            return chain.proceed(request);
        }
        return Mono.just(HttpResponse.status(HttpStatus.UNAUTHORIZED));
    }
}
