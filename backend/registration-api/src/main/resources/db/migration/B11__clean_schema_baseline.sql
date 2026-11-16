-- Empty-database snapshot after V1-V11. Keep legacy V1-V11 scripts for existing databases.
-- Historical backfills are intentionally represented by the final column defaults;
-- this baseline contains no registration data.
CREATE TABLE registration (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    registration_code   VARCHAR(50) NOT NULL UNIQUE,
    registration_type   VARCHAR(20) NOT NULL,
    turnus_code         VARCHAR(50) NOT NULL,
    pesel_hash          VARCHAR(64) NOT NULL,
    is_minor            BOOLEAN NOT NULL DEFAULT FALSE,
    status              VARCHAR(30) NOT NULL DEFAULT 'NEW',
    rejection_reason    VARCHAR(500),
    payload             LONGTEXT NOT NULL,
    created_at          DATETIME NOT NULL,
    updated_at          DATETIME,
    version             BIGINT NOT NULL DEFAULT 0,
    source              VARCHAR(20) NOT NULL DEFAULT 'FORMULARZ',
    CONSTRAINT uk_registration_turnus_pesel UNIQUE (turnus_code, pesel_hash),
    INDEX idx_turnus (turnus_code),
    INDEX idx_pesel (pesel_hash),
    INDEX idx_status (status)
) ENGINE=InnoDB;

CREATE TABLE registration_counter (
    turnus_code VARCHAR(50) NOT NULL,
    registration_type VARCHAR(20) NOT NULL,
    last_number BIGINT NOT NULL,
    PRIMARY KEY (turnus_code, registration_type),
    CONSTRAINT chk_registration_counter_positive CHECK (last_number > 0)
) ENGINE=InnoDB;

CREATE TABLE registration_status_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    registration_id BIGINT NOT NULL,
    previous_status VARCHAR(30) NOT NULL,
    new_status VARCHAR(30) NOT NULL,
    changed_at DATETIME(6) NOT NULL,
    actor_account_id VARCHAR(36) NOT NULL,
    actor_username VARCHAR(64) NOT NULL,
    rejection_reason VARCHAR(500),
    CONSTRAINT fk_registration_status_history_registration
        FOREIGN KEY (registration_id) REFERENCES registration(id),
    INDEX idx_registration_status_history_order (registration_id, changed_at, id)
) ENGINE=InnoDB;

CREATE TABLE registration_edit_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    registration_id BIGINT NOT NULL,
    changed_at DATETIME(6) NOT NULL,
    actor_account_id VARCHAR(36) NOT NULL,
    actor_username VARCHAR(64) NOT NULL,
    changed_fields TEXT NOT NULL,
    CONSTRAINT fk_registration_edit_history_registration
        FOREIGN KEY (registration_id) REFERENCES registration(id),
    INDEX idx_registration_edit_history_order (registration_id, changed_at, id)
) ENGINE=InnoDB;

CREATE TABLE registration_payment (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    registration_id BIGINT NOT NULL,
    payment_date DATE NOT NULL,
    amount DECIMAL(10,2) NOT NULL,
    method VARCHAR(30) NOT NULL,
    label VARCHAR(100) NOT NULL,
    note VARCHAR(300),
    idempotency_key VARCHAR(64) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    actor_account_id VARCHAR(36) NOT NULL,
    actor_username VARCHAR(64) NOT NULL,
    entry_type VARCHAR(10) NOT NULL DEFAULT 'PAYMENT',
    CONSTRAINT fk_registration_payment_registration
        FOREIGN KEY (registration_id) REFERENCES registration(id),
    CONSTRAINT chk_registration_payment_amount CHECK (amount > 0),
    CONSTRAINT uq_registration_payment_idempotency UNIQUE (registration_id, idempotency_key),
    CONSTRAINT chk_registration_payment_entry_type CHECK (entry_type IN ('PAYMENT', 'REFUND')),
    INDEX idx_registration_payment_registration_date (registration_id, payment_date, id)
) ENGINE=InnoDB;

CREATE TABLE registration_payment_audit (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    payment_id BIGINT NOT NULL,
    changed_at DATETIME(6) NOT NULL,
    actor_account_id VARCHAR(36) NOT NULL,
    actor_username VARCHAR(64) NOT NULL,
    reason VARCHAR(300) NOT NULL,
    previous_payment_date DATE NOT NULL,
    previous_amount DECIMAL(10,2) NOT NULL,
    previous_method VARCHAR(30) NOT NULL,
    previous_label VARCHAR(100) NOT NULL,
    previous_note VARCHAR(300),
    new_payment_date DATE NOT NULL,
    new_amount DECIMAL(10,2) NOT NULL,
    new_method VARCHAR(30) NOT NULL,
    new_label VARCHAR(100) NOT NULL,
    new_note VARCHAR(300),
    CONSTRAINT fk_registration_payment_audit_payment
        FOREIGN KEY (payment_id) REFERENCES registration_payment(id),
    INDEX idx_registration_payment_audit_order (payment_id, changed_at, id)
) ENGINE=InnoDB;

CREATE TABLE registration_discount (
    registration_id BIGINT PRIMARY KEY,
    amount DECIMAL(10,2) NOT NULL,
    description VARCHAR(300) NOT NULL,
    changed_at DATETIME(6) NOT NULL,
    actor_account_id VARCHAR(36) NOT NULL,
    actor_username VARCHAR(64) NOT NULL,
    CONSTRAINT fk_registration_discount_registration
        FOREIGN KEY (registration_id) REFERENCES registration(id),
    CONSTRAINT chk_registration_discount_amount CHECK (amount >= 0)
) ENGINE=InnoDB;

CREATE TABLE registration_discount_audit (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    registration_id BIGINT NOT NULL,
    previous_amount DECIMAL(10,2),
    previous_description VARCHAR(300),
    new_amount DECIMAL(10,2) NOT NULL,
    new_description VARCHAR(300) NOT NULL,
    changed_at DATETIME(6) NOT NULL,
    actor_account_id VARCHAR(36) NOT NULL,
    actor_username VARCHAR(64) NOT NULL,
    CONSTRAINT fk_registration_discount_audit_registration
        FOREIGN KEY (registration_id) REFERENCES registration(id),
    INDEX idx_registration_discount_audit_order (registration_id, changed_at, id)
) ENGINE=InnoDB;

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
