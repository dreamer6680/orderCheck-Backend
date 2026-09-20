package com.packflow.app.order;

import com.packflow.app.outbound.OutboundRecord;
import com.packflow.app.outbound.OutboundStatus;
import java.math.BigDecimal;
import java.util.List;

/** Derive fulfillment from immutable completed shipments, not a mutable order-level counter. */
public final class Fulfillment {
    private Fulfillment() { }

    public static BigDecimal shipped(SalesOrderItem item, List<OutboundRecord> records) {
        return sum(item, records, OutboundStatus.COMPLETED, true);
    }

    public static BigDecimal pending(SalesOrderItem item, List<OutboundRecord> records) {
        return sum(item, records, OutboundStatus.PENDING, false);
    }

    public static BigDecimal remaining(SalesOrderItem item, List<OutboundRecord> records) {
        return item.getOrderedQuantity().subtract(shipped(item, records))
                .subtract(item.getWaivedQuantity()).max(BigDecimal.ZERO);
    }

    public static BigDecimal unplanned(SalesOrderItem item, List<OutboundRecord> records) {
        return remaining(item, records).subtract(pending(item, records)).max(BigDecimal.ZERO);
    }

    private static BigDecimal sum(SalesOrderItem item, List<OutboundRecord> records,
            OutboundStatus status, boolean actual) {
        return records.stream()
                .filter(record -> record.getOrderItem().getId().equals(item.getId())
                        && record.getStatus() == status)
                .map(record -> actual ? record.getActualQuantity() : record.getPlannedQuantity())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
