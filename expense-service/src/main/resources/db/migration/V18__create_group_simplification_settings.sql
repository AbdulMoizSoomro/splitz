CREATE TABLE group_simplification_settings (
    id BIGSERIAL PRIMARY KEY,
    group_id BIGINT NOT NULL UNIQUE,
    simplification_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE group_simplification_opt_outs (
    settings_id BIGINT NOT NULL REFERENCES group_simplification_settings(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL,
    PRIMARY KEY (settings_id, user_id)
);
