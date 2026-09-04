package com.logistics.order.service;

import com.logistics.order.dto.request.AllocationPreviewRequest;
import com.logistics.order.dto.response.AllocationPreviewResponse;
import com.logistics.order.dto.response.AllocationStrategyResponse;

import java.util.List;

/** Runs the engine without creating an order or touching stock. */
public interface AllocationPreviewService {

    AllocationPreviewResponse preview(AllocationPreviewRequest request);

    List<AllocationStrategyResponse> availableStrategies();
}
