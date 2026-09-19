package com.packflow.app.inventory;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InboundRecordRepository extends JpaRepository<InboundRecord, Long> {

    List<InboundRecord> findByProductId(Long productId);

    List<InboundRecord> findAllByOrderByCreatedAtDesc();
}
