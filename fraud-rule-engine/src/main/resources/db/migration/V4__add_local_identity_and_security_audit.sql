CREATE TABLE app_user (
    user_id UUID PRIMARY KEY,
    display_name VARCHAR(100) NOT NULL,
    normalized_email VARCHAR(254) NOT NULL UNIQUE,
    password_hash VARCHAR(100),
    role VARCHAR(32) NOT NULL,
    status VARCHAR(16) NOT NULL,
    token_version BIGINT NOT NULL DEFAULT 0,
    failed_login_attempts INTEGER NOT NULL DEFAULT 0,
    locked_until TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_app_user_role CHECK (role IN ('ADMIN', 'FRAUD_ANALYST', 'AUDITOR', 'SERVICE')),
    CONSTRAINT ck_app_user_status CHECK (status IN ('ACTIVE', 'DISABLED')),
    CONSTRAINT ck_app_user_password CHECK (
        (role = 'SERVICE' AND password_hash IS NULL)
        OR (role <> 'SERVICE' AND password_hash IS NOT NULL)
    ),
    CONSTRAINT ck_app_user_token_version CHECK (token_version >= 0),
    CONSTRAINT ck_app_user_failed_login_attempts CHECK (failed_login_attempts >= 0)
);

CREATE TABLE user_security_audit (
    audit_id BIGSERIAL PRIMARY KEY,
    actor_user_id UUID,
    target_user_id UUID,
    action VARCHAR(32) NOT NULL,
    outcome VARCHAR(16) NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_user_security_audit_outcome CHECK (outcome IN ('SUCCESS', 'FAILURE'))
);

CREATE INDEX ix_user_security_audit_occurred_at
    ON user_security_audit (occurred_at DESC, audit_id DESC);
CREATE INDEX ix_user_security_audit_target
    ON user_security_audit (target_user_id, occurred_at DESC);

CREATE TABLE user_bootstrap_lock (
    lock_id SMALLINT PRIMARY KEY,
    CONSTRAINT ck_user_bootstrap_lock_singleton CHECK (lock_id = 1)
);

INSERT INTO user_bootstrap_lock (lock_id) VALUES (1);
