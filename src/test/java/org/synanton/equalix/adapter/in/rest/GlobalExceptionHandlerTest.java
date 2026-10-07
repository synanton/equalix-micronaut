package org.synanton.equalix.adapter.in.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.filter.ServerFilterChain;
import io.micronaut.http.simple.SimpleHttpHeaders;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import jakarta.validation.Validator;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import org.synanton.equalix.adapter.in.rest.dto.CompleteTaskRequest;
import org.synanton.equalix.adapter.in.rest.dto.CreateTaskRequest;
import org.synanton.equalix.adapter.in.rest.dto.ErrorResponse;
import org.synanton.equalix.config.ApiKeyAuthFilter;
import org.synanton.equalix.config.properties.SecurityProperties;
import org.synanton.equalix.domain.TaskNotFoundException;
import reactor.core.publisher.Flux;

/**
 * Micronaut port of the oracle's {@code GlobalExceptionHandlerTest}. Same behaviors —
 * envelope codes/bodies for 404/400/validation, API-key enforcement — exercised as focused
 * unit tests instead of MockMvc: the handlers are plain
 * {@code ExceptionHandler} beans and the auth gate is a plain filter.
 */
class GlobalExceptionHandlerTest {

    private static final Instant FIXED = Instant.parse("2026-01-01T12:00:00Z");
    private static final String API_KEY = "test-api-key";

    private final Clock clock = Clock.fixed(FIXED, ZoneOffset.UTC);

    @SuppressWarnings("unchecked")
    private static HttpRequest<?> request(String path, String apiKey) {
        HttpRequest<?> request = mock(HttpRequest.class);
        when(request.getPath()).thenReturn(path);
        SimpleHttpHeaders headers = new SimpleHttpHeaders();
        if (apiKey != null) {
            headers.add(ApiKeyAuthFilter.API_KEY_HEADER, apiKey);
        }
        when(request.getHeaders()).thenReturn(headers);
        return request;
    }

    @Test
    void shouldReturn404WithStructuredBodyWhenTaskNotFound() {
        UUID id = UUID.randomUUID();
        GlobalExceptionHandler.TaskNotFoundHandler handler =
            new GlobalExceptionHandler.TaskNotFoundHandler(clock);

        HttpResponse<?> response = handler.handle(request("/api/v1/tasks/" + id, API_KEY),
            new TaskNotFoundException(id));

        assertThat(response.getStatus() == HttpStatus.NOT_FOUND).isTrue();
        ErrorResponse body = (ErrorResponse) response.getBody().orElseThrow();
        assertThat(body.getCode()).isEqualTo("NOT_FOUND");
        assertThat(body.getMessage()).isEqualTo("Task not found: " + id);
        assertThat(body.getTimestamp()).isEqualTo(FIXED);
    }

    @Test
    void shouldReturn400WithFieldErrorsForInvalidCreateRequest() {
        Validator validator = Validation.byDefaultProvider().configure()
            .messageInterpolator(new ParameterMessageInterpolator())
            .buildValidatorFactory().getValidator();
        CreateTaskRequest bad = new CreateTaskRequest();
        bad.setFairnessKey("");
        bad.setWeight(new java.math.BigDecimal("-1"));
        Set<ConstraintViolation<CreateTaskRequest>> violations = validator.validate(bad);

        GlobalExceptionHandler.ValidationHandler handler =
            new GlobalExceptionHandler.ValidationHandler(clock);
        HttpResponse<?> response = handler.handle(request("/api/v1/tasks", API_KEY),
            new jakarta.validation.ConstraintViolationException(violations));

        assertThat(violations).isNotEmpty();
        assertThat(response.getStatus() == HttpStatus.BAD_REQUEST).isTrue();
        ErrorResponse body = (ErrorResponse) response.getBody().orElseThrow();
        assertThat(body.getCode()).isEqualTo("VALIDATION_FAILED");
        assertThat(body.getFieldErrors()).isNotEmpty();
    }

