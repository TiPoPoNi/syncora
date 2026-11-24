# Multi-Tenancy Architecture & SaaS Workspace Isolation (Phase 12)

## 1. Overview & Architectural Principles

The **Project Resource Allocation & Scheduling System (PRS)** uses a **shared-database, shared-schema** multi-tenancy model. Every organization (tenant) operates within an isolated workspace boundary sharing the existing PostgreSQL relational schema and Spring Boot application tier without requiring duplicate microservices, separate per-tenant databases, or external billing infrastructure.

```
                      ┌──────────────────────────────────────┐
                      │    Client Request / Browser UI       │
                      │ (Header: X-Organization-Id: <tenant>) │
                      └──────────────────┬───────────────────┘
                                         │
                                         ▼
                      ┌──────────────────────────────────────┐
                      │    Spring Security & TenantFilter    │
                      │ - Verifies JWT identity              │
                      │ - Validates user membership in org   │
                      │ - Blocks operations if org SUSPENDED │
                      │ - Populates ThreadLocal TenantContext │
                      └──────────────────┬───────────────────┘
                                         │
                   ┌─────────────────────┴─────────────────────┐
                   ▼                                           ▼
      ┌─────────────────────────┐                 ┌─────────────────────────┐
      │  Tenant-Scoped Services │                 │  Scheduling & Optimizer │
      │ - ProjectService        │                 │ - Only candidate        │
      │ - ClientService         │                 │   workers in tenant org │
      │ - WorkerService         │                 │ - Cross-org assignment  │
      │ - VendorService         │                 │   strictly rejected     │
      │ - PortfolioAnalytics    │                 │ - CPM & baselines       │
      │ - Exports (CSV/iCal)    │                 │   project-bounded       │
      └────────────┬────────────┘                 └────────────┬────────────┘
                   │                                           │
                   └─────────────────────┬─────────────────────┘
                                         │
                                         ▼
                      ┌──────────────────────────────────────┐
                      │      PostgreSQL Relational Schema     │
                      │  - organizations                     │
                      │  - organization_memberships          │
                      │  - projects (organization_id FK)     │
                      │  - clients (organization_id FK)      │
                      │  - workers (organization_id FK)      │
                      │  - vendors (organization_id FK)      │
                      │  - tasks (parent project bounded)    │
                      └──────────────────────────────────────┘
```

---

## 2. Organization & Membership Data Model

### `organizations` Table
| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | BIGSERIAL | PRIMARY KEY | Unique identifier for the organization |
| `name` | VARCHAR(255) | NOT NULL | Human-readable organization name |
| `slug` | VARCHAR(100) | NOT NULL, UNIQUE | Canonical slug identifier (e.g. `acme-global`) |
| `status` | VARCHAR(30) | NOT NULL | `ACTIVE` or `SUSPENDED` |
| `created_at` | TIMESTAMP | DEFAULT NOW() | Creation timestamp |
| `updated_at` | TIMESTAMP | DEFAULT NOW() | Last update timestamp |

### `organization_memberships` Table
| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | BIGSERIAL | PRIMARY KEY | Unique identifier for membership |
| `user_id` | BIGINT | NOT NULL, FK `users(id)` | Enrolled user ID |
| `organization_id` | BIGINT | NOT NULL, FK `organizations(id)` | Associated organization ID |
| `role` | VARCHAR(50) | NOT NULL | Workspace role (`ORGANIZATION_ADMIN`, `PROJECT_MANAGER`, `CLIENT`, `WORKER`, `VENDOR`) |
| `status` | VARCHAR(30) | NOT NULL | `ACTIVE` or `SUSPENDED` |
| `created_at` | TIMESTAMP | DEFAULT NOW() | Creation timestamp |
| `updated_at` | TIMESTAMP | DEFAULT NOW() | Last update timestamp |

*Constraint*: `UNIQUE(user_id, organization_id)` prevents duplicate memberships.

---

## 3. Role & Permission Matrix (RBAC & Tenancy)

