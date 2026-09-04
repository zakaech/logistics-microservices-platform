package com.logistics.inventory.service.impl;

import com.logistics.inventory.domain.entity.Warehouse;
import com.logistics.inventory.domain.vo.Address;
import com.logistics.inventory.domain.vo.GeoPoint;
import com.logistics.inventory.dto.common.AddressDto;
import com.logistics.inventory.dto.request.CreateWarehouseRequest;
import com.logistics.inventory.dto.request.UpdateWarehouseRequest;
import com.logistics.inventory.dto.response.PagedResponse;
import com.logistics.inventory.dto.response.WarehouseResponse;
import com.logistics.inventory.exception.DuplicateResourceException;
import com.logistics.inventory.exception.ResourceNotFoundException;
import com.logistics.inventory.mapper.InventoryMapper;
import com.logistics.inventory.repository.StockItemRepository;
import com.logistics.inventory.repository.WarehouseRepository;
import com.logistics.inventory.service.WarehouseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WarehouseServiceImpl implements WarehouseService {

    private final WarehouseRepository warehouseRepository;
    private final StockItemRepository stockItemRepository;
    private final InventoryMapper inventoryMapper;

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<WarehouseResponse> findAll(Boolean active, Pageable pageable) {
        Page<Warehouse> page = (active == null)
                ? warehouseRepository.findAll(pageable)
                : warehouseRepository.findByActive(active, pageable);

        return PagedResponse.from(page, warehouse -> inventoryMapper.toResponse(
                warehouse, stockItemRepository.countByWarehouseId(warehouse.getId())));
    }

    @Override
    @Transactional(readOnly = true)
    public WarehouseResponse findById(UUID id) {
        Warehouse warehouse = requireById(id);
        return inventoryMapper.toResponse(warehouse,
                stockItemRepository.countByWarehouseId(id));
    }

    @Override
    @Transactional(readOnly = true)
    public Warehouse requireById(UUID id) {
        return warehouseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Warehouse", id));
    }

    @Override
    @Transactional
    public WarehouseResponse create(CreateWarehouseRequest request) {
        if (warehouseRepository.existsByCode(request.code())) {
            throw new DuplicateResourceException("Warehouse", "code", request.code());
        }

        Warehouse warehouse = Warehouse.builder()
                .code(request.code())
                .name(request.name().trim())
                .address(toAddress(request.address()))
                .location(new GeoPoint(request.latitude(), request.longitude()))
                .active(true)
                .build();

        Warehouse saved = warehouseRepository.save(warehouse);
        log.info("Created warehouse {} ({})", saved.getCode(), saved.getId());
        return inventoryMapper.toResponse(saved, 0L);
    }

    @Override
    @Transactional
    public WarehouseResponse update(UUID id, UpdateWarehouseRequest request) {
        Warehouse warehouse = requireById(id);

        warehouse.setName(request.name().trim());
        warehouse.setAddress(toAddress(request.address()));
        warehouse.setLocation(new GeoPoint(request.latitude(), request.longitude()));

        Warehouse saved = warehouseRepository.save(warehouse);
        return inventoryMapper.toResponse(saved, stockItemRepository.countByWarehouseId(id));
    }

    @Override
    @Transactional
    public WarehouseResponse changeStatus(UUID id, boolean active) {
        Warehouse warehouse = requireById(id);
        warehouse.setActive(active);

        Warehouse saved = warehouseRepository.save(warehouse);
        log.info("Warehouse {} is now {}", saved.getCode(), active ? "active" : "inactive");
        return inventoryMapper.toResponse(saved, stockItemRepository.countByWarehouseId(id));
    }

    private Address toAddress(AddressDto dto) {
        return new Address(dto.line1(), dto.city(), dto.postalCode(), dto.country());
    }
}
