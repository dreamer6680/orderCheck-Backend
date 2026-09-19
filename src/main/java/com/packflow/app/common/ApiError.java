package com.packflow.app.common;

import java.time.OffsetDateTime;
import java.util.Map;

public record ApiError(
        int code,
        String message,
        String requestId,
        String path,
        OffsetDateTime timestamp,
        Map<String, String> errors) {
    public ApiError {
        errors = errors == null ? Map.of() : Map.copyOf(errors);
    }
}
