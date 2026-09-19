package com.packflow.app.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ApiError> status(ResponseStatusException exception, HttpServletRequest request) {
        HttpStatusCode status = exception.getStatusCode();
        String message = exception.getReason() == null ? "Request failed" : exception.getReason();
        log.warn("Business request rejected: method={} path={} status={} requestId={} reason={}",
                request.getMethod(), request.getRequestURI(), status.value(), requestId(request), message);
        return response(status, message, request, Map.of());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    ResponseEntity<ApiError> invalidBinding(Exception exception, HttpServletRequest request) {
        org.springframework.validation.BindingResult binding = exception instanceof MethodArgumentNotValidException method
                ? method.getBindingResult() : ((BindException) exception).getBindingResult();
        Map<String, String> fields = new LinkedHashMap<>();
        binding.getFieldErrors().forEach(error ->
                fields.putIfAbsent(error.getField(), error.getDefaultMessage() == null ? "Invalid value" : error.getDefaultMessage()));
        log.warn("Validation failed: method={} path={} requestId={} fields={}",
                request.getMethod(), request.getRequestURI(), requestId(request), fields.keySet());
        return response(HttpStatus.BAD_REQUEST, "Validation failed", request, fields);
    }

    @ExceptionHandler({ConstraintViolationException.class, HandlerMethodValidationException.class,
            HttpMessageNotReadableException.class, IllegalArgumentException.class})
    ResponseEntity<ApiError> badRequest(Exception exception, HttpServletRequest request) {
        log.warn("Invalid request: method={} path={} requestId={} type={}",
                request.getMethod(), request.getRequestURI(), requestId(request), exception.getClass().getSimpleName());
        return response(HttpStatus.BAD_REQUEST, "Invalid request parameters or body", request, Map.of());
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiError> forbidden(AccessDeniedException exception, HttpServletRequest request) {
        log.warn("Forbidden request: method={} path={} requestId={}",
                request.getMethod(), request.getRequestURI(), requestId(request));
        return response(HttpStatus.FORBIDDEN, "Access denied", request, Map.of());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(Exception exception, HttpServletRequest request) {
        log.error("Unhandled request failure: method={} path={} requestId={}",
                request.getMethod(), request.getRequestURI(), requestId(request), exception);
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error", request, Map.of());
    }

    public static String requestId(HttpServletRequest request) {
        Object value = request.getAttribute(RequestTraceFilter.REQUEST_ID);
        return value == null ? "unknown" : value.toString();
    }

    public static ResponseEntity<ApiError> response(HttpStatusCode status, String message,
                                                      HttpServletRequest request, Map<String, String> fields) {
        return ResponseEntity.status(status).body(new ApiError(status.value(), message,
                requestId(request), request.getRequestURI(), OffsetDateTime.now(), fields));
    }
}
