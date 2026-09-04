package com.logistics.order.client.impl;

import com.logistics.order.allocation.model.WarehouseCandidate;
import com.logistics.order.client.InventoryClient;
import com.logistics.order.client.dto.ReservationCommand;
import com.logistics.order.client.dto.ReservationHandle;
import com.logistics.order.domain.vo.GeoPoint;
import com.logistics.order.exception.AllocationFailedException;
import com.logistics.order.exception.UpstreamServiceException;
import com.logistics.order.security.ServiceTokenProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Adapter over inventory-service.
 *
 * <p>Besides translating payloads, this class turns one specific downstream answer into a domain
 * event: a {@code 409} on reservation means the stock moved between reading availability and
 * reserving it. That is not an outage and not a bug - it is the race the whole two-phase protocol
 * exists to handle - so it becomes an {@link AllocationFailedException} the orchestrator can
 * respond to by re-planning, rather than a 503 that would just give up.
 */
@Slf4j
@Component
public class InventoryRestClient implements InventoryClient {

    private static final String SERVICE = "inventory-service";

    private final RestClient restClient;
    private final ServiceTokenProvider tokenProvider;

    public InventoryRestClient(RestClient downstreamRestClient, ServiceTokenProvider tokenProvider) {
        this.restClient = downstreamRestClient;
        this.tokenProvider = tokenProvider;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<WarehouseCandidate> fetchAvailability(Set<String> productIds) {
        if (productIds.isEmpty()) {
            return List.of();
        }
        try {
            Map<String, Object> response = restClient.post()
                    .uri("/api/v1/inventory/availability")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenProvider.currentToken())
                    .body(Map.of("productIds", productIds))
                    .retrieve()
                    .body(Map.class);

            if (response == null || response.get("warehouses") == null) {
                return List.of();
            }

            List<Map<String, Object>> warehouses = (List<Map<String, Object>>) response.get("warehouses");
            List<WarehouseCandidate> candidates = new ArrayList<>();

            for (Map<String, Object> warehouse : warehouses) {
                Map<String, Object> availability =
                        (Map<String, Object>) warehouse.getOrDefault("availability", Map.of());

                Map<String, Integer> stock = new LinkedHashMap<>();
                availability.forEach((productId, quantity) ->
                        stock.put(productId, ((Number) quantity).intValue()));

                candidates.add(new WarehouseCandidate(
                        UUID.fromString((String) warehouse.get("warehouseId")),
                        (String) warehouse.get("code"),
                        new GeoPoint(toDecimal(warehouse.get("latitude")),
                                toDecimal(warehouse.get("longitude"))),
                        stock));
            }
            return candidates;

        } catch (ResourceAccessException e) {
            throw new UpstreamServiceException(SERVICE, "Inventory unreachable: " + e.getMessage(), e);
        } catch (RestClientResponseException e) {
            handleTokenFailure(e);
            throw new UpstreamServiceException(SERVICE,
                    "Availability lookup returned " + e.getStatusCode() + ".", e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public ReservationHandle reserve(ReservationCommand command) {
        try {
            Map<String, Object> response = restClient.post()
                    .uri("/api/v1/inventory/reservations")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenProvider.currentToken())
                    .header("Idempotency-Key", command.reference())
                    .body(command)
                    .retrieve()
                    .body(Map.class);

            if (response == null) {
                throw new UpstreamServiceException(SERVICE, "Empty response from the reservation endpoint.");
            }

            return new ReservationHandle(
                    UUID.fromString((String) response.get("id")),
                    (String) response.get("reference"),
                    (String) response.get("status"),
                    response.get("expiresAt") == null
                            ? null : Instant.parse((String) response.get("expiresAt")));

        } catch (ResourceAccessException e) {
            throw new UpstreamServiceException(SERVICE, "Inventory unreachable: " + e.getMessage(), e);
        } catch (RestClientResponseException e) {
            handleTokenFailure(e);
            if (e.getStatusCode().value() == 409) {
                // Stock moved since the snapshot. A domain outcome, not a failure of the platform.
                log.info("Reservation '{}' refused for insufficient stock", command.reference());
                throw new AllocationFailedException(List.of());
            }
            throw new UpstreamServiceException(SERVICE,
                    "Reservation returned " + e.getStatusCode() + ".", e);
        }
    }

    @Override
    public void confirm(UUID reservationId) {
        settle(reservationId, "confirm");
    }

    @Override
    public void cancel(UUID reservationId) {
        settle(reservationId, "cancel");
    }

    private void settle(UUID reservationId, String action) {
        try {
            restClient.post()
                    .uri("/api/v1/inventory/reservations/{id}/" + action, reservationId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenProvider.currentToken())
                    .retrieve()
                    .toBodilessEntity();
        } catch (ResourceAccessException e) {
            throw new UpstreamServiceException(SERVICE,
                    "Could not " + action + " reservation " + reservationId + ": " + e.getMessage(), e);
        } catch (RestClientResponseException e) {
            handleTokenFailure(e);
            throw new UpstreamServiceException(SERVICE,
                    "Reservation " + action + " returned " + e.getStatusCode() + ".", e);
        }
    }

    /** A 401 means our token is stale; drop it so the next call fetches a fresh one. */
    private void handleTokenFailure(RestClientResponseException e) {
        if (e.getStatusCode().value() == 401) {
            tokenProvider.invalidate();
        }
    }

    private BigDecimal toDecimal(Object value) {
        return value == null ? null : new BigDecimal(String.valueOf(value));
    }
}
