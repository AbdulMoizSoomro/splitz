CREATE TABLE debt_simplification_plans (
    id BIGSERIAL PRIMARY KEY,
    group_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    original_transaction_count INT NOT NULL DEFAULT 0,
    simplified_transaction_count INT NOT NULL DEFAULT 0,
    total_debt_volume NUMERIC(19, 2) NOT NULL DEFAULT 0.00,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE plan_opt_out_users (
    plan_id BIGINT NOT NULL REFERENCES debt_simplification_plans(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL,
    PRIMARY KEY (plan_id, user_id)
);

CREATE TABLE simplified_debt_transactions (
    id BIGSERIAL PRIMARY KEY,
    plan_id BIGINT NOT NULL REFERENCES debt_simplification_plans(id) ON DELETE CASCADE,
    from_user_id BIGINT NOT NULL,
    from_username VARCHAR(255),
    to_user_id BIGINT NOT NULL,
    to_username VARCHAR(255),
    amount NUMERIC(19, 2) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
