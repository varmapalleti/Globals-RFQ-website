DROP TABLE IF EXISTS password_reset_otps;
DROP TABLE IF EXISTS email_verification_tokens;

CREATE TABLE email_verification_tokens (
                                           id          BIGINT AUTO_INCREMENT PRIMARY KEY,
                                           user_id     BIGINT       NOT NULL,
                                           email_type  VARCHAR(10)  NOT NULL,
                                           email       VARCHAR(190) NOT NULL,
                                           token_hash  VARCHAR(128) NOT NULL UNIQUE,
                                           expires_at  TIMESTAMP    NOT NULL,
                                           used        BOOLEAN      NOT NULL DEFAULT FALSE,
                                           used_at     TIMESTAMP    NULL,
                                           created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                           CONSTRAINT fk_evt_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
                                           INDEX idx_evt_user_type_created (user_id, email_type, created_at)
) ENGINE=InnoDB;

CREATE TABLE password_reset_otps (
                                     id                     BIGINT AUTO_INCREMENT PRIMARY KEY,
                                     user_id                BIGINT       NOT NULL,
                                     email_type             VARCHAR(10)  NOT NULL,
                                     delivery_email         VARCHAR(190) NOT NULL,
                                     otp_hash               VARCHAR(100) NOT NULL,
                                     attempt_count          INT          NOT NULL DEFAULT 0,
                                     verified_at            TIMESTAMP    NULL,
                                     reset_token_hash       VARCHAR(128) NULL UNIQUE,
                                     reset_token_expires_at TIMESTAMP    NULL,
                                     expires_at             TIMESTAMP    NOT NULL,
                                     used                   BOOLEAN      NOT NULL DEFAULT FALSE,
                                     used_at                TIMESTAMP    NULL,
                                     requested_ip           VARCHAR(45)  NULL,
                                     created_at             TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                     CONSTRAINT fk_pro_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
                                     INDEX idx_pro_user_created (user_id, created_at)
) ENGINE=InnoDB;