-- V8 Migration: Workflow Notifications, Preferences, Reminder Deduplication & Outbox

-- 1. Create notifications table for in-app and delivery tracking
CREATE TABLE notifications (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    event_type VARCHAR(50) NOT NULL,
    title VARCHAR(255) NOT NULL,
    message TEXT NOT NULL,
    related_project_id BIGINT REFERENCES projects(id) ON DELETE CASCADE,
    related_task_id BIGINT REFERENCES tasks(id) ON DELETE SET NULL,
    is_read BOOLEAN DEFAULT FALSE,
    delivery_channel VARCHAR(30) DEFAULT 'IN_APP',
    delivery_status VARCHAR(30) DEFAULT 'SENT',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    read_at TIMESTAMP
);

-- 2. Create user notification preferences table
CREATE TABLE notification_preferences (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    in_app_enabled BOOLEAN DEFAULT TRUE,
    email_enabled BOOLEAN DEFAULT FALSE,
    deadline_reminders_enabled BOOLEAN DEFAULT TRUE,
    task_assignments_enabled BOOLEAN DEFAULT TRUE,
    delay_alerts_enabled BOOLEAN DEFAULT TRUE,
    recommendation_alerts_enabled BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 3. Create reminder deduplication log to prevent duplicate notifications
CREATE TABLE reminder_deduplication_logs (
    id BIGSERIAL PRIMARY KEY,
    target_type VARCHAR(30) NOT NULL,
    target_id BIGINT NOT NULL,
    reminder_type VARCHAR(30) NOT NULL,
    event_date DATE NOT NULL,
    recipient_user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_reminder_dedup UNIQUE (target_type, target_id, reminder_type, event_date, recipient_user_id)
);

-- 4. Create notification outbox for reliable asynchronous email delivery and retries
CREATE TABLE notification_outbox (
    id BIGSERIAL PRIMARY KEY,
    notification_id BIGINT REFERENCES notifications(id) ON DELETE CASCADE,
    recipient_email VARCHAR(255) NOT NULL,
    subject VARCHAR(255) NOT NULL,
    body TEXT NOT NULL,
    status VARCHAR(30) DEFAULT 'PENDING',
    retry_count INTEGER DEFAULT 0,
    max_retries INTEGER DEFAULT 3,
    error_message TEXT,
    last_attempt_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 5. Performance and lookup indexes
CREATE INDEX idx_notifications_user_read ON notifications(user_id, is_read, created_at DESC);
CREATE INDEX idx_notifications_project ON notifications(related_project_id);
CREATE INDEX idx_notification_outbox_status ON notification_outbox(status);
CREATE INDEX idx_reminder_dedup_lookup ON reminder_deduplication_logs(target_type, target_id, reminder_type, event_date);
