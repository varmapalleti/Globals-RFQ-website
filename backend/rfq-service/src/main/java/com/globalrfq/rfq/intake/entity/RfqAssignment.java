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
import jakarta.persistence.Version;

import java.time.Instant;

@Entity
@Table(name = "rfq_assignments")
public class RfqAssignment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rfq_id", nullable = false)
    private Rfq rfq;

    @Enumerated(EnumType.STRING)
    @Column(name = "assignment_role", nullable = false, length = 40)
    private AssignmentRole assignmentRole;

    @Column(name = "assigned_user_id", nullable = false)
    private Long assignedUserId;

    @Column(name = "assigned_by_user_id", nullable = false)
    private Long assignedByUserId;

    @Column(length = 1000)
    private String reason;

    @Column(name = "assigned_at", nullable = false)
    private Instant assignedAt = Instant.now();

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "active_slot")
    private Byte activeSlot = Byte.valueOf((byte) 1);

    @Version
    @Column(nullable = false)
    private long version;

    protected RfqAssignment() { }

    public RfqAssignment(Rfq rfq, AssignmentRole assignmentRole, Long assignedUserId, Long assignedByUserId, String reason) {
        this.rfq = rfq;
        this.assignmentRole = assignmentRole;
        this.assignedUserId = assignedUserId;
        this.assignedByUserId = assignedByUserId;
        this.reason = reason;
    }

    public Long getId() { return id; }
    public Rfq getRfq() { return rfq; }
    public AssignmentRole getAssignmentRole() { return assignmentRole; }
    public Long getAssignedUserId() { return assignedUserId; }
    public Long getAssignedByUserId() { return assignedByUserId; }
    public String getReason() { return reason; }
    public Instant getAssignedAt() { return assignedAt; }
    public boolean isActive() { return active; }
    public long getVersion() { return version; }

    public void deactivate() {
        this.active = false;
        this.activeSlot = null;
    }
}
