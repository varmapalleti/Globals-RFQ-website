CREATE TABLE rfqs (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    public_id CHAR(36) NOT NULL,
    source_system VARCHAR(80) NOT NULL,
    source_rfq_id VARCHAR(120) NOT NULL,
    source_reference VARCHAR(255) NULL,
    customer_name VARCHAR(190) NOT NULL,
    customer_company VARCHAR(190) NULL,
    buyer_name VARCHAR(150) NULL,
    buyer_email VARCHAR(190) NULL,
    buyer_phone VARCHAR(40) NULL,
    request_title VARCHAR(255) NULL,
    due_at_local DATETIME(6) NULL,
    due_time_zone VARCHAR(80) NULL,
    requested_delivery_date DATE NULL,
    ship_to_text TEXT NULL,
    source_url VARCHAR(1000) NULL,
    terms_and_conditions LONGTEXT NULL,
    source_data_json JSON NULL,
    status VARCHAR(40) NOT NULL,
    customer_decision VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    created_by BIGINT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_rfqs_public_id UNIQUE (public_id),
    CONSTRAINT uq_rfqs_source_identity UNIQUE (source_system, source_rfq_id),
    INDEX idx_rfqs_status_created (status, created_at),
    INDEX idx_rfqs_customer (customer_name, created_at),
    INDEX idx_rfqs_due_at (due_at_local)
) ENGINE=InnoDB;

CREATE TABLE rfq_line_items (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    rfq_id BIGINT NOT NULL,
    source_line_id VARCHAR(120) NOT NULL,
    line_number INT NOT NULL,
    description TEXT NOT NULL,
    requested_quantity DECIMAL(18,4) NOT NULL,
    unit_of_measure VARCHAR(40) NULL,
    customer_part_number VARCHAR(190) NULL,
    requested_manufacturer VARCHAR(190) NULL,
    requested_part_number VARCHAR(190) NULL,
    technical_specifications LONGTEXT NULL,
    source_data_json JSON NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_rfq_items_rfq FOREIGN KEY (rfq_id) REFERENCES rfqs(id) ON DELETE RESTRICT,
    CONSTRAINT uq_rfq_item_source UNIQUE (rfq_id, source_line_id),
    CONSTRAINT uq_rfq_item_line_number UNIQUE (rfq_id, line_number),
    INDEX idx_rfq_items_rfq (rfq_id)
) ENGINE=InnoDB;

CREATE TABLE rfq_assignments (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    rfq_id BIGINT NOT NULL,
    assignment_role VARCHAR(40) NOT NULL,
    assigned_user_id BIGINT NOT NULL,
    assigned_by_user_id BIGINT NOT NULL,
    reason VARCHAR(1000) NULL,
    assigned_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    active_slot TINYINT NULL DEFAULT 1,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_rfq_assignments_rfq FOREIGN KEY (rfq_id) REFERENCES rfqs(id) ON DELETE RESTRICT,
    CONSTRAINT uq_rfq_active_assignment UNIQUE (rfq_id, assignment_role, active_slot),
    INDEX idx_rfq_assignment_user (assigned_user_id, active, assignment_role),
    INDEX idx_rfq_assignment_rfq (rfq_id, assignment_role, active)
) ENGINE=InnoDB;

CREATE TABLE rfq_workflow_events (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    rfq_id BIGINT NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    actor_user_id BIGINT NOT NULL,
    actor_roles_json JSON NOT NULL,
    from_status VARCHAR(40) NULL,
    to_status VARCHAR(40) NULL,
    reason VARCHAR(1000) NULL,
    payload_json JSON NULL,
    correlation_id VARCHAR(64) NULL,
    occurred_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_rfq_event_rfq FOREIGN KEY (rfq_id) REFERENCES rfqs(id) ON DELETE RESTRICT,
    INDEX idx_rfq_events_history (rfq_id, occurred_at, id),
    INDEX idx_rfq_events_actor (actor_user_id, occurred_at),
    INDEX idx_rfq_events_type (event_type, occurred_at)
) ENGINE=InnoDB;

CREATE TABLE rfq_customer_decisions (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    rfq_id BIGINT NOT NULL,
    decision VARCHAR(32) NOT NULL,
    quotation_version_id BIGINT NULL,
    recorded_by_user_id BIGINT NOT NULL,
    notes VARCHAR(2000) NULL,
    source_reference VARCHAR(255) NULL,
    idempotency_key VARCHAR(64) NOT NULL,
    decided_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_customer_decision_rfq FOREIGN KEY (rfq_id) REFERENCES rfqs(id) ON DELETE RESTRICT,
    CONSTRAINT uq_customer_decision_idempotency UNIQUE (rfq_id, idempotency_key),
    INDEX idx_customer_decisions_rfq (rfq_id, decided_at)
) ENGINE=InnoDB;

CREATE TABLE rfq_customer_submissions (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    rfq_id BIGINT NOT NULL,
    quotation_version_id BIGINT NOT NULL,
    sent_by_user_id BIGINT NOT NULL,
    external_reference VARCHAR(255) NULL,
    idempotency_key VARCHAR(64) NOT NULL,
    send_channel VARCHAR(60) NOT NULL DEFAULT 'CLIENT_SOFTWARE_MANUAL',
    result VARCHAR(32) NOT NULL,
    notes VARCHAR(2000) NULL,
    sent_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_customer_submission_rfq FOREIGN KEY (rfq_id) REFERENCES rfqs(id) ON DELETE RESTRICT,
    CONSTRAINT uq_customer_submission_idempotency UNIQUE (rfq_id, idempotency_key),
    INDEX idx_customer_submissions_rfq (rfq_id, sent_at)
) ENGINE=InnoDB;
