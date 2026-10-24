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
