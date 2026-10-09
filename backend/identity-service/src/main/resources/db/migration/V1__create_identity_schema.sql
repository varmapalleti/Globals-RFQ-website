CREATE TABLE roles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE,
    description VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;


CREATE TABLE users(
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    employee_code VARCHAR(30) UNIQUE,
    full_name VARCHAR(150) NOT NULL,
    official_email VARCHAR(150) NOT NULL UNIQUE,
    official_email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    official_email_verified_at TIMESTAMP NULL,
    personal_email VARCHAR(150) NOT NULL UNIQUE ,
    personal_email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    personal_email_verified_at TIMESTAMP NULL,
    phone VARCHAR(25),
    password_hash VARCHAR(255) NOT NULL,
    status VARCHAR(25) NOT NULL DEFAULT 'PENDING_VERIFICATION',
    failed_login_attempts INT NOT NULL DEFAULT 0,
    locked_until TIMESTAMP NULL,
    last_login_at TIMESTAMP NULL,
    must_change_password BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by BIGINT NULL,


    CONSTRAINT chk_users_distinct_emails
                  CHECK ( personal_email IS NULL OR personal_email <> official_email ),

    INDEX idx_users_status (status)
) ENGINE=InnoDB;


CREATE TABLE refresh_tokens(
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    token_hash VARCHAR(128) NOT NULL UNIQUE,
    expires_at TIMESTAMP NOT NULL,
    revoked BOOLEAN  NOT NULL DEFAULT FALSE,
    replaced_by VARCHAR(128) NULL,
    ip_address VARCHAR(45),
    user_agent VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_rt_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_rt_user (user_id)
)ENGINE=InnoDB;


CREATE TABLE email_verification_tokens (
      id BIGINT AUTO_INCREMENT PRIMARY KEY,
       user_id     BIGINT       NOT NULL,
       email_type  VARCHAR(10)  NOT NULL,
       email       VARCHAR(190) NOT NULL,
       token_hash  VARCHAR(128) NOT NULL UNIQUE,
       expires_at  TIMESTAMP    NOT NULL,
       used  BOOLEAN  NOT NULL DEFAULT FALSE,
       used_at  TIMESTAMP    NULL,
       created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

       CONSTRAINT fk_evt_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
       INDEX idx_evt_user_type (user_id, email_type)
) ENGINE=InnoDB;


CREATE TABLE password_reset_otps (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id        BIGINT       NOT NULL,
    delivery_email VARCHAR(190) NOT NULL,
    otp_hash       VARCHAR(128) NOT NULL,
    attempt_count  INT          NOT NULL DEFAULT 0,
    max_attempts   INT          NOT NULL DEFAULT 5,
    expires_at     TIMESTAMP    NOT NULL,
    used           BOOLEAN      NOT NULL DEFAULT FALSE,
    used_at        TIMESTAMP    NULL,
    requested_ip   VARCHAR(45),
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_pro_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_pro_user_created (user_id, created_at)
) ENGINE=InnoDB;


CREATE TABLE security_audit_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_type VARCHAR(50) NOT NULL,
    actor_user_id BIGINT NULL,
    target_user_id BIGINT NULL,
    service_name VARCHAR(50) NOT NULL DEFAULT 'identity-service',
    entity_type VARCHAR(50),
    entity_id VARCHAR(50),
    action VARCHAR(30),
    outcome VARCHAR(20) NOT NULL,
    ip_address VARCHAR(45),
    user_agent VARCHAR(255),
    correlation_id VARCHAR(64),
    metadata_json JSON,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_audit_event (event_type),
    INDEX idx_audit_actor (actor_user_id),
    INDEX idx_audit_created (created_at)
) ENGINE=InnoDB;

CREATE TABLE notification_outbox(
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    channel VARCHAR(10)  NOT NULL,
    template_code VARCHAR(60) NOT NULL,
    recipient VARCHAR(190) NOT NULL,
    variables_json JSON,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    provider VARCHAR(20) NOT NULL DEFAULT 'MOCK',
    attempt_count INT NOT NULL DEFAULT 0,
    failure_reason VARCHAR(500),
    correlation_id VARCHAR(64),
    scheduled_at TIMESTAMP NULL,
    sent_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_outbox_status (status)
) ENGINE=InnoDB;