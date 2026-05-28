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
    failed_login_count INT NOT NULL DEFAULT 0,
    locked_until DATETIME NULL,
    last_login_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (user_id),
    CONSTRAINT uk_auth_user_username UNIQUE (username),
    CONSTRAINT uk_auth_user_phone UNIQUE (phone),
    CONSTRAINT uk_auth_user_email UNIQUE (email)
);

CREATE INDEX IF NOT EXISTS idx_auth_user_type
    ON t_auth_user (user_type);

CREATE INDEX IF NOT EXISTS idx_auth_user_status
    ON t_auth_user (account_status);

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
    CONSTRAINT uk_auth_role_code UNIQUE (role_code)
);

CREATE INDEX IF NOT EXISTS idx_auth_role_enabled
    ON t_auth_role (enabled);

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
    CONSTRAINT uk_auth_permission_code UNIQUE (permission_code)
);

CREATE INDEX IF NOT EXISTS idx_auth_permission_type
    ON t_auth_permission (permission_type);

CREATE INDEX IF NOT EXISTS idx_auth_permission_module
    ON t_auth_permission (module_code);

CREATE TABLE IF NOT EXISTS t_auth_user_role (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    assigned_by BIGINT NULL,
    assigned_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_auth_user_role UNIQUE (user_id, role_id),
    CONSTRAINT fk_auth_user_role_user FOREIGN KEY (user_id) REFERENCES t_auth_user (user_id),
    CONSTRAINT fk_auth_user_role_role FOREIGN KEY (role_id) REFERENCES t_auth_role (role_id)
);

CREATE INDEX IF NOT EXISTS idx_auth_user_role_user
    ON t_auth_user_role (user_id);

CREATE INDEX IF NOT EXISTS idx_auth_user_role_role
    ON t_auth_user_role (role_id);

CREATE TABLE IF NOT EXISTS t_auth_role_permission (
    id BIGINT NOT NULL AUTO_INCREMENT,
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    assigned_by BIGINT NULL,
    assigned_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_auth_role_permission UNIQUE (role_id, permission_id),
    CONSTRAINT fk_auth_role_permission_role FOREIGN KEY (role_id) REFERENCES t_auth_role (role_id),
    CONSTRAINT fk_auth_role_permission_permission FOREIGN KEY (permission_id) REFERENCES t_auth_permission (permission_id)
);

CREATE INDEX IF NOT EXISTS idx_auth_role_permission_role
    ON t_auth_role_permission (role_id);

CREATE INDEX IF NOT EXISTS idx_auth_role_permission_permission
    ON t_auth_role_permission (permission_id);

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
    CONSTRAINT uk_auth_session_token UNIQUE (token_id),
    CONSTRAINT fk_auth_session_user FOREIGN KEY (user_id) REFERENCES t_auth_user (user_id)
);

CREATE INDEX IF NOT EXISTS idx_auth_session_user
    ON t_auth_session (user_id);

CREATE INDEX IF NOT EXISTS idx_auth_session_expires
    ON t_auth_session (expires_at);

CREATE INDEX IF NOT EXISTS idx_auth_session_status
    ON t_auth_session (status);

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
    PRIMARY KEY (log_id)
);

CREATE INDEX IF NOT EXISTS idx_auth_audit_operator
    ON t_auth_audit_log (operator_id);

CREATE INDEX IF NOT EXISTS idx_auth_audit_operation
    ON t_auth_audit_log (operation_type);

CREATE INDEX IF NOT EXISTS idx_auth_audit_result
    ON t_auth_audit_log (result_status);

CREATE INDEX IF NOT EXISTS idx_auth_audit_created
    ON t_auth_audit_log (created_at);
