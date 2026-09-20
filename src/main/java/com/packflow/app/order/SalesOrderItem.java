package com.packflow.app.order;

import com.packflow.app.product.Product;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "sales_order_item", uniqueConstraints = @UniqueConstraint(columnNames = {"order_id", "product_id"}))
public class SalesOrderItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "order_id", nullable = false)
    private SalesOrder order;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "product_id", nullable = false)
    private Product product;
    @Column(name = "ordered_quantity", nullable = false, precision = 18, scale = 3)
    private BigDecimal orderedQuantity;
    @Column(name = "waived_quantity", nullable = false, precision = 18, scale = 3)
    private BigDecimal waivedQuantity = BigDecimal.ZERO;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected SalesOrderItem() { }

    public SalesOrderItem(SalesOrder order, Product product, BigDecimal orderedQuantity) {
        this.order = order;
        this.product = product;
        this.orderedQuantity = orderedQuantity;
    }

    public void waive(BigDecimal quantity) {
        if (quantity == null || quantity.signum() < 0
                || waivedQuantity.add(quantity).compareTo(orderedQuantity) > 0) {
            throw new IllegalArgumentException("Invalid waived quantity");
        }
        waivedQuantity = waivedQuantity.add(quantity);
    }

    public BigDecimal getWaivedQuantity() { return waivedQuantity; }
    public Long getId() { return id; }
    public SalesOrder getOrder() { return order; }
    public Product getProduct() { return product; }
    public BigDecimal getOrderedQuantity() { return orderedQuantity; }
}
