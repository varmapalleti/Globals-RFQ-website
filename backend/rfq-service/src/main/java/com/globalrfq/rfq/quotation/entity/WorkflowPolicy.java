package com.globalrfq.rfq.quotation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "rfq_workflow_policy")
public class WorkflowPolicy {
    @Id
    private Byte id;

    @Column(name = "max_formal_rejections", nullable = false)
    private int maxFormalRejections;

    @Column(name = "updated_by_user_id")
    private Long updatedByUserId;

    @Column(name = "change_reason", length = 1000)
    private String changeReason;

    protected WorkflowPolicy() { }

    public void update(int maxFormalRejections, Long updatedByUserId, String changeReason) {
        this.maxFormalRejections = maxFormalRejections;
        this.updatedByUserId = updatedByUserId;
        this.changeReason = changeReason;
    }

    public int getMaxFormalRejections() { return maxFormalRejections; }
}
