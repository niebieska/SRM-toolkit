ALTER TABLE registration_payment
    ADD COLUMN minimum_net_retained DECIMAL(10,2) NOT NULL DEFAULT 0;
