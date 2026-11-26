package com.globalrfq.rfq.quotation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "rfq_mandatory_field_config")
public class MandatoryFieldConfig {
    @Id
    @Column(name = "field_key", length = 80)
    private String fieldKey;

    @Column(name = "is_required", nullable = false)
    private boolean required;

    @Column(name = "updated_by_user_id", nullable = false)
    private Long updatedByUserId;

    @Column(name = "change_reason", nullable = false, length = 1000)
    private String changeReason;

    protected MandatoryFieldConfig() { }

    public MandatoryFieldConfig(String fieldKey, boolean required, Long updatedByUserId, String changeReason) {
        this.fieldKey = fieldKey;
        this.required = required;
        this.updatedByUserId = updatedByUserId;
        this.changeReason = changeReason;
    }

    public String getFieldKey() { return fieldKey; }
    public boolean isRequired() { return required; }
}
