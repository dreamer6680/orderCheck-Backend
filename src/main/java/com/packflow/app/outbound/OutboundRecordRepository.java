package com.packflow.app.outbound;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboundRecordRepository extends JpaRepository<OutboundRecord, Long> {
    List<OutboundRecord> findByOrderId(Long orderId);
    List<OutboundRecord> findByStatusOrderByCreatedAtDescIdDesc(OutboundStatus status);
    List<OutboundRecord> findAllByOrderByCreatedAtDescIdDesc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select record from OutboundRecord record where record.id = :id")
    Optional<OutboundRecord> findByIdForUpdate(@Param("id") Long id);

    boolean existsByOrderId(Long orderId);
}
