-- V9 Migration: Multi-Organization Workspaces & SaaS Architecture (Phase 12)

-- 1. Create Organizations Table
CREATE TABLE organizations (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    slug VARCHAR(100) UNIQUE NOT NULL,
    status VARCHAR(20) DEFAULT 'ACTIVE' NOT NULL, -- 'ACTIVE', 'SUSPENDED'
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 2. Create Organization Memberships Table
CREATE TABLE organization_memberships (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    organization_id BIGINT NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    role VARCHAR(30) NOT NULL, -- 'ORGANIZATION_ADMIN', 'PROJECT_MANAGER', 'CLIENT', 'WORKER', 'VENDOR'
    status VARCHAR(20) DEFAULT 'ACTIVE' NOT NULL, -- 'ACTIVE', 'SUSPENDED'
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (user_id, organization_id)
);

-- 3. Add organization_id column to business entities
ALTER TABLE projects ADD COLUMN organization_id BIGINT REFERENCES organizations(id) ON DELETE CASCADE;
ALTER TABLE clients ADD COLUMN organization_id BIGINT REFERENCES organizations(id) ON DELETE CASCADE;
ALTER TABLE workers ADD COLUMN organization_id BIGINT REFERENCES organizations(id) ON DELETE CASCADE;
ALTER TABLE vendors ADD COLUMN organization_id BIGINT REFERENCES organizations(id) ON DELETE CASCADE;

-- 4. Seed Canonical Default Organization
INSERT INTO organizations (name, slug, status)
VALUES ('Acme Global Solutions', 'acme-global', 'ACTIVE');

-- 5. Seed Secondary Organization for Multi-Tenant Isolation Testing & Demo
INSERT INTO organizations (name, slug, status)
VALUES ('Stark Tech Industries', 'stark-tech', 'ACTIVE');

-- 6. Backfill existing business entities with default organization ID
UPDATE projects SET organization_id = (SELECT id FROM organizations WHERE slug = 'acme-global' LIMIT 1);
UPDATE clients SET organization_id = (SELECT id FROM organizations WHERE slug = 'acme-global' LIMIT 1);
UPDATE workers SET organization_id = (SELECT id FROM organizations WHERE slug = 'acme-global' LIMIT 1);
UPDATE vendors SET organization_id = (SELECT id FROM organizations WHERE slug = 'acme-global' LIMIT 1);

-- 7. Seed Initial Memberships for existing users in Default Organization
-- admin -> ORGANIZATION_ADMIN
INSERT INTO organization_memberships (user_id, organization_id, role, status)
SELECT u.id, o.id, 'ORGANIZATION_ADMIN', 'ACTIVE'
FROM users u, organizations o
WHERE u.username = 'admin' AND o.slug = 'acme-global';

-- manager -> PROJECT_MANAGER
INSERT INTO organization_memberships (user_id, organization_id, role, status)
SELECT u.id, o.id, 'PROJECT_MANAGER', 'ACTIVE'
FROM users u, organizations o
WHERE u.username = 'manager' AND o.slug = 'acme-global';

-- worker -> WORKER
INSERT INTO organization_memberships (user_id, organization_id, role, status)
SELECT u.id, o.id, 'WORKER', 'ACTIVE'
FROM users u, organizations o
WHERE u.username = 'worker' AND o.slug = 'acme-global';

-- client -> CLIENT
INSERT INTO organization_memberships (user_id, organization_id, role, status)
SELECT u.id, o.id, 'CLIENT', 'ACTIVE'
FROM users u, organizations o
WHERE u.username = 'client' AND o.slug = 'acme-global';

-- vendor -> VENDOR
INSERT INTO organization_memberships (user_id, organization_id, role, status)
SELECT u.id, o.id, 'VENDOR', 'ACTIVE'
FROM users u, organizations o
WHERE u.username = 'vendor' AND o.slug = 'acme-global';

-- Also enroll admin in Stark Tech as ORGANIZATION_ADMIN for seamless administrative oversight
INSERT INTO organization_memberships (user_id, organization_id, role, status)
SELECT u.id, o.id, 'ORGANIZATION_ADMIN', 'ACTIVE'
FROM users u, organizations o
WHERE u.username = 'admin' AND o.slug = 'stark-tech';

-- 8. Create Performance Composite Indexes
CREATE INDEX idx_projects_org ON projects(organization_id, status);
CREATE INDEX idx_clients_org ON clients(organization_id, status);
CREATE INDEX idx_workers_org ON workers(organization_id, status);
CREATE INDEX idx_vendors_org ON vendors(organization_id, status);
CREATE INDEX idx_org_memberships_user ON organization_memberships(user_id, organization_id);
CREATE INDEX idx_org_memberships_org ON organization_memberships(organization_id, status);
