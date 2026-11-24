-- V10__budget_procurement_cost_management.sql
-- Phase 13: Project Budgeting, Procurement Tracking, Vendor Quotations, Purchase Orders, Deliveries, and Cost Management

-- 1. Project Budgets Table
CREATE TABLE project_budgets (
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL UNIQUE REFERENCES projects(id) ON DELETE CASCADE,
    initial_budget NUMERIC(15, 2) NOT NULL DEFAULT 0.00,
    approved_budget NUMERIC(15, 2) NOT NULL DEFAULT 0.00,
    currency VARCHAR(10) NOT NULL DEFAULT 'INR',
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    notes TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 2. Budget Revisions Table (Preserves immutable historical approved budget amounts)
CREATE TABLE budget_revisions (
    id BIGSERIAL PRIMARY KEY,
    budget_id BIGINT NOT NULL REFERENCES project_budgets(id) ON DELETE CASCADE,
    revision_number INT NOT NULL,
    previous_amount NUMERIC(15, 2) NOT NULL,
    revised_amount NUMERIC(15, 2) NOT NULL,
    reason TEXT NOT NULL,
    approved_by VARCHAR(100),
    approved_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 3. Vendor Quotations Table
CREATE TABLE vendor_quotations (
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    vendor_id BIGINT NOT NULL REFERENCES vendors(id) ON DELETE RESTRICT,
    quote_reference VARCHAR(50) NOT NULL UNIQUE,
    issue_date DATE NOT NULL,
    valid_until DATE,
    currency VARCHAR(10) NOT NULL DEFAULT 'INR',
    tax_amount NUMERIC(15, 2) NOT NULL DEFAULT 0.00,
    total_amount NUMERIC(15, 2) NOT NULL DEFAULT 0.00,
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    notes TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 4. Quotation Items Table
CREATE TABLE quotation_items (
    id BIGSERIAL PRIMARY KEY,
    quotation_id BIGINT NOT NULL REFERENCES vendor_quotations(id) ON DELETE CASCADE,
    description VARCHAR(255) NOT NULL,
    quantity NUMERIC(10, 2) NOT NULL,
    unit_price NUMERIC(15, 2) NOT NULL,
    subtotal NUMERIC(15, 2) NOT NULL
);

-- 5. Purchase Orders Table
CREATE TABLE purchase_orders (
    id BIGSERIAL PRIMARY KEY,
    po_reference VARCHAR(50) NOT NULL UNIQUE,
    project_id BIGINT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    vendor_id BIGINT NOT NULL REFERENCES vendors(id) ON DELETE RESTRICT,
    quotation_id BIGINT REFERENCES vendor_quotations(id) ON DELETE SET NULL,
    order_date DATE NOT NULL,
    expected_delivery_date DATE,
    currency VARCHAR(10) NOT NULL DEFAULT 'INR',
    total_committed_amount NUMERIC(15, 2) NOT NULL DEFAULT 0.00,
    approval_status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    delivery_status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    approved_by VARCHAR(100),
    approved_at TIMESTAMP,
    notes TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 6. Purchase Order Lines Table
CREATE TABLE purchase_order_lines (
    id BIGSERIAL PRIMARY KEY,
    purchase_order_id BIGINT NOT NULL REFERENCES purchase_orders(id) ON DELETE CASCADE,
    description VARCHAR(255) NOT NULL,
    quantity NUMERIC(10, 2) NOT NULL,
    unit_price NUMERIC(15, 2) NOT NULL,
    line_total NUMERIC(15, 2) NOT NULL,
    delivered_quantity NUMERIC(10, 2) NOT NULL DEFAULT 0.00
);

-- 7. Purchase Order Deliveries Table
CREATE TABLE purchase_order_deliveries (
    id BIGSERIAL PRIMARY KEY,
    purchase_order_id BIGINT NOT NULL REFERENCES purchase_orders(id) ON DELETE CASCADE,
    po_line_id BIGINT REFERENCES purchase_order_lines(id) ON DELETE CASCADE,
    delivery_reference VARCHAR(50) NOT NULL,
    expected_delivery_date DATE,
    actual_delivery_date DATE NOT NULL,
    quantity_delivered NUMERIC(10, 2) NOT NULL,
    received_by VARCHAR(100),
    status VARCHAR(30) NOT NULL DEFAULT 'ACCEPTED',
    notes TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 8. Project Expenses / Recorded Costs Table
CREATE TABLE project_expenses (
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    category VARCHAR(50) NOT NULL,
    amount NUMERIC(15, 2) NOT NULL,
    currency VARCHAR(10) NOT NULL DEFAULT 'INR',
    expense_date DATE NOT NULL,
    description VARCHAR(255) NOT NULL,
    task_id BIGINT REFERENCES tasks(id) ON DELETE SET NULL,
    vendor_id BIGINT REFERENCES vendors(id) ON DELETE SET NULL,
    purchase_order_id BIGINT REFERENCES purchase_orders(id) ON DELETE SET NULL,
    worker_id BIGINT REFERENCES workers(id) ON DELETE SET NULL,
    recorded_by VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 9. Performance B-Tree Indexes
CREATE INDEX idx_project_budgets_project ON project_budgets(project_id);
CREATE INDEX idx_budget_revisions_budget ON budget_revisions(budget_id);
CREATE INDEX idx_vendor_quotations_project ON vendor_quotations(project_id, status);
CREATE INDEX idx_purchase_orders_project ON purchase_orders(project_id, approval_status);
CREATE INDEX idx_purchase_orders_vendor ON purchase_orders(vendor_id);
CREATE INDEX idx_project_expenses_project ON project_expenses(project_id, category);
CREATE INDEX idx_project_expenses_date ON project_expenses(project_id, expense_date);
