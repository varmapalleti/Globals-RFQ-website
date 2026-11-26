package com.globalrfq.rfq.quotation.repository;

import com.globalrfq.rfq.quotation.entity.QuotationDraft;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface QuotationDraftRepository extends JpaRepository<QuotationDraft, Long> {
    Optional<QuotationDraft> findByRfqId(Long rfqId);
}
