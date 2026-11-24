// Realistic mock data and state management for SYNCORA Live Testing Demo

export interface MockProject {
  id: number;
  name: string;
  description: string;
  clientId: number;
  clientName: string;
  managerId: number;
  managerName: string;
  startDate: string;
  deadline: string;
  priority: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
  status: 'PLANNED' | 'IN_PROGRESS' | 'ON_HOLD' | 'COMPLETED' | 'CANCELLED';
  progress: number;
  budget?: number;
  createdAt?: string;
}

export interface MockTask {
  id: number;
  projectId: number;
  projectName?: string;
  name: string;
  description?: string;
  estimatedDuration: number;
  plannedStartDate?: string;
  plannedFinishDate?: string;
  priority: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
  status: 'PLANNED' | 'IN_PROGRESS' | 'COMPLETED' | 'ON_HOLD' | 'CANCELLED';
  assignedWorkerId?: number;
  assignedWorkerName?: string;
  predecessorTaskIds?: number[];
  progress?: number;
}

export interface MockWorker {
  id: number;
  userId?: number;
  name: string;
  email: string;
  skills: string;
  experience?: string;
  dailyCapacity: number;
  status: 'AVAILABLE' | 'OCCUPIED' | 'ON_LEAVE' | 'INACTIVE';
  activeAssignedTasks?: number;
}

export interface MockClient {
  id: number;
  name: string;
  company: string;
  email: string;
  phone?: string;
  status: 'ACTIVE' | 'INACTIVE';
  activeProjectsCount?: number;
}

export interface MockVendor {
  id: number;
  name: string;
  company: string;
  email: string;
  phone?: string;
  services: string;
  status: 'ACTIVE' | 'INACTIVE';
  rating?: number;
}

export const INITIAL_ORGS = [
  {
    id: 1,
    name: 'Acme Corporation',
    slug: 'acme',
    orgCode: 'ACM',
    status: 'ACTIVE',
    userRole: 'ORGANIZATION_OWNER',
  },
  {
    id: 2,
    name: 'Stark Industries',
    slug: 'stark-industries',
    orgCode: 'STK',
    status: 'ACTIVE',
    userRole: 'ORGANIZATION_ADMIN',
  },
];

export const INITIAL_CLIENTS: MockClient[] = [
  { id: 1, name: 'Acme Corp', company: 'Acme Corporation', email: 'contact@acme.com', phone: '+1 (555) 019-2831', status: 'ACTIVE', activeProjectsCount: 2 },
  { id: 2, name: 'Wayne Enterprises', company: 'Wayne Global Holdings', email: 'ops@wayne.com', phone: '+1 (555) 438-9210', status: 'ACTIVE', activeProjectsCount: 1 },
  { id: 3, name: 'Cyberdyne Systems', company: 'Cyberdyne AI Solutions', email: 'procurement@cyberdyne.io', phone: '+1 (555) 890-1234', status: 'ACTIVE', activeProjectsCount: 1 },
];

export const INITIAL_WORKERS: MockWorker[] = [
  { id: 1, name: 'Alice Chen', email: 'alice.chen@acme.com', skills: 'System Architecture, Java, Spring Boot, PostgreSQL', experience: '8 years', dailyCapacity: 8, status: 'AVAILABLE', activeAssignedTasks: 2 },
  { id: 2, name: 'Bob Smith', email: 'bob.smith@acme.com', skills: 'Backend Engineering, Distributed Systems, Redis', experience: '5 years', dailyCapacity: 8, status: 'OCCUPIED', activeAssignedTasks: 3 },
  { id: 3, name: 'Charlie Davis', email: 'charlie.davis@acme.com', skills: 'Frontend Engineering, React, TypeScript, Tailwind CSS', experience: '4 years', dailyCapacity: 8, status: 'AVAILABLE', activeAssignedTasks: 1 },
  { id: 4, name: 'Dana White', email: 'dana.white@acme.com', skills: 'DevOps, CI/CD, Kubernetes, Terraform, Cloud', experience: '6 years', dailyCapacity: 8, status: 'AVAILABLE', activeAssignedTasks: 1 },
  { id: 5, name: 'Ethan Hunt', email: 'ethan.hunt@acme.com', skills: 'Security Auditing, Cryptography, OAuth2, Penetration Testing', experience: '7 years', dailyCapacity: 8, status: 'OCCUPIED', activeAssignedTasks: 2 },
];

