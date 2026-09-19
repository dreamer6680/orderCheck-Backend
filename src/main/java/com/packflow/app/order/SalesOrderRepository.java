package com.packflow.app.order;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SalesOrderRepository extends JpaRepository<SalesOrder, Long> {
    long countByStatus(OrderStatus status);
    long countByStatusAndDeliveryDate(OrderStatus status, LocalDate deliveryDate);
    long countByStatusAndDeliveryDateIsNull(OrderStatus status);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from SalesOrder o where o.id = :id")
    Optional<SalesOrder> findByIdForUpdate(@Param("id") Long id);

    @Query("select o from SalesOrder o where (:status is null or o.status = :status) "
            + "and (lower(o.orderNo) like :pattern or lower(o.customerName) like :pattern) "
            + "order by o.createdAt desc, o.id desc")
    List<SalesOrder> search(@Param("status") OrderStatus status, @Param("pattern") String pattern, Pageable pageable);
}
