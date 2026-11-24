-- V4 Migration: Resource-Aware Schedule Optimization & Recommendations

-- 1. Add required_skills to tasks
ALTER TABLE tasks ADD COLUMN required_skills VARCHAR(255);

-- Populate required_skills for existing demo tasks
UPDATE tasks SET required_skills = 'Business Analysis, Planning' WHERE name = 'Requirements';
UPDATE tasks SET required_skills = 'Frontend, React, UI/UX' WHERE name = 'UI Design';
UPDATE tasks SET required_skills = 'Backend, Java, Spring' WHERE name = 'Backend Development';
UPDATE tasks SET required_skills = 'Frontend, React, UI/UX' WHERE name = 'Frontend Development';
UPDATE tasks SET required_skills = 'Backend, Java, Spring' WHERE name = 'Integration';
UPDATE tasks SET required_skills = 'QA, Automation, Testing' WHERE name = 'Testing';
UPDATE tasks SET required_skills = 'DevOps, CI/CD, AWS' WHERE name = 'Deployment';
UPDATE tasks SET required_skills = 'Backend, Java, Spring' WHERE name = 'API Integration';

-- 2. Add Qualified Alternative Workers
INSERT INTO workers (user_id, name, email, skills, experience, status, daily_capacity)
VALUES 
(NULL, 'Vikram Mehta', 'vikram@example.com', 'Backend, Java, Spring, Microservices', 6, 'AVAILABLE', 8),
(NULL, 'Ananya Sharma', 'ananya@example.com', 'Frontend, React, UI/UX, TypeScript', 4, 'AVAILABLE', 8),
(NULL, 'Karan Verma', 'karan@example.com', 'QA, Automation, Testing, Cypress', 3, 'AVAILABLE', 8);

-- 3. Add Qualified Vendors
INSERT INTO vendors (name, company, email, phone, services, status)
VALUES
('CloudScale Systems', 'CloudScale Technologies Inc.', 'ops@cloudscale.com', '+1-555-0199', 'DevOps, CI/CD, AWS, Cloud Infrastructure', 'ACTIVE'),
('Apex Testing Labs', 'Apex QA Services Ltd.', 'contact@apextesting.com', '+1-555-0144', 'QA, Automation, Testing, Security Audit', 'ACTIVE'),
('Prime Cloud Solutions', 'Prime Cloud Corp', 'support@primecloud.com', '+1-555-0177', 'DevOps, CI/CD, AWS, Kubernetes', 'ACTIVE');

-- 4. Optimization Runs Table
CREATE TABLE optimization_runs (
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    initiated_by VARCHAR(100) NOT NULL,
    selected_objective VARCHAR(50) DEFAULT 'BALANCED',
    status VARCHAR(20) DEFAULT 'COMPLETED',
    candidates_evaluated INTEGER DEFAULT 0,
    feasible_candidates_count INTEGER DEFAULT 0,
    input_schedule_version VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 5. Schedule Alternatives Table
CREATE TABLE schedule_alternatives (
    id BIGSERIAL PRIMARY KEY,
    optimization_run_id BIGINT NOT NULL REFERENCES optimization_runs(id) ON DELETE CASCADE,
    alternative_rank INTEGER NOT NULL,
    title VARCHAR(255) NOT NULL,
    summary TEXT NOT NULL,
    proposed_reassignments TEXT,
    proposed_schedule_changes TEXT,
    project_completion_forecast DATE NOT NULL,
    deadline_status VARCHAR(20) NOT NULL,
    working_days_saved INTEGER DEFAULT 0,
    conflicts_resolved INTEGER DEFAULT 0,
    remaining_conflicts INTEGER DEFAULT 0,
    disruption_count INTEGER DEFAULT 0,
    objective_score DOUBLE PRECISION NOT NULL,
    constraint_status VARCHAR(20) DEFAULT 'VALID',
    trade_offs TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 6. Optimization Recommendations Table
CREATE TABLE optimization_recommendations (
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    optimization_run_id BIGINT REFERENCES optimization_runs(id) ON DELETE CASCADE,
    alternative_id BIGINT REFERENCES schedule_alternatives(id) ON DELETE SET NULL,
    category VARCHAR(50) NOT NULL,
    summary VARCHAR(255) NOT NULL,
    explanation TEXT NOT NULL,
    estimated_impact TEXT NOT NULL,
    status VARCHAR(20) DEFAULT 'PROPOSED', -- PROPOSED, APPROVED, REJECTED, APPLIED, EXPIRED
    approved_by VARCHAR(100),
    approved_at TIMESTAMP,
    applied_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 7. Optimization Audit Logs Table
CREATE TABLE optimization_audit_logs (
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    run_id BIGINT REFERENCES optimization_runs(id) ON DELETE SET NULL,
    recommendation_id BIGINT REFERENCES optimization_recommendations(id) ON DELETE SET NULL,
    action VARCHAR(50) NOT NULL,
    performed_by VARCHAR(100) NOT NULL,
    details TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