| Operation | Global `ADMIN` | `ORGANIZATION_ADMIN` | `PROJECT_MANAGER` | `WORKER` | `VENDOR` | `CLIENT` |
|---|:---:|:---:|:---:|:---:|:---:|:---:|
| **Create New Workspace** | Yes | Yes (creates new org) | No | No | No | No |
| **Manage Workspace Members** | All orgs | Own org only | Read members | Read members | Read members | Read members |
| **Change Member Roles** | All orgs | Own org only (non-ADMIN) | No | No | No | No |
| **Grant Global `ADMIN`** | System only | **Denied (400)** | **Denied (400)** | **Denied (400)** | **Denied (400)** | **Denied (400)** |
| **Demote Last Org Admin** | **Blocked (400)** | **Blocked (400)** | N/A | N/A | N/A | N/A |
| **Access Suspended Org Data** | **Blocked (403)** | **Blocked (403)** | **Blocked (403)** | **Blocked (403)** | **Blocked (403)** | **Blocked (403)** |
| **Manage Projects & Tasks** | Active org | Active org | Active org | Read assigned | Read assigned | Read assigned |
| **Assign Cross-Tenant Worker** | **Blocked (400)** | **Blocked (400)** | **Blocked (400)** | N/A | N/A | N/A |
| **Run Schedule Optimization** | Active org | Active org | Active org | No | No | No |
| **Portfolio Analytics** | Active org | Active org | Active org | No | No | Read summary |

---

## 4. Tenant Context & Security Filter Lifecycle

Tenant identification and isolation is handled via `TenantFilter`:
1. **Header Resolution**: Inspects `X-Organization-Id` HTTP header (or fallback query parameter `organizationId`).
2. **Membership Verification**: Verifies the authenticated user has an `ACTIVE` membership in the requested organization.
3. **Suspended Organization Enforcement**: If `org.status == "SUSPENDED"`, any request to business endpoints (projects, tasks, schedules, exports) immediately returns `403 Forbidden`. Only `/api/organizations` management endpoints remain accessible to reactivate the organization.
4. **ThreadLocal Scope**: Sets `TenantContext.setOrganization(org)` and `TenantContext.setMembership(mem)`.
5. **Leak Prevention**: Unconditionally cleans up the thread state in `finally { TenantContext.clear(); }` preventing thread pool pollution in Tomcat worker threads.

---

## 5. Cross-Organization Relationship & Isolation Rules

- **Projects**: Explicitly owned by a single organization via `organization_id`.
- **Clients**: Scoped to the organization. A project cannot be assigned a client belonging to a different organization.
- **Workers & Vendors**: Scoped to their respective organizations.
- **Task Assignments**: `TaskAssignmentService` verifies that any worker or vendor assigned to a task belongs strictly to the parent project's organization. Cross-organization assignments are rejected with `400 Bad Request`.
- **Optimization Engine**: `ScheduleOptimizationService` and `CandidateValidator` filter all candidates to the project's organization. Workers and vendors from other organizations are rejected during candidate generation.
- **Portfolio Analytics & Exports**: Aggregate metrics, CSV files, and calendar `.ics` files exclusively process projects belonging to the active organization context.

---

## 6. Migration Procedure & Data Backfill (`V9`)

The database migration `V9__multi_tenant_organizations.sql` preserves all pre-existing project data:
1. Creates `organizations` and `organization_memberships` tables.
2. Adds `organization_id` nullable foreign keys to `projects`, `clients`, `workers`, `vendors`.
3. Seeds the canonical default organization: `Acme Global Solutions` (slug `acme-global`, status `ACTIVE`).
4. Seeds a secondary test organization: `Stark Tech Industries` (slug `stark-tech`, status `ACTIVE`).
5. Backfills all existing records to `acme-global`.
6. Enrolls seed users (`admin`, `manager`, `worker`, `client`, `vendor`) into corresponding memberships in `Acme Global Solutions`.
7. Enrolls `admin` into `Stark Tech Industries` as `ORGANIZATION_ADMIN` for multi-tenant oversight.
8. Adds composite performance indexes (`idx_projects_org`, `idx_clients_org`, `idx_workers_org`, `idx_vendors_org`, `idx_org_memberships_user`, `idx_org_memberships_org`).

---

## 7. Organization REST API

| Method | Endpoint | Description | Status |
|---|---|---|---|
| `GET` | `/api/organizations` | List organizations accessible to current user | `200 OK` |
| `POST` | `/api/organizations` | Create a new organization and assign creator as admin | `201 Created` |
| `GET` | `/api/organizations/{orgId}` | Get details of a specific organization | `200 OK` |
| `PUT` | `/api/organizations/{orgId}` | Update organization profile and status | `200 OK` |
| `GET` | `/api/organizations/{orgId}/members` | List members of the organization | `200 OK` |
| `POST` | `/api/organizations/{orgId}/members` | Add an existing user as an organization member | `201 Created` |
| `PUT` | `/api/organizations/{orgId}/members/{memId}` | Update member role in organization | `200 OK` |
| `DELETE` | `/api/organizations/{orgId}/members/{memId}` | Deactivate member from organization | `200 OK` |
