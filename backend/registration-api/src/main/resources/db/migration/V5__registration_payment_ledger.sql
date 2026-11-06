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
    CONSTRAINT fk_registration_payment_registration
        FOREIGN KEY (registration_id) REFERENCES registration(id),
    CONSTRAINT chk_registration_payment_amount CHECK (amount > 0),
    CONSTRAINT uq_registration_payment_idempotency UNIQUE (registration_id, idempotency_key),
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
