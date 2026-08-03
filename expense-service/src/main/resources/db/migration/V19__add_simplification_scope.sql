ALTER TABLE group_simplification_settings
    ADD COLUMN simplification_scope VARCHAR(32) NOT NULL DEFAULT 'INTRA_GROUP';
