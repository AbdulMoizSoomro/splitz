CREATE TABLE replicated_users (
    user_id BIGINT PRIMARY KEY,
    username VARCHAR(100) NOT NULL,
    full_name VARCHAR(200),
    email VARCHAR(200),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE replicated_friendships (
    id VARCHAR(100) PRIMARY KEY,
    user_id BIGINT NOT NULL,
    friend_id BIGINT NOT NULL,
    status VARCHAR(50) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE processed_events (
    event_id VARCHAR(100) PRIMARY KEY,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL
);
