package com.packflow.app.dashboard;

import com.packflow.app.inventory.InboundRecordRepository;
import com.packflow.app.order.OrderStatus;
import com.packflow.app.order.SalesOrderRepository;
import com.packflow.app.outbound.OutboundRecordRepository;
import com.packflow.app.outbound.OutboundStatus;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WarehouseProgressService {
    private final InboundRecordRepository inbounds;
    private final OutboundRecordRepository outbounds;
    private final SalesOrderRepository orders;
    private final ZoneId businessZone;

    public WarehouseProgressService(
            InboundRecordRepository inbounds,
            OutboundRecordRepository outbounds,
            SalesOrderRepository orders,
            @Value("${app.warehouse.time-zone}") String timeZone) {
        this.inbounds = inbounds;
        this.outbounds = outbounds;
        this.orders = orders;
        this.businessZone = ZoneId.of(timeZone);
    }

    @Transactional(readOnly = true)
    public WarehouseProgressResponse today() {
        LocalDate today = LocalDate.now(businessZone);
        OffsetDateTime start = today.atStartOfDay(businessZone).toOffsetDateTime();
        OffsetDateTime end = today.plusDays(1).atStartOfDay(businessZone).toOffsetDateTime();

        long todayInbound = inbounds.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(start, end);
        long totalInbound = inbounds.count();
        long todayOutbound = outbounds.countByStatusAndCompletedAtGreaterThanEqualAndCompletedAtLessThan(
                OutboundStatus.COMPLETED, start, end);
        long pendingOutbound = outbounds.countByStatus(OutboundStatus.PENDING);
        long todayPlannedOutbound = outbounds.countByStatusAndPlannedOutboundDate(OutboundStatus.PENDING, today);
        long completedOutbound = outbounds.countByStatus(OutboundStatus.COMPLETED);
        long differences = outbounds.countByStatusAndDifferenceReasonIsNotNull(OutboundStatus.COMPLETED);
        long totalOrders = orders.count();
        long pendingOutboundOrders = orders.countByStatus(OrderStatus.PENDING_OUTBOUND);
        long todayDuePendingOrders = orders.countByStatusAndDeliveryDate(OrderStatus.PENDING_OUTBOUND, today);
        long undatedPendingOrders = orders.countByStatusAndDeliveryDateIsNull(OrderStatus.PENDING_OUTBOUND);
        // A due order can still need attention before stock verification, or after it becomes abnormal.
        List<OrderStatus> unfulfilledStatuses = List.of(
                OrderStatus.PENDING_CHECK, OrderStatus.PENDING_OUTBOUND, OrderStatus.ABNORMAL);
        long unfulfilledOrders = orders.countByStatusIn(unfulfilledStatuses);
        long dueTodayUnfulfilled = orders.countByStatusInAndDeliveryDate(unfulfilledStatuses, today);
        long undatedUnfulfilled = orders.countByStatusInAndDeliveryDateIsNull(unfulfilledStatuses);
        long abnormalOrders = orders.countByStatus(OrderStatus.ABNORMAL);
        return new WarehouseProgressResponse(
                today, businessZone.getId(), todayInbound, todayOutbound, pendingOutbound,
                differences, OffsetDateTime.now(businessZone), totalInbound, todayPlannedOutbound,
                completedOutbound, percentage(todayInbound, totalInbound),
                percentage(todayPlannedOutbound, pendingOutbound), percentage(differences, completedOutbound),
                totalOrders, pendingOutboundOrders, abnormalOrders,
                percentage(abnormalOrders, totalOrders), todayDuePendingOrders,
                percentage(todayDuePendingOrders, pendingOutboundOrders), undatedPendingOrders,
                unfulfilledOrders, dueTodayUnfulfilled,
                percentage(dueTodayUnfulfilled, unfulfilledOrders), undatedUnfulfilled);
    }

    /** Returns a percentage rounded to one decimal; no tasks/records means 0%, not 100%. */
    private static BigDecimal percentage(long numerator, long denominator) {
        if (denominator == 0) return BigDecimal.ZERO.setScale(1);
        return BigDecimal.valueOf(numerator).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(denominator), 1, RoundingMode.HALF_UP);
    }

    /** Difference count includes all completed outbound records with a documented quantity difference;
     * it does not imply an unresolved discrepancy workflow. */
    public record WarehouseProgressResponse(
            LocalDate businessDate,
            String timeZone,
            long todayInboundCount,
            long todayOutboundCount,
            long pendingOutboundCount,
            long differenceRecordCount,
            OffsetDateTime updatedAt,
            long totalInboundCount,
            long todayPendingOutboundCount,
            long completedOutboundCount,
            BigDecimal todayInboundPercent,
            BigDecimal todayPendingOutboundPercent,
            BigDecimal differencePercent,
            long totalOrderCount,
            long pendingOutboundOrderCount,
            long abnormalOrderCount,
            BigDecimal abnormalOrderPercent,
            long todayDuePendingOrderCount,
            BigDecimal todayDuePendingOrderPercent,
            long undatedPendingOrderCount,
            long unfulfilledOrderCount,
            long todayDueUnfulfilledOrderCount,
            BigDecimal todayDueUnfulfilledOrderPercent,
            long undatedUnfulfilledOrderCount) {
    }
}
