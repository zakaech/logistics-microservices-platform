package com.logistics.order.dto.request;

import com.logistics.order.dto.common.GeoPointDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Runs the allocation engine WITHOUT creating an order or touching stock.
 *
 * <p>Simulation matters for two reasons. Operationally, it answers "where would this ship from?"
 * before a customer commits. And it is the honest way to compare strategies: asking for several at
 * once runs each against the same availability snapshot, so any difference in the plans comes from
 * the rules and not from stock having moved in between.
 */
public record AllocationPreviewRequest(

        @NotEmpty(message = "lines doit contenir au moins un produit")
        @Size(max = 50, message = "50 lignes au maximum par simulation")
        List<@Valid Line> lines,

        @NotNull(message = "destination est obligatoire")
        @Valid GeoPointDto destination,

        /* Empty means every registered strategy, which is the useful default for a comparison. */
        List<String> strategies) {

    public record Line(

            @NotBlank(message = "productId est obligatoire")
            String productId,

            @NotNull(message = "quantity est obligatoire")
            @Min(value = 1, message = "quantity doit valoir au moins 1")
            @Max(value = 1000, message = "quantity ne doit pas dépasser 1000")
            Integer quantity) {
    }
}
