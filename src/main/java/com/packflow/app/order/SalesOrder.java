package com.packflow.app.order;

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
import java.time.OffsetDateTime;
import java.time.LocalDate;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "sales_order")
public class SalesOrder {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "order_no", nullable = false, unique = true, length = 50)
    private String orderNo;
    @Column(name = "customer_name", nullable = false, length = 150)
    private String customerName;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30)
    private OrderStatus status;
    @Column(name = "delivery_date")
    private LocalDate deliveryDate;
    @Enumerated(EnumType.STRING) @Column(name = "abnormal_type", length = 30)
    private OrderAbnormalType abnormalType;
    @Column(name = "exception_reason", columnDefinition = "text")
    private String exceptionReason;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private AppUser createdBy;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
    @UpdateTimestamp @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected SalesOrder() { }

    public SalesOrder(String orderNo, String customerName, AppUser createdBy) {
        this.orderNo = orderNo;
        this.customerName = customerName;
        this.createdBy = createdBy;
        this.status = OrderStatus.PENDING_CHECK;
    }

    public SalesOrder(String orderNo, String customerName, AppUser createdBy, LocalDate deliveryDate) {
        this(orderNo, customerName, createdBy);
        if (deliveryDate == null) throw new IllegalArgumentException("Delivery date is required");
        this.deliveryDate = deliveryDate;
    }

    public void changeDeliveryDate(LocalDate date) {
        if (date == null) throw new IllegalArgumentException("Delivery date is required");
        this.deliveryDate = date;
    }

    public void markPendingOutbound() {
        status = OrderStatus.PENDING_OUTBOUND;
        exceptionReason = null;
        abnormalType = null;
    }

    public void markAbnormal(String reason) {
        markAbnormal(OrderAbnormalType.OTHER, reason);
    }

    public void markAbnormal(OrderAbnormalType type, String reason) {
        if (type == null || reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Abnormal type and reason are required");
        }
        abnormalType = type;
        status = OrderStatus.ABNORMAL;
        exceptionReason = reason;
    }

    public void markCompleted() {
        status = OrderStatus.COMPLETED;
        exceptionReason = null;
        abnormalType = null;
    }

    public void cancel() {
        status = OrderStatus.CANCELLED;
        exceptionReason = null;
        abnormalType = null;
    }
    public Long getId() { return id; }
    public String getOrderNo() { return orderNo; }
    public String getCustomerName() { return customerName; }
    public OrderStatus getStatus() { return status; }
    public String getExceptionReason() { return exceptionReason; }
    public OrderAbnormalType getAbnormalType() { return abnormalType; }
    public LocalDate getDeliveryDate() { return deliveryDate; }
    public AppUser getCreatedBy() { return createdBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
