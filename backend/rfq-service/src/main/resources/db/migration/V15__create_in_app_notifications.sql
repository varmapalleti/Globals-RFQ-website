-- =====================================================================
-- V15: Create in-app notifications table
-- =====================================================================

CREATE TABLE in_app_notifications (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT NOT NULL,
    type            VARCHAR(64) NOT NULL,
    title           VARCHAR(255) NOT NULL,
    message         TEXT NOT NULL,
    rfq_public_id   VARCHAR(36) NULL,
    is_read         BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    read_at         TIMESTAMP(6) NULL,
    INDEX idx_notif_user_id (user_id),
    INDEX idx_notif_user_read (user_id, is_read),
    INDEX idx_notif_created_at (created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
