CREATE TABLE resumes (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id        UUID         REFERENCES jobs(id) ON DELETE CASCADE,
    file_name     VARCHAR(255) NOT NULL,
    file_content  BYTEA        NOT NULL,
    content_text  TEXT         NOT NULL,
    created_at    TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX idx_resumes_job_id ON resumes(job_id);
