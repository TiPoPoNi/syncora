-- V2 Migration: Scheduling Engine

-- Add new scheduling fields to projects
ALTER TABLE projects ADD COLUMN expected_completion_date DATE;

-- Add new scheduling fields to tasks
ALTER TABLE tasks ADD COLUMN planned_start_date DATE;
ALTER TABLE tasks ADD COLUMN planned_finish_date DATE;

-- Add working hours capacity to workers
ALTER TABLE workers ADD COLUMN daily_capacity INTEGER DEFAULT 8;

-- Create Task Dependencies Table
CREATE TABLE task_dependencies (
    id BIGSERIAL PRIMARY KEY,
    predecessor_task_id BIGINT NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    successor_task_id BIGINT NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    dependency_type VARCHAR(20) DEFAULT 'FINISH_TO_START',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (predecessor_task_id, successor_task_id)
);

-- Create Task Assignments Table
CREATE TABLE task_assignments (
    id BIGSERIAL PRIMARY KEY,
    task_id BIGINT NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    worker_id BIGINT REFERENCES workers(id) ON DELETE CASCADE,
    vendor_id BIGINT REFERENCES vendors(id) ON DELETE CASCADE,
    assignment_type VARCHAR(20) NOT NULL, -- 'WORKER' or 'VENDOR'
    allocation_percentage INTEGER DEFAULT 100,
    status VARCHAR(20) DEFAULT 'PLANNED',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Seed Phase 2 Demo Data

-- 1. Create a Client
INSERT INTO clients (name, company, email, status) VALUES ('Acme Corp', 'Acme Corporation', 'contact@acme.com', 'ACTIVE');

-- 2. Create Workers
INSERT INTO workers (user_id, name, email, skills, experience, status, daily_capacity) 
VALUES 
(NULL, 'Rahul Kumar', 'rahul@example.com', 'Backend, Java, Spring', 5, 'AVAILABLE', 8),
(NULL, 'Priya Singh', 'priya@example.com', 'Frontend, React, UI/UX', 4, 'AVAILABLE', 8),
(NULL, 'Amit Patel', 'amit@example.com', 'QA, Automation, Testing', 3, 'AVAILABLE', 8),
(NULL, 'Sneha Gupta', 'sneha@example.com', 'DevOps, CI/CD, AWS', 6, 'AVAILABLE', 8);

-- 3. Create a Project
INSERT INTO projects (name, description, client_id, start_date, deadline, priority, status)
VALUES ('E-Commerce Website Launch', 'Fullstack e-commerce application launch', 
        (SELECT id FROM clients WHERE name = 'Acme Corp' LIMIT 1),
        CURRENT_DATE, CURRENT_DATE + 30, 'HIGH', 'PLANNED');

-- 4. Create Tasks for the Project
INSERT INTO tasks (project_id, name, description, estimated_duration, priority, status)
VALUES 
((SELECT id FROM projects WHERE name = 'E-Commerce Website Launch' LIMIT 1), 'Requirements', 'Gather requirements', 2, 'HIGH', 'TODO'),
((SELECT id FROM projects WHERE name = 'E-Commerce Website Launch' LIMIT 1), 'UI Design', 'Create UI mockups', 3, 'MEDIUM', 'TODO'),
((SELECT id FROM projects WHERE name = 'E-Commerce Website Launch' LIMIT 1), 'Backend Development', 'Develop APIs', 5, 'HIGH', 'TODO'),
((SELECT id FROM projects WHERE name = 'E-Commerce Website Launch' LIMIT 1), 'Frontend Development', 'Build React UI', 4, 'HIGH', 'TODO'),
((SELECT id FROM projects WHERE name = 'E-Commerce Website Launch' LIMIT 1), 'Testing', 'QA and bug fixing', 3, 'HIGH', 'TODO'),
((SELECT id FROM projects WHERE name = 'E-Commerce Website Launch' LIMIT 1), 'Deployment', 'Production launch', 1, 'HIGH', 'TODO');

-- 5. Create Dependencies (Requirements -> UI Design & Backend -> ...)
INSERT INTO task_dependencies (predecessor_task_id, successor_task_id, dependency_type)
VALUES 
((SELECT id FROM tasks WHERE name = 'Requirements'), (SELECT id FROM tasks WHERE name = 'UI Design'), 'FINISH_TO_START'),
((SELECT id FROM tasks WHERE name = 'Requirements'), (SELECT id FROM tasks WHERE name = 'Backend Development'), 'FINISH_TO_START'),
((SELECT id FROM tasks WHERE name = 'UI Design'), (SELECT id FROM tasks WHERE name = 'Frontend Development'), 'FINISH_TO_START'),
((SELECT id FROM tasks WHERE name = 'Backend Development'), (SELECT id FROM tasks WHERE name = 'Testing'), 'FINISH_TO_START'),
((SELECT id FROM tasks WHERE name = 'Frontend Development'), (SELECT id FROM tasks WHERE name = 'Testing'), 'FINISH_TO_START'),
((SELECT id FROM tasks WHERE name = 'Testing'), (SELECT id FROM tasks WHERE name = 'Deployment'), 'FINISH_TO_START');

-- 6. Create Task Assignments
INSERT INTO task_assignments (task_id, worker_id, assignment_type)
VALUES
((SELECT id FROM tasks WHERE name = 'Backend Development'), (SELECT id FROM workers WHERE name = 'Rahul Kumar'), 'WORKER'),
((SELECT id FROM tasks WHERE name = 'Frontend Development'), (SELECT id FROM workers WHERE name = 'Priya Singh'), 'WORKER'),
((SELECT id FROM tasks WHERE name = 'Testing'), (SELECT id FROM workers WHERE name = 'Amit Patel'), 'WORKER'),
((SELECT id FROM tasks WHERE name = 'Deployment'), (SELECT id FROM workers WHERE name = 'Sneha Gupta'), 'WORKER');

-- Create a conflicting task for Rahul Kumar to demonstrate conflict detection
INSERT INTO projects (name, description, client_id, start_date, deadline, priority, status)
VALUES ('Internal API Gateway', 'Internal tool', 
        (SELECT id FROM clients WHERE name = 'Acme Corp' LIMIT 1),
        CURRENT_DATE, CURRENT_DATE + 10, 'MEDIUM', 'PLANNED');

INSERT INTO tasks (project_id, name, description, estimated_duration, priority, status)
VALUES 
((SELECT id FROM projects WHERE name = 'Internal API Gateway' LIMIT 1), 'API Integration', 'Integrate microservices', 4, 'MEDIUM', 'TODO');

INSERT INTO task_assignments (task_id, worker_id, assignment_type)
VALUES
((SELECT id FROM tasks WHERE name = 'API Integration'), (SELECT id FROM workers WHERE name = 'Rahul Kumar'), 'WORKER');
