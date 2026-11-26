CREATE TABLE rfq_quotation_drafts (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    rfq_id BIGINT NOT NULL,
    draft_json JSON NOT NULL,
    saved_by_user_id BIGINT NOT NULL,
    saved_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_quotation_draft_rfq FOREIGN KEY (rfq_id) REFERENCES rfqs(id) ON DELETE RESTRICT,
    CONSTRAINT uq_quotation_draft_rfq UNIQUE (rfq_id)
) ENGINE=InnoDB;

CREATE TABLE rfq_quotation_versions (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    rfq_id BIGINT NOT NULL,
    version_number INT NOT NULL,
    idempotency_key VARCHAR(64) NOT NULL,
    request_sha256 CHAR(64) NOT NULL,
    currency CHAR(3) NULL,
    subtotal DECIMAL(18,2) NULL,
    freight DECIMAL(18,2) NOT NULL DEFAULT 0,
    other_charges DECIMAL(18,2) NOT NULL DEFAULT 0,
    discount DECIMAL(18,2) NOT NULL DEFAULT 0,
    tax_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    grand_total DECIMAL(18,2) NULL,
    quotation_json JSON NOT NULL,
    mandatory_fields_snapshot_json JSON NOT NULL,
    missing_mandatory_fields_json JSON NOT NULL,
    submitted_by_user_id BIGINT NOT NULL,
    submitted_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_quotation_version_rfq FOREIGN KEY (rfq_id) REFERENCES rfqs(id) ON DELETE RESTRICT,
    CONSTRAINT uq_quotation_version_number UNIQUE (rfq_id, version_number),
    CONSTRAINT uq_quotation_version_idempotency UNIQUE (rfq_id, idempotency_key),
    INDEX idx_quotation_versions_history (rfq_id, version_number)
) ENGINE=InnoDB;

CREATE TABLE rfq_quotation_version_reviews (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    quotation_version_id BIGINT NOT NULL,
    decision VARCHAR(16) NOT NULL,
    reviewer_user_id BIGINT NOT NULL,
    reason VARCHAR(2000) NULL,
    decided_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_quotation_review_version FOREIGN KEY (quotation_version_id) REFERENCES rfq_quotation_versions(id) ON DELETE RESTRICT,
    CONSTRAINT uq_quotation_review_version UNIQUE (quotation_version_id),
    INDEX idx_quotation_reviews_actor (reviewer_user_id, decided_at)
) ENGINE=InnoDB;

CREATE TABLE rfq_mandatory_field_config (
    field_key VARCHAR(80) NOT NULL PRIMARY KEY,
    is_required BOOLEAN NOT NULL,
    updated_by_user_id BIGINT NOT NULL,
    change_reason VARCHAR(1000) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)
) ENGINE=InnoDB;

CREATE TABLE rfq_workflow_policy (
    id TINYINT NOT NULL PRIMARY KEY,
    max_formal_rejections INT NOT NULL,
    updated_by_user_id BIGINT NULL,
    change_reason VARCHAR(1000) NULL,
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT chk_rfq_max_rejections CHECK (max_formal_rejections BETWEEN 1 AND 20)
) ENGINE=InnoDB;

INSERT INTO rfq_workflow_policy (id, max_formal_rejections) VALUES (1, 3);
