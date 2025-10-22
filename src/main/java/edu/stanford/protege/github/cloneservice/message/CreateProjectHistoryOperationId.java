package edu.stanford.protege.github.cloneservice.message;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Objects;
import java.util.UUID;

public record CreateProjectHistoryOperationId(@JsonValue String operationId) {

    public CreateProjectHistoryOperationId {
        Objects.requireNonNull(operationId, "Operation ID should not be null");
    }

    public static CreateProjectHistoryOperationId generate() {
        return new CreateProjectHistoryOperationId(UUID.randomUUID().toString());
    }

    @JsonCreator
    public static CreateProjectHistoryOperationId valueOf(String operationId) {
        return new CreateProjectHistoryOperationId(operationId);
    }
}
