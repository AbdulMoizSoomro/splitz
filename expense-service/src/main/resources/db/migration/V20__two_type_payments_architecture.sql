-- 1. Add type, group_id, and notes columns to payments table
ALTER TABLE payments ADD COLUMN type VARCHAR(20) NOT NULL DEFAULT 'GROUP';
ALTER TABLE payments ADD COLUMN group_id BIGINT;
ALTER TABLE payments ADD COLUMN notes VARCHAR(255);

-- 2. Backfill group_id and type from existing settlement_allocations if any exist
UPDATE payments
SET group_id = (
    SELECT sa.group_id
    FROM settlement_allocations sa
    WHERE sa.payment_id = payments.id AND sa.group_id IS NOT NULL
    LIMIT 1
);

UPDATE payments
SET type = 'DIRECT'
WHERE group_id IS NULL;

-- 3. Add foreign key and check constraint
ALTER TABLE payments
    ADD CONSTRAINT fk_payment_group FOREIGN KEY (group_id) REFERENCES groups(id) ON DELETE RESTRICT;

ALTER TABLE payments
    ADD CONSTRAINT chk_payment_type_group CHECK (
        (type = 'GROUP' AND group_id IS NOT NULL) OR
        (type = 'DIRECT' AND group_id IS NULL)
    );

-- 4. Create indexes
CREATE INDEX idx_payments_group_id ON payments(group_id);
CREATE INDEX idx_payments_type_status ON payments(type, status);

-- 5. Drop deprecated settlement_allocations table
DROP TABLE IF EXISTS settlement_allocations;
