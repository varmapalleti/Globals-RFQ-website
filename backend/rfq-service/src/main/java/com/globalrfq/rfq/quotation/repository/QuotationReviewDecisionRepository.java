package com.globalrfq.rfq.quotation.repository;

import com.globalrfq.rfq.quotation.entity.QuotationReviewDecision;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface QuotationReviewDecisionRepository extends JpaRepository<QuotationReviewDecision, Long> {
    Optional<QuotationReviewDecision> findByQuotationVersionId(Long quotationVersionId);
    long countByQuotationVersionRfqIdAndDecision(Long rfqId, String decision);
}
