package com.logistics.inventory.mapper;

import com.logistics.inventory.domain.entity.Reservation;
import com.logistics.inventory.domain.entity.ReservationLine;
import com.logistics.inventory.domain.entity.StockItem;
import com.logistics.inventory.domain.entity.StockMovement;
import com.logistics.inventory.domain.entity.Warehouse;
import com.logistics.inventory.dto.common.AddressDto;
import com.logistics.inventory.dto.response.ReservationResponse;
import com.logistics.inventory.dto.response.StockItemResponse;
import com.logistics.inventory.dto.response.StockMovementResponse;
import com.logistics.inventory.dto.response.WarehouseResponse;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Entity to DTO translation.
 *
 * <p>Hand-written because several of these mappings compute rather than copy: available quantity
 * and the low-stock flag are derived, and a reservation is regrouped by warehouse on the way out.
 * Expressing that in generator annotations would hide the logic in unchecked strings.
 */
@Component
public class InventoryMapper {

    public WarehouseResponse toResponse(Warehouse warehouse, long distinctProducts) {
        return new WarehouseResponse(
                warehouse.getId(),
                warehouse.getCode(),
                warehouse.getName(),
                toAddressDto(warehouse),
                warehouse.getLocation().getLatitude(),
                warehouse.getLocation().getLongitude(),
                warehouse.isActive(),
                distinctProducts,
                warehouse.getCreatedAt(),
                warehouse.getUpdatedAt());
    }

    public StockItemResponse toResponse(StockItem item) {
        Warehouse warehouse = item.getWarehouse();
        return new StockItemResponse(
                item.getId(),
                warehouse.getId(),
                warehouse.getCode(),
                item.getProductId(),
                item.getQuantityOnHand(),
                item.getQuantityReserved(),
                item.availableQuantity(),
                item.getReorderThreshold(),
                item.isLowStock(),
                item.getUpdatedAt());
    }

    public StockMovementResponse toResponse(StockMovement movement) {
        StockItem item = movement.getStockItem();
        return new StockMovementResponse(
                movement.getId(),
                item.getWarehouse().getId(),
                item.getWarehouse().getCode(),
                item.getProductId(),
                movement.getType(),
                movement.getQuantity(),
                movement.getReference(),
                movement.getCreatedBy(),
                movement.getOccurredAt());
    }

    /** Regroups the flat reservation lines by warehouse, which is how a caller reasons about them. */
    public ReservationResponse toResponse(Reservation reservation) {
        Map<Warehouse, List<ReservationLine>> byWarehouse = new LinkedHashMap<>();
        for (ReservationLine line : reservation.getLines()) {
            byWarehouse.computeIfAbsent(line.getStockItem().getWarehouse(), w -> new java.util.ArrayList<>())
                    .add(line);
        }

        List<ReservationResponse.Segment> segments = byWarehouse.entrySet().stream()
                .map(entry -> new ReservationResponse.Segment(
                        entry.getKey().getId(),
                        entry.getKey().getCode(),
                        entry.getValue().stream()
                                .map(line -> new ReservationResponse.Line(
                                        line.getStockItem().getProductId(), line.getQuantity()))
                                .toList()))
                .toList();

        return new ReservationResponse(
                reservation.getId(),
                reservation.getReference(),
                reservation.getStatus(),
                reservation.getExpiresAt(),
                reservation.getCreatedAt(),
                reservation.getUpdatedAt(),
                segments);
    }

    public AddressDto toAddressDto(Warehouse warehouse) {
        var address = warehouse.getAddress();
        return new AddressDto(address.getLine1(), address.getCity(),
                address.getPostalCode(), address.getCountry());
    }
}
