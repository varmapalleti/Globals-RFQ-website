package com.globalrfq.rfq.intake.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "rfq_customer_submissions")
public class RfqCustomerSubmission {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rfq_id", nullable = false)
    private Rfq rfq;

    @Column(name = "quotation_version_id", nullable = false)
    private Long quotationVersionId;

    @Column(name = "sent_by_user_id", nullable = false)
    private Long sentByUserId;

    @Column(name = "external_reference", length = 255)
    private String externalReference;

    @Column(name = "idempotency_key", nullable = false, length = 64)
    private String idempotencyKey;

    @Column(name = "send_channel", nullable = false, length = 60)
    private String sendChannel = "CLIENT_SOFTWARE_MANUAL";

    @Column(nullable = false, length = 32)
    private String result = "SENT";

    @Column(length = 2000)
    private String notes;

    @Column(name = "sent_at", insertable = false, updatable = false)
    private Instant sentAt;

    protected RfqCustomerSubmission() { }

    public RfqCustomerSubmission(Rfq rfq, Long quotationVersionId, Long sentByUserId,
                                 String externalReference, String idempotencyKey, String notes) {
        this.rfq = rfq;
        this.quotationVersionId = quotationVersionId;
        this.sentByUserId = sentByUserId;
        this.externalReference = externalReference;
        this.idempotencyKey = idempotencyKey;
        this.notes = notes;
    }

    public Long getId() { return id; }
    public Rfq getRfq() { return rfq; }
    public Long getQuotationVersionId() { return quotationVersionId; }
    public Long getSentByUserId() { return sentByUserId; }
    public String getExternalReference() { return externalReference; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getSendChannel() { return sendChannel; }
    public String getResult() { return result; }
    public String getNotes() { return notes; }
    public Instant getSentAt() { return sentAt; }
}
