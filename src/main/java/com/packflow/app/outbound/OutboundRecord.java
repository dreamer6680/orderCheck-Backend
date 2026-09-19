package com.packflow.app.outbound;

import com.packflow.app.order.SalesOrder;
import com.packflow.app.order.SalesOrderItem;
import com.packflow.app.product.Product;
import com.packflow.app.user.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.LocalDate;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "outbound_record")
public class OutboundRecord {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "record_no", nullable = false, unique = true, length = 50)
    private String recordNo;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "order_id", nullable = false)
    private SalesOrder order;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "order_item_id", nullable = false, unique = true)
    private SalesOrderItem orderItem;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "product_id", nullable = false)
    private Product product;
    @Column(name = "planned_outbound_date", nullable = false)
    private LocalDate plannedOutboundDate;
    @Column(name = "planned_quantity", nullable = false, precision = 18, scale = 3)
    private BigDecimal plannedQuantity;
    @Column(name = "actual_quantity", precision = 18, scale = 3)
    private BigDecimal actualQuantity;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private OutboundStatus status;
    @Column(name = "difference_reason", length = 500)
    private String differenceReason;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "operator_id")
    private AppUser operator;
    @Column(name = "completed_at")
    private OffsetDateTime completedAt;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
    @UpdateTimestamp @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected OutboundRecord() { }

    public OutboundRecord(String recordNo, SalesOrderItem item, LocalDate plannedOutboundDate) {
        if (plannedOutboundDate == null) throw new IllegalArgumentException("Planned outbound date is required");
        this.plannedOutboundDate = plannedOutboundDate;
        this.recordNo = recordNo;
        this.order = item.getOrder();
        this.orderItem = item;
        this.product = item.getProduct();
        this.plannedQuantity = item.getOrderedQuantity();
        this.status = OutboundStatus.PENDING;
    }

    public void reschedule(LocalDate date) {
        if (status != OutboundStatus.PENDING) throw new IllegalStateException("Only pending tasks can be rescheduled");
        if (date == null) throw new IllegalArgumentException("Planned outbound date is required");
        plannedOutboundDate = date;
    }

    public void cancelPending() {
        if (status == OutboundStatus.PENDING) status = OutboundStatus.CANCELLED;
    }

    public void complete(BigDecimal actualQuantity, String differenceReason, AppUser operator) {
        this.actualQuantity = actualQuantity;
        this.differenceReason = differenceReason;
        this.operator = operator;
        this.completedAt = OffsetDateTime.now();
        this.status = OutboundStatus.COMPLETED;
    }

    public Long getId() { return id; }
    public String getRecordNo() { return recordNo; }
    public SalesOrder getOrder() { return order; }
    public SalesOrderItem getOrderItem() { return orderItem; }
    public Product getProduct() { return product; }
    public LocalDate getPlannedOutboundDate() { return plannedOutboundDate; }
    public BigDecimal getPlannedQuantity() { return plannedQuantity; }
    public BigDecimal getActualQuantity() { return actualQuantity; }
    public OutboundStatus getStatus() { return status; }
    public String getDifferenceReason() { return differenceReason; }
    public AppUser getOperator() { return operator; }
    public OffsetDateTime getCompletedAt() { return completedAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
