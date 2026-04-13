ALTER TABLE jobs
    ADD COLUMN fit_score INTEGER CHECK (fit_score BETWEEN 0 AND 100),
    ADD COLUMN notes     TEXT;
