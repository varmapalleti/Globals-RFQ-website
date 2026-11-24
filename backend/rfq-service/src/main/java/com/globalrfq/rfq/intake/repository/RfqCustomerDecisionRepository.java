package com.globalrfq.rfq.intake.repository;

import com.globalrfq.rfq.intake.entity.RfqCustomerDecisionRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RfqCustomerDecisionRepository extends JpaRepository<RfqCustomerDecisionRecord, Long> {
    Optional<RfqCustomerDecisionRecord> findByRfqIdAndIdempotencyKey(Long rfqId, String idempotencyKey);
}
