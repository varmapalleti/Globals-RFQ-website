package com.globalrfq.rfq.intake.repository;

import com.globalrfq.rfq.intake.entity.RfqLineItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface RfqLineItemRepository extends JpaRepository<RfqLineItem, Long> {
    List<RfqLineItem> findByRfqIdOrderByLineNumberAsc(Long rfqId);
    java.util.Optional<RfqLineItem> findByRfqIdAndLineNumber(Long rfqId, int lineNumber);

    @Query("select l.rfq.id as rfqId, count(l.id) as lineCount from RfqLineItem l where l.rfq.id in :rfqIds group by l.rfq.id")
    List<RfqLineCount> countByRfqIdIn(@Param("rfqIds") Collection<Long> rfqIds);

    interface RfqLineCount {
        Long getRfqId();
        Long getLineCount();
    }
}
