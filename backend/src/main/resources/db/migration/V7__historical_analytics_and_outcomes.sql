-- V7 Migration: Historical Analytics, Task Outcomes & ML Delay Predictions

-- 1. Add actual execution dates to tasks
ALTER TABLE tasks ADD COLUMN actual_start_date DATE;
ALTER TABLE tasks ADD COLUMN actual_finish_date DATE;

-- 2. Create immutable historical task outcome archive table
CREATE TABLE task_outcome_records (
    id BIGSERIAL PRIMARY KEY,
    task_id BIGINT REFERENCES tasks(id) ON DELETE SET NULL,
    project_id BIGINT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    task_name VARCHAR(100) NOT NULL,
    planned_start_date DATE,
    planned_finish_date DATE,
    actual_start_date DATE NOT NULL,
    actual_finish_date DATE NOT NULL,
    planned_duration INTEGER,
    actual_duration INTEGER,
    variance_working_days INTEGER,
    is_delayed BOOLEAN NOT NULL,
    primary_delay_reason VARCHAR(255),
    total_delays_reported INTEGER DEFAULT 0,
    assigned_worker_id BIGINT REFERENCES workers(id) ON DELETE SET NULL,
    was_critical_path BOOLEAN DEFAULT FALSE,
    recorded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 3. Create prediction audit & inference log table
CREATE TABLE task_delay_predictions (
    id BIGSERIAL PRIMARY KEY,
    task_id BIGINT NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    project_id BIGINT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    prediction_target VARCHAR(50) DEFAULT 'TASK_DELAY_RISK',
    delay_risk_score DOUBLE PRECISION NOT NULL,
    risk_level VARCHAR(20) NOT NULL,
    model_version VARCHAR(50) NOT NULL,
    features_snapshot TEXT,
    contributing_factors TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 4. Create performance indexes
CREATE INDEX idx_task_outcomes_project ON task_outcome_records(project_id);
CREATE INDEX idx_task_outcomes_delayed ON task_outcome_records(is_delayed);
CREATE INDEX idx_task_outcomes_recorded_at ON task_outcome_records(recorded_at);
CREATE INDEX idx_predictions_task_project ON task_delay_predictions(task_id, project_id);
