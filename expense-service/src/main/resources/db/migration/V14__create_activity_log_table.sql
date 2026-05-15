CREATE TABLE activity_logs (
    id BIGSERIAL PRIMARY KEY,
    group_id BIGINT NOT NULL,
    type VARCHAR(50) NOT NULL,
    actor_id BIGINT NOT NULL,
    entity_id BIGINT,
    entity_name VARCHAR(255),
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    details TEXT
);

CREATE INDEX idx_activity_logs_group_id ON activity_logs(group_id);
