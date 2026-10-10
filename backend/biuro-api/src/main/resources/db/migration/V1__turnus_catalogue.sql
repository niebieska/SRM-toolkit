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
    capacity INTEGER NOT NULL,
    active BOOLEAN NOT NULL,
    registration_open BOOLEAN NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_turnus_dates CHECK (end_date >= start_date),
    CONSTRAINT chk_turnus_capacity CHECK (capacity > 0),
    CONSTRAINT chk_turnus_age CHECK (min_age BETWEEN 0 AND 120)
);
