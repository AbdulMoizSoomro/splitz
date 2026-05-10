ALTER TABLE friendship_settlements ADD COLUMN group_id BIGINT;
CREATE INDEX idx_friendship_settlement_group_id ON friendship_settlements(group_id);
