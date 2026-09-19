package com.packflow.app.common;

import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/orders/123");
        request.setAttribute(RequestTraceFilter.REQUEST_ID, "test-request-123");
        return request;
    }

    @Test
    void businessErrorKeepsHttpStatusAndTraceId() {
        var response = handler.status(new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"), request());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(404);
        assertThat(response.getBody().message()).isEqualTo("Order not found");
        assertThat(response.getBody().requestId()).isEqualTo("test-request-123");
        assertThat(response.getBody().path()).isEqualTo("/api/orders/123");
        assertThat(response.getBody().errors()).isEqualTo(Map.of());
    }

    @Test
    void unexpectedErrorDoesNotLeakExceptionDetails() {
        var response = handler.unexpected(new IllegalStateException("database password=secret"), request());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("Internal server error");
    }

    @Test
    void requestTraceFilterAddsCorrelationHeader() throws Exception {
        var filter = new RequestTraceFilter();
        var request = new MockHttpServletRequest("GET", "/api/orders");
        request.addHeader("X-Request-ID", "client-abc");
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> {
            assertThat(req.getAttribute(RequestTraceFilter.REQUEST_ID)).isEqualTo("client-abc");
            ((HttpServletResponse) res).setStatus(200);
        });
        assertThat(response.getHeader("X-Request-ID")).isEqualTo("client-abc");
    }

    @Test
    void invalidCorrelationHeaderIsReplaced() throws Exception {
        var filter = new RequestTraceFilter();
        var request = new MockHttpServletRequest("GET", "/api/orders");
        request.addHeader("X-Request-ID", "bad id with spaces");
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> {});
        assertThat(response.getHeader("X-Request-ID")).isNotEqualTo("bad id with spaces");
        assertThat(response.getHeader("X-Request-ID")).isNotBlank();
    }
}
