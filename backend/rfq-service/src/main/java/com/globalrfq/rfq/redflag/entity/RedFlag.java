package com.globalrfq.rfq.redflag.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "red_flags")
public class RedFlag {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rfq_id", nullable = false)
    private Long rfqId;

    @Column(name = "rfq_public_id", nullable = false, length = 36)
    private String rfqPublicId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private RedFlagSeverity severity;

    @Column(nullable = false, columnDefinition = "text")
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private RedFlagStatus status = RedFlagStatus.OPEN;

    @Column(name = "raised_by", nullable = false)
    private Long raisedBy;

    @Column(name = "assigned_to")
    private Long assignedTo;

    @Column(name = "resolution_note", columnDefinition = "text")
    private String resolutionNote;

    @Column(name = "resolved_by")
    private Long resolvedBy;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected RedFlag() { }

    public RedFlag(Long rfqId, String rfqPublicId, RedFlagSeverity severity, String reason,
                   Long raisedBy, Long assignedTo) {
        this.rfqId = rfqId;
        this.rfqPublicId = rfqPublicId;
        this.severity = severity;
        this.reason = reason;
        this.raisedBy = raisedBy;
        this.assignedTo = assignedTo;
    }

    public void resolve(String resolutionNote, Long resolvedBy) {
        this.status = RedFlagStatus.RESOLVED;
        this.resolutionNote = resolutionNote;
        this.resolvedBy = resolvedBy;
        this.resolvedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void dismiss(String resolutionNote, Long dismissedBy) {
        this.status = RedFlagStatus.DISMISSED;
        this.resolutionNote = resolutionNote;
        this.resolvedBy = dismissedBy;
        this.resolvedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getRfqId() { return rfqId; }
    public String getRfqPublicId() { return rfqPublicId; }
    public RedFlagSeverity getSeverity() { return severity; }
    public String getReason() { return reason; }
    public RedFlagStatus getStatus() { return status; }
    public Long getRaisedBy() { return raisedBy; }
    public Long getAssignedTo() { return assignedTo; }
    public String getResolutionNote() { return resolutionNote; }
    public Long getResolvedBy() { return resolvedBy; }
    public Instant getResolvedAt() { return resolvedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
