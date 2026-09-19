package com.packflow.app.outbound;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public final class OutboundDtos {
    private OutboundDtos() { }

    public record CompleteRequest(
            @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 15, fraction = 3)
            BigDecimal actualQuantity,
            @Size(max = 500) String differenceReason) { }

    /** Read-only stock snapshot; completion still validates stock under a database lock. */
    public record OutboundCheckResponse(
            Long outboundId,
            String recordNo,
            Long orderId,
            String orderNo,
            String customerName,
            Long productId,
            String sku,
            String productName,
            String unit,
            BigDecimal plannedQuantity,
            BigDecimal physicalQuantity,
            BigDecimal reservedQuantity,
            BigDecimal availableForTask,
            OutboundStatus status,
            boolean executable,
            String reason,
            OffsetDateTime checkedAt) { }

    public record OutboundResponse(
            Long id,
            String recordNo,
            Long orderId,
            String orderNo,
            String customerName,
            Long orderItemId,
            Long productId,
            String sku,
            String productName,
            String unit,
            BigDecimal plannedQuantity,
            BigDecimal actualQuantity,
            OutboundStatus status,
            String differenceReason,
            String operatorUsername,
            OffsetDateTime completedAt,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt) { }
}
