package com.logistics.gateway.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.logistics.gateway.support.ProblemResponseWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ResponseStatusException;

import java.net.ConnectException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A downstream that is merely unreachable is not a server error on our side. These tests pin the
 * mapping the API contract promises: 503 for a connectivity failure, 500 only for a genuine bug,
 * and RFC 7807 in both cases.
 */
class GatewayExceptionHandlerTest {

    private GatewayExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GatewayExceptionHandler(
                new ProblemResponseWriter(new ObjectMapper().findAndRegisterModules()));
    }

    @Test
    @DisplayName("an unresolvable service name becomes 503, not 500")
    void unknownHostBecomesServiceUnavailable() {
        MockServerWebExchange exchange = exchange();

        handler.handle(exchange, new UnknownHostException("Failed to resolve 'order-service'"))
                .block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(body(exchange)).contains("problems/service-unavailable");
    }

    @Test
    @DisplayName("a refused connection becomes 503, even wrapped several layers deep")
    void wrappedConnectExceptionBecomesServiceUnavailable() {
        MockServerWebExchange exchange = exchange();
        Throwable wrapped = new IllegalStateException("routing failed",
                new RuntimeException("netty", new ConnectException("Connection refused")));

        handler.handle(exchange, wrapped).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    @DisplayName("a downstream that never answers becomes 503")
    void timeoutBecomesServiceUnavailable() {
        MockServerWebExchange exchange = exchange();

        handler.handle(exchange, new TimeoutException("response timeout")).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    @DisplayName("an unmatched route stays a 404")
    void unmatchedRouteStaysNotFound() {
        MockServerWebExchange exchange = exchange();

        handler.handle(exchange, new ResponseStatusException(HttpStatus.NOT_FOUND)).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("anything else is a 500 in problem+json, with no stack trace in the body")
    void unexpectedErrorBecomesInternalError() {
        MockServerWebExchange exchange = exchange();

        handler.handle(exchange, new IllegalStateException("a genuine bug")).block();

        assertThat(exchange.getResponse().getStatusCode())
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(exchange.getResponse().getHeaders().getContentType())
                .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(body(exchange))
                .contains("problems/internal-error")
                .doesNotContain("a genuine bug");
    }

    private MockServerWebExchange exchange() {
        return MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/orders"));
    }

    private String body(MockServerWebExchange exchange) {
        return exchange.getResponse().getBody()
                .map(this::readAll)
                .reduce("", String::concat)
                .block();
    }

    private String readAll(DataBuffer buffer) {
        byte[] bytes = new byte[buffer.readableByteCount()];
        buffer.read(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