export const INITIAL_VENDORS: MockVendor[] = [
  { id: 1, name: 'CloudScale Infrastructure', company: 'CloudScale Inc', email: 'billing@cloudscale.net', phone: '+1 (800) 555-0199', services: 'Cloud Hosting, Managed Kubernetes, Edge CDN', status: 'ACTIVE', rating: 4.8 },
  { id: 2, name: 'TechSupply Hardware', company: 'TechSupply Global Ltd', email: 'orders@techsupply.com', phone: '+1 (888) 555-0211', services: 'Enterprise Server Racks, NVMe Storage, Hardware Procurement', status: 'ACTIVE', rating: 4.6 },
  { id: 3, name: 'FastTrack Logistics', company: 'FastTrack Freight Systems', email: 'ops@fasttracklogistics.com', phone: '+1 (877) 555-0322', services: 'Global Freight, Secure Hardware Transit, Warehousing', status: 'ACTIVE', rating: 4.9 },
];

export const INITIAL_PROJECTS: MockProject[] = [
  {
    id: 1,
    name: 'ERP Modernization & CPM Scheduling Platform',
    description: 'Enterprise ERP core upgrade incorporating deterministic Critical Path Method (CPM) algorithms and resource conflict detection.',
    clientId: 1,
    clientName: 'Acme Corp',
    managerId: 1,
    managerName: 'Alex Mercer (Project Manager)',
    startDate: '2026-11-01',
    deadline: '2027-02-28',
    priority: 'HIGH',
    status: 'IN_PROGRESS',
    progress: 68,
    budget: 4500000,
    createdAt: '2026-11-01T08:00:00Z',
  },
  {
    id: 2,
    name: 'Multi-Cloud Zero-Trust Infrastructure Migration',
    description: 'Comprehensive AWS to hybrid cloud migration with cryptographic SSO, isolated multi-tenant network policies, and audit logging.',
    clientId: 2,
    clientName: 'Wayne Enterprises',
    managerId: 1,
    managerName: 'Alex Mercer (Project Manager)',
    startDate: '2026-11-10',
    deadline: '2027-01-31',
    priority: 'CRITICAL',
    status: 'IN_PROGRESS',
    progress: 42,
    budget: 7200000,
    createdAt: '2026-11-10T09:00:00Z',
  },
  {
    id: 3,
    name: 'Predictive ML Delay Risk Forecasting Engine',
    description: 'Scikit-learn pipeline for estimating vendor and task delay probabilities based on verified historical completion metrics.',
    clientId: 1,
    clientName: 'Acme Corp',
    managerId: 1,
    managerName: 'Alex Mercer (Project Manager)',
    startDate: '2026-10-15',
    deadline: '2026-12-20',
    priority: 'MEDIUM',
    status: 'IN_PROGRESS',
    progress: 85,
    budget: 2800000,
    createdAt: '2026-10-15T10:00:00Z',
  },
  {
    id: 4,
    name: 'Unified Procurement & Deliverables Approval System',
    description: 'End-to-end purchase order lifecycle, delivery challan verification, and anti-self-approval deliverables workflow.',
    clientId: 3,
    clientName: 'Cyberdyne Systems',
    managerId: 1,
    managerName: 'Alex Mercer (Project Manager)',
    startDate: '2026-09-01',
    deadline: '2026-11-15',
    priority: 'HIGH',
    status: 'COMPLETED',
    progress: 100,
    budget: 3500000,
    createdAt: '2026-09-01T08:00:00Z',
  },
];

