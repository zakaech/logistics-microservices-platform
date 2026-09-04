package com.logistics.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Gives every request a correlation id and echoes it back.
 *
 * <p>With four services in the path, a support case is unusable without one: this is the value that
 * ties a browser error to the log lines it produced in each service. Runs before authentication so
 * that a rejected request still carries an id in its error body.
 *
 * <p>An id supplied by the caller is kept, which is what lets a front-end trace a whole user action.
 */
@Component
public class CorrelationIdGlobalFilter implements GlobalFilter, Ordered {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final int ORDER = -200;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String requestId = exchange.getRequest().getHeaders().getFirst(REQUEST_ID_HEADER);
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }

        String resolved = requestId;
        ServerHttpRequest request = exchange.getRequest().mutate()
                .header(REQUEST_ID_HEADER, resolved)
                .build();

        exchange.getResponse().getHeaders().set(REQUEST_ID_HEADER, resolved);
        return chain.filter(exchange.mutate().request(request).build());
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
