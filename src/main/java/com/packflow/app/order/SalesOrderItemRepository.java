package com.packflow.app.order;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SalesOrderItemRepository extends JpaRepository<SalesOrderItem, Long> {
    List<SalesOrderItem> findByOrderIdOrderByProductIdAsc(Long orderId);
}
