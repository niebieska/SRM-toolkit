ALTER TABLE registration_payment
    ADD COLUMN entry_type VARCHAR(10) NOT NULL DEFAULT 'PAYMENT';

ALTER TABLE registration_payment
    ADD CONSTRAINT chk_registration_payment_entry_type CHECK (entry_type IN ('PAYMENT', 'REFUND'));

CREATE TABLE registration_payment_settlement (
    registration_id BIGINT PRIMARY KEY,
    settled BOOLEAN NOT NULL,
    note VARCHAR(300),
    changed_at DATETIME(6) NOT NULL,
    actor_account_id VARCHAR(36) NOT NULL,
    actor_username VARCHAR(64) NOT NULL,
    CONSTRAINT fk_registration_payment_settlement_registration
        FOREIGN KEY (registration_id) REFERENCES registration(id)
) ENGINE=InnoDB;

CREATE TABLE registration_payment_settlement_audit (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    registration_id BIGINT NOT NULL,
    settled BOOLEAN NOT NULL,
    note VARCHAR(300),
    changed_at DATETIME(6) NOT NULL,
    actor_account_id VARCHAR(36) NOT NULL,
    actor_username VARCHAR(64) NOT NULL,
    CONSTRAINT fk_registration_payment_settlement_audit_registration
        FOREIGN KEY (registration_id) REFERENCES registration(id),
    INDEX idx_registration_payment_settlement_audit_order (registration_id, changed_at, id)
) ENGINE=InnoDB;
