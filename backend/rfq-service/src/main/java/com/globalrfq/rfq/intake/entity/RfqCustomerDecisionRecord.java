package com.globalrfq.rfq.intake.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "rfq_customer_decisions")
public class RfqCustomerDecisionRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rfq_id", nullable = false)
    private Rfq rfq;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private CustomerDecision decision;

    @Column(name = "quotation_version_id")
    private Long quotationVersionId;

    @Column(name = "recorded_by_user_id", nullable = false)
    private Long recordedByUserId;

    @Column(length = 2000)
    private String notes;

    @Column(name = "source_reference", length = 255)
    private String sourceReference;

    @Column(name = "idempotency_key", nullable = false, length = 64)
    private String idempotencyKey;

    @Column(name = "decided_at", insertable = false, updatable = false)
    private Instant decidedAt;

    protected RfqCustomerDecisionRecord() { }

    public RfqCustomerDecisionRecord(Rfq rfq, CustomerDecision decision, Long quotationVersionId,
                                     Long recordedByUserId, String notes, String sourceReference,
                                     String idempotencyKey) {
        this.rfq = rfq;
        this.decision = decision;
        this.quotationVersionId = quotationVersionId;
        this.recordedByUserId = recordedByUserId;
        this.notes = notes;
        this.sourceReference = sourceReference;
        this.idempotencyKey = idempotencyKey;
    }

    public Long getId() { return id; }
    public CustomerDecision getDecision() { return decision; }
    public Long getQuotationVersionId() { return quotationVersionId; }
    public Long getRecordedByUserId() { return recordedByUserId; }
    public String getNotes() { return notes; }
    public String getSourceReference() { return sourceReference; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public Instant getDecidedAt() { return decidedAt; }
}
