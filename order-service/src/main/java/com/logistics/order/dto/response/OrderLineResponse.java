package com.logistics.order.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

/** An order line, with the catalogue values as they were when the order was placed. */
public record OrderLineResponse(
        UUID id,
        String productId,
        String sku,
        String name,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal lineTotal,
        String currency) {
}
