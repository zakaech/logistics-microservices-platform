package com.logistics.order.controller;

import com.logistics.order.dto.request.AllocationPreviewRequest;
import com.logistics.order.dto.response.AllocationPreviewResponse;
import com.logistics.order.dto.response.AllocationStrategyResponse;
import com.logistics.order.service.AllocationPreviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Simulation of the allocation engine.
 *
 * <p>Answers "where would this ship from, and why?" without creating an order or touching stock.
 * Restricted to staff: it exposes the shape of the warehouse network and its stock depth, which is
 * commercially sensitive in a way a customer-facing endpoint should not be.
 */
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@Tag(name = "Allocation", description = "Simuler le moteur d'affectation sans créer de commande")
public class AllocationPreviewController {

    private final AllocationPreviewService previewService;

    @PostMapping("/allocation-preview")
    @PreAuthorize("hasAnyRole('WAREHOUSE_MANAGER', 'ADMIN')")
    @Operation(summary = "Exécuter une ou plusieurs stratégies sur le stock réel, sans rien modifier")
    public AllocationPreviewResponse preview(@Valid @RequestBody AllocationPreviewRequest request) {
        return previewService.preview(request);
    }

    @GetMapping("/allocation-strategies")
    @Operation(summary = "Les stratégies disponibles et celle appliquée par défaut")
    public List<AllocationStrategyResponse> strategies() {
        return previewService.availableStrategies();
    }
}
