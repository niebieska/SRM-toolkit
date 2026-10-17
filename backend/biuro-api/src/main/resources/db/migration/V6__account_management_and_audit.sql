ALTER TABLE biuro_account ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

CREATE TABLE biuro_account_turnus (
    account_id VARCHAR(36) NOT NULL,
    turnus_code VARCHAR(50) NOT NULL,
    PRIMARY KEY (account_id, turnus_code),
    FOREIGN KEY (account_id) REFERENCES biuro_account(id) ON DELETE CASCADE,
    FOREIGN KEY (turnus_code) REFERENCES turnus(turnus_code) ON DELETE CASCADE
);

-- Account administration is infrequent; serialize its mutations to protect the last admin.
CREATE TABLE biuro_account_admin_lock (id INTEGER PRIMARY KEY);
INSERT INTO biuro_account_admin_lock (id) VALUES (1);

-- No account FKs: historical events must survive later account removal.
CREATE TABLE biuro_account_audit (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    occurred_at TIMESTAMP(6) NOT NULL,
    actor_account_id VARCHAR(36),
    actor_username VARCHAR(64),
    target_account_id VARCHAR(36),
    event_type VARCHAR(40) NOT NULL,
    details TEXT NOT NULL
);
CREATE INDEX idx_biuro_audit_target ON biuro_account_audit(target_account_id, id);
