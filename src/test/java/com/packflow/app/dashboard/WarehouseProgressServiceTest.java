package com.packflow.app.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.packflow.app.inventory.InboundRecordRepository;
import com.packflow.app.outbound.OutboundRecordRepository;
import com.packflow.app.outbound.OutboundStatus;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class WarehouseProgressServiceTest {
    @Test
    void emptyWarehouseReturnsZeroPercentsWithoutDividingByZero() {
        var inbounds = mock(InboundRecordRepository.class);
        var outbounds = mock(OutboundRecordRepository.class);
        var result = new WarehouseProgressService(inbounds, outbounds, "Asia/Shanghai").today();
        assertThat(result.todayInboundPercent()).isEqualByComparingTo("0.0");
        assertThat(result.todayPendingOutboundPercent()).isEqualByComparingTo("0.0");
        assertThat(result.differencePercent()).isEqualByComparingTo("0.0");
        assertThat(result.pendingOutboundCount()).isZero();
    }

    @Test
    void aggregatesRealCountsUsingBusinessDayBoundaries() {
        InboundRecordRepository inbounds = mock(InboundRecordRepository.class);
        OutboundRecordRepository outbounds = mock(OutboundRecordRepository.class);
        WarehouseProgressService service =
                new WarehouseProgressService(inbounds, outbounds, "Asia/Shanghai");

        when(inbounds.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                any(OffsetDateTime.class), any(OffsetDateTime.class))).thenReturn(7L);
        when(outbounds.countByStatusAndCompletedAtGreaterThanEqualAndCompletedAtLessThan(
                eq(OutboundStatus.COMPLETED),
                any(OffsetDateTime.class), any(OffsetDateTime.class))).thenReturn(5L);
        when(inbounds.count()).thenReturn(14L);
        when(outbounds.countByStatus(OutboundStatus.PENDING)).thenReturn(3L);
        when(outbounds.countByStatusAndPlannedOutboundDate(eq(OutboundStatus.PENDING), any(java.time.LocalDate.class)))
                .thenReturn(2L);
        when(outbounds.countByStatus(OutboundStatus.COMPLETED)).thenReturn(8L);
        when(outbounds.countByStatusAndDifferenceReasonIsNotNull(OutboundStatus.COMPLETED))
                .thenReturn(2L);

        var result = service.today();

        assertThat(result.todayInboundCount()).isEqualTo(7);
        assertThat(result.todayOutboundCount()).isEqualTo(5);
        assertThat(result.pendingOutboundCount()).isEqualTo(3);
        assertThat(result.differenceRecordCount()).isEqualTo(2);
        assertThat(result.totalInboundCount()).isEqualTo(14);
        assertThat(result.todayPendingOutboundCount()).isEqualTo(2);
        assertThat(result.completedOutboundCount()).isEqualTo(8);
        assertThat(result.todayInboundPercent()).isEqualByComparingTo("50.0");
        assertThat(result.todayPendingOutboundPercent()).isEqualByComparingTo("66.7");
        assertThat(result.differencePercent()).isEqualByComparingTo("25.0");
        verify(outbounds).countByStatusAndPlannedOutboundDate(OutboundStatus.PENDING, result.businessDate());
        assertThat(result.timeZone()).isEqualTo("Asia/Shanghai");

        ArgumentCaptor<OffsetDateTime> start = ArgumentCaptor.forClass(OffsetDateTime.class);
        ArgumentCaptor<OffsetDateTime> end = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(inbounds).countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                start.capture(), end.capture());

        var zone = ZoneId.of(result.timeZone());
        assertThat(start.getValue().toLocalDate()).isEqualTo(result.businessDate());
        assertThat(start.getValue().toLocalTime()).isEqualTo(java.time.LocalTime.MIDNIGHT);
        assertThat(end.getValue().toLocalDate()).isEqualTo(result.businessDate().plusDays(1));
        assertThat(end.getValue().toLocalTime()).isEqualTo(java.time.LocalTime.MIDNIGHT);
        assertThat(start.getValue().atZoneSameInstant(zone).toLocalDate())
                .isEqualTo(result.businessDate());
    }
}
