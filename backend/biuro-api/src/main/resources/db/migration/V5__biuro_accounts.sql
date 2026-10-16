CREATE TABLE biuro_account (
    id VARCHAR(36) PRIMARY KEY,
    username VARCHAR(64) NOT NULL UNIQUE,
    display_name VARCHAR(150) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    password_change_required BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT chk_biuro_username_lower CHECK (username = LOWER(username))
);

CREATE TABLE biuro_account_role (
    account_id VARCHAR(36) NOT NULL,
    role VARCHAR(20) NOT NULL,
    PRIMARY KEY (account_id, role),
    FOREIGN KEY (account_id) REFERENCES biuro_account(id),
    CONSTRAINT chk_biuro_role CHECK (role IN ('SYS_ADMIN', 'OPERATOR', 'VIEWER'))
);

CREATE TABLE biuro_session (
    id VARCHAR(36) PRIMARY KEY,
    account_id VARCHAR(36) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    FOREIGN KEY (account_id) REFERENCES biuro_account(id)
);
CREATE INDEX idx_biuro_session_account ON biuro_session(account_id);
CREATE INDEX idx_biuro_session_expiry ON biuro_session(expires_at);

-- Serializes first-account bootstrap across simultaneous application starts.
CREATE TABLE biuro_account_setup (
    id INTEGER PRIMARY KEY,
    completed BOOLEAN NOT NULL
);
INSERT INTO biuro_account_setup (id, completed) VALUES (1, FALSE);
