ALTER TABLE password_reset_otps
    ADD COLUMN email_type             VARCHAR(10)  NOT NULL DEFAULT 'PERSONAL' AFTER delivery_email,
    ADD COLUMN verified_at            TIMESTAMP    NULL AFTER attempt_count,
    ADD COLUMN reset_token_hash       VARCHAR(128) NULL UNIQUE AFTER verified_at,
    ADD COLUMN reset_token_expires_at TIMESTAMP    NULL AFTER reset_token_hash;