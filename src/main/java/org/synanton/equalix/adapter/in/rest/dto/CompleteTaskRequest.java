package org.synanton.equalix.adapter.in.rest.dto;

import io.micronaut.core.annotation.Introspected;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import org.jspecify.annotations.Nullable;

@Introspected
public class CompleteTaskRequest {

    private boolean success;

    @Nullable
    private byte[] result;

    @Nullable
    private String error;

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    @Nullable
    public byte[] getResult() {
        return result;
    }

    public void setResult(@Nullable byte[] result) {
        this.result = result;
    }

    @Nullable
    public String getError() {
        return error;
    }

    public void setError(@Nullable String error) {
        this.error = error;
    }

    @JsonIgnore
    @AssertTrue(message = "error must be provided when success is false")
    public boolean isErrorPresentWhenFailed() {
        if (success) {
            return true;
        }
        return error != null && !error.isBlank();
    }
}
