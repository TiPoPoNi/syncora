import React, { useState, useEffect, useMemo } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../services/api';
import { useOrganization } from '../context/OrganizationContext';
import {
  Calendar,
  Plus,
  Search,
  Clock,
  User,
  CheckCircle2,
  AlertCircle,
  AlertTriangle,
  FolderKanban,
  Edit2,
  Trash2,
  BarChart3,
  GitBranch,
  X,
  ShieldAlert,
} from 'lucide-react';
import {
  Button,
  Card,
  Badge,
  BadgeVariant,
  Modal,
  ConfirmDialog,
  Input,
  Select,
  EmptyState,
  LoadingSpinner,
  Alert,
} from '../components/ui';

interface Task {
  id: number;
  projectId?: number;
  projectName?: string;
  name: string;
  description?: string;
  startDate?: string;
  deadline?: string;
  estimatedDuration?: number;
  plannedStartDate?: string;
  plannedFinishDate?: string;
  priority?: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
  status: 'TODO' | 'IN_PROGRESS' | 'BLOCKED' | 'COMPLETED';
  progress?: number;
  assignedWorkerId?: number;
  assignedWorkerName?: string;
  assignedWorkerSkills?: string;
  predecessorCount?: number;
  successorCount?: number;
  isBlocked?: boolean;
  blockedReason?: string;
}

interface Project {
  id: number;
  name: string;
}

interface Worker {
  id: number;
  name: string;
  email?: string;
  skills?: string;
  status?: string;
  activeTaskCount?: number;
}

interface DependencyItem {
  dependencyId: number;
  taskId: number;
  taskName: string;
  status: string;
  progress: number;
  estimatedDuration: number;
  plannedStartDate?: string;
  plannedFinishDate?: string;
  dependencyType?: string;
  blocking?: boolean;
}

interface GateItem {
  gateId: number;
  deliverableId: number;
  deliverableName: string;
  deliverableStatus: string;
  gateType: string;
  isMandatory: boolean;
  satisfied: boolean;
}

interface TaskDependencyDetails {
  taskId: number;
  taskName: string;
  projectId?: number;
  projectName?: string;
  status: string;
  progress: number;
  blocked: boolean;
  blockingReasons: string[];
  predecessors: DependencyItem[];
  successors: DependencyItem[];
  approvalGates: GateItem[];
}

