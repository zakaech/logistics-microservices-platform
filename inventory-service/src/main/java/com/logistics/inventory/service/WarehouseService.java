package com.logistics.inventory.service;

import com.logistics.inventory.domain.entity.Warehouse;
import com.logistics.inventory.dto.request.CreateWarehouseRequest;
import com.logistics.inventory.dto.request.UpdateWarehouseRequest;
import com.logistics.inventory.dto.response.PagedResponse;
import com.logistics.inventory.dto.response.WarehouseResponse;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface WarehouseService {

    PagedResponse<WarehouseResponse> findAll(Boolean active, Pageable pageable);

    WarehouseResponse findById(UUID id);

    /** Loads a warehouse or fails. Used by the stock and reservation services. */
    Warehouse requireById(UUID id);

    WarehouseResponse create(CreateWarehouseRequest request);

    WarehouseResponse update(UUID id, UpdateWarehouseRequest request);

    /** Deactivating removes a site from availability and allocation without losing its history. */
    WarehouseResponse changeStatus(UUID id, boolean active);
}
