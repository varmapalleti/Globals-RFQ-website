package com.globalrfq.rfq.workflow.repository;

import com.globalrfq.rfq.workflow.entity.RfqWorkflowEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RfqWorkflowEventRepository extends JpaRepository<RfqWorkflowEvent, Long> {
    List<RfqWorkflowEvent> findByRfqIdOrderByOccurredAtAscIdAsc(Long rfqId);
}
