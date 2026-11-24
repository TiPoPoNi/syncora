import {
  INITIAL_ORGS,
  INITIAL_CLIENTS,
  INITIAL_WORKERS,
  INITIAL_VENDORS,
  INITIAL_PROJECTS,
  INITIAL_TASKS,
  MockProject,
  MockTask,
  MockWorker,
  MockClient,
  MockVendor,
} from './mockData';

// LocalStorage Persistence Keys
const STORAGE_PREFIX = 'syncora_demo_';
function getStorage<T>(key: string, defaultVal: T): T {
  try {
    const raw = localStorage.getItem(STORAGE_PREFIX + key);
    if (!raw) {
      localStorage.setItem(STORAGE_PREFIX + key, JSON.stringify(defaultVal));
      return defaultVal;
    }
    return JSON.parse(raw);
  } catch {
    return defaultVal;
  }
}

function setStorage<T>(key: string, val: T): void {
  try {
    localStorage.setItem(STORAGE_PREFIX + key, JSON.stringify(val));
  } catch {}
}

export async function handleMockRequest(config: any): Promise<any> {
  const method = (config.method || 'get').toLowerCase();
  const fullUrl = config.url || '';
  // Normalize URL by stripping baseURL or protocol/host if present
  const urlPath = fullUrl.replace(/^(?:https?:\/\/[^/]+)?(?:\/api)?/, '').split('?')[0];

  // Helper response builder
  const ok = (data: any, status = 200) => {
    return {
      data,
      status,
      statusText: 'OK',
      headers: { 'content-type': 'application/json' },
      config,
    };
  };

  // 1. Health check
  if (urlPath === '/health') {
    return ok({ status: 'UP', mode: 'STANDALONE_DEMO', message: 'Syncora Live Interactive Demo Mode' });
  }

  // 2. Authentication
  if (urlPath === '/auth/login' && method === 'post') {
    const body = typeof config.data === 'string' ? JSON.parse(config.data || '{}') : (config.data || {});
    const username = (body.username || 'admin').toLowerCase();
    const orgCode = (body.organizationCode || 'ACM').toUpperCase();

    let role = 'ADMIN';
    let userRole = 'ORGANIZATION_OWNER';
    let name = 'Demo Administrator';
    let empId = 'ACM-EMP-0001';

    if (username.includes('manager')) {
      role = 'PROJECT_MANAGER';
      userRole = 'PROJECT_MANAGER';
      name = 'Demo Project Manager';
      empId = 'ACM-EMP-0002';
    } else if (username.includes('worker')) {
      role = 'WORKER';
      userRole = 'WORKER';
      name = 'Demo Worker';
      empId = 'ACM-EMP-0003';
    } else if (username.includes('client')) {
      role = 'CLIENT';
      userRole = 'CLIENT';
      name = 'Demo Client Stakeholder';
      empId = 'ACM-EMP-0004';
    } else if (username.includes('vendor')) {
      role = 'VENDOR';
      userRole = 'VENDOR';
      name = 'Demo Vendor Partner';
      empId = 'ACM-EMP-0005';
    }

    const orgName = orgCode === 'STK' ? 'Stark Industries' : 'Acme Corporation';
    const orgSlug = orgCode === 'STK' ? 'stark-industries' : 'acme';
    const orgId = orgCode === 'STK' ? 2 : 1;

    const userPayload = {
      id: 1,
      username: username,
      role: role,
      userRole: userRole,
      token: 'demo_live_jwt_token_syncora_2026',
      email: `${username}@${orgSlug}.com`,
      organizationId: orgId,
      organizationName: orgName,
      organizationSlug: orgSlug,
      organizationCode: orgCode,
      employeeId: empId,
      memberships: [
        {
          organizationId: 1,
          organizationName: 'Acme Corporation',
          organizationSlug: 'acme',
          organizationCode: 'ACM',
          role: 'ORGANIZATION_OWNER',
          status: 'ACTIVE',
          employeeId: 'ACM-EMP-0001',
        },
        {
          organizationId: 2,
          organizationName: 'Stark Industries',
          organizationSlug: 'stark-industries',
          organizationCode: 'STK',
          role: 'ORGANIZATION_ADMIN',
          status: 'ACTIVE',
          employeeId: 'STK-EMP-0001',
        },
      ],
    };

    return ok(userPayload);
  }

  // 3. Organizations Listing & Registration
  if (urlPath === '/organizations') {
    const orgs = getStorage('orgs', INITIAL_ORGS);
    return ok(orgs);
  }

  if (urlPath === '/auth/register-organization' && method === 'post') {
    const body = typeof config.data === 'string' ? JSON.parse(config.data || '{}') : (config.data || {});
    const orgs = getStorage('orgs', INITIAL_ORGS);
    const newOrg = {
      id: orgs.length + 1,
      name: body.organizationName || 'New Workspace',
      slug: (body.organizationSlug || 'new-workspace').toLowerCase(),
      orgCode: (body.organizationCode || 'NEW').toUpperCase(),
      status: 'ACTIVE',
      userRole: 'ORGANIZATION_OWNER',
    };
    orgs.push(newOrg);
    setStorage('orgs', orgs);

    const userPayload = {
      id: 10,
      username: body.ownerUsername || 'owner',
      role: 'ADMIN',
      userRole: 'ORGANIZATION_OWNER',
      token: 'demo_registered_token_2026',
      email: body.ownerEmail || 'owner@new.com',
      organizationId: newOrg.id,
      organizationName: newOrg.name,
      organizationSlug: newOrg.slug,
      organizationCode: newOrg.orgCode,
      employeeId: `${newOrg.orgCode}-EMP-0001`,
      memberships: [newOrg],
    };
    return ok(userPayload);
  }

  // 4. Dashboard Stats
  if (urlPath === '/dashboard') {
    const projects = getStorage<MockProject[]>('projects', INITIAL_PROJECTS);
    const tasks = getStorage<MockTask[]>('tasks', INITIAL_TASKS);
    const workers = getStorage<MockWorker[]>('workers', INITIAL_WORKERS);
    const vendors = getStorage<MockVendor[]>('vendors', INITIAL_VENDORS);

    const activeProjects = projects.filter((p) => p.status === 'IN_PROGRESS').length;
    const completedProjects = projects.filter((p) => p.status === 'COMPLETED').length;

    const stats = {
      totalProjects: projects.length,
      activeProjects: activeProjects,
      completedProjects: completedProjects,
      delayedProjects: 1,
      projectsAtRisk: 2,
      totalWorkers: workers.length,
      totalVendors: vendors.length,
      upcomingDeadlines: 4,
      overdueTasks: 0,
      resourceConflictsCount: 1,
      recentProjects: projects.slice(0, 4).map((p) => ({
        id: p.id,
        name: p.name,
        clientName: p.clientName,
        status: p.status,
        progress: p.progress,
        deadline: p.deadline,
        isDelayed: p.id === 2,
        hasResourceConflict: p.id === 1,
        atRisk: p.id === 1 || p.id === 2,
        riskReason: p.id === 1 ? 'Worker Alice Chen allocated to concurrent CPM critical tasks' : undefined,
      })),
      projectsRequiringAttention: [
        {
          id: 1,
          name: 'ERP Modernization & CPM Scheduling Platform',
          clientName: 'Acme Corp',
          status: 'IN_PROGRESS',
          progress: 68,
          deadline: '2027-02-28',
          isDelayed: false,
          hasResourceConflict: true,
          atRisk: true,
          riskReason: 'Temporal resource overlap on Critical Path task #3',
          conflictDetails: ['Alice Chen scheduled on concurrent critical tasks during week of 2026-11-20'],
        },
        {
          id: 2,
          name: 'Multi-Cloud Zero-Trust Infrastructure Migration',
          clientName: 'Wayne Enterprises',
          status: 'IN_PROGRESS',
          progress: 42,
          deadline: '2027-01-31',
          isDelayed: true,
          hasResourceConflict: false,
          atRisk: true,
          riskReason: 'Cloud hardware delivery delayed 4 working days by supplier',
        },
      ],
      upcomingMilestones: [
        {
          id: 101,
          taskName: 'Topological Sort & CPM Forward/Backward Pass Validation',
          projectName: 'ERP Modernization Platform',
          deadline: '2026-12-05',
          priority: 'CRITICAL',
          status: 'IN_PROGRESS',
          deadlineExceeded: false,
        },
        {
          id: 102,
          taskName: 'Gantt Timeline Visual Verification',
          projectName: 'ERP Modernization Platform',
          deadline: '2026-12-15',
          priority: 'HIGH',
          status: 'IN_PROGRESS',
          deadlineExceeded: false,
        },
        {
          id: 103,
          taskName: 'Zero-Trust Gateway Gateway Deployment',
          projectName: 'Multi-Cloud Infrastructure',
          deadline: '2026-12-28',
          priority: 'HIGH',
          status: 'PLANNED',
          deadlineExceeded: false,
        },
      ],
      resourceWorkload: workers.map((w) => ({
        workerId: w.id,
        name: w.name,
        skills: w.skills,
        status: w.status,
        rosterStatus: w.status,
        activeAssignedTasks: w.activeAssignedTasks || 1,
        hasConflict: w.id === 1,
        workloadStatus: w.id === 1 ? 'OVERALLOCATED' : 'OPTIMAL',
        workloadSummary: w.id === 1 ? '8.0h / 8.0h capacity (1 conflict detected)' : '6.5h / 8.0h capacity',
      })),
      recentActivities: [
        {
          id: 1,
          action: 'SCHEDULE_RECALCULATED',
          projectName: 'ERP Modernization & CPM Scheduling Platform',
          performedBy: 'Alex Mercer (Project Manager)',
          details: 'Topological Critical Path recalculated: Earliest finish remains within SLA.',
          timestamp: '2026-11-24T06:15:00Z',
        },
        {
          id: 2,
          action: 'RECOMMENDATION_PROPOSED',
          projectName: 'ERP Modernization & CPM Scheduling Platform',
          performedBy: 'Bounded Optimizer Engine',
          details: 'Proposed peer reassignment for task #3 to Charlie Davis to eliminate overlap.',
          timestamp: '2026-11-24T05:40:00Z',
        },
        {
          id: 3,
          action: 'PURCHASE_ORDER_APPROVED',
          projectName: 'Multi-Cloud Zero-Trust Infrastructure Migration',
          performedBy: 'Alex Mercer',
          details: 'PO-2026-004 approved for CloudScale Infrastructure ($45,000).',
          timestamp: '2026-11-23T18:20:00Z',
        },
      ],
    };

    return ok(stats);
  }

  // 5. Projects CRUD
  if (urlPath === '/projects') {
    const projects = getStorage<MockProject[]>('projects', INITIAL_PROJECTS);
    if (method === 'get') {
      return ok(projects);
    }
    if (method === 'post') {
      const body = typeof config.data === 'string' ? JSON.parse(config.data || '{}') : (config.data || {});
      const clients = getStorage<MockClient[]>('clients', INITIAL_CLIENTS);
      const matchedClient = clients.find((c) => c.id === Number(body.clientId));
      const newProj: MockProject = {
        id: Date.now(),
        name: body.name || 'Untitled Project',
        description: body.description || '',
        clientId: Number(body.clientId) || 1,
        clientName: matchedClient?.name || 'Acme Corp',
        managerId: 1,
        managerName: 'Alex Mercer (Project Manager)',
        startDate: body.startDate || new Date().toISOString().split('T')[0],
        deadline: body.deadline || '2027-03-31',
        priority: body.priority || 'MEDIUM',
        status: body.status || 'PLANNED',
        progress: 0,
        createdAt: new Date().toISOString(),
      };
      projects.unshift(newProj);
      setStorage('projects', projects);
      return ok(newProj, 201);
    }
  }

  // Single Project match /projects/:id
  const projectDetailMatch = urlPath.match(/^\/projects\/(\d+)$/);
  if (projectDetailMatch) {
    const id = Number(projectDetailMatch[1]);
    const projects = getStorage<MockProject[]>('projects', INITIAL_PROJECTS);
    const existing = projects.find((p) => p.id === id);

    if (method === 'get') {
      return existing ? ok(existing) : ok(projects[0]);
    }
    if (method === 'put') {
      const body = typeof config.data === 'string' ? JSON.parse(config.data || '{}') : (config.data || {});
      const idx = projects.findIndex((p) => p.id === id);
      if (idx !== -1) {
        projects[idx] = { ...projects[idx], ...body };
        setStorage('projects', projects);
        return ok(projects[idx]);
      }
      return ok(body);
    }
    if (method === 'delete') {
      const filtered = projects.filter((p) => p.id !== id);
      setStorage('projects', filtered);
      return ok({ message: 'Project deleted successfully' });
    }
  }

  // 6. Gantt & CPM Data: /projects/:id/gantt
  const ganttMatch = urlPath.match(/^\/projects\/(\d+)\/gantt$/);
  if (ganttMatch) {
    const id = Number(ganttMatch[1]);
    const tasks = getStorage<MockTask[]>('tasks', INITIAL_TASKS);
    const projTasks = tasks.filter((t) => t.projectId === id || id === 1);

    const ganttTasks = projTasks.map((t) => ({
      id: t.id,
      name: t.name,
      startDate: t.plannedStartDate || '2026-11-01',
      finishDate: t.plannedFinishDate || '2026-11-15',
      duration: t.estimatedDuration || 5,
      progress: t.progress || 0,
      status: t.status,
      critical: t.id === 1 || t.id === 2 || t.id === 5,
      delayed: t.id === 3,
      delayDays: t.id === 3 ? 2 : 0,
      totalSlack: t.id === 1 || t.id === 2 || t.id === 5 ? 0 : 3,
      dependencies: t.predecessorTaskIds || [],
      assignees: [t.assignedWorkerName || 'Alice Chen'],
    }));

    return ok({
      projectId: id,
      projectName: 'ERP Modernization & CPM Scheduling Platform',
      projectStartDate: '2026-11-01',
      projectDeadline: '2027-02-28',
      expectedCompletionDate: '2026-12-30',
      deadlineStatus: 'ON_TRACK',
      currentDate: new Date().toISOString().split('T')[0],
      tasks: ganttTasks,
    });
  }

  // Critical Path Data: /projects/:id/critical-path
  const cpmMatch = urlPath.match(/^\/projects\/(\d+)\/critical-path$/);
  if (cpmMatch) {
    const id = Number(cpmMatch[1]);
    return ok({
      projectId: id,
      projectName: 'ERP Modernization & CPM Scheduling Platform',
      projectStartDate: '2026-11-01',
      earliestProjectedCompletion: '2026-12-30',
      minimumDurationWorkingDays: 38,
      tasks: [
        {
          taskId: 1,
          taskName: 'Database Schema Design & Migration Scripts',
          duration: 5,
          earlyStart: 1,
          earlyFinish: 5,
          lateStart: 1,
          lateFinish: 5,
          totalSlack: 0,
          critical: true,
          earlyStartDate: '2026-11-01',
          earlyFinishDate: '2026-11-07',
          lateStartDate: '2026-11-01',
          lateFinishDate: '2026-11-07',
          predecessorTaskIds: [],
          successorTaskIds: [2],
        },
        {
          taskId: 2,
          taskName: 'Topological Sort & CPM Forward/Backward Pass',
          duration: 8,
          earlyStart: 6,
          earlyFinish: 13,
          lateStart: 6,
          lateFinish: 13,
          totalSlack: 0,
          critical: true,
          earlyStartDate: '2026-11-08',
          earlyFinishDate: '2026-11-18',
          lateStartDate: '2026-11-08',
          lateFinishDate: '2026-11-18',
          predecessorTaskIds: [1],
          successorTaskIds: [3, 4],
        },
        {
          taskId: 3,
          taskName: 'Resource Conflict Detection Engine',
          duration: 7,
          earlyStart: 14,
          earlyFinish: 20,
          lateStart: 16,
          lateFinish: 22,
          totalSlack: 2,
          critical: false,
          earlyStartDate: '2026-11-19',
          earlyFinishDate: '2026-11-28',
          lateStartDate: '2026-11-21',
          lateFinishDate: '2026-11-30',
          predecessorTaskIds: [2],
          successorTaskIds: [5],
        },
        {
          taskId: 4,
          taskName: 'Interactive Gantt Timeline Canvas',
          duration: 10,
          earlyStart: 14,
          earlyFinish: 23,
          lateStart: 14,
          lateFinish: 23,
          totalSlack: 0,
          critical: true,
          earlyStartDate: '2026-11-25',
          earlyFinishDate: '2026-12-08',
          lateStartDate: '2026-11-25',
          lateFinishDate: '2026-12-08',
          predecessorTaskIds: [2],
          successorTaskIds: [5],
        },
        {
          taskId: 5,
          taskName: 'Heuristic Bounded Schedule Optimizer',
          duration: 9,
          earlyStart: 24,
          earlyFinish: 32,
          lateStart: 24,
          lateFinish: 32,
          totalSlack: 0,
          critical: true,
          earlyStartDate: '2026-12-09',
          earlyFinishDate: '2026-12-21',
          lateStartDate: '2026-12-09',
          lateFinishDate: '2026-12-21',
          predecessorTaskIds: [3, 4],
          successorTaskIds: [6],
        },
        {
          taskId: 6,
          taskName: 'Security Hardening & E2E Regression Validation',
          duration: 6,
          earlyStart: 33,
          earlyFinish: 38,
          lateStart: 33,
          lateFinish: 38,
          totalSlack: 0,
          critical: true,
          earlyStartDate: '2026-12-22',
          earlyFinishDate: '2026-12-30',
          lateStartDate: '2026-12-22',
          lateFinishDate: '2026-12-30',
          predecessorTaskIds: [5],
          successorTaskIds: [],
        },
      ],
      criticalTaskIds: [1, 2, 4, 5, 6],
      representativeCriticalPath: [1, 2, 4, 5, 6],
      allCriticalPaths: [[1, 2, 4, 5, 6]],
      criticalTaskNames: [
        'Database Schema Design',
        'Topological Sort & CPM Pass',
        'Interactive Gantt Timeline',
        'Heuristic Bounded Optimizer',
        'Security Hardening & Regression',
      ],
    });
  }

  // Delay impact & Schedule details
  if (urlPath.includes('/delay-impact') || urlPath.includes('/schedule')) {
    return ok({
      projectId: 1,
      projectName: 'ERP Modernization & CPM Scheduling Platform',
      directDelays: 1,
      propagatedDelays: 2,
      maxProjectedSlipDays: 3,
      baselineFinishDate: '2026-12-28',
      forecastedFinishDate: '2026-12-31',
      affectedTasks: [
        { taskId: 3, taskName: 'Resource Conflict Detection Engine', reportedDelayDays: 2, impact: 'SLACK_ABSORBED' },
        { taskId: 5, taskName: 'Heuristic Bounded Schedule Optimizer', reportedDelayDays: 1, impact: 'CRITICAL_PATH_SLIP' },
      ],
    });
  }

  // Optimization runs & recommendations
  if (urlPath.includes('/optimization/runs') || urlPath.includes('/recommendations')) {
    if (method === 'post') {
      return ok({ message: 'Optimization completed successfully with 3 ranked candidate alternatives' });
    }
    return ok([
      {
        id: 101,
        runId: 1,
        strategy: 'PEER_REASSIGNMENT',
        description: 'Reassign Task #3 (Conflict Detection) to Charlie Davis to eliminate Alice Chen double-booking',
        score: 94.5,
        deadlineAdherenceScore: 98.0,
        disruptionScore: 92.0,
        capacityBalanceScore: 93.5,
        status: 'PROPOSED',
        suggestedChanges: [
          { taskId: 3, taskName: 'Resource Conflict Detection', fromWorker: 'Alice Chen', toWorker: 'Charlie Davis' },
        ],
        createdAt: '2026-11-24T05:40:00Z',
      },
      {
        id: 102,
        runId: 1,
        strategy: 'SLACK_AWARE_SHIFT',
        description: 'Shift Task #3 forward by 2 working days utilizing available non-critical float',
        score: 88.2,
        deadlineAdherenceScore: 100.0,
        disruptionScore: 84.0,
        capacityBalanceScore: 80.5,
        status: 'PROPOSED',
        createdAt: '2026-11-24T05:40:00Z',
      },
    ]);
  }

  if (urlPath.includes('/approve') || urlPath.includes('/apply') || urlPath.includes('/reject')) {
    return ok({ message: 'Recommendation updated successfully' });
  }

  // 7. Tasks CRUD
  if (urlPath === '/tasks') {
    const tasks = getStorage<MockTask[]>('tasks', INITIAL_TASKS);
    if (method === 'get') {
      return ok(tasks);
    }
    if (method === 'post') {
      const body = typeof config.data === 'string' ? JSON.parse(config.data || '{}') : (config.data || {});
      const workers = getStorage<MockWorker[]>('workers', INITIAL_WORKERS);
      const matchedWorker = workers.find((w) => w.id === Number(body.assignedWorkerId));
      const newTask: MockTask = {
        id: Date.now(),
        projectId: Number(body.projectId) || 1,
        name: body.name || 'New Task',
        description: body.description || '',
        estimatedDuration: Number(body.estimatedDuration) || 5,
        plannedStartDate: body.plannedStartDate || '2026-12-01',
        plannedFinishDate: body.plannedFinishDate || '2026-12-08',
        priority: body.priority || 'MEDIUM',
        status: body.status || 'PLANNED',
        assignedWorkerId: matchedWorker?.id,
        assignedWorkerName: matchedWorker?.name,
        predecessorTaskIds: body.predecessorTaskIds || [],
        progress: 0,
      };
      tasks.push(newTask);
      setStorage('tasks', tasks);
      return ok(newTask, 201);
    }
  }

  const taskMatch = urlPath.match(/^\/tasks\/(\d+)$/);
  if (taskMatch) {
    const id = Number(taskMatch[1]);
    const tasks = getStorage<MockTask[]>('tasks', INITIAL_TASKS);
    const existing = tasks.find((t) => t.id === id);

    if (method === 'get') {
      return existing ? ok(existing) : ok(tasks[0]);
    }
    if (method === 'put') {
      const body = typeof config.data === 'string' ? JSON.parse(config.data || '{}') : (config.data || {});
      const idx = tasks.findIndex((t) => t.id === id);
      if (idx !== -1) {
        tasks[idx] = { ...tasks[idx], ...body };
        setStorage('tasks', tasks);
        return ok(tasks[idx]);
      }
      return ok(body);
    }
    if (method === 'delete') {
      const filtered = tasks.filter((t) => t.id !== id);
      setStorage('tasks', filtered);
      return ok({ message: 'Task deleted successfully' });
    }
  }

  if (urlPath.includes('/dependency-details')) {
    return ok({
      taskId: 1,
      predecessors: [],
      successors: [{ id: 2, name: 'Topological Sort & CPM Pass', type: 'FINISH_TO_START' }],
    });
  }

  // 8. Workers CRUD
  if (urlPath === '/workers') {
    const workers = getStorage<MockWorker[]>('workers', INITIAL_WORKERS);
    if (method === 'get') {
      return ok(workers);
    }
    if (method === 'post') {
      const body = typeof config.data === 'string' ? JSON.parse(config.data || '{}') : (config.data || {});
      const newWorker: MockWorker = {
        id: Date.now(),
        name: body.name || 'New Engineer',
        email: body.email || 'engineer@acme.com',
        skills: body.skills || 'Software Engineering',
        dailyCapacity: Number(body.dailyCapacity) || 8,
        status: 'AVAILABLE',
        activeAssignedTasks: 0,
      };
      workers.push(newWorker);
      setStorage('workers', workers);
      return ok(newWorker, 201);
    }
  }

  const workerMatch = urlPath.match(/^\/workers\/(\d+)$/);
  if (workerMatch) {
    const id = Number(workerMatch[1]);
    const workers = getStorage<MockWorker[]>('workers', INITIAL_WORKERS);
    if (method === 'put') {
      const body = typeof config.data === 'string' ? JSON.parse(config.data || '{}') : (config.data || {});
      const idx = workers.findIndex((w) => w.id === id);
      if (idx !== -1) {
        workers[idx] = { ...workers[idx], ...body };
        setStorage('workers', workers);
        return ok(workers[idx]);
      }
      return ok(body);
    }
    if (method === 'delete') {
      const filtered = workers.filter((w) => w.id !== id);
      setStorage('workers', filtered);
      return ok({ message: 'Worker deleted successfully' });
    }
  }

  // 9. Clients CRUD
  if (urlPath === '/clients') {
    const clients = getStorage<MockClient[]>('clients', INITIAL_CLIENTS);
    if (method === 'get') {
      return ok(clients);
    }
    if (method === 'post') {
      const body = typeof config.data === 'string' ? JSON.parse(config.data || '{}') : (config.data || {});
      const newClient: MockClient = {
        id: Date.now(),
        name: body.name || 'New Client Corp',
        company: body.company || body.name || 'New Client Company',
        email: body.email || 'client@example.com',
        phone: body.phone || '+1 (555) 000-0000',
        status: 'ACTIVE',
        activeProjectsCount: 0,
      };
      clients.push(newClient);
      setStorage('clients', clients);
      return ok(newClient, 201);
    }
  }

  // 10. Vendors CRUD
  if (urlPath === '/vendors') {
    const vendors = getStorage<MockVendor[]>('vendors', INITIAL_VENDORS);
    if (method === 'get') {
      return ok(vendors);
    }
    if (method === 'post') {
      const body = typeof config.data === 'string' ? JSON.parse(config.data || '{}') : (config.data || {});
      const newVendor: MockVendor = {
        id: Date.now(),
        name: body.name || 'New Supplier',
        company: body.company || body.name || 'New Supplier Inc',
        email: body.email || 'supplier@example.com',
        phone: body.phone || '+1 (800) 000-0000',
        services: body.services || 'General Services',
        status: 'ACTIVE',
        rating: 5.0,
      };
      vendors.push(newVendor);
      setStorage('vendors', vendors);
      return ok(newVendor, 201);
    }
  }

  // 11. Portfolio Analytics
  if (urlPath.includes('/analytics') || urlPath.includes('/portfolio-kpis')) {
    return ok({
      totalProjects: 4,
      activeProjects: 3,
      completedProjects: 1,
      onHoldProjects: 0,
      cancelledProjects: 0,
      projectsForecastToMissDeadlines: 0,
      projectsMeetingDeadlines: 3,
      historicalOnTimeDeliveryRatePercent: 92.5,
      averageScheduleVarianceDays: 1.2,
      totalOverdueTasks: 0,
      resourceUtilizationPercent: 84.0,
      vendorDeliveryReliabilityPercent: 94.8,
      reportingPeriodStart: '2026-10-01',
      reportingPeriodEnd: '2026-12-31',
      metricFormulas: {
        variance: 'Actual Finish - Planned Baseline Finish',
        utilization: 'Assigned Working Days / Standard 8h Capacity',
      },
    });
  }

  // 12. Cost & Budget Management: /cost-management or /budget
  if (urlPath.includes('/cost-management') || urlPath.includes('/budget')) {
    return ok({
      projectId: 1,
      projectName: 'ERP Modernization & CPM Scheduling Platform',
      currency: 'USD',
      approvedBudget: 4500000,
      totalCommittedCost: 3200000,
      totalActualCost: 2850000,
      remainingBudget: 1650000,
      remainingCommittedBudget: 1300000,
      costVariance: -350000,
      costVariancePercent: -10.9,
      isOverBudget: false,
      categoryBreakdown: {
        LABOR: 1850000,
        MATERIALS: 420000,
        VENDOR_SERVICES: 380000,
        EQUIPMENT: 120000,
        TRANSPORTATION: 30000,
        OTHER: 50000,
      },
    });
  }

  // 13. Deliverables Management
  if (urlPath.includes('/deliverables')) {
    return ok([
      {
        id: 1,
        projectId: 1,
        projectName: 'ERP Modernization & CPM Scheduling Platform',
        taskId: 1,
        taskName: 'Database Schema Design & Migration Scripts',
        name: 'Flyway Relational Migration & ER Diagram Specification',
        description: 'Comprehensive schema definition, foreign key cascade constraints, and tenant isolation indexes.',
        deliverableType: 'TECHNICAL_DOCUMENTATION',
        responsibleUserId: 1,
        responsibleUsername: 'Alice Chen',
        assignedReviewerId: 2,
        assignedReviewerUsername: 'Alex Mercer',
        requiredSubmissionDate: '2026-11-08',
        actualSubmissionDate: '2026-11-07',
        status: 'APPROVED',
        currentVersionNumber: 1,
        isClientVisible: true,
        isConfidential: false,
        isOverdue: false,
        documentsCount: 2,
        approvalsCount: 1,
      },
      {
        id: 2,
        projectId: 1,
        projectName: 'ERP Modernization & CPM Scheduling Platform',
        taskId: 2,
        taskName: 'Topological Sort & CPM Forward/Backward Pass',
        name: 'Deterministic Scheduling Benchmark & Mathematical Proof',
        description: 'Empirical verification across cases A-G verifying float calculations and cycle protection.',
        deliverableType: 'CODE_VERIFICATION',
        responsibleUserId: 1,
        responsibleUsername: 'Alice Chen',
        assignedReviewerId: 2,
        assignedReviewerUsername: 'Alex Mercer',
        requiredSubmissionDate: '2026-11-20',
        actualSubmissionDate: '2026-11-19',
        status: 'UNDER_REVIEW',
        currentVersionNumber: 2,
        isClientVisible: true,
        isConfidential: false,
        isOverdue: false,
        documentsCount: 1,
        approvalsCount: 0,
      },
    ]);
  }

  // 14. Notifications
  if (urlPath === '/notifications/unread-count') {
    return ok({ count: 2 });
  }

  if (urlPath.includes('/notifications')) {
    if (method === 'patch') {
      return ok({ message: 'Notification marked as read' });
    }
    return ok([
      {
        id: 1,
        title: 'Schedule Recalculated',
        message: 'CPM pass completed for ERP Modernization: critical path is on track.',
        type: 'SCHEDULE_UPDATE',
        read: false,
        createdAt: '2026-11-24T06:15:00Z',
      },
      {
        id: 2,
        title: 'New Optimization Proposal',
        message: 'Bounded optimizer generated 2 candidate alternatives for resource overlap.',
        type: 'OPTIMIZATION',
        read: false,
        createdAt: '2026-11-24T05:40:00Z',
      },
    ]);
  }

  // 15. User Preferences
  if (urlPath.includes('/notification-preferences')) {
    return ok({
      inAppTaskAssigned: true,
      inAppDeadlineReminder: true,
      inAppScheduleRecalculated: true,
      emailDigestWeekly: false,
    });
  }

  // 16. Integrations & API Keys
  if (urlPath.includes('/integrations')) {
    return ok({
      calendars: [
        { provider: 'GOOGLE_CALENDAR', connected: true, account: 'alex.mercer@acme.com', lastSync: '2026-11-24T06:00:00Z' },
        { provider: 'OUTLOOK_CALENDAR', connected: false },
      ],
      webhooks: [
        { id: 1, name: 'Slack Notifications Bot', url: 'https://api.example.com/webhooks/demo-channel', events: ['PROJECT_DELAY', 'SCHEDULE_OPTIMIZED'], active: true },
      ],
      apiKeys: [
        { id: 1, name: 'CI/CD Deployment Token', prefix: 'prs_live_a89f', createdAt: '2026-11-01', expiresAt: '2027-11-01' },
      ],
    });
  }

  // 17. AI Assistant
  if (urlPath.includes('/assistant')) {
    return ok({
      provider: 'Syncora Grounded AI Simulator (Isolated Fast-Path)',
      status: 'READY',
      history: [
        {
          id: 1,
          sender: 'ASSISTANT',
          text: 'Welcome to the SYNCORA AI Scheduling Assistant. I can explain Critical Path bottlenecks, simulate downstream delay impacts, and compare candidate schedule alternatives without mutating your live database.',
          timestamp: '2026-11-24T06:00:00Z',
        },
      ],
      response: 'Based on CPM forward/backward calculations for ERP Modernization, the representative critical path comprises Tasks #1, #2, #4, #5, and #6. Task #3 has 2 days of total slack; delaying Task #3 by up to 2 days will not impact the project deadline of 2027-02-28.',
    });
  }

  // Default fallback for any unhandled GET/POST/PUT/DELETE
  return ok({ message: 'Request processed successfully in Syncora Demo Mode' });
}
