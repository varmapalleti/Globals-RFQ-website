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
@Table(name = "rfq_reviewer_reassignment_requests")
public class ReviewerReassignmentRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rfq_id", nullable = false)
    private Rfq rfq;

    @Column(name = "requested_by_reviewer_id", nullable = false)
    private Long requestedByReviewerId;

    @Column(nullable = false, length = 1000)
    private String reason;

    @Column(name = "idempotency_key", nullable = false, length = 64)
    private String idempotencyKey;

    @Column(name = "request_sha256", nullable = false, length = 64)
    private String requestSha256;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ReviewerReassignmentStatus status = ReviewerReassignmentStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "rfq_status_before_request", length = 40)
    private RfqStatus rfqStatusBeforeRequest;

    @Column(name = "resolved_by_admin_id")
    private Long resolvedByAdminId;

    @Column(name = "resolution_reason", length = 1000)
    private String resolutionReason;

    @Column(name = "replacement_reviewer_id")
    private Long replacementReviewerId;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt = Instant.now();

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    protected ReviewerReassignmentRequest() { }

    public ReviewerReassignmentRequest(Rfq rfq, Long requestedByReviewerId, String reason,
                                       String idempotencyKey, String requestSha256, RfqStatus rfqStatusBeforeRequest) {
        this.rfq = rfq;
        this.requestedByReviewerId = requestedByReviewerId;
        this.reason = reason;
        this.idempotencyKey = idempotencyKey;
        this.requestSha256 = requestSha256;
        this.rfqStatusBeforeRequest = rfqStatusBeforeRequest;
    }

    public void resolve(ReviewerReassignmentStatus status, Long adminId, String reason, Long replacementReviewerId) {
        this.status = status;
        this.resolvedByAdminId = adminId;
        this.resolutionReason = reason;
        this.replacementReviewerId = replacementReviewerId;
        this.resolvedAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getRfqId() { return rfq.getId(); }
    public Long getRequestedByReviewerId() { return requestedByReviewerId; }
    public String getReason() { return reason; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getRequestSha256() { return requestSha256; }
    public ReviewerReassignmentStatus getStatus() { return status; }
    public RfqStatus getRfqStatusBeforeRequest() { return rfqStatusBeforeRequest; }
    public Long getResolvedByAdminId() { return resolvedByAdminId; }
    public String getResolutionReason() { return resolutionReason; }
    public Long getReplacementReviewerId() { return replacementReviewerId; }
    public Instant getRequestedAt() { return requestedAt; }
    public Instant getResolvedAt() { return resolvedAt; }
}
