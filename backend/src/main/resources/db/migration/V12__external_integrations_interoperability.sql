-- Phase 15: External Integrations & Interoperability
-- Cross-compatible with PostgreSQL and H2 (PostgreSQL mode)

CREATE TABLE IF NOT EXISTS external_calendar_integrations (
    id BIGSERIAL PRIMARY KEY,
    organization_id BIGINT NOT NULL,
    user_id BIGINT,
    provider VARCHAR(50) NOT NULL,
    account_email VARCHAR(255),
    calendar_id VARCHAR(255),
    access_token TEXT,
    refresh_token TEXT,
    token_expires_at TIMESTAMP,
    status VARCHAR(50) NOT NULL DEFAULT 'NOT_CONFIGURED',
    last_sync_at TIMESTAMP,
    last_error TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_cal_org FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE CASCADE,
    CONSTRAINT fk_cal_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT uq_cal_org_user_provider UNIQUE (organization_id, user_id, provider)
);
CREATE INDEX IF NOT EXISTS idx_cal_org_provider ON external_calendar_integrations(organization_id, provider);

CREATE TABLE IF NOT EXISTS calendar_event_mappings (
    id BIGSERIAL PRIMARY KEY,
    integration_id BIGINT NOT NULL,
    entity_type VARCHAR(50) NOT NULL,
    entity_id BIGINT NOT NULL,
    external_event_id VARCHAR(255) NOT NULL,
    sync_hash VARCHAR(64),
    sync_status VARCHAR(50) NOT NULL DEFAULT 'SYNCED',
    last_synced_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_error TEXT,
    CONSTRAINT fk_mapping_integration FOREIGN KEY (integration_id) REFERENCES external_calendar_integrations(id) ON DELETE CASCADE,
    CONSTRAINT uq_cal_mapping UNIQUE (integration_id, entity_type, entity_id)
);
CREATE INDEX IF NOT EXISTS idx_mapping_entity ON calendar_event_mappings(entity_type, entity_id);

CREATE TABLE IF NOT EXISTS webhook_subscriptions (
    id BIGSERIAL PRIMARY KEY,
    organization_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    destination_url VARCHAR(1000) NOT NULL,
    secret VARCHAR(255) NOT NULL,
    is_enabled BOOLEAN DEFAULT TRUE,
    subscribed_events TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_delivered_at TIMESTAMP,
    failure_count INT DEFAULT 0,
    status VARCHAR(50) DEFAULT 'ACTIVE',
    CONSTRAINT fk_webhook_org FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_webhook_org ON webhook_subscriptions(organization_id);

CREATE TABLE IF NOT EXISTS webhook_deliveries (
    id BIGSERIAL PRIMARY KEY,
    subscription_id BIGINT NOT NULL,
    event_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload TEXT NOT NULL,
    response_status_code INT,
    response_body TEXT,
    duration_ms BIGINT,
    status VARCHAR(50) NOT NULL,
    attempt_count INT DEFAULT 1,
    error_message TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_delivery_sub FOREIGN KEY (subscription_id) REFERENCES webhook_subscriptions(id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_delivery_sub ON webhook_deliveries(subscription_id);
CREATE INDEX IF NOT EXISTS idx_delivery_event ON webhook_deliveries(event_id);

CREATE TABLE IF NOT EXISTS api_keys (
    id BIGSERIAL PRIMARY KEY,
    organization_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    key_prefix VARCHAR(32) NOT NULL,
    key_hash VARCHAR(64) NOT NULL UNIQUE,
    scopes VARCHAR(500) NOT NULL,
    created_by_user_id BIGINT,
    expires_at TIMESTAMP,
    revoked_at TIMESTAMP,
    last_used_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_apikey_org FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE CASCADE,
    CONSTRAINT fk_apikey_user FOREIGN KEY (created_by_user_id) REFERENCES users(id) ON DELETE SET NULL
);
CREATE INDEX IF NOT EXISTS idx_apikey_org ON api_keys(organization_id);
CREATE INDEX IF NOT EXISTS idx_apikey_hash ON api_keys(key_hash);
