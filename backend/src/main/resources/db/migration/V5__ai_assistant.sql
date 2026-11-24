-- V5 Migration: AI Assistant Chat History & Scenario Records

CREATE TABLE assistant_chat_messages (
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    username VARCHAR(100) NOT NULL,
    role VARCHAR(20) NOT NULL, -- 'user' or 'assistant'
    content TEXT NOT NULL,
    citations TEXT,
    hypothetical BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_assistant_chat_proj_user ON assistant_chat_messages(project_id, username);
