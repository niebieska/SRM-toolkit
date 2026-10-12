ALTER TABLE turnus CHANGE COLUMN capacity participant_capacity INT NOT NULL;
ALTER TABLE turnus CHANGE COLUMN registration_open participant_registration_open BOOLEAN NOT NULL;
ALTER TABLE turnus ADD COLUMN staff_capacity INT NOT NULL DEFAULT 10;
ALTER TABLE turnus ADD COLUMN staff_registration_open BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE turnus SET staff_registration_open = participant_registration_open;
ALTER TABLE turnus ADD CONSTRAINT chk_turnus_staff_capacity CHECK (staff_capacity > 0);
