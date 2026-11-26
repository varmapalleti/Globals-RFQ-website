package com.globalrfq.rfq.intake.repository;

import com.globalrfq.rfq.intake.entity.RfqCustomerSubmission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RfqCustomerSubmissionRepository extends JpaRepository<RfqCustomerSubmission, Long> {
    Optional<RfqCustomerSubmission> findByRfqIdAndIdempotencyKey(Long rfqId, String idempotencyKey);
    List<RfqCustomerSubmission> findByRfqIdOrderBySentAtDesc(Long rfqId);
}
