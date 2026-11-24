import React, { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { api } from '../services/api';
import {
  FolderKanban,
  Users,
  Briefcase,
  AlertTriangle,
  Clock,
  Sparkles,
  Bot,
  ArrowRight,
  TrendingUp,
  Calendar,
  CheckCircle2,
  ShieldAlert,
  BarChart3,
  Layers,
  RefreshCw,
  Building2,
  Plus,
  AlertCircle,
  Activity,
  Check,
  ChevronRight,
  ExternalLink,
} from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import { useOrganization } from '../context/OrganizationContext';
import { LoadingSpinner, Badge, Button, Modal, Input, Select, Alert, EmptyState } from '../components/ui';

interface ProjectSummary {
  id: number;
  name: string;
  clientName: string;
  status: string;
  progress: number;
  deadline?: string | null;
  expectedCompletionDate?: string | null;
  isDelayed: boolean;
  hasResourceConflict?: boolean;
  atRisk?: boolean;
  riskReason?: string;
  conflictDetails?: string[];
}

interface Milestone {
  id: number;
  taskName: string;
  projectName: string;
  deadline?: string | null;
  plannedFinishDate?: string | null;
  priority: string;
  status: string;
  deadlineExceeded?: boolean;
  delayDays?: number | null;
}

interface WorkerWorkload {
  workerId: number;
  name: string;
  skills: string;
  status: string;
  rosterStatus?: string;
  activeAssignedTasks: number;
  hasConflict?: boolean;
  workloadStatus?: string;
  workloadSummary?: string;
}

interface DashboardActivity {
  id: number;
  action: string;
  projectName: string;
  performedBy: string;
  details: string;
  timestamp: string;
}

interface DashboardStats {
  totalProjects: number;
  activeProjects: number;
  completedProjects: number;
  delayedProjects: number;
  projectsAtRisk: number;
  totalWorkers: number;
  totalVendors: number;
  upcomingDeadlines: number;
  overdueTasks: number;
  resourceConflictsCount: number;
  recentProjects?: ProjectSummary[];
  projectsRequiringAttention?: ProjectSummary[];
  upcomingMilestones?: Milestone[];
  resourceWorkload?: WorkerWorkload[];
  recentActivities?: DashboardActivity[];
}

interface ClientOption {
  id: number;
  name: string;
}

export function Dashboard() {
  const { user } = useAuth();
  const { currentOrg, organizations, loading: orgLoading, error: orgError, switchOrganization } = useOrganization();
  const navigate = useNavigate();

  const [stats, setStats] = useState<DashboardStats | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [clients, setClients] = useState<ClientOption[]>([]);

  // Quick Action Modals
  const [showCreateProjectModal, setShowCreateProjectModal] = useState(false);
  const [showAddTaskModal, setShowAddTaskModal] = useState(false);
  const [showAddClientModal, setShowAddClientModal] = useState(false);
  const [submittingModal, setSubmittingModal] = useState(false);
  const [modalError, setModalError] = useState<string | null>(null);

  // Quick Project Form
  const [projectName, setProjectName] = useState('');
  const [projectDesc, setProjectDesc] = useState('');
  const [projectClientId, setProjectClientId] = useState('');
  const [projectStartDate, setProjectStartDate] = useState('');
  const [projectDeadline, setProjectDeadline] = useState('');
  const [projectPriority, setProjectPriority] = useState('MEDIUM');

  // Quick Task Form
  const [taskProjectId, setTaskProjectId] = useState('');
  const [taskName, setTaskName] = useState('');
  const [taskDeadline, setTaskDeadline] = useState('');
  const [taskDuration, setTaskDuration] = useState('5');
  const [taskPriority, setTaskPriority] = useState('MEDIUM');

  // Quick Client Form
  const [clientName, setClientName] = useState('');
  const [clientCompany, setClientCompany] = useState('');
  const [clientEmail, setClientEmail] = useState('');
  const [clientPhone, setClientPhone] = useState('');

  const isManagerOrAdmin = 
    user?.role === 'ADMIN' || 
    user?.role === 'PROJECT_MANAGER' ||
    currentOrg?.userRole === 'ORGANIZATION_ADMIN' ||
    currentOrg?.userRole === 'ORGANIZATION_OWNER' ||
    currentOrg?.userRole === 'PROJECT_MANAGER';

  useEffect(() => {
    // When org is loading, wait
    if (orgLoading) {
      return;
    }

    // If no workspace is selected, do not request organization data
    if (!currentOrg) {
      setStats(null);
      setError(null);
      setLoading(false);
      return;
    }

    loadDashboard(currentOrg.id);
    fetchClients();
  }, [currentOrg?.id, orgLoading]);

  const loadDashboard = async (orgId?: number) => {
    try {
      setLoading(true);
      setError(null);
      const res = await api.get<DashboardStats>('/dashboard');
      setStats(res.data);
    } catch (err: any) {
      console.error('Failed to load dashboard:', err);
      const status = err?.response?.status;
      const msg = err?.response?.data?.message || err?.message || 'Unable to retrieve dashboard metrics.';
      if (status === 403) {
        setError('Access restricted: You do not have active membership in this workspace.');
      } else if (status === 401) {
        setError('Your session has expired. Please sign in again.');
      } else {
        setError(`Failed to retrieve dashboard operations: ${msg}`);
      }
      setStats(null);
    } finally {
      setLoading(false);
    }
  };

  const fetchClients = async () => {
    try {
      const res = await api.get<ClientOption[]>('/clients');
      setClients(res.data || []);
    } catch (e) {
      // Clients may be restricted for non-admins, ignore silently
    }
  };

  // Quick Actions Submission Handlers
  const handleQuickCreateProject = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!projectName.trim()) return;
    try {
      setSubmittingModal(true);
      setModalError(null);
      await api.post('/projects', {
        name: projectName.trim(),
        description: projectDesc.trim() || undefined,
        clientId: projectClientId ? Number(projectClientId) : undefined,
        startDate: projectStartDate || undefined,
        deadline: projectDeadline || undefined,
        priority: projectPriority,
        status: 'PLANNED',
      });
      setShowCreateProjectModal(false);
      setProjectName('');
      setProjectDesc('');
      setProjectClientId('');
      setProjectStartDate('');
      setProjectDeadline('');
      loadDashboard(currentOrg?.id);
    } catch (err: any) {
      setModalError(err?.response?.data?.message || 'Failed to create project');
    } finally {
      setSubmittingModal(false);
    }
  };

  const handleQuickAddTask = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!taskName.trim() || !taskProjectId) return;
    try {
      setSubmittingModal(true);
      setModalError(null);
      await api.post('/tasks', {
        projectId: Number(taskProjectId),
        name: taskName.trim(),
        deadline: taskDeadline || undefined,
        estimatedDuration: Number(taskDuration) || 5,
        priority: taskPriority,
        status: 'TODO',
      });
      setShowAddTaskModal(false);
      setTaskName('');
      setTaskProjectId('');
      setTaskDeadline('');
      setTaskDuration('5');
      loadDashboard(currentOrg?.id);
    } catch (err: any) {
      setModalError(err?.response?.data?.message || 'Failed to add task');
    } finally {
      setSubmittingModal(false);
    }
  };

  const handleQuickAddClient = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!clientName.trim()) return;
    try {
      setSubmittingModal(true);
      setModalError(null);
      await api.post('/clients', {
        name: clientName.trim(),
        company: clientCompany.trim() || undefined,
        email: clientEmail.trim() || undefined,
        phone: clientPhone.trim() || undefined,
        status: 'ACTIVE',
      });
      setShowAddClientModal(false);
      setClientName('');
      setClientCompany('');
      setClientEmail('');
      setClientPhone('');
      fetchClients();
      loadDashboard(currentOrg?.id);
    } catch (err: any) {
      setModalError(err?.response?.data?.message || 'Failed to create client');
    } finally {
      setSubmittingModal(false);
    }
  };

  // Format activity action for human readability
  const formatAction = (action: string) => {
    return action
      .replace(/_/g, ' ')
      .toLowerCase()
      .replace(/\b\w/g, (c) => c.toUpperCase());
  };

  // Format activity timestamp
  const formatTime = (ts?: string) => {
    if (!ts) return '';
    try {
      const d = new Date(ts);
      return d.toLocaleDateString(undefined, {
        month: 'short',
        day: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
      });
    } catch (e) {
      return ts;
    }
  };

  // STATE 1: Initial Loading
  if (orgLoading || (loading && !stats && !error)) {
    return (
      <div className="py-20 flex justify-center">
        <LoadingSpinner message="Loading project operations dashboard..." />
      </div>
    );
  }

  // STATE 2: API Failure / Error State with Retry
  if (error && !loading) {
    return (
      <div className="max-w-2xl mx-auto py-12 px-4 space-y-6 animate-fadeIn">
        <div className="bg-white rounded-2xl border border-rose-200 shadow-sm p-8 text-center space-y-4">
          <div className="w-14 h-14 rounded-2xl bg-rose-50 text-rose-600 mx-auto flex items-center justify-center border border-rose-100 shadow-xs">
            <AlertTriangle className="w-7 h-7" />
          </div>
          <div className="space-y-1">
            <h2 className="text-xl font-black text-gray-900 tracking-tight">
              Dashboard Operations Unavailable
            </h2>
            <p className="text-sm text-gray-600 max-w-md mx-auto">
              {error}
            </p>
          </div>
          <div className="flex flex-wrap items-center justify-center gap-3 pt-2">
            <Button
              variant="primary"
              onClick={() => loadDashboard(currentOrg?.id)}
              className="gap-2"
            >
              <RefreshCw className="w-4 h-4" />
              Retry Connection
            </Button>
            {organizations.length > 1 && (
              <Button
                variant="outline"
                onClick={() => {
                  const alt = organizations.find((o) => o.id !== currentOrg?.id && o.status === 'ACTIVE');
                  if (alt) switchOrganization(alt);
                }}
              >
                Switch Workspace
              </Button>
            )}
          </div>
        </div>
      </div>
    );
  }

  // STATE 3: No Workspace Selected (User has workspaces, but none active)
  if (!currentOrg && organizations.length > 0) {
    return (
      <div className="max-w-3xl mx-auto py-12 px-4 space-y-6 animate-fadeIn">
        <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-8 space-y-6">
          <div className="text-center space-y-2">
            <div className="w-12 h-12 rounded-xl bg-blue-50 text-blue-600 mx-auto flex items-center justify-center border border-blue-100">
              <Building2 className="w-6 h-6" />
            </div>
            <h2 className="text-xl font-bold text-gray-900 tracking-tight">
              Workspace Selection Required
            </h2>
            <p className="text-sm text-gray-500 max-w-md mx-auto">
              Please select an authorized workspace from the list below to view live project metrics, schedules, and resource workloads.
            </p>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 pt-2">
            {organizations.map((org) => (
              <button
                key={org.id}
                onClick={() => switchOrganization(org)}
                className="p-4 rounded-xl border border-gray-200 hover:border-blue-500 hover:bg-blue-50/40 text-left transition flex items-center justify-between group"
              >
                <div>
                  <div className="font-bold text-gray-900 group-hover:text-blue-700 flex items-center gap-1.5">
                    <span>{org.name}</span>
                    {org.orgCode && (
                      <span className="text-[10px] font-mono font-bold bg-gray-100 text-gray-600 px-1.5 py-0.5 rounded">
                        {org.orgCode}
                      </span>
                    )}
                  </div>
                  <div className="text-xs text-gray-400 font-mono mt-0.5">{org.slug}</div>
                  {org.userRole && (
                    <div className="text-[10px] text-blue-600 font-semibold mt-1">
                      Role: {org.userRole}
                    </div>
                  )}
                </div>
                <ChevronRight className="w-5 h-5 text-gray-400 group-hover:text-blue-600 group-hover:translate-x-0.5 transition" />
              </button>
            ))}
          </div>
        </div>
      </div>
    );
  }

  // STATE 4: No Organization Membership Found
  if (!currentOrg && organizations.length === 0) {
    return (
      <div className="max-w-2xl mx-auto py-16 px-4 animate-fadeIn">
        <EmptyState
          icon={ShieldAlert}
          title="No Organization Workspace Assigned"
          description="Your account is authenticated, but is not currently a member of any active workspaces. Request an invitation from your team administrator, or register a new workspace."
          actionLabel="Set Up Workspace"
          onAction={() => navigate('/organization')}
        />
      </div>
    );
  }

  // STATE 5: Workspace Selected Has 0 Projects
  if (stats && stats.totalProjects === 0) {
    return (
      <div className="space-y-6 animate-fadeIn pb-12">
        {/* Workspace Context Top Bar */}
        <div className="bg-gradient-to-r from-slate-900 via-indigo-950 to-slate-900 text-white rounded-2xl p-6 shadow-xl border border-indigo-900/40 flex flex-wrap items-center justify-between gap-4">
          <div>
            <div className="flex items-center space-x-2.5">
              <h2 className="text-2xl font-black tracking-tight">{currentOrg?.name || 'Project Operations'}</h2>
              {currentOrg?.orgCode && (
                <span className="px-2.5 py-0.5 rounded-full text-xs font-bold tracking-wider bg-blue-500/20 text-blue-300 border border-blue-500/40 font-mono">
                  {currentOrg.orgCode}
                </span>
              )}
            </div>
            <p className="text-xs text-indigo-200/80 mt-1 max-w-xl">
              Deterministic Critical Path Method (CPM) analytics, topological delay propagation, heuristic optimization, and workforce leveling.
            </p>
          </div>

          <div className="flex items-center space-x-2">
            <Button
              variant="outline"
              size="sm"
              onClick={() => loadDashboard(currentOrg?.id)}
              className="bg-white/10 text-white border-white/20 hover:bg-white/20 text-xs"
            >
              <RefreshCw className="w-3.5 h-3.5 mr-1.5" />
              Refresh
            </Button>
          </div>
        </div>

        {/* Empty Workspace Onboarding Card */}
        <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-12 text-center max-w-2xl mx-auto space-y-5">
          <div className="w-16 h-16 rounded-2xl bg-indigo-50 text-indigo-600 mx-auto flex items-center justify-center border border-indigo-100 shadow-xs">
            <FolderKanban className="w-8 h-8" />
          </div>
          <div className="space-y-2">
            <h2 className="text-xl font-black text-gray-900 tracking-tight">
              Welcome to {currentOrg?.name}
            </h2>
            <p className="text-sm text-gray-500 max-w-md mx-auto">
              This workspace does not have any active projects yet. Create your first project to initialize topological task scheduling, resource leveling, and CPM analytics.
            </p>
          </div>

          <div className="pt-2 flex flex-wrap items-center justify-center gap-3">
            {isManagerOrAdmin ? (
              <>
                <Button
                  variant="primary"
                  onClick={() => setShowCreateProjectModal(true)}
                  className="gap-2"
                >
                  <Plus className="w-4 h-4" />
                  Create First Project
                </Button>
                <Button
                  variant="outline"
                  onClick={() => setShowAddClientModal(true)}
                  className="gap-2"
                >
                  <Users className="w-4 h-4" />
                  Add Client
                </Button>
              </>
            ) : (
              <p className="text-xs text-gray-400 italic">
                You have view-only access. Project creation is managed by project managers and workspace administrators.
              </p>
            )}
          </div>
        </div>

        {/* Quick Modals for Empty Workspace */}
        {renderQuickModals()}
      </div>
    );
  }

  // STATE 6: Successful Dashboard Data Retrieval
  const projectsRequiringAttentionList = stats?.projectsRequiringAttention ||
    (stats?.recentProjects || []).filter(
      (p) => p.atRisk || p.isDelayed || p.hasResourceConflict || (p.deadline && p.expectedCompletionDate && p.expectedCompletionDate > p.deadline)
    );

  const statCards = [
    {
      label: 'Active Projects',
      value: stats?.activeProjects ?? 0,
      sub: `${stats?.totalProjects ?? 0} total registered`,
      icon: FolderKanban,
      color: 'text-blue-600',
      bg: 'bg-blue-50 border-blue-200',
      link: '/projects',
    },
    {
      label: 'Projects at Risk',
      value: stats?.projectsAtRisk ?? 0,
      sub: `${stats?.delayedProjects ?? 0} forecast delayed`,
      icon: AlertTriangle,
      color: (stats?.projectsAtRisk ?? 0) > 0 ? 'text-amber-600' : 'text-emerald-600',
      bg: (stats?.projectsAtRisk ?? 0) > 0 ? 'bg-amber-50 border-amber-200' : 'bg-emerald-50 border-emerald-200',
      link: '/schedule',
    },
    {
      label: 'Resource Overlaps',
      value: stats?.resourceConflictsCount ?? 0,
      sub: (stats?.resourceConflictsCount ?? 0) > 0 ? 'Requires leveling' : 'Zero conflict state',
      icon: Users,
      color: (stats?.resourceConflictsCount ?? 0) > 0 ? 'text-rose-600' : 'text-emerald-600',
      bg: (stats?.resourceConflictsCount ?? 0) > 0 ? 'bg-rose-50 border-rose-200' : 'bg-emerald-50 border-emerald-200',
      link: '/schedule?tab=conflicts',
    },
    {
      label: 'Overdue Tasks',
      value: stats?.overdueTasks ?? 0,
      sub: `${stats?.upcomingDeadlines ?? 0} due in 30 days`,
      icon: Clock,
      color: (stats?.overdueTasks ?? 0) > 0 ? 'text-rose-600' : 'text-indigo-600',
      bg: (stats?.overdueTasks ?? 0) > 0 ? 'bg-rose-50 border-rose-200' : 'bg-indigo-50 border-indigo-200',
      link: '/tasks',
    },
  ];

  function renderQuickModals() {
    return (
      <>
        {/* Quick Create Project Modal */}
        <Modal
          isOpen={showCreateProjectModal}
          onClose={() => setShowCreateProjectModal(false)}
          title="Create New Project"
        >
          <form onSubmit={handleQuickCreateProject} className="space-y-4">
            {modalError && <Alert type="error">{modalError}</Alert>}
            <Input
              label="Project Name"
              required
              value={projectName}
              onChange={(e) => setProjectName(e.target.value)}
              placeholder="e.g. NextGen Web Portal"
            />
            <Input
              label="Description"
              value={projectDesc}
              onChange={(e) => setProjectDesc(e.target.value)}
              placeholder="Scope of work and milestones"
            />
            {clients.length > 0 && (
              <Select
                label="Client"
                value={projectClientId}
                onChange={(e) => setProjectClientId(e.target.value)}
                options={[
                  { value: '', label: 'Select a client (optional)' },
                  ...clients.map((c) => ({ value: c.id.toString(), label: c.name })),
                ]}
              />
            )}
            <div className="grid grid-cols-2 gap-3">
              <Input
                label="Start Date"
                type="date"
                value={projectStartDate}
                onChange={(e) => setProjectStartDate(e.target.value)}
              />
              <Input
                label="Deadline"
                type="date"
                value={projectDeadline}
                onChange={(e) => setProjectDeadline(e.target.value)}
              />
            </div>
            <Select
              label="Priority"
              value={projectPriority}
              onChange={(e) => setProjectPriority(e.target.value)}
              options={[
                { value: 'LOW', label: 'Low' },
                { value: 'MEDIUM', label: 'Medium' },
                { value: 'HIGH', label: 'High' },
                { value: 'CRITICAL', label: 'Critical' },
              ]}
            />
            <div className="flex justify-end gap-2 pt-2">
              <Button variant="outline" type="button" onClick={() => setShowCreateProjectModal(false)}>
                Cancel
              </Button>
              <Button variant="primary" type="submit" disabled={submittingModal}>
                {submittingModal ? 'Creating...' : 'Create Project'}
              </Button>
            </div>
          </form>
        </Modal>

        {/* Quick Add Task Modal */}
        <Modal
          isOpen={showAddTaskModal}
          onClose={() => setShowAddTaskModal(false)}
          title="Add New Task"
        >
          <form onSubmit={handleQuickAddTask} className="space-y-4">
            {modalError && <Alert type="error">{modalError}</Alert>}
            <Select
              label="Assign to Project"
              required
              value={taskProjectId}
              onChange={(e) => setTaskProjectId(e.target.value)}
              options={[
                { value: '', label: 'Select project...' },
                ...(stats?.recentProjects || []).map((p) => ({
                  value: p.id.toString(),
                  label: p.name,
                })),
              ]}
            />
            <Input
              label="Task Name"
              required
              value={taskName}
              onChange={(e) => setTaskName(e.target.value)}
              placeholder="e.g. Database Schema Migration"
            />
            <div className="grid grid-cols-2 gap-3">
              <Input
                label="Estimated Duration (Days)"
                type="number"
                min="1"
                value={taskDuration}
                onChange={(e) => setTaskDuration(e.target.value)}
              />
              <Input
                label="Task Deadline"
                type="date"
                value={taskDeadline}
                onChange={(e) => setTaskDeadline(e.target.value)}
              />
            </div>
            <Select
              label="Priority"
              value={taskPriority}
              onChange={(e) => setTaskPriority(e.target.value)}
              options={[
                { value: 'LOW', label: 'Low' },
                { value: 'MEDIUM', label: 'Medium' },
                { value: 'HIGH', label: 'High' },
                { value: 'CRITICAL', label: 'Critical' },
              ]}
            />
            <div className="flex justify-end gap-2 pt-2">
              <Button variant="outline" type="button" onClick={() => setShowAddTaskModal(false)}>
                Cancel
              </Button>
              <Button variant="primary" type="submit" disabled={submittingModal}>
                {submittingModal ? 'Adding...' : 'Add Task'}
              </Button>
            </div>
          </form>
        </Modal>

        {/* Quick Add Client Modal */}
        <Modal
          isOpen={showAddClientModal}
          onClose={() => setShowAddClientModal(false)}
          title="Add New Client"
        >
          <form onSubmit={handleQuickAddClient} className="space-y-4">
            {modalError && <Alert type="error">{modalError}</Alert>}
            <Input
              label="Client Contact Name"
              required
              value={clientName}
              onChange={(e) => setClientName(e.target.value)}
              placeholder="e.g. Sarah Connor"
            />
            <Input
              label="Company Name"
              value={clientCompany}
              onChange={(e) => setClientCompany(e.target.value)}
              placeholder="e.g. Cyberdyne Systems"
            />
            <div className="grid grid-cols-2 gap-3">
              <Input
                label="Email"
                type="email"
                value={clientEmail}
                onChange={(e) => setClientEmail(e.target.value)}
                placeholder="sarah@example.com"
              />
              <Input
                label="Phone"
                value={clientPhone}
                onChange={(e) => setClientPhone(e.target.value)}
                placeholder="+1-555-0199"
              />
            </div>
            <div className="flex justify-end gap-2 pt-2">
              <Button variant="outline" type="button" onClick={() => setShowAddClientModal(false)}>
                Cancel
              </Button>
              <Button variant="primary" type="submit" disabled={submittingModal}>
                {submittingModal ? 'Saving...' : 'Add Client'}
              </Button>
            </div>
          </form>
        </Modal>
      </>
    );
  }

  return (
    <div className="space-y-8 animate-fadeIn pb-12">
      {/* Top Welcome Bar & Operational Quick Actions */}
      <div className="bg-gradient-to-r from-slate-900 via-indigo-950 to-slate-900 text-white rounded-2xl p-6 shadow-xl border border-indigo-900/40 flex flex-wrap items-center justify-between gap-4">
        <div>
          <div className="flex items-center space-x-2.5">
            <h2 className="text-2xl font-black tracking-tight">{currentOrg?.name || 'Project Operations'}</h2>
            {currentOrg?.orgCode && (
              <span className="px-2.5 py-0.5 rounded-full text-xs font-bold uppercase tracking-wider bg-blue-500/20 text-blue-300 border border-blue-500/40 font-mono">
                {currentOrg.orgCode}
              </span>
            )}
          </div>
          <p className="text-xs text-indigo-200/80 mt-1 max-w-xl">
            Deterministic CPM schedule tracking, automated conflict leveling, multi-objective optimization, and grounded AI operations.
          </p>
        </div>

        <div className="flex flex-wrap items-center gap-2.5">
          {isManagerOrAdmin && (
            <>
              <button
                onClick={() => setShowCreateProjectModal(true)}
                className="flex items-center px-3.5 py-2 bg-blue-600 hover:bg-blue-500 text-white rounded-xl text-xs font-bold transition shadow-sm"
              >
                <Plus className="w-3.5 h-3.5 mr-1" />
                Create Project
              </button>
              <button
                onClick={() => setShowAddTaskModal(true)}
                className="flex items-center px-3.5 py-2 bg-white/10 hover:bg-white/20 text-white rounded-xl text-xs font-bold transition border border-white/10"
              >
                <Plus className="w-3.5 h-3.5 mr-1 text-blue-300" />
                Add Task
              </button>
            </>
          )}

          <Link
            to="/schedule?tab=optimization"
            className="flex items-center px-3.5 py-2 bg-indigo-600/80 hover:bg-indigo-500 text-white rounded-xl text-xs font-bold transition shadow-sm"
          >
            <Sparkles className="w-3.5 h-3.5 mr-1" />
            Optimizer
          </Link>
          <Link
            to="/schedule?tab=assistant"
            className="flex items-center px-3.5 py-2 bg-white/10 hover:bg-white/20 text-white rounded-xl text-xs font-bold transition border border-white/10"
          >
            <Bot className="w-3.5 h-3.5 mr-1 text-indigo-300" />
            AI Assistant
          </Link>
        </div>
      </div>

      {/* Primary 4 KPI Grid */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-5">
        {statCards.map((stat, idx) => (
          <Link
            key={idx}
            to={stat.link}
            className="rounded-2xl border p-5 bg-white shadow-2xs hover:shadow-md transition flex items-center justify-between group cursor-pointer"
          >
            <div>
              <p className="text-xs font-bold text-gray-500 uppercase tracking-wide group-hover:text-blue-600 transition">
                {stat.label}
              </p>
              <p className="text-3xl font-black text-gray-900 mt-1">{stat.value}</p>
              <p className="text-[11px] text-gray-400 mt-1 font-medium">{stat.sub}</p>
            </div>
            <div className={`w-12 h-12 rounded-xl flex items-center justify-center border ${stat.bg}`}>
              <stat.icon className={`w-6 h-6 ${stat.color}`} />
            </div>
          </Link>
        ))}
      </div>

      {/* Secondary Capacity Stats Bar */}
      <div className="bg-white rounded-xl border border-gray-200 p-4 shadow-2xs flex flex-wrap items-center justify-between text-xs text-gray-600 gap-4">
        <div className="flex items-center gap-6">
          <div className="flex items-center gap-2">
            <Briefcase className="w-4 h-4 text-indigo-600" />
            <span><strong>{stats?.totalWorkers ?? 0}</strong> active workforce members</span>
          </div>
          <span>•</span>
          <div className="flex items-center gap-2">
            <Users className="w-4 h-4 text-blue-600" />
            <span><strong>{stats?.totalVendors ?? 0}</strong> registered suppliers</span>
          </div>
          <span>•</span>
          <div className="flex items-center gap-2">
            <CheckCircle2 className="w-4 h-4 text-emerald-600" />
            <span><strong>{stats?.completedProjects ?? 0}</strong> completed deliverables</span>
          </div>
        </div>

        <button
          onClick={() => loadDashboard(currentOrg?.id)}
          className="text-xs text-gray-500 hover:text-blue-600 font-medium flex items-center gap-1 ml-auto"
        >
          <RefreshCw className="w-3.5 h-3.5" />
          Refresh Metrics
        </button>
      </div>

      {/* Main Grid: Projects Requiring Attention & Progress Overview */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Left 2 Cols: Projects Requiring Attention & Active Projects */}
        <div className="lg:col-span-2 space-y-6">
          {/* Section A: Projects Requiring Attention */}
          <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-6 space-y-4">
            <div className="flex items-center justify-between pb-3 border-b border-gray-100">
              <div>
                <h2 className="text-base font-bold text-gray-900 flex items-center">
                  <AlertCircle className="w-4 h-4 mr-2 text-rose-600" />
                  Projects Requiring Attention
                </h2>
                <p className="text-xs text-gray-500 mt-0.5">
                  Critical deadline variances, downstream delay ripples, or detected resource overlaps.
                </p>
              </div>
              <Link to="/schedule" className="text-xs font-bold text-blue-600 hover:text-blue-800 flex items-center">
                <span>Schedule Analysis</span>
                <ArrowRight className="w-3.5 h-3.5 ml-1" />
              </Link>
            </div>

            <div className="space-y-3">
              {projectsRequiringAttentionList.length > 0 ? (
                projectsRequiringAttentionList.map((p) => (
                  <div
                    key={p.id}
                    className="p-4 rounded-xl border border-rose-200 bg-rose-50/40 hover:bg-rose-50/80 transition flex flex-wrap items-center justify-between gap-4"
                  >
                    <div className="space-y-1.5 max-w-md">
                      <div className="flex items-center space-x-2">
                        <Link
                          to="/schedule"
                          className="text-sm font-bold text-gray-900 hover:text-rose-700 transition"
                        >
                          {p.name}
                        </Link>
                        {p.isDelayed && (
                          <Badge variant="danger" dot size="sm">
                            Forecast Delayed
                          </Badge>
                        )}
                        {p.hasResourceConflict && (
                          <Badge variant="warning" dot size="sm">
                            Resource Overlap
                          </Badge>
                        )}
                      </div>
                      <p className="text-xs text-rose-900 font-medium leading-relaxed">
                        {p.riskReason || (p.isDelayed ? 'Forecast delayed beyond target deadline (calculated CPM)' : 'Detected resource contention')}
                      </p>
                      <p className="text-[11px] text-gray-500">Client: {p.clientName}</p>
                    </div>

                    <div className="flex items-center space-x-6 text-xs">
                      <div>
                        <span className="text-gray-400 block text-[10px] uppercase font-bold">Target Finish</span>
                        <span className="font-semibold text-gray-800">{p.deadline || 'None'}</span>
                      </div>
                      <div>
                        <span className="text-gray-400 block text-[10px] uppercase font-bold">Calculated CPM</span>
                        <span className={`font-bold ${p.isDelayed ? 'text-rose-600' : 'text-gray-800'}`}>
                          {p.expectedCompletionDate || 'Pending'}
                        </span>
                      </div>
                      <Link
                        to={p.hasResourceConflict ? '/schedule?tab=conflicts' : '/schedule?tab=optimization'}
                        className="px-3 py-1.5 rounded-lg bg-white border border-rose-300 text-rose-700 hover:bg-rose-100 font-bold text-xs shadow-2xs transition"
                      >
                        {p.hasResourceConflict ? 'Resolve Overlap' : 'Resolve Delay'}
                      </Link>
                    </div>
                  </div>
                ))
              ) : (
                <div className="p-6 rounded-xl border border-emerald-100 bg-emerald-50/50 flex items-center gap-3 text-xs text-emerald-800">
                  <CheckCircle2 className="w-5 h-5 text-emerald-600 flex-shrink-0" />
                  <div>
                    <span className="font-bold block">All projects tracking to schedule</span>
                    <span className="text-emerald-700/80">
                      Zero critical-path delays and zero unresolved resource overlaps in this workspace.
                    </span>
                  </div>
                </div>
              )}
            </div>
          </div>

          {/* Section D: Active Project Progress Overview */}
          <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-6 space-y-4">
            <div className="flex items-center justify-between pb-3 border-b border-gray-100">
              <div>
                <h2 className="text-base font-bold text-gray-900 flex items-center">
                  <FolderKanban className="w-4 h-4 mr-2 text-blue-600" />
                  Project Progress Overview
                </h2>
                <p className="text-xs text-gray-500 mt-0.5">
                  Precedence-governed milestone progress and delivery timelines.
                </p>
              </div>
              <Link to="/projects" className="text-xs font-bold text-blue-600 hover:text-blue-800 flex items-center">
                <span>View All Projects</span>
                <ArrowRight className="w-3.5 h-3.5 ml-1" />
              </Link>
            </div>

            <div className="space-y-3">
              {(stats?.recentProjects || []).length > 0 ? (
                (stats?.recentProjects || []).map((p) => (
                  <div
                    key={p.id}
                    className="p-4 rounded-xl border border-gray-200 hover:border-blue-300 transition bg-gray-50/40 flex flex-wrap items-center justify-between gap-4"
                  >
                    <div className="space-y-1 min-w-[200px]">
                      <div className="flex items-center space-x-2">
                        <Link
                          to="/schedule"
                          className="text-sm font-bold text-gray-900 hover:text-blue-600 transition"
                        >
                          {p.name}
                        </Link>
                        <Badge
                          variant={p.status === 'COMPLETED' ? 'success' : p.isDelayed ? 'danger' : 'info'}
                          size="sm"
                        >
                          {p.status}
                        </Badge>
                      </div>
                      <p className="text-xs text-gray-500">Client: {p.clientName}</p>
                    </div>

                    <div className="flex items-center space-x-6 text-xs flex-1 justify-end">
                      <div className="w-36">
                        <div className="flex justify-between text-[10px] font-bold text-gray-500 mb-1">
                          <span>Progress</span>
                          <span>{p.progress}%</span>
                        </div>
                        <div className="w-full bg-gray-200 h-2 rounded-full overflow-hidden">
                          <div
                            className={`h-full rounded-full transition-all duration-300 ${
                              p.status === 'COMPLETED'
                                ? 'bg-emerald-600'
                                : p.isDelayed
                                ? 'bg-amber-500'
                                : 'bg-blue-600'
                            }`}
                            style={{ width: `${p.progress}%` }}
                          />
                        </div>
                      </div>

                      <div className="text-right min-w-[90px]">
                        <span className="text-gray-400 block text-[10px] uppercase font-bold">Target Finish</span>
                        <span className="font-semibold text-gray-800">{p.deadline || 'None'}</span>
                      </div>

                      <Link
                        to="/schedule"
                        className="px-3 py-1.5 rounded-lg bg-white border border-gray-200 text-gray-700 hover:bg-blue-50 hover:text-blue-700 hover:border-blue-300 font-bold text-xs shadow-2xs transition"
                      >
                        Gantt
                      </Link>
                    </div>
                  </div>
                ))
              ) : (
                <div className="p-8 text-center text-gray-400 text-xs italic">
                  No registered projects found.
                </div>
              )}
            </div>
          </div>
        </div>

        {/* Right 1 Col: Team Workload & Recent Activity */}
        <div className="space-y-6">
          {/* Section E: Resource Allocation & Capacity */}
          <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-6 space-y-4">
            <div className="flex items-center justify-between pb-3 border-b border-gray-100">
              <div>
                <h2 className="text-base font-bold text-gray-900 flex items-center">
                  <Users className="w-4 h-4 mr-2 text-indigo-600" />
                  Team Workload
                </h2>
                <p className="text-xs text-gray-500 mt-0.5">Current concurrent assignments.</p>
              </div>
              <Link to="/workers" className="text-xs font-bold text-blue-600 hover:text-blue-800">
                Roster
              </Link>
            </div>

            <div className="space-y-2.5">
              {(stats?.resourceWorkload || []).length > 0 ? (
                (stats?.resourceWorkload || []).map((w) => {
                  const isOverallocated = w.workloadStatus === 'OVERALLOCATED' || w.hasConflict;
                  const isActive = w.workloadStatus === 'ACTIVE' || (w.activeAssignedTasks > 0 && !isOverallocated);
                  const isUnavailable = w.workloadStatus === 'UNAVAILABLE' || w.rosterStatus === 'UNAVAILABLE';

                  return (
                    <div
                      key={w.workerId}
                      className={`p-3 rounded-xl border text-xs transition ${
                        isOverallocated
                          ? 'bg-rose-50/50 border-rose-200 hover:border-rose-300'
                          : 'bg-gray-50 border-gray-200 hover:border-gray-300'
                      }`}
                    >
                      <div className="flex items-center justify-between">
                        <div>
                          <div className="font-bold text-gray-900 flex items-center gap-1.5">
                            <span>{w.name}</span>
                            {isOverallocated && (
                              <span className="w-2 h-2 rounded-full bg-rose-500 animate-pulse" title="Schedule Conflict: Concurrent Overlapping Tasks" />
                            )}
                          </div>
                          <div className="text-[11px] text-gray-400 truncate max-w-[140px]">
                            {w.skills || 'General Engineering'}
                          </div>
                        </div>

                        <div className="text-right">
                          <span
                            className={`px-2 py-0.5 rounded-full font-bold text-[10px] ${
                              isOverallocated
                                ? 'bg-rose-100 text-rose-700 border border-rose-200'
                                : isActive
                                ? 'bg-blue-100 text-blue-700 border border-blue-200'
                                : isUnavailable
                                ? 'bg-gray-200 text-gray-700 border border-gray-300'
                                : 'bg-emerald-100 text-emerald-700 border border-emerald-200'
                            }`}
                          >
                            {w.workloadSummary || `${w.activeAssignedTasks} task${w.activeAssignedTasks === 1 ? '' : 's'}`}
                          </span>
                          <span className="block text-[10px] text-gray-500 mt-0.5 font-medium">
                            Roster: <span className={isUnavailable ? 'text-rose-600 font-semibold' : 'text-gray-700 font-semibold'}>{w.rosterStatus || w.status}</span>
                          </span>
                        </div>
                      </div>
                    </div>
                  );
                })
              ) : (
                <div className="p-6 text-center text-gray-400 text-xs italic">
                  No workers configured in this workspace.
                </div>
              )}
            </div>
            <div className="pt-2 border-t border-gray-100 text-[10px] text-gray-400 leading-tight">
              <span className="font-semibold text-gray-500">Availability Guide:</span> Roster status reflects administrative deployment availability; Workload status reflects active concurrent task allocations and schedule overlap detection.
            </div>
          </div>

          {/* Section C: Recent Operational Activity */}
          <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-6 space-y-4">
            <div className="flex items-center justify-between pb-3 border-b border-gray-100">
              <div>
                <h2 className="text-base font-bold text-gray-900 flex items-center">
                  <Activity className="w-4 h-4 mr-2 text-blue-600" />
                  Recent Activity
                </h2>
                <p className="text-xs text-gray-500 mt-0.5">Immutable audit events & approvals.</p>
              </div>
            </div>

            <div className="space-y-3">
              {(stats?.recentActivities || []).length > 0 ? (
                (stats?.recentActivities || []).map((act) => (
                  <div
                    key={act.id}
                    className="p-3 bg-gray-50 rounded-xl border border-gray-200 text-xs space-y-1"
                  >
                    <div className="flex items-center justify-between text-[11px]">
                      <span className="font-bold text-gray-800 flex items-center gap-1">
                        <span className="w-1.5 h-1.5 rounded-full bg-blue-600 inline-block" />
                        {formatAction(act.action)}
                      </span>
                      <span className="text-[10px] text-gray-400">{formatTime(act.timestamp)}</span>
                    </div>
                    <p className="text-gray-600 text-[11px] line-clamp-2">{act.details || act.projectName}</p>
                    <div className="text-[10px] text-gray-400">By {act.performedBy || 'System'}</div>
                  </div>
                ))
              ) : (
                <div className="p-6 text-center text-gray-400 text-xs italic">
                  No recent audit activities recorded.
                </div>
              )}
            </div>
          </div>
        </div>
      </div>

      {/* Section B: Upcoming Deadlines & Target Milestones */}
      <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-6 space-y-4">
        <div className="flex items-center justify-between pb-3 border-b border-gray-100">
          <div>
            <h2 className="text-base font-bold text-gray-900 flex items-center">
              <Calendar className="w-4 h-4 mr-2 text-blue-600" />
              Upcoming Critical Tasks & Target Milestones
            </h2>
            <p className="text-xs text-gray-500 mt-0.5">
              Scheduled milestone deliveries ordered by planned completion date. Required deadlines are explicitly distinct from calculated CPM dates.
            </p>
          </div>
          <Link to="/tasks" className="text-xs font-bold text-blue-600 hover:text-blue-800 flex items-center">
            <span>View All Tasks</span>
            <ArrowRight className="w-3.5 h-3.5 ml-1" />
          </Link>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full text-left text-xs">
            <thead className="bg-gray-50 text-gray-500 uppercase font-bold text-[10px]">
              <tr>
                <th className="py-3 px-4">Task Name</th>
                <th className="py-3 px-4">Project</th>
                <th className="py-3 px-4">Priority</th>
                <th className="py-3 px-4">Status</th>
                <th className="py-3 px-4">Target Deadline (Required)</th>
                <th className="py-3 px-4">Planned Finish (CPM)</th>
                <th className="py-3 px-4 text-right">Action</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100 text-gray-700">
              {(stats?.upcomingMilestones || []).length > 0 ? (
                (stats?.upcomingMilestones || []).map((m) => (
                  <tr key={m.id} className="hover:bg-gray-50/80 transition">
                    <td className="py-3 px-4 font-bold text-gray-900">{m.taskName}</td>
                    <td className="py-3 px-4 text-gray-600">{m.projectName}</td>
                    <td className="py-3 px-4">
                      <Badge
                        variant={
                          m.priority === 'HIGH' || m.priority === 'CRITICAL'
                            ? 'danger'
                            : m.priority === 'MEDIUM'
                            ? 'warning'
                            : 'info'
                        }
                        size="sm"
                      >
                        {m.priority}
                      </Badge>
                    </td>
                    <td className="py-3 px-4">
                      <span className="font-semibold text-gray-700">{m.status}</span>
                    </td>
                    <td className="py-3 px-4 font-semibold">
                      {m.deadline ? (
                        <span className="text-gray-800">{m.deadline}</span>
                      ) : (
                        <span className="text-gray-400 italic">Not set</span>
                      )}
                    </td>
                    <td className="py-3 px-4 font-bold">
                      {m.plannedFinishDate ? (
                        <div className="flex items-center gap-1.5">
                          <span className={m.deadlineExceeded ? 'text-rose-600' : 'text-blue-700'}>
                            {m.plannedFinishDate}
                          </span>
                          {m.deadlineExceeded && (
                            <span className="text-[10px] font-bold px-1.5 py-0.5 rounded bg-rose-100 text-rose-700 border border-rose-200">
                              Slip (+{m.delayDays}d)
                            </span>
                          )}
                        </div>
                      ) : (
                        <span className="text-gray-400 italic">Pending Recalculation</span>
                      )}
                    </td>
                    <td className="py-3 px-4 text-right">
                      <Link
                        to="/tasks"
                        className="text-xs text-blue-600 hover:text-blue-800 font-semibold"
                      >
                        View
                      </Link>
                    </td>
                  </tr>
                ))
              ) : (
                <tr>
                  <td colSpan={7} className="py-8 text-center text-gray-400 italic">
                    No upcoming tasks found in this workspace.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </div>

      {/* Quick Modals */}
      {renderQuickModals()}
    </div>
  );
}
