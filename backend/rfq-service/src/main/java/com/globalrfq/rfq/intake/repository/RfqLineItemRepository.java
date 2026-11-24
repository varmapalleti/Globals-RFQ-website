package com.globalrfq.rfq.intake.repository;

import com.globalrfq.rfq.intake.entity.RfqLineItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RfqLineItemRepository extends JpaRepository<RfqLineItem, Long> {
    List<RfqLineItem> findByRfqIdOrderByLineNumberAsc(Long rfqId);
    java.util.Optional<RfqLineItem> findByRfqIdAndLineNumber(Long rfqId, int lineNumber);
}
