package com.globalrfq.rfq.quotation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "rfq_quotation_version_reviews")
public class QuotationReviewDecision {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quotation_version_id", nullable = false, unique = true)
    private QuotationVersion quotationVersion;

    @Column(nullable = false, length = 16)
    private String decision;

    @Column(name = "reviewer_user_id", nullable = false)
    private Long reviewerUserId;

    @Column(length = 2000)
    private String reason;

    @Column(name = "decided_at", nullable = false)
    private Instant decidedAt = Instant.now();

    protected QuotationReviewDecision() { }

    public QuotationReviewDecision(QuotationVersion quotationVersion, String decision, Long reviewerUserId, String reason) {
        this.quotationVersion = quotationVersion;
        this.decision = decision;
        this.reviewerUserId = reviewerUserId;
        this.reason = reason;
    }

    public Long getId() { return id; }
    public QuotationVersion getQuotationVersion() { return quotationVersion; }
    public String getDecision() { return decision; }
    public Long getReviewerUserId() { return reviewerUserId; }
    public String getReason() { return reason; }
    public Instant getDecidedAt() { return decidedAt; }
}
