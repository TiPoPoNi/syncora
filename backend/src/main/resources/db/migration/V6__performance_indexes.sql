-- V6__performance_indexes.sql
-- Production database index optimization for high-throughput scheduling queries,
-- CPM dependency traversal, conflict detection, and audit lookups.

-- Task Lookups & CPM Graph Traversal Indexes
CREATE INDEX IF NOT EXISTS idx_tasks_project_id ON tasks(project_id);
CREATE INDEX IF NOT EXISTS idx_tasks_status ON tasks(status);
CREATE INDEX IF NOT EXISTS idx_tasks_planned_dates ON tasks(planned_start_date, planned_finish_date);

-- Dependency Graph Lookups
CREATE INDEX IF NOT EXISTS idx_task_deps_successor ON task_dependencies(successor_task_id);
CREATE INDEX IF NOT EXISTS idx_task_deps_predecessor ON task_dependencies(predecessor_task_id);

-- Resource Assignment & Overlap Detection Indexes
CREATE INDEX IF NOT EXISTS idx_task_assignments_task ON task_assignments(task_id);
CREATE INDEX IF NOT EXISTS idx_task_assignments_worker ON task_assignments(worker_id);
CREATE INDEX IF NOT EXISTS idx_task_assignments_vendor ON task_assignments(vendor_id);

-- Delay Propagation & Baseline Audit Indexes
CREATE INDEX IF NOT EXISTS idx_task_delays_task_id ON task_delays(task_id);
CREATE INDEX IF NOT EXISTS idx_baselines_project ON project_baselines(project_id);
CREATE INDEX IF NOT EXISTS idx_task_baselines_task ON task_baselines(task_id);

-- Optimization Engine & Audit Indexes
CREATE INDEX IF NOT EXISTS idx_opt_runs_project ON optimization_runs(project_id);
CREATE INDEX IF NOT EXISTS idx_opt_recs_project_status ON optimization_recommendations(project_id, status);
CREATE INDEX IF NOT EXISTS idx_opt_audit_project ON optimization_audit_logs(project_id);

-- AI Assistant Chat History Scoping Indexes
CREATE INDEX IF NOT EXISTS idx_chat_project_user ON assistant_chat_messages(project_id, username);
