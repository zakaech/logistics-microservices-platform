package com.logistics.order.allocation;

import com.logistics.order.allocation.model.AllocationPlan;
import com.logistics.order.allocation.model.AllocationRequest;

/**
 * Decides which warehouse - or which combination - fulfils an order.
 *
 * <p>This is the extension point the whole service is built around. The allocation rule is the part
 * of a logistics platform most likely to change: today by distance, tomorrow by carrier cost, by
 * warehouse workload, or by whichever site has a truck leaving in the morning. Putting it behind an
 * interface means a new rule is a new class and a line of configuration, with no existing class
 * modified - the Open/Closed principle, concretely.
 *
 * <p><b>Implementations must be pure.</b> No repository, no HTTP call, no clock, no randomness. The
 * caller supplies an availability snapshot and gets a decision back. That constraint is what makes
 * the engine testable without a single mock, and what makes a plan reproducible: given the same
 * input, a strategy must always return the same plan, which is why every comparator ends with a
 * deterministic tie-break.
 */
public interface WarehouseAllocationStrategy {

    /** Stable identifier, used in configuration, in the API, and persisted on the order. */
    String name();

    /** One sentence, surfaced by the API so a user can choose between strategies knowingly. */
    String description();

    /**
     * @throws com.logistics.order.exception.AllocationFailedException when the network cannot
     *                                                                 satisfy the request
     */
    AllocationPlan allocate(AllocationRequest request);
}
