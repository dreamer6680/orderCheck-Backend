package com.packflow.app.dashboard;

import com.packflow.app.inventory.InboundRecordRepository;
import com.packflow.app.outbound.OutboundRecordRepository;
import com.packflow.app.outbound.OutboundStatus;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WarehouseProgressService {
    private final InboundRecordRepository inbounds;
    private final OutboundRecordRepository outbounds;
    private final ZoneId businessZone;

    public WarehouseProgressService(
            InboundRecordRepository inbounds,
            OutboundRecordRepository outbounds,
            @Value("${app.warehouse.time-zone:Asia/Shanghai}") String timeZone) {
        this.inbounds = inbounds;
        this.outbounds = outbounds;
        this.businessZone = ZoneId.of(timeZone);
    }

    @Transactional(readOnly = true)
    public WarehouseProgressResponse today() {
        LocalDate today = LocalDate.now(businessZone);
        OffsetDateTime start = today.atStartOfDay(businessZone).toOffsetDateTime();
        OffsetDateTime end = today.plusDays(1).atStartOfDay(businessZone).toOffsetDateTime();

        return new WarehouseProgressResponse(
                today,
                businessZone.getId(),
                inbounds.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(start, end),
                outbounds.countByStatusAndCompletedAtGreaterThanEqualAndCompletedAtLessThan(
                        OutboundStatus.COMPLETED, start, end),
                outbounds.countByStatus(OutboundStatus.PENDING),
                outbounds.countByStatusAndDifferenceReasonIsNotNull(OutboundStatus.COMPLETED),
                OffsetDateTime.now(businessZone));
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
            OffsetDateTime updatedAt) {
    }
}
