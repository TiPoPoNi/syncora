-- Phase 14: Deliverables, Document Management & Approval Workflows
-- Cross-compatible with PostgreSQL and H2 (PostgreSQL mode)

CREATE TABLE IF NOT EXISTS project_deliverables (
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL,
    task_id BIGINT,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    deliverable_type VARCHAR(50) NOT NULL,
    responsible_user_id BIGINT,
    assigned_reviewer_id BIGINT,
    required_submission_date DATE,
    actual_submission_date DATE,
    requested_decision_date DATE,
    actual_decision_date DATE,
    status VARCHAR(50) NOT NULL DEFAULT 'PLANNED',
    current_version_number INT DEFAULT 1,
    is_client_visible BOOLEAN DEFAULT TRUE,
    is_confidential BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_deliv_project FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE,
    CONSTRAINT fk_deliv_task FOREIGN KEY (task_id) REFERENCES tasks(id) ON DELETE SET NULL,
    CONSTRAINT fk_deliv_responsible_user FOREIGN KEY (responsible_user_id) REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT fk_deliv_reviewer FOREIGN KEY (assigned_reviewer_id) REFERENCES users(id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS deliverable_documents (
    id BIGSERIAL PRIMARY KEY,
    deliverable_id BIGINT NOT NULL,
    version_number INT NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    storage_key VARCHAR(255) NOT NULL,
    file_path VARCHAR(500) NOT NULL,
    file_size BIGINT NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    checksum VARCHAR(64),
    version_notes TEXT,
    submission_status VARCHAR(50) NOT NULL DEFAULT 'SUBMITTED',
    uploaded_by VARCHAR(100) NOT NULL,
    uploaded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_doc_deliverable FOREIGN KEY (deliverable_id) REFERENCES project_deliverables(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS deliverable_approvals (
    id BIGSERIAL PRIMARY KEY,
    deliverable_id BIGINT NOT NULL,
    document_id BIGINT,
    reviewer_id BIGINT,
    decision VARCHAR(50) NOT NULL,
    decision_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    comments TEXT,
    previous_status VARCHAR(50),
    resulting_status VARCHAR(50),
    turnaround_hours NUMERIC(10,2),
    CONSTRAINT fk_appr_deliverable FOREIGN KEY (deliverable_id) REFERENCES project_deliverables(id) ON DELETE CASCADE,
    CONSTRAINT fk_appr_document FOREIGN KEY (document_id) REFERENCES deliverable_documents(id) ON DELETE SET NULL,
    CONSTRAINT fk_appr_reviewer FOREIGN KEY (reviewer_id) REFERENCES users(id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS task_approval_gates (
    id BIGSERIAL PRIMARY KEY,
    task_id BIGINT NOT NULL,
    deliverable_id BIGINT NOT NULL,
    gate_type VARCHAR(50) NOT NULL DEFAULT 'COMPLETION_GATE',
    is_mandatory BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_gate_task FOREIGN KEY (task_id) REFERENCES tasks(id) ON DELETE CASCADE,
    CONSTRAINT fk_gate_deliverable FOREIGN KEY (deliverable_id) REFERENCES project_deliverables(id) ON DELETE CASCADE,
    CONSTRAINT uq_gate_task_deliverable UNIQUE (task_id, deliverable_id, gate_type)
);

CREATE INDEX IF NOT EXISTS idx_deliverables_project ON project_deliverables(project_id);
CREATE INDEX IF NOT EXISTS idx_deliverables_task ON project_deliverables(task_id);
CREATE INDEX IF NOT EXISTS idx_deliverables_status ON project_deliverables(status);
CREATE INDEX IF NOT EXISTS idx_deliverable_docs_deliverable ON deliverable_documents(deliverable_id);
CREATE INDEX IF NOT EXISTS idx_deliverable_approvals_deliv ON deliverable_approvals(deliverable_id);
CREATE INDEX IF NOT EXISTS idx_task_approval_gates_task ON task_approval_gates(task_id);
