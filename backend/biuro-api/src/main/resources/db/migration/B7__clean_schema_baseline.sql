-- Empty-database snapshot after V1-V7. Keep V1-V7 scripts for existing databases.
-- In particular, this baseline must not import the historical sample Turnusy from V2.
CREATE TABLE turnus (
    turnus_code VARCHAR(50) PRIMARY KEY,
    turnus_name VARCHAR(200) NOT NULL,
    turnus_description TEXT,
    turnus_location VARCHAR(500) NOT NULL,
    season_type VARCHAR(20) NOT NULL,
    season_year INTEGER NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    min_age INTEGER NOT NULL,
    participant_capacity INT NOT NULL,
    active BOOLEAN NOT NULL,
    participant_registration_open BOOLEAN NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    staff_capacity INT NOT NULL DEFAULT 10,
    staff_registration_open BOOLEAN NOT NULL DEFAULT FALSE,
    staff_requirements TEXT NOT NULL DEFAULT '[]',
    participant_fee DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
    staff_fees TEXT NOT NULL DEFAULT '[]',
    fees_configured BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT chk_turnus_dates CHECK (end_date >= start_date),
    CONSTRAINT chk_turnus_age CHECK (min_age BETWEEN 0 AND 120),
    CONSTRAINT chk_turnus_capacity CHECK (participant_capacity > 0),
    CONSTRAINT chk_turnus_staff_capacity CHECK (staff_capacity > 0),
    CONSTRAINT chk_turnus_participant_fee CHECK (participant_fee >= 0)
) ENGINE=InnoDB;

CREATE TABLE biuro_account (
    id VARCHAR(36) PRIMARY KEY,
    username VARCHAR(64) NOT NULL UNIQUE,
    display_name VARCHAR(150) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    password_change_required BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_biuro_username_lower CHECK (username = LOWER(username))
) ENGINE=InnoDB;

CREATE TABLE biuro_account_role (
    account_id VARCHAR(36) NOT NULL,
    role VARCHAR(20) NOT NULL,
    PRIMARY KEY (account_id, role),
    FOREIGN KEY (account_id) REFERENCES biuro_account(id),
    CONSTRAINT chk_biuro_role CHECK (role IN ('SYS_ADMIN', 'OPERATOR', 'VIEWER'))
) ENGINE=InnoDB;

CREATE TABLE biuro_session (
    id VARCHAR(36) PRIMARY KEY,
    account_id VARCHAR(36) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    FOREIGN KEY (account_id) REFERENCES biuro_account(id),
    INDEX idx_biuro_session_account (account_id),
    INDEX idx_biuro_session_expiry (expires_at)
) ENGINE=InnoDB;

CREATE TABLE biuro_account_setup (
    id INTEGER PRIMARY KEY,
    completed BOOLEAN NOT NULL
) ENGINE=InnoDB;
INSERT INTO biuro_account_setup (id, completed) VALUES (1, FALSE);

CREATE TABLE biuro_account_turnus (
    account_id VARCHAR(36) NOT NULL,
    turnus_code VARCHAR(50) NOT NULL,
    PRIMARY KEY (account_id, turnus_code),
    FOREIGN KEY (account_id) REFERENCES biuro_account(id) ON DELETE CASCADE,
    FOREIGN KEY (turnus_code) REFERENCES turnus(turnus_code) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE biuro_account_admin_lock (
    id INTEGER PRIMARY KEY
) ENGINE=InnoDB;
INSERT INTO biuro_account_admin_lock (id) VALUES (1);

CREATE TABLE biuro_account_audit (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    occurred_at TIMESTAMP(6) NOT NULL,
    actor_account_id VARCHAR(36),
    actor_username VARCHAR(64),
    target_account_id VARCHAR(36),
    event_type VARCHAR(40) NOT NULL,
    details TEXT NOT NULL,
    INDEX idx_biuro_audit_target (target_account_id, id)
) ENGINE=InnoDB;
