CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE transactions (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id  VARCHAR(100) NOT NULL,
    amount          NUMERIC(19, 4) NOT NULL,
    merchant_id     VARCHAR(100) NOT NULL,
    merchant_category VARCHAR(50),
    country_code    CHAR(2)      NOT NULL,
    currency_code   CHAR(3)      NOT NULL,
    fraud_score     DECIMAL(4, 3),
    decision        VARCHAR(20),
    embedding       vector(768),
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    processed_at    TIMESTAMP,

    CONSTRAINT uq_transaction_id UNIQUE (transaction_id)
);

CREATE INDEX idx_transactions_merchant_id ON transactions (merchant_id);
CREATE INDEX idx_transactions_decision    ON transactions (decision);
CREATE INDEX idx_transactions_created_at  ON transactions (created_at);

-- HNSW index for approximate nearest-neighbor cosine similarity search
CREATE INDEX idx_transactions_embedding ON transactions
    USING hnsw (embedding vector_cosine_ops);

CREATE TABLE fraud_audit_log (
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id UUID         NOT NULL REFERENCES transactions (id),
    fraud_score    DECIMAL(4, 3) NOT NULL,
    decision       VARCHAR(20)  NOT NULL,
    reasoning      TEXT,
    model_version  VARCHAR(50),
    created_at     TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_fraud_audit_log_transaction_id ON fraud_audit_log (transaction_id);
CREATE INDEX idx_fraud_audit_log_created_at     ON fraud_audit_log (created_at);
