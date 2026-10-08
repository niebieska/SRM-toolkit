-- Existing duplicates must be reviewed before this migration can succeed.
-- No registrations are deleted or renumbered.
ALTER TABLE registration
    ADD CONSTRAINT uk_registration_turnus_pesel UNIQUE (turnus_code, pesel_hash);

CREATE TABLE registration_counter (
    turnus_code VARCHAR(50) NOT NULL,
    registration_type VARCHAR(20) NOT NULL,
    last_number BIGINT NOT NULL,
    PRIMARY KEY (turnus_code, registration_type),
    CONSTRAINT chk_registration_counter_positive CHECK (last_number > 0)
) ENGINE=InnoDB;

-- Preserve issued numeric suffixes when upgrading the old shared counter scheme.
-- Only codes matching the expected group prefix and a positive numeric suffix qualify.
INSERT INTO registration_counter (turnus_code, registration_type, last_number)
SELECT turnus_code, registration_type,
       MAX(CAST(SUBSTRING_INDEX(registration_code, '-', -1) AS SIGNED))
FROM registration
WHERE registration_type IN ('PARTICIPANT', 'STAFF')
  AND registration_code = CONCAT('REG-', IF(registration_type = 'PARTICIPANT', 'P', 'S'),
      '-', turnus_code, '-', SUBSTRING_INDEX(registration_code, '-', -1))
  AND SUBSTRING_INDEX(registration_code, '-', -1) REGEXP '^[1-9][0-9]*$'
GROUP BY turnus_code, registration_type;
