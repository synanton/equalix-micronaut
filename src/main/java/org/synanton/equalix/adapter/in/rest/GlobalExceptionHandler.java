package org.synanton.equalix.adapter.in.rest;

import io.micronaut.context.annotation.Primary;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import jakarta.inject.Singleton;
import jakarta.inject.Inject;
import jakarta.persistence.EntityNotFoundException;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.synanton.equalix.adapter.in.rest.dto.ErrorResponse;
import org.synanton.equalix.domain.TaskNotFoundException;

/**
 * Global error envelope (same {@code code/message/timestamp/fieldErrors} shape as the Spring oracle).
 * One {@link ExceptionHandler} bean per exception type — Micronaut resolves the most specific match.
 */
public final class GlobalExceptionHandler {


    private GlobalExceptionHandler() {
    }

    static ErrorResponse notFound(String message, Clock clock) {
        return ErrorResponse.of("NOT_FOUND", message, clock.instant());
    }

    @Singleton
    public static class TaskNotFoundHandler implements ExceptionHandler<TaskNotFoundException, HttpResponse<?>> {
        private final Clock clock;

        @Inject
        public TaskNotFoundHandler(Clock clock) {
            this.clock = clock;
        }

        @Override
        public HttpResponse<?> handle(HttpRequest request, TaskNotFoundException ex) {
            return HttpResponse.status(HttpStatus.NOT_FOUND).body(notFound(ex.getMessage(), clock));
        }
    }

    @Singleton
    public static class EntityNotFoundHandler implements ExceptionHandler<EntityNotFoundException, HttpResponse<?>> {
        private final Clock clock;

        @Inject
        public EntityNotFoundHandler(Clock clock) {
            this.clock = clock;
        }

        @Override
        public HttpResponse<?> handle(HttpRequest request, EntityNotFoundException ex) {
            return HttpResponse.status(HttpStatus.NOT_FOUND).body(notFound(ex.getMessage(), clock));
        }
    }

    @Singleton
    public static class BadRequestHandler implements ExceptionHandler<IllegalArgumentException, HttpResponse<?>> {
        private final Clock clock;

        @Inject
        public BadRequestHandler(Clock clock) {
            this.clock = clock;
        }

        @Override
        public HttpResponse<?> handle(HttpRequest request, IllegalArgumentException ex) {
            return HttpResponse.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of("BAD_REQUEST", ex.getMessage(), clock.instant()));
        }
    }

    @Singleton
    @Primary
    public static class ValidationHandler
            implements ExceptionHandler<jakarta.validation.ConstraintViolationException, HttpResponse<?>> {
        private final Clock clock;

        @Inject
        public ValidationHandler(Clock clock) {
            this.clock = clock;
        }

        @Override
        public HttpResponse<?> handle(HttpRequest request, jakarta.validation.ConstraintViolationException ex) {
            List<Map<String, String>> fieldErrors = ex.getConstraintViolations().stream()
                .map(v -> Map.of(
                    "field", v.getPropertyPath() == null ? "unknown" : v.getPropertyPath().toString(),
                    "message", v.getMessage() == null ? "invalid" : v.getMessage()))
                .toList();
            ErrorResponse body = new ErrorResponse(
                "VALIDATION_FAILED", "Request validation failed", clock.instant(), fieldErrors);
            return HttpResponse.status(HttpStatus.BAD_REQUEST).body(body);
        }
    }

    @Slf4j
    @Singleton
    public static class GenericHandler implements ExceptionHandler<Exception, HttpResponse<?>> {
        private final Clock clock;

        @Inject
        public GenericHandler(Clock clock) {
            this.clock = clock;
        }

        @Override
        public HttpResponse<?> handle(HttpRequest request, Exception ex) {
            log.error("Unhandled exception", ex);
            return HttpResponse.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of("INTERNAL_ERROR", "Internal server error", clock.instant()));
        }
    }
}
