package org.synanton.equalix.adapter.in.rest.dto;

import io.micronaut.core.annotation.Introspected;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@Introspected
public class CreateTaskRequest {

    @NotBlank
    private String fairnessKey;

    @Positive
    private BigDecimal weight = new BigDecimal("1.0");

    @NotNull
    private byte[] payload;

    private boolean sequential;

    @Nullable
    private Long sequenceNumber;

    @Nullable
    private UUID dependsOnTaskId;

    private boolean requiresPreviousResult;

    public String getFairnessKey() {
        return fairnessKey;
    }

    public void setFairnessKey(String fairnessKey) {
        this.fairnessKey = fairnessKey;
    }

    public BigDecimal getWeight() {
        return weight;
    }

    public void setWeight(BigDecimal weight) {
        this.weight = weight;
    }

    public byte[] getPayload() {
        return payload;
    }

    public void setPayload(byte[] payload) {
        this.payload = payload;
    }

    public boolean isSequential() {
        return sequential;
    }

    public void setSequential(boolean sequential) {
        this.sequential = sequential;
    }

    @Nullable
    public Long getSequenceNumber() {
        return sequenceNumber;
    }

    public void setSequenceNumber(@Nullable Long sequenceNumber) {
        this.sequenceNumber = sequenceNumber;
    }

    @Nullable
    public UUID getDependsOnTaskId() {
        return dependsOnTaskId;
    }

    public void setDependsOnTaskId(@Nullable UUID dependsOnTaskId) {
        this.dependsOnTaskId = dependsOnTaskId;
    }

    public boolean isRequiresPreviousResult() {
        return requiresPreviousResult;
    }

    public void setRequiresPreviousResult(boolean requiresPreviousResult) {
        this.requiresPreviousResult = requiresPreviousResult;
    }

    @AssertTrue(message = "sequenceNumber is required when sequential is true")
    public boolean isSequenceNumberPresentWhenSequential() {
        return !sequential || sequenceNumber != null;
    }
}
