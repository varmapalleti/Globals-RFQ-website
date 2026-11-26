ALTER TABLE users
    ADD COLUMN status_reason VARCHAR(500) NULL,
    ADD COLUMN status_changed_by BIGINT NULL,
    ADD COLUMN status_changed_at TIMESTAMP NULL,
    ADD COLUMN status_before_disabled VARCHAR(25) NULL,
    ADD CONSTRAINT fk_users_status_changed_by
        FOREIGN KEY (status_changed_by) REFERENCES users(id) ON DELETE SET NULL;

ALTER TABLE roles
    ADD COLUMN role_rank INT NOT NULL DEFAULT 3;

UPDATE roles
SET role_rank = CASE name
    WHEN 'SUPER_ADMIN' THEN 1
    WHEN 'ADMIN' THEN 2
    ELSE 3
END;

CREATE INDEX idx_roles_rank ON roles(role_rank);
CREATE INDEX idx_refresh_tokens_expiry ON refresh_tokens(expires_at);
CREATE INDEX idx_reset_otps_expiry ON password_reset_otps(expires_at);
CREATE INDEX idx_audit_event_created ON security_audit_logs(event_type, created_at);
CREATE INDEX idx_audit_target_created ON security_audit_logs(target_user_id, created_at);
ALTER TABLE notification_outbox MODIFY recipient VARCHAR(512) NOT NULL;
ALTER TABLE notification_outbox
    ADD COLUMN payload_encrypted BOOLEAN NOT NULL DEFAULT FALSE;
CREATE INDEX idx_outbox_retry ON notification_outbox(payload_encrypted, status, scheduled_at, attempt_count);

UPDATE notification_outbox
SET status = 'FAILED',
    scheduled_at = NULL,
    failure_reason = COALESCE(failure_reason, 'Legacy unencrypted notification requires manual resend')
WHERE payload_encrypted = FALSE AND status IN ('PENDING', 'PROCESSING');

UPDATE notification_outbox
SET status = 'FAILED',
    scheduled_at = NULL,
    failure_reason = COALESCE(failure_reason, 'Legacy unencrypted notification requires manual resend')
WHERE payload_encrypted = FALSE AND status IN ('PENDING', 'PROCESSING');

ALTER TABLE email_verification_tokens
    ADD COLUMN purpose VARCHAR(20) NOT NULL DEFAULT 'VERIFICATION';

CREATE INDEX idx_evt_purpose_expiry ON email_verification_tokens(purpose, expires_at);

ALTER TABLE users
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
