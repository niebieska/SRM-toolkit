ALTER TABLE turnus ADD COLUMN participant_fee DECIMAL(10, 2) NOT NULL DEFAULT 0.00;
ALTER TABLE turnus ADD COLUMN staff_fees TEXT NOT NULL DEFAULT '[]';
ALTER TABLE turnus ADD COLUMN fees_configured BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE turnus ADD CONSTRAINT chk_turnus_participant_fee CHECK (participant_fee >= 0);
