package com.packflow.app.order;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public final class OrderDtos {
    private OrderDtos() { }

    public record CreateOrderRequest(
            @NotBlank @Size(max = 150) String customerName,
            @NotEmpty List<@NotNull @Valid ItemRequest> items) { }

    public record ItemRequest(
            @NotNull @Positive Long productId,
            @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 15, fraction = 3)
            BigDecimal orderedQuantity) { }

    public record ItemResponse(Long id, Long productId, String sku, String productName,
            String unit, BigDecimal orderedQuantity) { }

    public record OrderResponse(Long id, String orderNo, String customerName, OrderStatus status,
            String exceptionReason, String createdBy, OffsetDateTime createdAt, OffsetDateTime updatedAt,
            List<ItemResponse> items) { }
}
