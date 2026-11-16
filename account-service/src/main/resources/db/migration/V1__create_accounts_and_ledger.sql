-- Copyright (c) 2026 Jyotiprakash Parida — SPDX-License-Identifier: MIT

CREATE TABLE accounts (
    id              UUID            PRIMARY KEY,
    account_number  VARCHAR(20)     NOT NULL UNIQUE,
    owner_subject   VARCHAR(64)     NOT NULL,
    account_type    VARCHAR(16)     NOT NULL CHECK (account_type IN ('SAVINGS', 'CURRENT')),
    currency        VARCHAR(3)      NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    balance         NUMERIC(19, 4)  NOT NULL CHECK (balance >= 0),
    status          VARCHAR(16)     NOT NULL CHECK (status IN ('ACTIVE', 'FROZEN', 'CLOSED')),
    version         BIGINT          NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL,
    updated_at      TIMESTAMPTZ     NOT NULL
);

-- Every customer-facing query is scoped by owner_subject (BOLA/IDOR prevention).
CREATE INDEX idx_accounts_owner_subject ON accounts (owner_subject);

CREATE TABLE ledger_entries (
    id              UUID            PRIMARY KEY,
    transfer_id     UUID,
    account_id      UUID            NOT NULL REFERENCES accounts (id),
    entry_type      VARCHAR(8)      NOT NULL CHECK (entry_type IN ('DEBIT', 'CREDIT')),
    amount          NUMERIC(19, 4)  NOT NULL CHECK (amount > 0),
    actor_subject   VARCHAR(64)     NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL
);

-- A transfer can be posted at most once: retries are detected, never double-applied.
CREATE UNIQUE INDEX uq_ledger_transfer_entry ON ledger_entries (transfer_id, entry_type) WHERE transfer_id IS NOT NULL;
CREATE INDEX idx_ledger_account ON ledger_entries (account_id, created_at);
