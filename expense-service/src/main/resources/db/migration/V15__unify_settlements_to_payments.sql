-- Drop old tables
DROP TABLE IF EXISTS friendship_settlements;
DROP TABLE IF EXISTS settlements;

-- Create payments table
CREATE TABLE payments (
    id BIGSERIAL PRIMARY KEY,
    payer_id BIGINT NOT NULL,
    payee_id BIGINT NOT NULL,
    amount DECIMAL(19, 2) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    marked_paid_at TIMESTAMP,
    settled_at TIMESTAMP,
    version INTEGER NOT NULL DEFAULT 0
);

-- Create settlement_allocations table
CREATE TABLE settlement_allocations (
    id BIGSERIAL PRIMARY KEY,
    payment_id BIGINT NOT NULL,
    group_id BIGINT,
    amount DECIMAL(19, 2) NOT NULL,
    CONSTRAINT fk_allocation_payment FOREIGN KEY (payment_id) REFERENCES payments(id) ON DELETE CASCADE,
    CONSTRAINT fk_allocation_group FOREIGN KEY (group_id) REFERENCES groups(id)
);

-- Create indexes for performance and constraint support
CREATE INDEX idx_payment_payer_id ON payments(payer_id);
CREATE INDEX idx_payment_payee_id ON payments(payee_id);
CREATE INDEX idx_allocation_payment_id ON settlement_allocations(payment_id);
CREATE INDEX idx_allocation_group_id ON settlement_allocations(group_id);
