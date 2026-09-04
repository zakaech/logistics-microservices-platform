package com.logistics.order.client;

import com.logistics.order.allocation.model.WarehouseCandidate;
import com.logistics.order.client.dto.ReservationCommand;
import com.logistics.order.client.dto.ReservationHandle;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The port through which this service reaches stock.
 *
 * <p>{@link #fetchAvailability} returns {@link WarehouseCandidate} directly: translating the
 * inventory payload into the engine's own vocabulary is the adapter's job, so the allocation
 * strategies never see a foreign DTO.
 */
public interface InventoryClient {

    /** Stock and coordinates for a set of products, across active warehouses, in one call. */
    List<WarehouseCandidate> fetchAvailability(Set<String> productIds);

    /**
     * Holds stock. Idempotent on the command reference.
     *
     * @throws com.logistics.order.exception.AllocationFailedException if stock moved and the hold
     *                                                                can no longer be taken
     */
    ReservationHandle reserve(ReservationCommand command);

    /** Consumes the hold: stock leaves the warehouse. */
    void confirm(UUID reservationId);

    /** Compensating action: gives the held units back. */
    void cancel(UUID reservationId);
}
