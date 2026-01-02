CREATE TABLE categories (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    icon VARCHAR(50),
    color VARCHAR(50),
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Seed default categories
INSERT INTO categories (name, icon, color, is_default) VALUES
('Food & Dining', '🍕', '#FF6B6B', TRUE),
('Transport', '🚗', '#4ECDC4', TRUE),
('Entertainment', '🎬', '#45B7D1', TRUE),
('Utilities', '💡', '#96CEB4', TRUE),
('Shopping', '🛒', '#FFEAA7', TRUE),
('Other', '📦', '#DFE6E9', TRUE);
