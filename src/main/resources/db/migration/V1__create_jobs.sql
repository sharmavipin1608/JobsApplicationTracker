CREATE TABLE jobs (
    id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    company    VARCHAR(255) NOT NULL,
    role       VARCHAR(255) NOT NULL,
    jd_text    TEXT,
    status     VARCHAR(50)  NOT NULL DEFAULT 'UNDETERMINED',
    applied_at TIMESTAMP,
    created_at TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at TIMESTAMP    NOT NULL DEFAULT now()
);
