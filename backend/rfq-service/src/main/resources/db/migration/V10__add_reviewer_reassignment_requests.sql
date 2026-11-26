CREATE TABLE rfq_reviewer_reassignment_requests (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    rfq_id BIGINT NOT NULL,
    requested_by_reviewer_id BIGINT NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    idempotency_key VARCHAR(64) NOT NULL,
    request_sha256 CHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    resolved_by_admin_id BIGINT NULL,
    resolution_reason VARCHAR(1000) NULL,
    replacement_reviewer_id BIGINT NULL,
    requested_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    resolved_at TIMESTAMP(6) NULL,
    CONSTRAINT fk_reviewer_reassignment_rfq FOREIGN KEY (rfq_id) REFERENCES rfqs(id) ON DELETE RESTRICT,
    CONSTRAINT uq_reviewer_reassignment_idempotency UNIQUE (rfq_id, idempotency_key),
    INDEX idx_reviewer_reassignment_rfq (rfq_id, requested_at),
    INDEX idx_reviewer_reassignment_pending (rfq_id, status)
) ENGINE=InnoDB;
