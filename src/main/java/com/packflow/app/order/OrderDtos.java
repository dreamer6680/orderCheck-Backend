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
import java.time.LocalDate;
import java.util.List;

public final class OrderDtos {
    private OrderDtos() { }

    public record CreateOrderRequest(
            @NotBlank @Size(max = 150) String customerName,
            @NotNull LocalDate deliveryDate,
            @NotEmpty List<@NotNull @Valid ItemRequest> items) { }

    public record DeliveryDateRequest(@NotNull LocalDate deliveryDate) { }

    public record UnableToDeliverRequest(@NotBlank @Size(max = 500) String reason) { }

    public record PartialOutboundRequest(
            @NotNull Boolean customerAgreed) { }

    public record SupplementalRequest(
            @NotNull @Positive Long orderItemId,
            @DecimalMin(value = "0", inclusive = false) @Digits(integer = 15, fraction = 3)
            BigDecimal quantity) { }

    public record AcceptShortDeliveryRequest(@NotBlank @Size(max = 500) String reason) { }

    public record OrderEventResponse(Long id, OrderEventType eventType, String description,
            String operatorUsername, OffsetDateTime createdAt) { }

    public record ItemRequest(
            @NotNull @Positive Long productId,
            @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 15, fraction = 3)
            BigDecimal orderedQuantity) { }

    public record ItemResponse(Long id, Long productId, String sku, String productName,
            String unit, BigDecimal orderedQuantity, BigDecimal shippedQuantity,
            BigDecimal pendingQuantity, BigDecimal waivedQuantity, BigDecimal remainingQuantity) { }

    public record OrderResponse(Long id, String orderNo, String customerName, OrderStatus status,
            LocalDate deliveryDate, String exceptionReason, OrderAbnormalType abnormalType, String createdBy, OffsetDateTime createdAt, OffsetDateTime updatedAt,
            List<ItemResponse> items, List<OrderEventResponse> events) { }
}
