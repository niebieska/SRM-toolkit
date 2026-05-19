CREATE TABLE registration (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    registration_code   VARCHAR(50)  NOT NULL UNIQUE,
    registration_type   VARCHAR(20)  NOT NULL,
    turnus_code         VARCHAR(50)  NOT NULL,
    pesel_hash          VARCHAR(64)  NOT NULL,
    is_minor            BOOLEAN      NOT NULL DEFAULT FALSE,
    status              VARCHAR(30)  NOT NULL DEFAULT 'NEW',
    rejection_reason    VARCHAR(500),
    payload             LONGTEXT     NOT NULL,
    created_at          DATETIME     NOT NULL,
    updated_at          DATETIME,
    INDEX idx_turnus (turnus_code),
    INDEX idx_pesel (pesel_hash),
    INDEX idx_status (status)
);