export function Tasks() {
  const { currentOrg } = useOrganization();
  const [tasks, setTasks] = useState<Task[]>([]);
  const [projects, setProjects] = useState<Project[]>([]);
  const [workers, setWorkers] = useState<Worker[]>([]);
  const [loading, setLoading] = useState(true);

  // Filters
  const [searchQuery, setSearchQuery] = useState('');
  const [projectFilter, setProjectFilter] = useState<string>('ALL');
  const [statusFilter, setStatusFilter] = useState<string>('ALL');
  const [priorityFilter, setPriorityFilter] = useState<string>('ALL');
  const [alertMessage, setAlertMessage] = useState<{ type: 'success' | 'error'; text: string } | null>(null);

  // Modals
  const [isCreateModalOpen, setIsCreateModalOpen] = useState(false);
  const [isEditModalOpen, setIsEditModalOpen] = useState(false);
  const [isDeleteModalOpen, setIsDeleteModalOpen] = useState(false);
  const [isDependencyModalOpen, setIsDependencyModalOpen] = useState(false);
  const [selectedTask, setSelectedTask] = useState<Task | null>(null);
  const [dependencyDetails, setDependencyDetails] = useState<TaskDependencyDetails | null>(null);
  const [isLoadingDependencies, setIsLoadingDependencies] = useState(false);
  const [selectedPredecessorId, setSelectedPredecessorId] = useState<string>('');
  const [isAddingDependency, setIsAddingDependency] = useState(false);
  const [dependencyModalError, setDependencyModalError] = useState<string | null>(null);
  const [completingTaskId, setCompletingTaskId] = useState<number | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [validationError, setValidationError] = useState<string | null>(null);

  // Auto-dismiss transient notifications after 5 seconds while allowing manual dismissal
  useEffect(() => {
    if (alertMessage) {
      const timer = setTimeout(() => {
        setAlertMessage(null);
      }, 5000);
      return () => clearTimeout(timer);
    }
  }, [alertMessage]);

  useEffect(() => {
    if (dependencyModalError) {
      const timer = setTimeout(() => {
        setDependencyModalError(null);
      }, 6000);
      return () => clearTimeout(timer);
    }
  }, [dependencyModalError]);

  // Form state
  const [formData, setFormData] = useState({
    name: '',
    description: '',
    projectId: '',
    estimatedDuration: 3,
    priority: 'MEDIUM' as 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL',
    status: 'TODO' as 'TODO' | 'IN_PROGRESS' | 'BLOCKED' | 'COMPLETED',
    progress: 0,
    startDate: '',
    deadline: '',
    assignedWorkerId: '',
  });

  const loadData = async () => {
    try {
      setLoading(true);
      const [tasksRes, projRes, workersRes] = await Promise.all([
        api.get('/tasks'),
        api.get('/projects').catch(() => ({ data: [] })),
        api.get('/workers').catch(() => ({ data: [] })),
      ]);
      setTasks(tasksRes.data || []);
      setProjects(projRes.data || []);
      setWorkers(workersRes.data || []);
    } catch (err: any) {
      console.error('Failed to load tasks:', err);
      setAlertMessage({ type: 'error', text: 'Failed to load task ledger.' });
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadData();
  }, [currentOrg?.id]);

  const handleOpenCreate = () => {
    setValidationError(null);
    setFormData({
      name: '',
      description: '',
      projectId: projects.length > 0 ? String(projects[0].id) : '',
      estimatedDuration: 3,
      priority: 'MEDIUM',
      status: 'TODO',
      progress: 0,
      startDate: new Date().toISOString().split('T')[0],
      deadline: '',
      assignedWorkerId: '',
    });
    setIsCreateModalOpen(true);
  };

  const handleOpenEdit = (task: Task) => {
    setValidationError(null);
    setSelectedTask(task);
    setFormData({
      name: task.name,
      description: task.description || '',
      projectId: task.projectId ? String(task.projectId) : '',
      estimatedDuration: task.estimatedDuration || 1,
      priority: task.priority || 'MEDIUM',
      status: task.status,
      progress: task.progress || 0,
      startDate: task.startDate || '',
      deadline: task.deadline || '',
      assignedWorkerId: task.assignedWorkerId ? String(task.assignedWorkerId) : '',
    });
    setIsEditModalOpen(true);
  };

  const handleOpenDelete = (task: Task) => {
    setSelectedTask(task);
    setIsDeleteModalOpen(true);
  };

  const handleOpenDependencies = async (task: Task) => {
    setSelectedTask(task);
    setSelectedPredecessorId('');
    setDependencyModalError(null);
    setIsDependencyModalOpen(true);
    setIsLoadingDependencies(true);
    try {
      const res = await api.get(`/tasks/${task.id}/dependency-details`);
      setDependencyDetails(res.data);
    } catch (err: any) {
      console.error('Failed to load dependency details:', err);
      setAlertMessage({
        type: 'error',
        text: err.response?.data?.message || 'Failed to load dependency details.',
      });
    } finally {
      setIsLoadingDependencies(false);
    }
  };

  const handleAddDependency = async () => {
    if (!selectedTask || !selectedPredecessorId) return;
    setDependencyModalError(null);

    try {
      setIsAddingDependency(true);
      await api.post(`/tasks/${selectedTask.id}/dependencies`, {
        predecessorTaskId: Number(selectedPredecessorId),
      });
      setSelectedPredecessorId('');
      // Reload details and task list
      const res = await api.get(`/tasks/${selectedTask.id}/dependency-details`);
      setDependencyDetails(res.data);
      loadData();
      setAlertMessage({ type: 'success', text: 'Dependency added successfully.' });
    } catch (err: any) {
      const errorMsg =
        err.response?.data?.message ||
        (typeof err.response?.data === 'string' ? err.response?.data : null) ||
        'Adding this dependency would create an invalid or circular dependency.';
      setDependencyModalError(errorMsg);
      setAlertMessage({
        type: 'error',
        text: errorMsg,
      });
    } finally {
      setIsAddingDependency(false);
    }
  };

  const handleRemoveDependency = async (dependencyId: number) => {
    if (!selectedTask) return;

    try {
      await api.delete(`/dependencies/${dependencyId}`);
      // Reload details and task list
      const res = await api.get(`/tasks/${selectedTask.id}/dependency-details`);
      setDependencyDetails(res.data);
      loadData();
      setAlertMessage({ type: 'success', text: 'Dependency removed.' });
    } catch (err: any) {
      setAlertMessage({
        type: 'error',
        text: err.response?.data?.message || 'Failed to remove dependency.',
      });
    }
  };

  const validateDates = (start?: string, deadline?: string): string | null => {
    if (start && deadline && deadline < start) {
      return 'Task deadline cannot be before the start date.';
    }
    return null;
  };

  const handleCreateSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setValidationError(null);

    if (!formData.name.trim()) {
      setValidationError('Task name is required.');
      return;
    }
    if (!formData.projectId) {
      setValidationError('Project is required.');
      return;
    }
    if (formData.estimatedDuration < 1) {
      setValidationError('Estimated duration must be at least 1 working day.');
      return;
    }

    const dateErr = validateDates(formData.startDate, formData.deadline);
    if (dateErr) {
      setValidationError(dateErr);
      return;
    }

    try {
      setIsSubmitting(true);
      const payload = {
        name: formData.name.trim(),
        description: formData.description.trim() || undefined,
        projectId: Number(formData.projectId),
        estimatedDuration: Number(formData.estimatedDuration),
        priority: formData.priority,
        status: formData.status,
        progress: Number(formData.progress),
        startDate: formData.startDate || undefined,
        deadline: formData.deadline || undefined,
        assignedWorkerId: formData.assignedWorkerId ? Number(formData.assignedWorkerId) : undefined,
      };

      await api.post('/tasks', payload);
      setAlertMessage({ type: 'success', text: `Task "${formData.name}" created successfully.` });
      setIsCreateModalOpen(false);
      loadData();
    } catch (err: any) {
      setValidationError(err.response?.data?.message || err.response?.data || 'Failed to create task.');
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleEditSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedTask) return;
    setValidationError(null);

    if (!formData.name.trim()) {
      setValidationError('Task name is required.');
      return;
    }
    if (formData.estimatedDuration < 1) {
      setValidationError('Estimated duration must be at least 1 working day.');
      return;
    }

    const dateErr = validateDates(formData.startDate, formData.deadline);
    if (dateErr) {
      setValidationError(dateErr);
      return;
    }

    try {
      setIsSubmitting(true);
      const payload = {
        id: selectedTask.id,
        name: formData.name.trim(),
        description: formData.description.trim() || undefined,
        projectId: formData.projectId ? Number(formData.projectId) : undefined,
        estimatedDuration: Number(formData.estimatedDuration),
        priority: formData.priority,
        status: formData.status,
        progress: Number(formData.progress),
        startDate: formData.startDate || undefined,
        deadline: formData.deadline || undefined,
        // If unassigned was chosen explicitly, pass 0 so backend can remove assignment
        assignedWorkerId: formData.assignedWorkerId ? Number(formData.assignedWorkerId) : 0,
      };

      await api.put(`/tasks/${selectedTask.id}`, payload);
      setAlertMessage({ type: 'success', text: `Task "${formData.name}" updated successfully.` });
      setIsEditModalOpen(false);
      loadData();
    } catch (err: any) {
      setValidationError(err.response?.data?.message || err.response?.data || 'Failed to update task.');
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleDeleteConfirm = async () => {
    if (!selectedTask) return;

    try {
      setIsSubmitting(true);
      await api.delete(`/tasks/${selectedTask.id}`);
      setAlertMessage({ type: 'success', text: `Task "${selectedTask.name}" deleted.` });
      setIsDeleteModalOpen(false);
      loadData();
    } catch (err: any) {
      setAlertMessage({ type: 'error', text: err.response?.data?.message || 'Failed to delete task.' });
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleQuickProgressUpdate = async (task: Task, newProgress: number) => {
    if (completingTaskId) return; // Prevent duplicate submissions
    if (task.status === 'COMPLETED') return;
    if (task.isBlocked) {
      setAlertMessage({
        type: 'error',
        text: `Cannot complete "${task.name}": ${task.blockedReason || 'Prerequisite tasks or approval gates remain unsatisfied.'}`,
      });
      return;
    }

    try {
      setCompletingTaskId(task.id);
      const nextStatus = newProgress === 100 ? 'COMPLETED' : newProgress > 0 ? 'IN_PROGRESS' : 'TODO';
      await api.put(`/tasks/${task.id}`, {
        id: task.id,
        name: task.name,
        progress: newProgress,
        status: nextStatus,
      });
      setAlertMessage({ type: 'success', text: `Task "${task.name}" marked completed.` });
      await loadData();
    } catch (err: any) {
      setAlertMessage({
        type: 'error',
        text: err.response?.data?.message || err.response?.data || 'Failed to update task progress.',
      });
    } finally {
      setCompletingTaskId(null);
    }
  };

  const filteredTasks = useMemo(() => {
    return tasks.filter((t) => {
      const q = searchQuery.toLowerCase();
      const matchesSearch =
        t.name.toLowerCase().includes(q) ||
        (t.projectName && t.projectName.toLowerCase().includes(q)) ||
        (t.assignedWorkerName && t.assignedWorkerName.toLowerCase().includes(q)) ||
        (t.assignedWorkerSkills && t.assignedWorkerSkills.toLowerCase().includes(q));
      const matchesProject = projectFilter === 'ALL' || String(t.projectId) === projectFilter;
      const matchesStatus = statusFilter === 'ALL' || t.status === statusFilter;
      const matchesPriority = priorityFilter === 'ALL' || t.priority === priorityFilter;
      return matchesSearch && matchesProject && matchesStatus && matchesPriority;
    });
  }, [tasks, searchQuery, projectFilter, statusFilter, priorityFilter]);

  // Consistent KPI metrics across all active projects
  const stats = useMemo(() => {
    const total = tasks.length;
    const completed = tasks.filter((t) => t.status === 'COMPLETED').length;
    const inProgress = tasks.filter((t) => t.status === 'IN_PROGRESS').length;
    const blocked = tasks.filter((t) => t.status === 'BLOCKED' || t.isBlocked).length;
    return { total, completed, inProgress, blocked };
  }, [tasks]);

  const getStatusBadgeVariant = (status: string): BadgeVariant => {
    switch (status) {
      case 'COMPLETED':
        return 'success';
      case 'IN_PROGRESS':
        return 'info';
      case 'BLOCKED':
        return 'danger';
      case 'TODO':
      default:
        return 'neutral';
    }
  };

  const getStatusLabel = (status: string, progress?: number): string => {
    if (status === 'COMPLETED' || progress === 100) return 'Completed';
    if (status === 'IN_PROGRESS') return 'In Progress';
    if (status === 'BLOCKED') return 'Blocked';
    if (status === 'TODO') return 'To Do';
    return status.replace('_', ' ');
  };

  const getPriorityBadgeVariant = (priority?: string): BadgeVariant => {
    switch (priority) {
      case 'CRITICAL':
      case 'HIGH':
        return 'danger';
      case 'MEDIUM':
        return 'warning';
      case 'LOW':
      default:
        return 'neutral';
    }
  };

  // Other tasks in the same project eligible to be predecessors
  const availablePredecessors = useMemo(() => {
    if (!selectedTask) return [];
    const currentPredIds = new Set(dependencyDetails?.predecessors.map((p) => p.taskId) || []);
    return tasks.filter(
      (t) =>
        t.id !== selectedTask.id &&
        t.projectId === selectedTask.projectId &&
        !currentPredIds.has(t.id)
    );
  }, [tasks, selectedTask, dependencyDetails]);

  return (
    <div className="p-8 space-y-6 max-w-7xl mx-auto">
      {/* Top Banner */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-black text-gray-900 tracking-tight flex items-center gap-2.5">
            <Calendar className="w-7 h-7 text-blue-600" />
            Tasks Ledger
          </h1>
          <p className="text-sm text-gray-500 mt-1">
            Track individual work packages, duration estimates, progress gates, and assignments.
          </p>
        </div>
        <Button
          variant="primary"
          size="md"
          leftIcon={<Plus className="w-4 h-4" />}
          onClick={handleOpenCreate}
          title="Create New Task"
          aria-label="Create New Task"
        >
          New Task
        </Button>
      </div>

      {alertMessage && (
        <Alert
          type={alertMessage.type}
          onClose={() => setAlertMessage(null)}
          title={alertMessage.type === 'success' ? 'Success' : 'Notice'}
        >
          {alertMessage.text}
        </Alert>
      )}

      {/* KPI Stats Grid */}
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
        <Card className="p-5">
          <span className="text-xs font-bold text-gray-400 uppercase tracking-wider">Total Tasks</span>
          <div className="text-2xl font-black text-gray-900 mt-1">{stats.total}</div>
          <span className="text-xs text-gray-500 mt-0.5 block">Across all active projects</span>
        </Card>
        <Card className="p-5">
          <span className="text-xs font-bold text-blue-600 uppercase tracking-wider">In Progress</span>
          <div className="text-2xl font-black text-blue-600 mt-1">{stats.inProgress}</div>
          <span className="text-xs text-gray-500 mt-0.5 block">Currently being executed</span>
        </Card>
        <Card className="p-5">
          <span className="text-xs font-bold text-rose-600 uppercase tracking-wider">Blocked</span>
          <div className="text-2xl font-black text-rose-600 mt-1">{stats.blocked}</div>
          <span className="text-xs text-gray-500 mt-0.5 block">Awaiting dependency / approval</span>
        </Card>
        <Card className="p-5">
          <span className="text-xs font-bold text-emerald-600 uppercase tracking-wider">Completed</span>
          <div className="text-2xl font-black text-emerald-600 mt-1">{stats.completed}</div>
          <span className="text-xs text-gray-500 mt-0.5 block">100% finished</span>
        </Card>
      </div>

      {/* Filter and Search Bar */}
      <Card className="p-4">
        <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-4">
          <div className="flex flex-col sm:flex-row items-center gap-3 flex-1">
            <div className="relative w-full sm:w-64">
              <Input
                placeholder="Search by task or worker..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                leftIcon={<Search className="w-4 h-4" />}
                aria-label="Search by task name or worker"
              />
            </div>

            <div className="w-full sm:w-48">
              <Select
                value={projectFilter}
                onChange={(e) => setProjectFilter(e.target.value)}
                aria-label="Filter by project"
              >
                <option value="ALL">All Projects</option>
                {projects.map((p) => (
                  <option key={p.id} value={String(p.id)}>
                    {p.name}
                  </option>
                ))}
              </Select>
            </div>

            <div className="w-full sm:w-40">
              <Select
                value={priorityFilter}
                onChange={(e) => setPriorityFilter(e.target.value)}
                aria-label="Filter by priority"
              >
                <option value="ALL">All Priorities</option>
                <option value="LOW">Low</option>
                <option value="MEDIUM">Medium</option>
                <option value="HIGH">High</option>
                <option value="CRITICAL">Critical</option>
              </Select>
            </div>
          </div>

          {/* Status Filter Tabs */}
          <div className="flex items-center gap-1.5 overflow-x-auto pb-1 sm:pb-0">
            {['ALL', 'TODO', 'IN_PROGRESS', 'BLOCKED', 'COMPLETED'].map((st) => (
              <button
                key={st}
                onClick={() => setStatusFilter(st)}
                aria-label={`Filter by status ${st}`}
                className={`px-3 py-1.5 rounded-xl text-xs font-bold transition-all shrink-0 ${
                  statusFilter === st
                    ? 'bg-blue-600 text-white shadow-2xs'
                    : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
                }`}
              >
                {st === 'TODO' ? 'TO DO' : st.replace('_', ' ')}
              </button>
            ))}
          </div>
        </div>
      </Card>

      {/* Tasks Table */}
      {loading ? (
        <LoadingSpinner label="Loading task ledger..." size="lg" />
      ) : filteredTasks.length === 0 ? (
        <EmptyState
          icon={<Calendar className="w-10 h-10 text-gray-400" />}
          title={
            searchQuery || projectFilter !== 'ALL' || statusFilter !== 'ALL' || priorityFilter !== 'ALL'
              ? 'No matching tasks found'
              : 'No tasks created yet'
          }
          description={
            searchQuery || projectFilter !== 'ALL' || statusFilter !== 'ALL' || priorityFilter !== 'ALL'
              ? 'Try adjusting your search query, priority, or status filters.'
              : 'Add your first task and assign duration and dependencies to generate CPM schedules.'
          }
          actionLabel={
            searchQuery || projectFilter !== 'ALL' || statusFilter !== 'ALL' || priorityFilter !== 'ALL'
              ? 'Clear Filters'
              : 'Add First Task'
          }
          onAction={() => {
            if (searchQuery || projectFilter !== 'ALL' || statusFilter !== 'ALL' || priorityFilter !== 'ALL') {
              setSearchQuery('');
              setProjectFilter('ALL');
              setStatusFilter('ALL');
              setPriorityFilter('ALL');
            } else {
              handleOpenCreate();
            }
          }}
        />
      ) : (
        <Card className="overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm text-gray-600">
              <thead className="bg-gray-50/80 text-xs font-bold text-gray-500 uppercase border-b border-gray-200">
                <tr>
                  <th className="px-6 py-4">Task Name & Project</th>
                  <th className="px-6 py-4">Status & Priority</th>
                  <th className="px-6 py-4">Assigned Worker</th>
                  <th className="px-6 py-4">Duration & Dates</th>
                  <th className="px-6 py-4">Dependencies</th>
                  <th className="px-6 py-4">Progress</th>
                  <th className="px-6 py-4 text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {filteredTasks.map((task) => (
                  <tr key={task.id} className="hover:bg-gray-50/80 transition-colors">
                    {/* Task Name & Project */}
                    <td className="px-6 py-4">
                      <div className="space-y-1">
                        <div className="font-bold text-gray-900">{task.name}</div>
                        {task.projectName && (
                          <div className="text-xs text-gray-500 flex items-center gap-1.5">
                            <FolderKanban className="w-3.5 h-3.5 text-gray-400" />
                            <span>{task.projectName}</span>
                          </div>
                        )}
                      </div>
                    </td>

                    {/* Status & Priority */}
                    <td className="px-6 py-4">
                      <div className="flex flex-wrap items-center gap-1.5">
                        <Badge variant={getStatusBadgeVariant(task.status)} size="sm" dot>
                          {getStatusLabel(task.status, task.progress)}
                        </Badge>
                        {task.priority && (
                          <Badge variant={getPriorityBadgeVariant(task.priority)} size="sm">
                            {task.priority}
                          </Badge>
                        )}
                        {task.isBlocked && (
                          <span
                            title={task.blockedReason || 'Blocked by predecessor or gate'}
                            className="inline-flex items-center gap-1 text-[10px] font-bold text-amber-700 bg-amber-50 px-1.5 py-0.5 rounded border border-amber-200"
                          >
                            <AlertTriangle className="w-3 h-3 text-amber-600" />
                            Blocked
                          </span>
                        )}
                      </div>
                    </td>

                    {/* Assigned Worker */}
                    <td className="px-6 py-4 text-xs">
                      {task.assignedWorkerName ? (
                        <div className="space-y-0.5">
                          <div className="flex items-center gap-1.5 font-semibold text-gray-800">
                            <User className="w-3.5 h-3.5 text-blue-500 shrink-0" />
                            <span>{task.assignedWorkerName}</span>
                          </div>
                          {task.assignedWorkerSkills && (
                            <div
                              className="text-[11px] text-gray-500 truncate max-w-[140px]"
                              title={`Skills: ${task.assignedWorkerSkills}`}
                            >
                              {task.assignedWorkerSkills}
                            </div>
                          )}
                        </div>
                      ) : (
                        <span className="text-gray-400 italic">Unassigned</span>
                      )}
                    </td>

                    {/* Duration & Dates */}
                    <td className="px-6 py-4 text-xs">
                      <div className="space-y-1">
                        <div className="flex items-center gap-1.5 text-gray-700 font-semibold">
                          <Clock className="w-3.5 h-3.5 text-gray-400 shrink-0" />
                          <span>{task.estimatedDuration || 1} working days</span>
                        </div>
                        {task.plannedStartDate && task.plannedFinishDate ? (
                          <div className="text-gray-600 text-[11px]">
                            <span className="font-medium text-gray-700">Planned: </span>
                            {task.plannedStartDate} → {task.plannedFinishDate}
                          </div>
                        ) : null}
                        {task.deadline ? (
                          <div className="text-[11px] text-amber-700 font-medium">
                            <span>Deadline: </span>
                            {task.deadline}
                          </div>
                        ) : null}
                        {!task.plannedStartDate && !task.deadline && (
                          <div className="text-gray-400 italic text-[11px]">Not scheduled</div>
                        )}
                      </div>
                    </td>

                    {/* Dependencies indicator */}
                    <td className="px-6 py-4 text-xs">
                      <button
                        onClick={(e) => {
                          e.stopPropagation();
                          handleOpenDependencies(task);
                        }}
                        title={`Inspect dependencies for ${task.name}: ${task.predecessorCount || 0} predecessors, ${task.successorCount || 0} successors${task.isBlocked ? ' (Blocked)' : ''}`}
                        aria-label={`Inspect dependencies for ${task.name}: ${task.predecessorCount || 0} predecessors, ${task.successorCount || 0} successors`}
                        className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-lg bg-gray-50 hover:bg-blue-50 text-gray-700 hover:text-blue-700 border border-gray-200 transition text-[11px] font-medium"
                      >
                        <GitBranch className="w-3.5 h-3.5 text-blue-500" />
                        <span>
                          {task.predecessorCount || 0} Pred · {task.successorCount || 0} Succ
                        </span>
                      </button>
                    </td>

                    {/* Progress */}
                    <td className="px-6 py-4">
                      <div className="w-36 space-y-1.5">
                        <div className="flex items-center justify-between text-xs font-bold text-gray-700">
                          <div className="flex items-center gap-1">
                            <span>{task.progress || 0}%</span>
                            {task.status === 'COMPLETED' && (
                              <span title="Task is 100% completed">
                                <CheckCircle2 className="w-3.5 h-3.5 text-emerald-600 inline" />
                              </span>
                            )}
                          </div>
                          {task.status === 'COMPLETED' ? (
                            <span className="text-[11px] text-emerald-600 font-bold">Done</span>
                          ) : task.isBlocked ? (
                            <button
                              disabled
                              title={`Cannot complete: ${task.blockedReason || 'Prerequisites or approval gates not satisfied'}`}
                              aria-label={`Task is blocked: ${task.blockedReason || 'Prerequisites not satisfied'}`}
                              className="text-[11px] text-gray-400 font-semibold cursor-not-allowed"
                            >
                              Blocked
                            </button>
                          ) : (
                            <button
                              onClick={(e) => {
                                e.stopPropagation();
                                handleQuickProgressUpdate(task, 100);
                              }}
                              disabled={completingTaskId === task.id}
                              title="Mark task 100% completed"
                              aria-label={`Mark task "${task.name}" completed`}
                              className="text-[11px] text-blue-600 hover:text-blue-800 font-semibold hover:underline disabled:opacity-50"
                            >
                              {completingTaskId === task.id ? 'Saving...' : 'Complete'}
                            </button>
                          )}
                        </div>
                        <div className="w-full bg-gray-200 h-2 rounded-full overflow-hidden">
                          <div
                            className={`h-full rounded-full transition-all duration-300 ${
                              task.status === 'COMPLETED'
                                ? 'bg-emerald-500'
                                : task.status === 'BLOCKED'
                                ? 'bg-rose-500'
                                : (task.progress || 0) > 0
                                ? 'bg-blue-600'
                                : 'bg-gray-300'
                            }`}
                            style={{ width: `${task.progress || 0}%` }}
                          />
                        </div>
                      </div>
                    </td>

                    {/* Row Actions */}
                    <td className="px-6 py-4 text-right">
                      <div className="flex items-center justify-end gap-1.5">
                        <Link
                          to="/schedule"
                          title="View on Schedule Timeline"
                          aria-label="View on Schedule Timeline"
                          onClick={(e) => e.stopPropagation()}
                          className="p-1.5 text-gray-500 hover:text-blue-600 hover:bg-blue-50 rounded-lg transition"
                        >
                          <BarChart3 className="w-4 h-4" />
                        </Link>
                        <button
                          onClick={(e) => {
                            e.stopPropagation();
                            handleOpenDependencies(task);
                          }}
                          title="Inspect Dependencies & Gates"
                          aria-label="Inspect Dependencies & Gates"
                          className="p-1.5 text-gray-500 hover:text-indigo-600 hover:bg-indigo-50 rounded-lg transition"
                        >
                          <GitBranch className="w-4 h-4" />
                        </button>
                        <button
                          onClick={(e) => {
                            e.stopPropagation();
                            handleOpenEdit(task);
                          }}
                          title="Edit Task"
                          aria-label="Edit Task"
                          className="p-1.5 text-gray-500 hover:text-blue-600 hover:bg-gray-100 rounded-lg transition"
                        >
                          <Edit2 className="w-4 h-4" />
                        </button>
                        <button
                          onClick={(e) => {
                            e.stopPropagation();
                            handleOpenDelete(task);
                          }}
                          title="Delete Task"
                          aria-label="Delete Task"
                          className="p-1.5 text-gray-500 hover:text-rose-600 hover:bg-rose-50 rounded-lg transition"
                        >
                          <Trash2 className="w-4 h-4" />
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </Card>
      )}

      {/* Create Task Modal */}
      <Modal
        isOpen={isCreateModalOpen}
        onClose={() => setIsCreateModalOpen(false)}
        title="Create New Task"
        description="Add a work package to a project for duration calculation and scheduling."
        maxWidth="lg"
      >
        <form onSubmit={handleCreateSubmit} className="space-y-4">
          {validationError && (
            <Alert type="error" onClose={() => setValidationError(null)}>
              {validationError}
            </Alert>
          )}

          <Input
            label="Task Name *"
            placeholder="e.g. Design Database Schema"
            value={formData.name}
            onChange={(e) => setFormData({ ...formData, name: e.target.value })}
            required
            aria-label="Task Name"
          />

          <div>
            <label className="block text-xs font-bold text-gray-700 tracking-tight mb-1.5">
              Task Description
            </label>
            <textarea
              className="w-full px-3.5 py-2 text-sm bg-white border border-gray-300 rounded-xl text-gray-900 placeholder:text-gray-400 focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500"
              rows={2}
              placeholder="Detailed acceptance criteria or technical tasks..."
              value={formData.description}
              onChange={(e) => setFormData({ ...formData, description: e.target.value })}
              aria-label="Task Description"
            />
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <Select
              label="Project *"
              value={formData.projectId}
              onChange={(e) => setFormData({ ...formData, projectId: e.target.value })}
              required
              aria-label="Project"
            >
              <option value="">Select Project</option>
              {projects.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.name}
                </option>
              ))}
            </Select>

            <Select
              label="Assigned Worker"
              value={formData.assignedWorkerId}
              onChange={(e) => setFormData({ ...formData, assignedWorkerId: e.target.value })}
              aria-label="Assigned Worker"
            >
              <option value="">Unassigned</option>
              {workers.map((w) => (
                <option key={w.id} value={w.id}>
                  {w.name} — {w.skills || 'General'} ({w.status || 'AVAILABLE'}, {w.activeTaskCount || 0} active)
                </option>
              ))}
            </Select>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
            <Input
              type="number"
              min="1"
              max="365"
              label="Estimated Duration (Days) *"
              value={formData.estimatedDuration}
              onChange={(e) => setFormData({ ...formData, estimatedDuration: Number(e.target.value) })}
              required
              aria-label="Estimated Duration"
            />

            <Select
              label="Priority"
              value={formData.priority}
              onChange={(e) => setFormData({ ...formData, priority: e.target.value as any })}
              aria-label="Priority"
            >
              <option value="LOW">Low</option>
              <option value="MEDIUM">Medium</option>
              <option value="HIGH">High</option>
              <option value="CRITICAL">Critical</option>
            </Select>

            <Select
              label="Status"
              value={formData.status}
              onChange={(e) => {
                const newStatus = e.target.value as any;
                setFormData({
                  ...formData,
                  status: newStatus,
                  progress: newStatus === 'COMPLETED' ? 100 : newStatus === 'TODO' ? 0 : formData.progress,
                });
              }}
              aria-label="Status"
            >
              <option value="TODO">To Do</option>
              <option value="IN_PROGRESS">In Progress</option>
              <option value="BLOCKED">Blocked</option>
              <option value="COMPLETED">Completed</option>
            </Select>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <Input
              type="date"
              label="Earliest Start Date"
              value={formData.startDate}
              onChange={(e) => setFormData({ ...formData, startDate: e.target.value })}
              aria-label="Earliest Start Date"
            />

            <Input
              type="date"
              label="Task Deadline"
              value={formData.deadline}
              onChange={(e) => setFormData({ ...formData, deadline: e.target.value })}
              aria-label="Task Deadline"
            />
          </div>

          <div className="flex items-center justify-end gap-3 pt-4 border-t border-gray-100">
            <Button
              variant="outline"
              size="md"
              onClick={() => setIsCreateModalOpen(false)}
              disabled={isSubmitting}
            >
              Cancel
            </Button>
            <Button type="submit" variant="primary" size="md" isLoading={isSubmitting}>
              Create Task
            </Button>
          </div>
        </form>
      </Modal>

      {/* Edit Task Modal */}
      <Modal
        isOpen={isEditModalOpen}
        onClose={() => setIsEditModalOpen(false)}
        title="Edit Task"
        description="Update task details, progress percentage, dates, or assigned engineer."
        maxWidth="lg"
      >
        <form onSubmit={handleEditSubmit} className="space-y-4">
          {validationError && (
            <Alert type="error" onClose={() => setValidationError(null)}>
              {validationError}
            </Alert>
          )}

          <Input
            label="Task Name *"
            value={formData.name}
            onChange={(e) => setFormData({ ...formData, name: e.target.value })}
            required
            aria-label="Task Name"
          />

          <div>
            <label className="block text-xs font-bold text-gray-700 tracking-tight mb-1.5">
              Task Description
            </label>
            <textarea
              className="w-full px-3.5 py-2 text-sm bg-white border border-gray-300 rounded-xl text-gray-900 placeholder:text-gray-400 focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500"
              rows={2}
              value={formData.description}
              onChange={(e) => setFormData({ ...formData, description: e.target.value })}
              aria-label="Task Description"
            />
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <Select
              label="Project *"
              value={formData.projectId}
              onChange={(e) => setFormData({ ...formData, projectId: e.target.value })}
              required
              aria-label="Project"
            >
              <option value="">Select Project</option>
              {projects.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.name}
                </option>
              ))}
            </Select>

            <Select
              label="Assigned Worker"
              value={formData.assignedWorkerId}
              onChange={(e) => setFormData({ ...formData, assignedWorkerId: e.target.value })}
              aria-label="Assigned Worker"
            >
              <option value="">Unassigned</option>
              {workers.map((w) => (
                <option key={w.id} value={w.id}>
                  {w.name} — {w.skills || 'General'} ({w.status || 'AVAILABLE'}, {w.activeTaskCount || 0} active)
                </option>
              ))}
            </Select>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
            <Input
              type="number"
              min="1"
              max="365"
              label="Estimated Duration (Days) *"
              value={formData.estimatedDuration}
              onChange={(e) => setFormData({ ...formData, estimatedDuration: Number(e.target.value) })}
              required
              aria-label="Estimated Duration"
            />

            <Select
              label="Priority"
              value={formData.priority}
              onChange={(e) => setFormData({ ...formData, priority: e.target.value as any })}
              aria-label="Priority"
            >
              <option value="LOW">Low</option>
              <option value="MEDIUM">Medium</option>
              <option value="HIGH">High</option>
              <option value="CRITICAL">Critical</option>
            </Select>

            <Select
              label="Status"
              value={formData.status}
              onChange={(e) => {
                const newStatus = e.target.value as any;
                setFormData({
                  ...formData,
                  status: newStatus,
                  progress: newStatus === 'COMPLETED' ? 100 : newStatus === 'TODO' ? 0 : formData.progress,
                });
              }}
              aria-label="Status"
            >
              <option value="TODO">To Do</option>
              <option value="IN_PROGRESS">In Progress</option>
              <option value="BLOCKED">Blocked</option>
              <option value="COMPLETED">Completed</option>
            </Select>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
            <Input
              type="number"
              min="0"
              max="100"
              label="Progress %"
              value={formData.progress}
              onChange={(e) => {
                const p = Number(e.target.value);
                setFormData({
                  ...formData,
                  progress: p,
                  status: p === 100 ? 'COMPLETED' : p > 0 && formData.status === 'TODO' ? 'IN_PROGRESS' : formData.status,
                });
              }}
              aria-label="Progress percentage"
            />

            <Input
              type="date"
              label="Start Date"
              value={formData.startDate}
              onChange={(e) => setFormData({ ...formData, startDate: e.target.value })}
              aria-label="Start Date"
            />

            <Input
              type="date"
              label="Task Deadline"
              value={formData.deadline}
              onChange={(e) => setFormData({ ...formData, deadline: e.target.value })}
              aria-label="Task Deadline"
            />
          </div>

          <div className="flex items-center justify-end gap-3 pt-4 border-t border-gray-100">
            <Button
              variant="outline"
              size="md"
              onClick={() => setIsEditModalOpen(false)}
              disabled={isSubmitting}
            >
              Cancel
            </Button>
            <Button type="submit" variant="primary" size="md" isLoading={isSubmitting}>
              Save Changes
            </Button>
          </div>
        </form>
      </Modal>

      {/* Dependency & Stage Gates Inspector Modal */}
      <Modal
        isOpen={isDependencyModalOpen}
        onClose={() => setIsDependencyModalOpen(false)}
        title="Task Dependencies & Stage Gates"
        description={`Inspect predecessor workflows, successor dependencies, and approval gates for "${selectedTask?.name}".`}
        maxWidth="lg"
      >
        <div className="space-y-5">
          {isLoadingDependencies ? (
            <LoadingSpinner label="Inspecting dependency graph..." size="md" />
          ) : (
            <>
              {/* Blocking Reasons Alert */}
              {dependencyDetails?.blocked && (
                <div className="p-3.5 bg-rose-50 border border-rose-200 rounded-xl space-y-1.5">
                  <div className="flex items-center gap-2 text-rose-800 font-bold text-xs uppercase tracking-wide">
                    <ShieldAlert className="w-4 h-4 text-rose-600" />
                    <span>Task Execution Blocked</span>
                  </div>
                  <ul className="text-xs text-rose-700 list-disc list-inside space-y-0.5">
                    {dependencyDetails.blockingReasons.length > 0 ? (
                      dependencyDetails.blockingReasons.map((reason, idx) => (
                        <li key={idx}>{reason}</li>
                      ))
                    ) : (
                      <li>Task status is marked as BLOCKED.</li>
                    )}
                  </ul>
                </div>
              )}

              {/* Predecessors Section */}
              <div className="space-y-2">
                <div className="flex items-center justify-between">
                  <h3 className="text-xs font-bold text-gray-800 uppercase tracking-wider">
                    Predecessor Tasks (Must Finish First)
                  </h3>
                  <span className="text-xs text-gray-400">
                    {dependencyDetails?.predecessors.length || 0} configured
                  </span>
                </div>

                {dependencyDetails?.predecessors.length === 0 ? (
                  <div className="p-3 bg-gray-50 border border-gray-200 rounded-xl text-xs text-gray-500 italic">
                    No predecessor tasks configured. This task can start as soon as scheduled.
                  </div>
                ) : (
                  <div className="divide-y divide-gray-100 border border-gray-200 rounded-xl overflow-hidden">
                    {dependencyDetails?.predecessors.map((pred) => (
                      <div
                        key={pred.dependencyId}
                        className="p-3 bg-white flex items-center justify-between gap-3 text-xs"
                      >
                        <div className="space-y-0.5">
                          <div className="font-bold text-gray-900 flex items-center gap-2">
                            <span>{pred.taskName}</span>
                            <Badge variant={getStatusBadgeVariant(pred.status)} size="sm">
                              {pred.status} ({pred.progress || 0}%)
                            </Badge>
                            {pred.blocking && (
                              <span className="text-[10px] text-rose-600 font-bold bg-rose-50 px-1.5 py-0.5 rounded">
                                Blocking
                              </span>
                            )}
                          </div>
                          <div className="text-[11px] text-gray-500">
                            {pred.estimatedDuration} days · Planned:{' '}
                            {pred.plannedStartDate && pred.plannedFinishDate
                              ? `${pred.plannedStartDate} → ${pred.plannedFinishDate}`
                              : 'Pending CPM'}
                          </div>
                        </div>

                        <button
                          onClick={() => handleRemoveDependency(pred.dependencyId)}
                          title="Remove Predecessor Dependency"
                          aria-label={`Remove dependency on ${pred.taskName}`}
                          className="p-1.5 text-gray-400 hover:text-rose-600 hover:bg-rose-50 rounded-lg transition"
                        >
                          <X className="w-4 h-4" />
                        </button>
                      </div>
                    ))}
                  </div>
                )}

                {/* Add Predecessor Control */}
                {dependencyModalError && (
                  <div className="pt-2">
                    <Alert
                      type="error"
                      onClose={() => setDependencyModalError(null)}
                      title="Invalid Dependency"
                    >
                      {dependencyModalError}
                    </Alert>
                  </div>
                )}

                {availablePredecessors.length > 0 && (
                  <div className="flex items-center gap-2 pt-2">
                    <Select
                      value={selectedPredecessorId}
                      onChange={(e) => {
                        setSelectedPredecessorId(e.target.value);
                        setDependencyModalError(null);
                      }}
                      aria-label="Select predecessor task to add"
                      className="text-xs"
                    >
                      <option value="">Select task in this project to add as predecessor...</option>
                      {availablePredecessors.map((t) => (
                        <option key={t.id} value={t.id}>
                          {t.name} ({t.status}, {t.progress || 0}%)
                        </option>
                      ))}
                    </Select>
                    <Button
                      variant="outline"
                      size="sm"
                      onClick={handleAddDependency}
                      disabled={!selectedPredecessorId || isAddingDependency}
                      isLoading={isAddingDependency}
                    >
                      Add Predecessor
                    </Button>
                  </div>
                )}
              </div>

              {/* Successors Section */}
              <div className="space-y-2 pt-2 border-t border-gray-100">
                <div className="flex items-center justify-between">
                  <h3 className="text-xs font-bold text-gray-800 uppercase tracking-wider">
                    Successor Tasks (Waiting on this Task)
                  </h3>
                  <span className="text-xs text-gray-400">
                    {dependencyDetails?.successors.length || 0} configured
                  </span>
                </div>

                {dependencyDetails?.successors.length === 0 ? (
                  <div className="p-3 bg-gray-50 border border-gray-200 rounded-xl text-xs text-gray-500 italic">
                    No successor tasks waiting on this task.
                  </div>
                ) : (
                  <div className="divide-y divide-gray-100 border border-gray-200 rounded-xl overflow-hidden">
                    {dependencyDetails?.successors.map((succ) => (
                      <div
                        key={succ.dependencyId}
                        className="p-3 bg-white flex items-center justify-between gap-3 text-xs"
                      >
                        <div className="space-y-0.5">
                          <div className="font-bold text-gray-900 flex items-center gap-2">
                            <span>{succ.taskName}</span>
                            <Badge variant={getStatusBadgeVariant(succ.status)} size="sm">
                              {succ.status}
                            </Badge>
                          </div>
                          <div className="text-[11px] text-gray-500">
                            {succ.estimatedDuration} days · Planned:{' '}
                            {succ.plannedStartDate && succ.plannedFinishDate
                              ? `${succ.plannedStartDate} → ${succ.plannedFinishDate}`
                              : 'Pending CPM'}
                          </div>
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </div>

              {/* Approval Gates Section */}
              <div className="space-y-2 pt-2 border-t border-gray-100">
                <div className="flex items-center justify-between">
                  <h3 className="text-xs font-bold text-gray-800 uppercase tracking-wider">
                    Stage Gate Approval Deliverables
                  </h3>
                  <span className="text-xs text-gray-400">
                    {dependencyDetails?.approvalGates.length || 0} attached
                  </span>
                </div>

                {dependencyDetails?.approvalGates.length === 0 ? (
                  <div className="p-3 bg-gray-50 border border-gray-200 rounded-xl text-xs text-gray-500 italic">
                    No approval gates attached to this task.
                  </div>
                ) : (
                  <div className="divide-y divide-gray-100 border border-gray-200 rounded-xl overflow-hidden">
                    {dependencyDetails?.approvalGates.map((gate) => (
                      <div
                        key={gate.gateId}
                        className="p-3 bg-white flex items-center justify-between gap-3 text-xs"
                      >
                        <div className="space-y-0.5">
                          <div className="font-bold text-gray-900 flex items-center gap-2">
                            <span>{gate.deliverableName}</span>
                            <Badge
                              variant={gate.satisfied ? 'success' : 'danger'}
                              size="sm"
                              dot
                            >
                              {gate.deliverableStatus}
                            </Badge>
                          </div>
                          <div className="text-[11px] text-gray-500">
                            Gate Type: {gate.gateType} · {gate.isMandatory ? 'Mandatory' : 'Optional'}
                          </div>
                        </div>
                        {gate.satisfied ? (
                          <span className="text-emerald-700 font-bold text-xs flex items-center gap-1">
                            <CheckCircle2 className="w-3.5 h-3.5" /> Satisfied
                          </span>
                        ) : (
                          <span className="text-amber-700 font-bold text-xs flex items-center gap-1">
                            <AlertCircle className="w-3.5 h-3.5" /> Pending Sign-off
                          </span>
                        )}
                      </div>
                    ))}
                  </div>
                )}
              </div>
            </>
          )}

          <div className="flex justify-end pt-3 border-t border-gray-100">
            <Button
              variant="outline"
              size="md"
              onClick={() => setIsDependencyModalOpen(false)}
            >
              Close
            </Button>
          </div>
        </div>
      </Modal>

      {/* Delete Confirmation */}
      <ConfirmDialog
        isOpen={isDeleteModalOpen}
        onClose={() => setIsDeleteModalOpen(false)}
        onConfirm={handleDeleteConfirm}
        title="Delete Task?"
        description={`Are you sure you want to delete task "${selectedTask?.name}"? Any dependent tasks or deliverable gates attached to this task will also be unlinked.`}
        confirmLabel="Delete Task"
        variant="danger"
        isLoading={isSubmitting}
      />
    </div>
  );
}
