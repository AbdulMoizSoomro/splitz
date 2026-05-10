CREATE TABLE friendship_settlements (
    id BIGSERIAL PRIMARY KEY,
    payer_id BIGINT NOT NULL,
    payee_id BIGINT NOT NULL,
    amount DECIMAL(19, 2) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    marked_paid_at TIMESTAMP,
    settled_at TIMESTAMP
);

CREATE INDEX idx_friendship_settlement_payer_id ON friendship_settlements(payer_id);
CREATE INDEX idx_friendship_settlement_payee_id ON friendship_settlements(payee_id);
