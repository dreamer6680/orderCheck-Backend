package com.packflow.app.inventory;

import java.util.List;
import java.time.OffsetDateTime;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InboundRecordRepository extends JpaRepository<InboundRecord, Long> {

    long countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(OffsetDateTime start, OffsetDateTime end);

    List<InboundRecord> findByProductId(Long productId);

    List<InboundRecord> findAllByOrderByCreatedAtDesc();
}