export const INITIAL_TASKS: MockTask[] = [
  {
    id: 1,
    projectId: 1,
    projectName: 'ERP Modernization & CPM Scheduling Platform',
    name: 'Database Schema Design & Migration Scripts',
    description: 'PostgreSQL schema with Flyway migrations for CPM dependencies, float tracking, and resource allocation.',
    estimatedDuration: 5,
    plannedStartDate: '2026-11-01',
    plannedFinishDate: '2026-11-07',
    priority: 'CRITICAL',
    status: 'COMPLETED',
    assignedWorkerId: 1,
    assignedWorkerName: 'Alice Chen',
    predecessorTaskIds: [],
    progress: 100,
  },
  {
    id: 2,
    projectId: 1,
    projectName: 'ERP Modernization & CPM Scheduling Platform',
    name: 'Kahn Algorithm Topological Sort & CPM Forward/Backward Pass',
    description: 'Deterministic scheduling engine calculating Earliest Start, Earliest Finish, Latest Start, and Total Float.',
    estimatedDuration: 8,
    plannedStartDate: '2026-11-08',
    plannedFinishDate: '2026-11-18',
    priority: 'CRITICAL',
    status: 'COMPLETED',
    assignedWorkerId: 1,
    assignedWorkerName: 'Alice Chen',
    predecessorTaskIds: [1],
    progress: 100,
  },
  {
    id: 3,
    projectId: 1,
    projectName: 'ERP Modernization & CPM Scheduling Platform',
    name: 'Resource Conflict Detection Engine',
    description: 'Cross-project calendar collision detector checking worker daily capacity and double-booking.',
    estimatedDuration: 7,
    plannedStartDate: '2026-11-19',
    plannedFinishDate: '2026-11-28',
    priority: 'HIGH',
    status: 'IN_PROGRESS',
    assignedWorkerId: 2,
    assignedWorkerName: 'Bob Smith',
    predecessorTaskIds: [2],
    progress: 75,
  },
  {
    id: 4,
    projectId: 1,
    projectName: 'ERP Modernization & CPM Scheduling Platform',
    name: 'Interactive Gantt Timeline & Dependency Canvas',
    description: 'React + SVG canvas with drag-and-drop task rescheduling, CPM path highlighting, and milestone pins.',
    estimatedDuration: 10,
    plannedStartDate: '2026-11-25',
    plannedFinishDate: '2026-12-08',
    priority: 'HIGH',
    status: 'IN_PROGRESS',
    assignedWorkerId: 3,
    assignedWorkerName: 'Charlie Davis',
    predecessorTaskIds: [2],
    progress: 60,
  },
  {
    id: 5,
    projectId: 1,
    projectName: 'ERP Modernization & CPM Scheduling Platform',
    name: 'Heuristic Bounded Schedule Optimizer',
    description: 'Discrete optimization engine evaluating deadline adherence, disruption minimization, and capacity balance.',
    estimatedDuration: 9,
    plannedStartDate: '2026-12-09',
    plannedFinishDate: '2026-12-21',
    priority: 'HIGH',
    status: 'PLANNED',
    assignedWorkerId: 1,
    assignedWorkerName: 'Alice Chen',
    predecessorTaskIds: [3, 4],
    progress: 0,
  },
  {
    id: 6,
    projectId: 1,
    projectName: 'ERP Modernization & CPM Scheduling Platform',
    name: 'Security Penetration Testing & End-to-End Regression Validation',
    description: 'Automated regression test suite (135 tests) verifying tenant boundaries, SSRF prevention, and CPM correctness.',
    estimatedDuration: 6,
    plannedStartDate: '2026-12-22',
    plannedFinishDate: '2026-12-30',
    priority: 'CRITICAL',
    status: 'PLANNED',
    assignedWorkerId: 5,
    assignedWorkerName: 'Ethan Hunt',
    predecessorTaskIds: [5],
    progress: 0,
  },
];
