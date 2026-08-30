CREATE TABLE IF NOT EXISTS t_auth_user (
    user_id BIGINT NOT NULL AUTO_INCREMENT,
    username VARCHAR(64) NOT NULL,
    user_type VARCHAR(32) NOT NULL,
    display_name VARCHAR(64) NOT NULL,
    phone VARCHAR(32) NULL,
    email VARCHAR(128) NULL,
    avatar_url VARCHAR(255) NULL,
    password_hash VARCHAR(255) NOT NULL,
    password_salt VARCHAR(128) NULL,
    account_status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    security_version BIGINT NOT NULL DEFAULT 1,
    failed_login_count INT NOT NULL DEFAULT 0,
    locked_until DATETIME NULL,
    last_login_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (user_id),
    KEY idx_auth_user_type (user_type),
    KEY idx_auth_user_status (account_status),
    CONSTRAINT uk_auth_user_username UNIQUE (username),
    CONSTRAINT uk_auth_user_phone UNIQUE (phone),
    CONSTRAINT uk_auth_user_email UNIQUE (email)
);

CREATE TABLE IF NOT EXISTS t_auth_role (
    role_id BIGINT NOT NULL AUTO_INCREMENT,
    role_code VARCHAR(64) NOT NULL,
    role_name VARCHAR(64) NOT NULL,
    description VARCHAR(255) NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (role_id),
    KEY idx_auth_role_enabled (enabled),
    CONSTRAINT uk_auth_role_code UNIQUE (role_code)
);

CREATE TABLE IF NOT EXISTS t_auth_permission (
    permission_id BIGINT NOT NULL AUTO_INCREMENT,
    permission_code VARCHAR(128) NOT NULL,
    permission_name VARCHAR(128) NOT NULL,
    permission_type VARCHAR(32) NOT NULL,
    module_code VARCHAR(32) NOT NULL,
    resource_pattern VARCHAR(255) NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (permission_id),
    KEY idx_auth_permission_type (permission_type),
    KEY idx_auth_permission_module (module_code),
    CONSTRAINT uk_auth_permission_code UNIQUE (permission_code)
);

CREATE TABLE IF NOT EXISTS t_auth_user_role (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    assigned_by BIGINT NULL,
    assigned_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_auth_user_role_user (user_id),
    KEY idx_auth_user_role_role (role_id),
    CONSTRAINT uk_auth_user_role UNIQUE (user_id, role_id),
    CONSTRAINT fk_auth_user_role_user FOREIGN KEY (user_id) REFERENCES t_auth_user (user_id),
    CONSTRAINT fk_auth_user_role_role FOREIGN KEY (role_id) REFERENCES t_auth_role (role_id)
);

CREATE TABLE IF NOT EXISTS t_auth_role_permission (
    id BIGINT NOT NULL AUTO_INCREMENT,
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    assigned_by BIGINT NULL,
    assigned_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_auth_role_permission_role (role_id),
    KEY idx_auth_role_permission_permission (permission_id),
    CONSTRAINT uk_auth_role_permission UNIQUE (role_id, permission_id),
    CONSTRAINT fk_auth_role_permission_role FOREIGN KEY (role_id) REFERENCES t_auth_role (role_id),
    CONSTRAINT fk_auth_role_permission_permission FOREIGN KEY (permission_id) REFERENCES t_auth_permission (permission_id)
);

CREATE TABLE IF NOT EXISTS t_auth_session (
    session_id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    token_id VARCHAR(128) NOT NULL,
    issued_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at DATETIME NOT NULL,
    revoked_at DATETIME NULL,
    client_ip VARCHAR(64) NULL,
    user_agent VARCHAR(255) NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'VALID',
    PRIMARY KEY (session_id),
    KEY idx_auth_session_user (user_id),
    KEY idx_auth_session_expires (expires_at),
    KEY idx_auth_session_status (status),
    CONSTRAINT uk_auth_session_token UNIQUE (token_id),
    CONSTRAINT fk_auth_session_user FOREIGN KEY (user_id) REFERENCES t_auth_user (user_id)
);

CREATE TABLE IF NOT EXISTS t_auth_audit_log (
    log_id BIGINT NOT NULL AUTO_INCREMENT,
    operator_id BIGINT NULL,
    operation_type VARCHAR(64) NOT NULL,
    target_type VARCHAR(64) NULL,
    target_id VARCHAR(64) NULL,
    result_status VARCHAR(32) NOT NULL,
    failure_reason VARCHAR(255) NULL,
    client_ip VARCHAR(64) NULL,
    user_agent VARCHAR(255) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (log_id),
    KEY idx_auth_audit_operator (operator_id),
    KEY idx_auth_audit_operation (operation_type),
    KEY idx_auth_audit_result (result_status),
    KEY idx_auth_audit_created (created_at)
);

CREATE TABLE IF NOT EXISTS t_identity_outbox_event (
    outbox_id BIGINT NOT NULL AUTO_INCREMENT,
    event_id CHAR(36) NOT NULL,
    event_type VARCHAR(128) NOT NULL,
    payload_version INT NOT NULL,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id VARCHAR(64) NOT NULL,
    aggregate_version BIGINT NOT NULL,
    occurred_at DATETIME NOT NULL,
    correlation_id VARCHAR(64) NOT NULL,
    payload_json JSON NOT NULL,
    delivery_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (outbox_id),
    UNIQUE KEY uq_identity_outbox_event_id (event_id),
    KEY idx_identity_outbox_pending (delivery_status, occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
