-- =====================================================================
-- V14: Create red_flags and purchase_orders tables
-- =====================================================================

CREATE TABLE red_flags (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    rfq_id          BIGINT NOT NULL,
    rfq_public_id   VARCHAR(36) NOT NULL,
    severity        VARCHAR(32) NOT NULL,
    reason          TEXT NOT NULL,
    status          VARCHAR(32) NOT NULL DEFAULT 'OPEN',
    raised_by       BIGINT NOT NULL,
    assigned_to     BIGINT NULL,
    resolution_note TEXT NULL,
    resolved_by     BIGINT NULL,
    resolved_at     TIMESTAMP(6) NULL,
    created_at      TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    INDEX idx_rf_rfq_id (rfq_id),
    INDEX idx_rf_status (status),
    INDEX idx_rf_severity (severity),
    INDEX idx_rf_raised_by (raised_by),
    INDEX idx_rf_created_at (created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE purchase_orders (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id                VARCHAR(64) NOT NULL UNIQUE,
    rfq_id                  BIGINT NOT NULL,
    rfq_public_id           VARCHAR(36) NOT NULL,
    quotation_version_id    BIGINT NULL,
    supplier_name           VARCHAR(190) NULL,
    status                  VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    assigned_to             BIGINT NULL,
    order_date              DATE NULL,
    expected_delivery       DATE NULL,
    notes                   TEXT NULL,
    created_by              BIGINT NOT NULL,
    created_at              TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at              TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    INDEX idx_po_rfq_id (rfq_id),
    INDEX idx_po_status (status),
    INDEX idx_po_order_id (order_id),
    INDEX idx_po_assigned_to (assigned_to),
    INDEX idx_po_created_at (created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
