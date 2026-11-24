package com.globalrfq.rfq.quotation.repository;

import com.globalrfq.rfq.quotation.entity.QuotationVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface QuotationVersionRepository extends JpaRepository<QuotationVersion, Long> {
    List<QuotationVersion> findByRfqIdOrderByVersionNumberDesc(Long rfqId);
    Optional<QuotationVersion> findByRfqIdAndId(Long rfqId, Long id);
    Optional<QuotationVersion> findByRfqIdAndIdempotencyKey(Long rfqId, String idempotencyKey);
    Optional<QuotationVersion> findTopByRfqIdOrderByVersionNumberDesc(Long rfqId);
    long countByRfqId(Long rfqId);
}
