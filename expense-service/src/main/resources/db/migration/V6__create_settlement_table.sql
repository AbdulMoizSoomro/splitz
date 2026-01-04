CREATE TABLE settlements (
    id BIGSERIAL PRIMARY KEY,
    group_id BIGINT NOT NULL,
    payer_id BIGINT NOT NULL,
    payee_id BIGINT NOT NULL,
    amount DECIMAL(19, 2) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    marked_paid_at TIMESTAMP,
    settled_at TIMESTAMP,
    CONSTRAINT fk_settlement_group FOREIGN KEY (group_id) REFERENCES groups(id)
);

CREATE INDEX idx_settlement_group_id ON settlements(group_id);
CREATE INDEX idx_settlement_payer_id ON settlements(payer_id);
CREATE INDEX idx_settlement_payee_id ON settlements(payee_id);
