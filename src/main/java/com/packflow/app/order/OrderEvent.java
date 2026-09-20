package com.packflow.app.order;

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
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "order_event")
public class OrderEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private SalesOrder order;
    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 40)
    private OrderEventType eventType;
    @Column(name = "description", nullable = false, columnDefinition = "text")
    private String description;
    @Column(name = "operator_username", length = 50)
    private String operatorUsername;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected OrderEvent() { }

    public OrderEvent(SalesOrder order, OrderEventType eventType,
            String description, String operatorUsername) {
        this.order = order;
        this.eventType = eventType;
        this.description = description;
        this.operatorUsername = operatorUsername;
    }

    public Long getId() { return id; }
    public OrderEventType getEventType() { return eventType; }
    public String getDescription() { return description; }
    public String getOperatorUsername() { return operatorUsername; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
