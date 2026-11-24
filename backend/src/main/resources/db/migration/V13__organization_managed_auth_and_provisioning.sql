-- V13 Migration: Organization-Managed Authentication & Employee Account Provisioning
-- Cross-compatible with PostgreSQL and H2 (PostgreSQL mode)

-- 1. Extend organizations with code prefix and atomic sequence counter
ALTER TABLE organizations ADD COLUMN IF NOT EXISTS org_code VARCHAR(10);
ALTER TABLE organizations ADD COLUMN IF NOT EXISTS employee_id_counter INT DEFAULT 0;

-- Backfill org_codes for initial seed organizations
UPDATE organizations SET org_code = 'ACM', employee_id_counter = 5 WHERE slug = 'acme-global';
UPDATE organizations SET org_code = 'STK', employee_id_counter = 1 WHERE slug = 'stark-tech';

-- 2. Extend organization_memberships with employee identity and invitation lifecycle
ALTER TABLE organization_memberships ADD COLUMN IF NOT EXISTS employee_id VARCHAR(50);
ALTER TABLE organization_memberships ADD COLUMN IF NOT EXISTS invitation_token VARCHAR(255);
ALTER TABLE organization_memberships ADD COLUMN IF NOT EXISTS invitation_status VARCHAR(20) DEFAULT 'ACCEPTED';
ALTER TABLE organization_memberships ADD COLUMN IF NOT EXISTS invitation_expires_at TIMESTAMP;
ALTER TABLE organization_memberships ADD COLUMN IF NOT EXISTS activated_at TIMESTAMP;

-- Backfill canonical employee IDs for seed memberships
UPDATE organization_memberships 
SET employee_id = 'ACM-EMP-0001', invitation_status = 'ACCEPTED', activated_at = CURRENT_TIMESTAMP 
WHERE user_id = (SELECT id FROM users WHERE username = 'admin') 
  AND organization_id = (SELECT id FROM organizations WHERE slug = 'acme-global');

UPDATE organization_memberships 
SET employee_id = 'ACM-EMP-0002', invitation_status = 'ACCEPTED', activated_at = CURRENT_TIMESTAMP 
WHERE user_id = (SELECT id FROM users WHERE username = 'manager') 
  AND organization_id = (SELECT id FROM organizations WHERE slug = 'acme-global');

UPDATE organization_memberships 
SET employee_id = 'ACM-EMP-0003', invitation_status = 'ACCEPTED', activated_at = CURRENT_TIMESTAMP 
WHERE user_id = (SELECT id FROM users WHERE username = 'worker') 
  AND organization_id = (SELECT id FROM organizations WHERE slug = 'acme-global');

UPDATE organization_memberships 
SET employee_id = 'ACM-EMP-0004', invitation_status = 'ACCEPTED', activated_at = CURRENT_TIMESTAMP 
WHERE user_id = (SELECT id FROM users WHERE username = 'client') 
  AND organization_id = (SELECT id FROM organizations WHERE slug = 'acme-global');

UPDATE organization_memberships 
SET employee_id = 'ACM-EMP-0005', invitation_status = 'ACCEPTED', activated_at = CURRENT_TIMESTAMP 
WHERE user_id = (SELECT id FROM users WHERE username = 'vendor') 
  AND organization_id = (SELECT id FROM organizations WHERE slug = 'acme-global');

UPDATE organization_memberships 
SET employee_id = 'STK-EMP-0001', invitation_status = 'ACCEPTED', activated_at = CURRENT_TIMESTAMP 
WHERE user_id = (SELECT id FROM users WHERE username = 'admin') 
  AND organization_id = (SELECT id FROM organizations WHERE slug = 'stark-tech');

-- 3. Extend users with account activation state, password recovery tokens, and security lockouts
ALTER TABLE users ADD COLUMN IF NOT EXISTS account_status VARCHAR(20) DEFAULT 'ACTIVE';
ALTER TABLE users ADD COLUMN IF NOT EXISTS password_reset_token VARCHAR(255);
ALTER TABLE users ADD COLUMN IF NOT EXISTS password_reset_expires_at TIMESTAMP;
ALTER TABLE users ADD COLUMN IF NOT EXISTS failed_login_attempts INT DEFAULT 0;
ALTER TABLE users ADD COLUMN IF NOT EXISTS locked_until TIMESTAMP;

-- Ensure all existing users have ACTIVE account_status
UPDATE users SET account_status = 'ACTIVE' WHERE account_status IS NULL;

-- 4. Create performance indexes
CREATE INDEX IF NOT EXISTS idx_org_mem_emp_id ON organization_memberships(organization_id, employee_id);
CREATE INDEX IF NOT EXISTS idx_org_mem_inv_token ON organization_memberships(invitation_token);
CREATE INDEX IF NOT EXISTS idx_users_reset_token ON users(password_reset_token);
CREATE INDEX IF NOT EXISTS idx_orgs_code ON organizations(org_code);
