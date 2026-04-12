CREATE TABLE agent_runs (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id      UUID         NOT NULL REFERENCES jobs(id) ON DELETE CASCADE,
    agent_name  VARCHAR(100) NOT NULL,
    model_used  VARCHAR(100),
    input_text  TEXT,
    output_text TEXT,
    status      VARCHAR(50)  NOT NULL DEFAULT 'PENDING',
    created_at  TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX idx_agent_runs_job_id ON agent_runs(job_id);
