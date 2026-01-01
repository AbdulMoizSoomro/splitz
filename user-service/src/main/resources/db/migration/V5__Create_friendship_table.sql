-- V5__Create_friendship_table.sql
-- Creates the friendships table for tracking friend relationships between users

CREATE TABLE IF NOT EXISTS friendships (
    id BIGSERIAL PRIMARY KEY,
    requester_id BIGINT NOT NULL,
    addressee_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    -- Foreign keys
    CONSTRAINT fk_friendship_requester FOREIGN KEY (requester_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_friendship_addressee FOREIGN KEY (addressee_id) REFERENCES users(id) ON DELETE CASCADE,
    
    -- Ensure unique friendship between two users (one direction)
    CONSTRAINT uk_friendship_requester_addressee UNIQUE (requester_id, addressee_id),
    
    -- Prevent self-friendship
    CONSTRAINT chk_no_self_friendship CHECK (requester_id <> addressee_id),
    
    -- Ensure valid status values
    CONSTRAINT chk_friendship_status CHECK (status IN ('PENDING', 'ACCEPTED', 'REJECTED', 'BLOCKED'))
);

-- Index for finding friendships by requester
CREATE INDEX IF NOT EXISTS idx_friendship_requester ON friendships(requester_id);

-- Index for finding friendships by addressee
CREATE INDEX IF NOT EXISTS idx_friendship_addressee ON friendships(addressee_id);

-- Index for finding friendships by status
CREATE INDEX IF NOT EXISTS idx_friendship_status ON friendships(status);

-- Composite index for common queries (user + status)
CREATE INDEX IF NOT EXISTS idx_friendship_requester_status ON friendships(requester_id, status);
CREATE INDEX IF NOT EXISTS idx_friendship_addressee_status ON friendships(addressee_id, status);
