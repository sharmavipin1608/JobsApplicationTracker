CREATE TABLE scores (
    id              UUID      PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id          UUID      NOT NULL REFERENCES jobs(id) ON DELETE CASCADE,
    fit_score       INTEGER   CHECK (fit_score BETWEEN 0 AND 100),
    recommendations TEXT,
    created_at      TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_scores_job_id ON scores(job_id);
