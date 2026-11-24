-- V3 Migration: Critical Path, Baselines & Delay Reporting

-- 1. Project Baselines
CREATE TABLE project_baselines (
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    version_name VARCHAR(50) NOT NULL,
    expected_completion_date DATE NOT NULL,
    minimum_duration_days INTEGER NOT NULL,
    created_by BIGINT REFERENCES users(id),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 2. Task Baselines
CREATE TABLE task_baselines (
    id BIGSERIAL PRIMARY KEY,
    baseline_id BIGINT NOT NULL REFERENCES project_baselines(id) ON DELETE CASCADE,
    task_id BIGINT NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    task_name VARCHAR(100) NOT NULL,
    planned_start_date DATE NOT NULL,
    planned_finish_date DATE NOT NULL,
    duration_days INTEGER NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 3. Task Delays
CREATE TABLE task_delays (
    id BIGSERIAL PRIMARY KEY,
    task_id BIGINT NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    delay_duration_days INTEGER NOT NULL,
    delay_reason VARCHAR(255) NOT NULL,
    reported_date DATE NOT NULL,
    updated_expected_finish DATE,
    notes TEXT,
    reported_by VARCHAR(100),
    status VARCHAR(20) DEFAULT 'REPORTED', -- 'REPORTED', 'ACKNOWLEDGED', 'RESOLVED'
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 4. Update Demo Project Tasks to match Phase 3 Specification
-- Insert 'Integration' task if not exists
INSERT INTO tasks (project_id, name, description, estimated_duration, priority, status)
SELECT id, 'Integration', 'System and API integration', 2, 'HIGH', 'TODO'
FROM projects 
WHERE name = 'E-Commerce Website Launch'
AND NOT EXISTS (
    SELECT 1 FROM tasks t 
    JOIN projects p ON t.project_id = p.id 
    WHERE p.name = 'E-Commerce Website Launch' AND t.name = 'Integration'
);

-- Remove old direct dependencies to Testing (Backend -> Testing, Frontend -> Testing)
DELETE FROM task_dependencies
WHERE predecessor_task_id IN (
    SELECT id FROM tasks WHERE name IN ('Backend Development', 'Frontend Development')
)
AND successor_task_id = (
    SELECT id FROM tasks WHERE name = 'Testing' AND project_id = (SELECT id FROM projects WHERE name = 'E-Commerce Website Launch' LIMIT 1)
);

-- Add dependencies: Backend -> Integration, Frontend -> Integration, Integration -> Testing
INSERT INTO task_dependencies (predecessor_task_id, successor_task_id, dependency_type)
VALUES
(
    (SELECT id FROM tasks WHERE name = 'Backend Development' AND project_id = (SELECT id FROM projects WHERE name = 'E-Commerce Website Launch' LIMIT 1)),
    (SELECT id FROM tasks WHERE name = 'Integration' AND project_id = (SELECT id FROM projects WHERE name = 'E-Commerce Website Launch' LIMIT 1)),
    'FINISH_TO_START'
),
(
    (SELECT id FROM tasks WHERE name = 'Frontend Development' AND project_id = (SELECT id FROM projects WHERE name = 'E-Commerce Website Launch' LIMIT 1)),
    (SELECT id FROM tasks WHERE name = 'Integration' AND project_id = (SELECT id FROM projects WHERE name = 'E-Commerce Website Launch' LIMIT 1)),
    'FINISH_TO_START'
),
(
    (SELECT id FROM tasks WHERE name = 'Integration' AND project_id = (SELECT id FROM projects WHERE name = 'E-Commerce Website Launch' LIMIT 1)),
    (SELECT id FROM tasks WHERE name = 'Testing' AND project_id = (SELECT id FROM projects WHERE name = 'E-Commerce Website Launch' LIMIT 1)),
    'FINISH_TO_START'
);

-- Assign a worker to Integration
INSERT INTO task_assignments (task_id, worker_id, assignment_type)
VALUES (
    (SELECT id FROM tasks WHERE name = 'Integration' AND project_id = (SELECT id FROM projects WHERE name = 'E-Commerce Website Launch' LIMIT 1)),
    (SELECT id FROM workers WHERE name = 'Rahul Kumar' LIMIT 1),
    'WORKER'
);
