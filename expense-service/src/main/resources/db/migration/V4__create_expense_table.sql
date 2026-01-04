CREATE TABLE expenses (
    id BIGSERIAL PRIMARY KEY,
    group_id BIGINT NOT NULL,
    description VARCHAR(255) NOT NULL,
    amount DECIMAL(19, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'EUR',
    paid_by BIGINT NOT NULL,
    category_id BIGINT,
    expense_date DATE,
    notes TEXT,
    receipt_url VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_expense_group FOREIGN KEY (group_id) REFERENCES groups(id),
    CONSTRAINT fk_expense_category FOREIGN KEY (category_id) REFERENCES categories(id)
);

CREATE INDEX idx_expense_group_id ON expenses(group_id);
CREATE INDEX idx_expense_paid_by ON expenses(paid_by);
