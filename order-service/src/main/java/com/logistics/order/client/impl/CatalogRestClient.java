package com.logistics.order.client.impl;

import com.logistics.order.client.CatalogClient;
import com.logistics.order.client.dto.ProductSnapshot;
import com.logistics.order.exception.UpstreamServiceException;
import com.logistics.order.security.ServiceTokenProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Adapter over catalog-service.
 *
 * <p>Failure handling is the substance of this class. Three outcomes are distinguished on purpose:
 * a transport failure (down, refused, timed out) and a 5xx both become
 * {@link UpstreamServiceException} and surface as 503, because retrying is meaningful; a 4xx is our
 * own mistake and is not dressed up as an outage.
 */
@Slf4j
@Component
public class CatalogRestClient implements CatalogClient {

    private static final String SERVICE = "catalog-service";

    private final RestClient restClient;
    private final ServiceTokenProvider tokenProvider;

    public CatalogRestClient(RestClient downstreamRestClient, ServiceTokenProvider tokenProvider) {
        this.restClient = downstreamRestClient;
        this.tokenProvider = tokenProvider;
    }

    @Override
    public Map<String, ProductSnapshot> fetchProducts(Set<String> productIds) {
        if (productIds.isEmpty()) {
            return Map.of();
        }
        try {
            List<Map<String, Object>> response = restClient.post()
                    .uri("/api/v1/products/batch")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenProvider.currentToken())
                    .body(Map.of("productIds", productIds))
                    .retrieve()
                    .body(List.class);

            if (response == null) {
                throw new UpstreamServiceException(SERVICE, "Empty response from the batch endpoint.");
            }

            return response.stream()
                    .map(this::toSnapshot)
                    .collect(Collectors.toMap(ProductSnapshot::id, Function.identity(),
                            (first, second) -> first));

        } catch (ResourceAccessException e) {
            // Connect or read timeout, DNS failure, connection refused.
            throw new UpstreamServiceException(SERVICE, "Catalogue unreachable: " + e.getMessage(), e);
        } catch (RestClientResponseException e) {
            // A 401 usually means our cached service token was revoked or the keys rotated;
            // dropping it lets the next attempt start clean rather than failing forever.
            if (e.getStatusCode().value() == 401) {
                tokenProvider.invalidate();
            }
            if (e.getStatusCode().is5xxServerError()) {
                throw new UpstreamServiceException(SERVICE,
                        "Catalogue returned " + e.getStatusCode() + ".", e);
            }
            throw new UpstreamServiceException(SERVICE,
                    "Catalogue rejected the request with " + e.getStatusCode() + ".", e);
        }
    }

    /** Translates the foreign payload into our own record. Nothing else in the service sees it. */
    @SuppressWarnings("unchecked")
    private ProductSnapshot toSnapshot(Map<String, Object> raw) {
        Map<String, Object> price = (Map<String, Object>) raw.get("price");
        return new ProductSnapshot(
                (String) raw.get("id"),
                (String) raw.get("sku"),
                (String) raw.get("name"),
                price == null ? null : new BigDecimal(String.valueOf(price.get("amount"))),
                price == null ? null : (String) price.get("currency"),
                "ACTIVE".equals(raw.get("status")));
    }
}
