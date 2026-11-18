CREATE TABLE notification (
    id UUID PRIMARY KEY,
    idempotency_key VARCHAR(128) NOT NULL UNIQUE,
    channel VARCHAR(8) NOT NULL CHECK (channel IN ('EMAIL', 'SMS', 'PUSH')),
    recipient VARCHAR(320) NOT NULL,
    message VARCHAR(2000) NOT NULL,
    status VARCHAR(10) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'SENT', 'FAILED')),
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    published BOOLEAN NOT NULL DEFAULT false,
    last_error TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX notification_dispatch_idx ON notification (next_attempt_at)
    WHERE status = 'PENDING' AND NOT published;
