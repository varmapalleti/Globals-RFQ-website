CREATE TABLE rfq_admin_audit_events (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    action_type VARCHAR(80) NOT NULL,
    actor_user_id BIGINT NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    before_json JSON NULL,
    after_json JSON NULL,
    correlation_id VARCHAR(64) NULL,
    occurred_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    INDEX idx_rfq_admin_audit_history (occurred_at, id),
    INDEX idx_rfq_admin_audit_actor (actor_user_id, occurred_at)
) ENGINE=InnoDB;
