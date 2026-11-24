package com.globalrfq.rfq.quotation.repository;

import com.globalrfq.rfq.quotation.entity.RfqAdminAuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RfqAdminAuditEventRepository extends JpaRepository<RfqAdminAuditEvent, Long> {
    List<RfqAdminAuditEvent> findTop100ByOrderByOccurredAtDescIdDesc();
}
