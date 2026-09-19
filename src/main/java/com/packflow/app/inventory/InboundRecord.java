package com.packflow.app.inventory;

import com.packflow.app.product.Product;
import com.packflow.app.user.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "inbound_record")
public class InboundRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "record_no", nullable = false, unique = true)
    private String recordNo;

    @ManyToOne(optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private BigDecimal quantity;

    @Column(length = 500)
    private String remark;

    @ManyToOne(optional = false)
    @JoinColumn(name = "operator_id", nullable = false)
    private AppUser operator;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected InboundRecord() {
    }

    public InboundRecord(String recordNo, Product product, BigDecimal quantity, String remark, AppUser operator) {
        this.recordNo = recordNo;
        this.product = product;
        this.quantity = quantity;
        this.remark = remark;
        this.operator = operator;
    }

    public Long getId() {
        return id;
    }

    public String getRecordNo() {
        return recordNo;
    }

    public Product getProduct() {
        return product;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public String getRemark() {
        return remark;
    }

    public AppUser getOperator() {
        return operator;
    }
}
