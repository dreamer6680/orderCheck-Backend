package com.packflow.app.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RequestTraceFilter extends OncePerRequestFilter {
    public static final String REQUEST_ID = "requestId";
    private static final Logger log = LoggerFactory.getLogger(RequestTraceFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String incoming = request.getHeader("X-Request-ID");
        String requestId = incoming != null && incoming.matches("[a-zA-Z0-9._-]{1,64}")
                ? incoming : UUID.randomUUID().toString();
        request.setAttribute(REQUEST_ID, requestId);
        response.setHeader("X-Request-ID", requestId);
        long started = System.nanoTime();
        try (MDC.MDCCloseable ignored = MDC.putCloseable(REQUEST_ID, requestId)) {
            try {
                chain.doFilter(request, response);
            } finally {
                long elapsedMs = (System.nanoTime() - started) / 1_000_000;
                String path = request.getRequestURI();
                if (response.getStatus() >= 500) {
                    log.error("HTTP {} {} status={} durationMs={} requestId={}",
                            request.getMethod(), path, response.getStatus(), elapsedMs, requestId);
                } else if (response.getStatus() >= 400) {
                    log.warn("HTTP {} {} status={} durationMs={} requestId={}",
                            request.getMethod(), path, response.getStatus(), elapsedMs, requestId);
                } else {
                    log.info("HTTP {} {} status={} durationMs={} requestId={}",
                            request.getMethod(), path, response.getStatus(), elapsedMs, requestId);
                }
            }
        }
    }
}
