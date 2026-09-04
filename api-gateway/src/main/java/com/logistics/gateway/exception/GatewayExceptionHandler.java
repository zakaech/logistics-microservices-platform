package com.logistics.gateway.exception;

import com.logistics.gateway.support.ProblemResponseWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.util.concurrent.TimeoutException;

/**
 * Turns edge failures into RFC 7807 responses.
 *
 * <p>Without this, Spring's default reactive error handler answers a downstream outage with a plain
 * {@code 500} in WebFlux's own JSON shape. Two things are wrong with that: a service that is merely
 * unreachable is not a server error on our side - it is a {@code 503}, exactly as the API contract
 * says - and a client would have to parse two different error formats depending on how far the
 * request got.
 *
 * <p>Ordered below Spring Boot's own {@code DefaultErrorWebExceptionHandler} (-1) so that this one
 * wins.
 */
@Slf4j
@Order(-2)
@Component
@RequiredArgsConstructor
public class GatewayExceptionHandler implements ErrorWebExceptionHandler {

    private final ProblemResponseWriter problemWriter;

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable throwable) {
        // Once bytes are on the wire the status line is already sent; rethrow so the server can
        // close the connection rather than corrupt a half-written body.
        if (exchange.getResponse().isCommitted()) {
            return Mono.error(throwable);
        }

        String path = exchange.getRequest().getPath().value();

        if (isDownstreamUnreachable(throwable)) {
            log.warn("Downstream unreachable for {} {}: {}",
                    exchange.getRequest().getMethod(), path, rootCause(throwable).toString());
            return problemWriter.serviceUnavailable(exchange,
                    "The service handling this request is currently unavailable.");
        }

        if (throwable instanceof ResponseStatusException statusException
                && statusException.getStatusCode().value() == 404) {
            return problemWriter.notFound(exchange, "No route matches this path.");
        }

        log.error("Unhandled gateway error on {} {}", exchange.getRequest().getMethod(), path,
                throwable);
        return problemWriter.internalError(exchange, "An unexpected error occurred.");
    }

    /**
     * A downstream is unreachable when the name does not resolve, the connection is refused, or it
     * never answers. The cause chain is walked because Reactor Netty wraps these several layers
     * deep.
     */
    private boolean isDownstreamUnreachable(Throwable throwable) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (current instanceof UnknownHostException
                    || current instanceof ConnectException
                    || current instanceof TimeoutException
                    || current instanceof io.netty.channel.ConnectTimeoutException
                    || current instanceof reactor.netty.http.client.PrematureCloseException) {
                return true;
            }
            // Reactor Netty reports a closed or reset connection as a plain IOException.
            if (current instanceof IOException && current.getCause() == null) {
                return true;
            }
            if (current.getCause() == current) {
                break;
            }
        }
        return false;
    }

    private Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }
}
