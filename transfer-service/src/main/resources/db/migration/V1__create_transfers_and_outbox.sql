-- Copyright (c) 2026 Jyotiprakash Parida — SPDX-License-Identifier: MIT

CREATE TABLE transfers (
    id                     UUID            PRIMARY KEY,
    owner_subject          VARCHAR(64)     NOT NULL,
    idempotency_key        VARCHAR(64)     NOT NULL,
    source_account_id      UUID            NOT NULL,
    target_account_number  VARCHAR(20)     NOT NULL,
    amount                 NUMERIC(19, 4)  NOT NULL CHECK (amount > 0),
    currency               VARCHAR(3)      NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    reference              VARCHAR(140),
    status                 VARCHAR(16)     NOT NULL CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED')),
    failure_code           VARCHAR(64),
    version                BIGINT          NOT NULL,
    created_at             TIMESTAMPTZ     NOT NULL,
    updated_at             TIMESTAMPTZ     NOT NULL,
    CONSTRAINT uq_transfers_owner_idempotency UNIQUE (owner_subject, idempotency_key)
);

CREATE INDEX idx_transfers_owner_created ON transfers (owner_subject, created_at DESC);

CREATE TABLE transfer_outbox (
    id            UUID         PRIMARY KEY,
    aggregate_id  UUID         NOT NULL,
    event_type    VARCHAR(64)  NOT NULL,
    payload       TEXT         NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL,
    published_at  TIMESTAMPTZ,
    attempts      INTEGER      NOT NULL DEFAULT 0
);

-- The relay only ever scans unpublished rows.
CREATE INDEX idx_outbox_unpublished ON transfer_outbox (created_at) WHERE published_at IS NULL;
