package com.logistics.inventory.controller;

import com.logistics.inventory.domain.enums.MovementType;
import com.logistics.inventory.dto.request.CreateStockMovementRequest;
import com.logistics.inventory.dto.request.UpsertStockItemRequest;
import com.logistics.inventory.dto.response.PagedResponse;
import com.logistics.inventory.dto.response.StockItemResponse;
import com.logistics.inventory.dto.response.StockMovementResponse;
import com.logistics.inventory.service.StockService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

/**
 * Stock levels and the movement ledger.
 *
 * <p>Everything here is restricted to warehouse managers and administrators. Stock levels are
 * commercially sensitive: how much of a product you hold is information a competitor would like.
 */
@RestController
@RequestMapping("/api/v1/stock")
@RequiredArgsConstructor
@Tag(name = "Stock", description = "Niveaux de stock et journal des mouvements en ajout seul")
public class StockController {

    private static final int MAX_PAGE_SIZE = 100;

    private final StockService stockService;

    @GetMapping
    @PreAuthorize("hasAnyRole('WAREHOUSE_MANAGER', 'ADMIN')")
    @Operation(summary = "Niveaux de stock, filtrés par entrepôt ou par produit")
    public PagedResponse<StockItemResponse> list(
            @RequestParam(required = false) UUID warehouseId,
            @RequestParam(required = false) String productId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return stockService.findStock(warehouseId, productId, pageable(page, size, "productId"));
    }

    @GetMapping("/item")
    @PreAuthorize("hasAnyRole('WAREHOUSE_MANAGER', 'ADMIN')")
    @Operation(summary = "Un niveau de stock, identifié par entrepôt et produit")
    public StockItemResponse findOne(@RequestParam UUID warehouseId,
                                     @RequestParam String productId) {
        return stockService.findOne(warehouseId, productId);
    }

    @PutMapping
    @PreAuthorize("hasAnyRole('WAREHOUSE_MANAGER', 'ADMIN')")
    @Operation(summary = "Définir un niveau de stock ABSOLU ; crée la ligne si le produit est nouveau sur ce site")
    public StockItemResponse upsert(@Valid @RequestBody UpsertStockItemRequest request) {
        return stockService.upsert(request);
    }

    @PostMapping("/movements")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('WAREHOUSE_MANAGER', 'ADMIN')")
    @Operation(summary = "Enregistrer un mouvement physique RELATIF : entrée, sortie ou ajustement")
    public StockMovementResponse recordMovement(
            @Valid @RequestBody CreateStockMovementRequest request) {
        return stockService.recordMovement(request);
    }

    @GetMapping("/movements")
    @PreAuthorize("hasAnyRole('WAREHOUSE_MANAGER', 'ADMIN')")
    @Operation(summary = "Historique des mouvements, la piste d'audit derrière chaque niveau de stock")
    public PagedResponse<StockMovementResponse> movements(
            @RequestParam(required = false) UUID warehouseId,
            @RequestParam(required = false) String productId,
            @RequestParam(required = false) MovementType type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "occurredAt"));

        return stockService.findMovements(warehouseId, productId, type, from, to, pageable);
    }

    private Pageable pageable(int page, int size, String sortBy) {
        return PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.ASC, sortBy));
    }
}
