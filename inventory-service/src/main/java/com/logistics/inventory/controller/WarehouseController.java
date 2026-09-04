package com.logistics.inventory.controller;

import com.logistics.inventory.dto.request.CreateWarehouseRequest;
import com.logistics.inventory.dto.request.UpdateWarehouseRequest;
import com.logistics.inventory.dto.request.UpdateWarehouseStatusRequest;
import com.logistics.inventory.dto.response.PagedResponse;
import com.logistics.inventory.dto.response.StockItemResponse;
import com.logistics.inventory.dto.response.WarehouseResponse;
import com.logistics.inventory.service.StockService;
import com.logistics.inventory.service.WarehouseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.UUID;

/**
 * Warehouse endpoints.
 *
 * <p>Any authenticated user may see where the warehouses are - a customer is shown which site ships
 * their order. Creating or moving one is an administrative act, and reading its stock is restricted
 * to the people who manage it.
 */
@RestController
@RequestMapping("/api/v1/warehouses")
@RequiredArgsConstructor
@Tag(name = "Warehouses", description = "Warehouse registry and per-warehouse stock")
public class WarehouseController {

    private static final int MAX_PAGE_SIZE = 100;

    private final WarehouseService warehouseService;
    private final StockService stockService;

    @GetMapping
    @Operation(summary = "List warehouses")
    public PagedResponse<WarehouseResponse> list(
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return warehouseService.findAll(active, pageable(page, size, "code"));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Fetch one warehouse")
    public WarehouseResponse getById(@PathVariable UUID id) {
        return warehouseService.findById(id);
    }

    @GetMapping("/{id}/stock")
    @PreAuthorize("hasAnyRole('WAREHOUSE_MANAGER', 'ADMIN')")
    @Operation(summary = "Stock held by one warehouse, optionally only the low-stock lines")
    public PagedResponse<StockItemResponse> stock(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "false") boolean lowStock,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return stockService.findByWarehouse(id, lowStock, pageable(page, size, "productId"));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Register a warehouse")
    public ResponseEntity<WarehouseResponse> create(
            @Valid @RequestBody CreateWarehouseRequest request) {
        WarehouseResponse created = warehouseService.create(request);
        return ResponseEntity
                .created(UriComponentsBuilder.fromPath("/api/v1/warehouses/{id}")
                        .buildAndExpand(created.id()).toUri())
                .body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Replace a warehouse")
    public WarehouseResponse update(@PathVariable UUID id,
                                    @Valid @RequestBody UpdateWarehouseRequest request) {
        return warehouseService.update(id, request);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Activate or deactivate; an inactive site is excluded from allocation")
    public WarehouseResponse changeStatus(@PathVariable UUID id,
                                          @Valid @RequestBody UpdateWarehouseStatusRequest request) {
        return warehouseService.changeStatus(id, request.active());
    }

    private Pageable pageable(int page, int size, String sortBy) {
        return PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.ASC, sortBy));
    }
}
