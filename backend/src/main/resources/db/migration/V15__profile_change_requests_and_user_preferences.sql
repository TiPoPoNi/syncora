-- V15 Migration: Profile Change Requests, User Profiles Extension, and User Preferences
-- Cross-compatible with PostgreSQL and H2 (PostgreSQL mode)

-- 1. Extend users table with profile fields
ALTER TABLE users ADD COLUMN IF NOT EXISTS full_name VARCHAR(100);
ALTER TABLE users ADD COLUMN IF NOT EXISTS phone VARCHAR(30);
ALTER TABLE users ADD COLUMN IF NOT EXISTS job_title VARCHAR(100);
ALTER TABLE users ADD COLUMN IF NOT EXISTS department VARCHAR(100);
ALTER TABLE users ADD COLUMN IF NOT EXISTS avatar_url VARCHAR(255);

-- Backfill profile data for initial seed users
UPDATE users SET full_name = 'System Administrator', job_title = 'Platform Administrator', department = 'IT & Infrastructure' WHERE username = 'admin';
UPDATE users SET full_name = 'Priya Sharma', job_title = 'Lead Project Manager', department = 'Project Operations' WHERE username = 'manager';
UPDATE users SET full_name = 'Rahul Kumar', job_title = 'Senior Backend Engineer', department = 'Software Engineering' WHERE username = 'worker';
UPDATE users SET full_name = 'Robert Vance', job_title = 'Client Executive', department = 'Client Relations' WHERE username = 'client';
UPDATE users SET full_name = 'TechCorp Logistics', job_title = 'Vendor Manager', department = 'Procurement' WHERE username = 'vendor';

-- 2. Create user_preferences table
CREATE TABLE IF NOT EXISTS user_preferences (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    theme VARCHAR(20) DEFAULT 'SYSTEM',
    language VARCHAR(10) DEFAULT 'en',
    time_zone VARCHAR(50) DEFAULT 'Asia/Kolkata',
    date_format VARCHAR(30) DEFAULT 'YYYY-MM-DD',
    first_day_of_week VARCHAR(20) DEFAULT 'MONDAY',
    text_size VARCHAR(20) DEFAULT 'DEFAULT',
    display_density VARCHAR(20) DEFAULT 'COMFORTABLE',
    reduced_motion BOOLEAN DEFAULT FALSE,
    high_contrast_focus BOOLEAN DEFAULT FALSE,
    in_app_notifications BOOLEAN DEFAULT TRUE,
    email_notifications BOOLEAN DEFAULT FALSE,
    task_assignments_notify BOOLEAN DEFAULT TRUE,
    deadline_reminders_notify BOOLEAN DEFAULT TRUE,
    project_risk_alerts_notify BOOLEAN DEFAULT TRUE,
    approval_requests_notify BOOLEAN DEFAULT TRUE,
    vendor_delay_notify BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Seed preferences for initial users
INSERT INTO user_preferences (user_id, theme, language, time_zone)
SELECT u.id, 'SYSTEM', 'en', 'Asia/Kolkata' 
FROM users u
WHERE NOT EXISTS (SELECT 1 FROM user_preferences p WHERE p.user_id = u.id);

-- 3. Create profile_change_requests table
CREATE TABLE IF NOT EXISTS profile_change_requests (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    organization_id BIGINT NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    proposed_full_name VARCHAR(100),
    proposed_phone VARCHAR(30),
    proposed_job_title VARCHAR(100),
    proposed_department VARCHAR(100),
    proposed_work_email VARCHAR(100),
    proposed_avatar_url VARCHAR(255),
    existing_full_name VARCHAR(100),
    existing_phone VARCHAR(30),
    existing_job_title VARCHAR(100),
    existing_department VARCHAR(100),
    existing_work_email VARCHAR(100),
    existing_avatar_url VARCHAR(255),
    submission_comment TEXT,
    reviewer_user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    reviewer_name VARCHAR(100),
    reviewer_comment TEXT,
    reviewed_at TIMESTAMP,
    email_verification_token VARCHAR(255),
    email_verified BOOLEAN DEFAULT FALSE,
    email_verification_expires_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 4. Create performance indexes
CREATE INDEX IF NOT EXISTS idx_pcr_user_status ON profile_change_requests(user_id, status);
CREATE INDEX IF NOT EXISTS idx_pcr_org_status ON profile_change_requests(organization_id, status);
CREATE INDEX IF NOT EXISTS idx_user_pref_user ON user_preferences(user_id);
