package com.packflow.app.product;

import com.packflow.app.inventory.Inventory;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "product")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String sku;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String unit;

    @Column(name = "safety_stock", nullable = false)
    private BigDecimal safetyStock;

    @Column(nullable = false)
    private boolean enabled;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @OneToOne(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private Inventory inventory;

    protected Product() {
    }

    public Product(String sku, String name, String unit, BigDecimal safetyStock, boolean enabled) {
        this.sku = sku;
        this.name = name;
        this.unit = unit;
        this.safetyStock = safetyStock;
        this.enabled = enabled;
        this.inventory = new Inventory(this);
    }

    public void update(String sku, String name, String unit, BigDecimal safetyStock, boolean enabled) {
        this.sku = sku;
        this.name = name;
        this.unit = unit;
        this.safetyStock = safetyStock;
        this.enabled = enabled;
    }

    public void disable() {
        this.enabled = false;
    }

    public Long getId() {
        return id;
    }

    public String getSku() {
        return sku;
    }

    public String getName() {
        return name;
    }

    public String getUnit() {
        return unit;
    }

    public BigDecimal getSafetyStock() {
        return safetyStock;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
