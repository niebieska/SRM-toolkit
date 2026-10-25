ALTER TABLE registration
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

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