    @Test
    void shouldReturn400WhenCompletionMarkedFailedButErrorMissing() {
        Validator validator = Validation.byDefaultProvider().configure()
            .messageInterpolator(new ParameterMessageInterpolator())
            .buildValidatorFactory().getValidator();
        CompleteTaskRequest bad = new CompleteTaskRequest();
        bad.setSuccess(false);
        Set<ConstraintViolation<CompleteTaskRequest>> violations = validator.validate(bad);

        GlobalExceptionHandler.ValidationHandler handler =
            new GlobalExceptionHandler.ValidationHandler(clock);
        HttpResponse<?> response = handler.handle(request("/api/v1/tasks/x/complete", API_KEY),
            new jakarta.validation.ConstraintViolationException(violations));

        assertThat(violations).isNotEmpty();
        assertThat(response.getStatus() == HttpStatus.BAD_REQUEST).isTrue();
        assertThat(((ErrorResponse) response.getBody().orElseThrow()).getCode())
            .isEqualTo("VALIDATION_FAILED");
    }

    @Test
    void shouldAcceptCompletionWithErrorWhenFailed() {
        Validator validator = Validation.byDefaultProvider().configure()
            .messageInterpolator(new ParameterMessageInterpolator())
            .buildValidatorFactory().getValidator();
        CompleteTaskRequest ok = new CompleteTaskRequest();
        ok.setSuccess(false);
        ok.setError("downstream failed");

        assertThat(validator.validate(ok)).isEmpty();
    }

    @Test
    void shouldReturn400ForIllegalArgumentException() {
        GlobalExceptionHandler.BadRequestHandler handler =
            new GlobalExceptionHandler.BadRequestHandler(clock);

        HttpResponse<?> response = handler.handle(request("/api/v1/tasks/x/complete", API_KEY),
            new IllegalArgumentException("invalid state"));

        assertThat(response.getStatus() == HttpStatus.BAD_REQUEST).isTrue();
        ErrorResponse body = (ErrorResponse) response.getBody().orElseThrow();
        assertThat(body.getCode()).isEqualTo("BAD_REQUEST");
        assertThat(body.getMessage()).isEqualTo("invalid state");
    }

    @Test
    void shouldReturn500ForUnhandledException() {
        GlobalExceptionHandler.GenericHandler handler =
            new GlobalExceptionHandler.GenericHandler(clock);

        HttpResponse<?> response = handler.handle(request("/api/v1/tasks", API_KEY),
            new IllegalStateException("boom"));

        assertThat(response.getStatus() == HttpStatus.INTERNAL_SERVER_ERROR).isTrue();
        assertThat(((ErrorResponse) response.getBody().orElseThrow()).getCode())
            .isEqualTo("INTERNAL_ERROR");
    }

    // --- API key gate ---

    private ApiKeyAuthFilter filter() {
        SecurityProperties props = new SecurityProperties();
        props.setApiKey(API_KEY);
        return new ApiKeyAuthFilter(props);
    }

    private static HttpStatus blockStatus(Publisher<MutableHttpResponse<?>> publisher) {
        MutableHttpResponse<?> response = Flux.from(publisher).blockFirst();
        assertThat(response).isNotNull();
        return response.getStatus();
    }

    @Test
    void shouldRejectRequestWithoutApiKey() {
        ServerFilterChain chain = mock(ServerFilterChain.class);

        assertThat(blockStatus(filter().doFilter(request("/api/v1/tasks/x", null), chain)) == HttpStatus.UNAUTHORIZED).isTrue();
        verifyNoInteractions(chain);
    }

    @Test
    void shouldRejectRequestWithWrongApiKey() {
        ServerFilterChain chain = mock(ServerFilterChain.class);

        assertThat(blockStatus(filter().doFilter(request("/api/v1/tasks/x", "wrong"), chain)) == HttpStatus.UNAUTHORIZED).isTrue();
        verifyNoInteractions(chain);
    }

    @Test
    void shouldProceedRequestWithCorrectApiKey() {
        ServerFilterChain chain = mock(ServerFilterChain.class);
        MutableHttpResponse<?> downstream = HttpResponse.ok();
        when(chain.proceed(any())).thenReturn(Flux.just(downstream));

        assertThat(blockStatus(filter().doFilter(request("/api/v1/tasks/x", API_KEY), chain)) == HttpStatus.OK).isTrue();
    }

    @Test
    void shouldLeaveHealthAndInfoOpen() {
        ServerFilterChain chain = mock(ServerFilterChain.class);
        MutableHttpResponse<?> downstream = HttpResponse.ok();
        when(chain.proceed(any())).thenReturn(Flux.just(downstream));

        assertThat(blockStatus(filter().doFilter(request("/health", null), chain)) == HttpStatus.OK).isTrue();
        assertThat(blockStatus(filter().doFilter(request("/info", null), chain)) == HttpStatus.OK).isTrue();
    }
}
