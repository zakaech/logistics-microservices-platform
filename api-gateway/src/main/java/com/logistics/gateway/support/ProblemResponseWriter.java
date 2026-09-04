package com.logistics.gateway.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.logistics.gateway.filter.CorrelationIdGlobalFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * Writes RFC 7807 responses from the edge.
 *
 * <p>The gateway must speak the same error language as the services behind it. Without this, a
 * client would parse {@code problem+json} for a business error and something else entirely for an
 * expired token - the two cases a front-end most often has to tell apart.
 */
@Component
@RequiredArgsConstructor
public class ProblemResponseWriter {

    private static final String BASE = "https://logistics.local/problems/";
    private static final URI UNAUTHORIZED_TYPE = URI.create(BASE + "unauthorized");
    private static final URI SERVICE_UNAVAILABLE_TYPE = URI.create(BASE + "service-unavailable");
    private static final URI NOT_FOUND_TYPE = URI.create(BASE + "resource-not-found");
    private static final URI INTERNAL_ERROR_TYPE = URI.create(BASE + "internal-error");

    private final ObjectMapper objectMapper;

    public Mono<Void> unauthorized(ServerWebExchange exchange, String detail) {
        return write(exchange, HttpStatus.UNAUTHORIZED, UNAUTHORIZED_TYPE, "Unauthorized", detail);
    }

    public Mono<Void> serviceUnavailable(ServerWebExchange exchange, String detail) {
        return write(exchange, HttpStatus.SERVICE_UNAVAILABLE, SERVICE_UNAVAILABLE_TYPE,
                "Service unavailable", detail);
    }

    public Mono<Void> notFound(ServerWebExchange exchange, String detail) {
        return write(exchange, HttpStatus.NOT_FOUND, NOT_FOUND_TYPE, "Resource not found", detail);
    }

    public Mono<Void> internalError(ServerWebExchange exchange, String detail) {
        return write(exchange, HttpStatus.INTERNAL_SERVER_ERROR, INTERNAL_ERROR_TYPE,
                "Internal error", detail);
    }

    private Mono<Void> write(ServerWebExchange exchange, HttpStatus status, URI type,
                             String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(type);
        problem.setTitle(title);
        problem.setInstance(URI.create(exchange.getRequest().getPath().value()));
        problem.setProperty("timestamp", Instant.now());

        String requestId = exchange.getRequest().getHeaders()
                .getFirst(CorrelationIdGlobalFilter.REQUEST_ID_HEADER);
        if (requestId != null) {
            problem.setProperty("requestId", requestId);
        }

        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders()
                .setContentType(MediaType.APPLICATION_PROBLEM_JSON);

        byte[] body;
        try {
            body = objectMapper.writeValueAsBytes(problem);
        } catch (JsonProcessingException e) {
            // Serialising a ProblemDetail cannot realistically fail; degrade rather than leak.
            body = ("{\"title\":\"" + title + "\",\"status\":" + status.value() + "}")
                    .getBytes(StandardCharsets.UTF_8);
        }

        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(body);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }
}
