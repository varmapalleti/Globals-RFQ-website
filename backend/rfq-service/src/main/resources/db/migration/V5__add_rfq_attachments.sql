CREATE TABLE rfq_attachments (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    public_id CHAR(36) NOT NULL,
    rfq_id BIGINT NOT NULL,
    original_file_name VARCHAR(255) NOT NULL,
    stored_object_key CHAR(36) NOT NULL,
    file_extension VARCHAR(12) NOT NULL,
    detected_media_type VARCHAR(100) NOT NULL,
    byte_size BIGINT NOT NULL,
    sha256 CHAR(64) NOT NULL,
    category VARCHAR(40) NOT NULL,
    scan_state VARCHAR(16) NOT NULL DEFAULT 'PENDING_SCAN',
    uploaded_by_user_id BIGINT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_rfq_attachment_rfq FOREIGN KEY (rfq_id) REFERENCES rfqs(id) ON DELETE RESTRICT,
    CONSTRAINT uq_rfq_attachment_public_id UNIQUE (public_id),
    CONSTRAINT uq_rfq_attachment_storage_key UNIQUE (stored_object_key),
    CONSTRAINT chk_rfq_attachment_scan_state CHECK (scan_state IN ('PENDING_SCAN','CLEAN','INFECTED')),
    INDEX idx_rfq_attachment_list (rfq_id, created_at)
) ENGINE=InnoDB;

CREATE TABLE rfq_attachment_scan_events (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    attachment_id BIGINT NOT NULL,
    scan_state VARCHAR(16) NOT NULL,
    scanner_name VARCHAR(120) NOT NULL,
    scan_reference VARCHAR(255) NULL,
    recorded_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_attachment_scan_attachment FOREIGN KEY (attachment_id) REFERENCES rfq_attachments(id) ON DELETE RESTRICT,
    INDEX idx_attachment_scan_history (attachment_id, recorded_at)
) ENGINE=InnoDB;
