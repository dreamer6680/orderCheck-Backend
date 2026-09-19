package com.packflow.app.inventory;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    Optional<Inventory> findByProductId(Long productId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select inventory from Inventory inventory where inventory.product.id = :productId")
    Optional<Inventory> findByProductIdForUpdate(@Param("productId") Long productId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select inventory from Inventory inventory where inventory.product.id in :productIds "
            + "order by inventory.product.id asc")
    List<Inventory> findAllByProductIdInForUpdate(@Param("productIds") List<Long> productIds);

    @Query(value = "select coalesce(sum(planned_quantity), 0) from outbound_record "
            + "where product_id = :productId and status = 'PENDING'", nativeQuery = true)
    BigDecimal pendingOutboundQuantity(@Param("productId") Long productId);
}
