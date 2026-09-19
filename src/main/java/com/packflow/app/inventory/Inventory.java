package com.packflow.app.inventory;

import com.packflow.app.outbound.OutboundRecord;
import com.packflow.app.outbound.OutboundStatus;
import com.packflow.app.product.Product;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "inventory")
public class Inventory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false)
    @JoinColumn(name = "product_id", nullable = false, unique = true)
    private Product product;

    @Column(nullable = false)
    private BigDecimal quantity;

    @Version
    @Column(nullable = false)
    private Long version;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Inventory() {
    }

    public Inventory(Product product) {
        this.product = product;
        this.quantity = BigDecimal.ZERO;
    }

    void recordInbound(InboundRecord inboundRecord) {
        if (inboundRecord == null || inboundRecord.getQuantity() == null
                || inboundRecord.getQuantity().signum() <= 0) {
            throw new IllegalArgumentException("Inbound record must have a positive quantity");
        }
        quantity = quantity.add(inboundRecord.getQuantity());
    }

    public void recordCompletedOutbound(OutboundRecord outboundRecord) {
        if (outboundRecord == null || outboundRecord.getStatus() != OutboundStatus.COMPLETED
                || outboundRecord.getActualQuantity() == null
                || outboundRecord.getActualQuantity().signum() <= 0
                || !product.getId().equals(outboundRecord.getProduct().getId())) {
            throw new IllegalArgumentException("A completed outbound record for this product is required");
        }
        if (quantity.compareTo(outboundRecord.getActualQuantity()) < 0) {
            throw new IllegalStateException("Insufficient physical inventory");
        }
        quantity = quantity.subtract(outboundRecord.getActualQuantity());
    }

    public Long getId() {
        return id;
    }

    public Product getProduct() {
        return product;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }
}
