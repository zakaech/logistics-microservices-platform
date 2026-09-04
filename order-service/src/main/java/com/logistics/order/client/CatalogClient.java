package com.logistics.order.client;

import com.logistics.order.client.dto.ProductSnapshot;

import java.util.Map;
import java.util.Set;

/**
 * The port through which this service reaches the catalogue.
 *
 * <p>An interface owned by the order domain, not by catalog-service. That is what lets the whole
 * order flow be tested against a stub, and what keeps a change in another team's payload from
 * reaching anything but the adapter behind it.
 */
public interface CatalogClient {

    /**
     * Resolves products in one call.
     *
     * @return snapshots by product id. An id absent from the map is unknown to the catalogue; the
     *         caller decides what that means rather than being handed a null or an exception.
     * @throws com.logistics.order.exception.UpstreamServiceException if the catalogue is unreachable
     */
    Map<String, ProductSnapshot> fetchProducts(Set<String> productIds);
}
