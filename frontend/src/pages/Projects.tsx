import React, { useState, useEffect, useMemo } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../services/api';
import { useOrganization } from '../context/OrganizationContext';
import {
  FolderKanban,
  Plus,
  Search,
  Calendar,
  Clock,
  ArrowRight,
  Edit2,
  Trash2,
  BarChart3,
  FileCheck,
  Building2,
  User as UserIcon,
  CheckCircle2,
  AlertTriangle,
  RotateCcw,
  ListTodo,
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

export interface Project {
  id: number;
  name: string;
  description?: string;
  clientId?: number;
  clientName?: string;
  managerId?: number;
  managerName?: string;
  startDate?: string;
  deadline?: string;
  priority?: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
  status: 'PLANNED' | 'IN_PROGRESS' | 'ON_HOLD' | 'COMPLETED' | 'CANCELLED';
  progress: number;
  createdAt?: string;
  updatedAt?: string;
}

export interface Client {
  id: number;
  name: string;
  company?: string;
}

export interface OrganizationMember {
  id: number;
  userId: number;
  username: string;
  fullName?: string;
  email: string;
  role: string;
  status: string;
}

interface ProjectFormData {
  name: string;
  description: string;
  clientId: string;
  managerId: string;
  priority: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
  startDate: string;
  deadline: string;
  status: 'PLANNED' | 'IN_PROGRESS' | 'ON_HOLD' | 'COMPLETED' | 'CANCELLED';
  progress: number;
}

const initialFormValues: ProjectFormData = {
  name: '',
  description: '',
  clientId: '',
  managerId: '',
  priority: 'MEDIUM',
  startDate: new Date().toISOString().split('T')[0],
  deadline: '',
  status: 'PLANNED',
  progress: 0,
};

export function Projects() {
  const { currentOrg } = useOrganization();
  const [projects, setProjects] = useState<Project[]>([]);
  const [clients, setClients] = useState<Client[]>([]);
  const [members, setMembers] = useState<OrganizationMember[]>([]);
  const [loading, setLoading] = useState(true);

  // Filters & Search
  const [searchQuery, setSearchQuery] = useState('');
  const [statusFilter, setStatusFilter] = useState<string>('ALL');
  const [priorityFilter, setPriorityFilter] = useState<string>('ALL');

  // Feedback notifications
  const [alertMessage, setAlertMessage] = useState<{ type: 'success' | 'error' | 'info'; text: string } | null>(null);

  // Modal State
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [modalMode, setModalMode] = useState<'create' | 'edit'>('create');
  const [selectedProject, setSelectedProject] = useState<Project | null>(null);
  const [formData, setFormData] = useState<ProjectFormData>(initialFormValues);
  const [formErrors, setFormErrors] = useState<Record<string, string>>({});
  const [modalError, setModalError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isFormDirty, setIsFormDirty] = useState(false);

  // Delete Dialog State
  const [isDeleteModalOpen, setIsDeleteModalOpen] = useState(false);
  const [projectToDelete, setProjectToDelete] = useState<Project | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);

  // Load all projects, clients, and active workspace members
  const loadData = async () => {
    try {
      setLoading(true);
      const [projRes, clientRes, memberRes] = await Promise.all([
        api.get<Project[]>('/projects'),
        api.get<Client[]>('/clients').catch(() => ({ data: [] })),
        currentOrg?.id
          ? api.get<OrganizationMember[]>(`/organizations/${currentOrg.id}/members?status=ACTIVE`).catch(() => ({ data: [] }))
          : Promise.resolve({ data: [] }),
      ]);

      setProjects(projRes.data || []);
      setClients(clientRes.data || []);
      setMembers(memberRes.data || []);
    } catch (err: any) {
      console.error('Failed to load projects workspace data:', err);
      setAlertMessage({ type: 'error', text: 'Failed to load projects. Please try again.' });
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadData();
  }, [currentOrg?.id]);

  // Client-side field validations
  const validateForm = (): boolean => {
    const errors: Record<string, string> = {};

    const trimmedName = formData.name.trim();
    if (!trimmedName) {
      errors.name = 'Project name is required.';
    } else if (trimmedName.length > 100) {
      errors.name = 'Project name cannot exceed 100 characters.';
    } else {
      // Check for duplicate project name in this workspace
      const duplicate = projects.find(
        (p) =>
          p.name.toLowerCase() === trimmedName.toLowerCase() &&
          (modalMode === 'create' || p.id !== selectedProject?.id)
      );
      if (duplicate) {
        errors.name = `A project named "${trimmedName}" already exists in this workspace.`;
      }
    }

    if (formData.description && formData.description.length > 1000) {
      errors.description = 'Description cannot exceed 1000 characters.';
    }

    if (formData.startDate && formData.deadline) {
      if (formData.deadline < formData.startDate) {
        errors.deadline = 'Contract deadline cannot be earlier than the start date.';
      }
    }

    if (formData.progress < 0 || formData.progress > 100) {
      errors.progress = 'Progress percentage must be between 0 and 100.';
    }

    setFormErrors(errors);
    return Object.keys(errors).length === 0;
  };

  // Open Create Modal
  const handleOpenCreate = () => {
    setModalMode('create');
    setSelectedProject(null);
    setFormData({
      ...initialFormValues,
      clientId: clients.length > 0 ? String(clients[0].id) : '',
      startDate: new Date().toISOString().split('T')[0],
    });
    setFormErrors({});
    setModalError(null);
    setIsFormDirty(false);
    setIsModalOpen(true);
  };

  // Open Edit Modal
  const handleOpenEdit = (project: Project) => {
    setModalMode('edit');
    setSelectedProject(project);
    setFormData({
      name: project.name,
      description: project.description || '',
      clientId: project.clientId ? String(project.clientId) : '',
      managerId: project.managerId ? String(project.managerId) : '',
      priority: project.priority || 'MEDIUM',
      startDate: project.startDate || '',
      deadline: project.deadline || '',
      status: project.status,
      progress: project.progress !== undefined ? project.progress : 0,
    });
    setFormErrors({});
    setModalError(null);
    setIsFormDirty(false);
    setIsModalOpen(true);
  };

  // Handle Form Input Change with dynamic status/progress synchronization
  const handleInputChange = (field: keyof ProjectFormData, value: any) => {
    setIsFormDirty(true);
    setFormData((prev) => {
      const updated = { ...prev, [field]: value };

      // Business Rule: Completed status enforces 100% progress
      if (field === 'status') {
        if (value === 'COMPLETED') {
          updated.progress = 100;
        } else if (value === 'PLANNED' && prev.progress === 100) {
          updated.progress = 0;
        }
      }

      // Business Rule: 100% progress suggests or syncs to COMPLETED
      if (field === 'progress') {
        const numVal = Number(value);
        if (numVal === 100 && prev.status === 'PLANNED') {
          updated.status = 'COMPLETED';
        } else if (numVal < 100 && prev.status === 'COMPLETED') {
          updated.status = 'IN_PROGRESS';
        }
      }

      return updated;
    });

    // Clear inline error when field is updated
    if (formErrors[field]) {
      setFormErrors((prev) => {
        const next = { ...prev };
        delete next[field];
        return next;
      });
    }
  };

  // Close Modal with Unsaved Changes Protection
  const handleCloseModal = () => {
    if (isFormDirty) {
      const confirmDiscard = window.confirm('You have unsaved changes. Discard your edits?');
      if (!confirmDiscard) return;
    }
    setIsModalOpen(false);
    setIsFormDirty(false);
    setModalError(null);
  };

  // Submit Project Form (Create or Edit)
  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setModalError(null);

    if (!validateForm()) {
      return;
    }

    try {
      setIsSubmitting(true);
      const payload: Partial<Project> = {
        name: formData.name.trim(),
        description: formData.description.trim() || undefined,
        clientId: formData.clientId ? Number(formData.clientId) : undefined,
        managerId: formData.managerId ? Number(formData.managerId) : undefined,
        priority: formData.priority,
        startDate: formData.startDate || undefined,
        deadline: formData.deadline || undefined,
        status: formData.status,
        progress: Number(formData.progress),
      };

      if (modalMode === 'create') {
        const res = await api.post<Project>('/projects', payload);
        setAlertMessage({
          type: 'success',
          text: `Project "${res.data.name}" was successfully created in ${currentOrg?.name || 'the workspace'}.`,
        });
      } else if (selectedProject) {
        const res = await api.put<Project>(`/projects/${selectedProject.id}`, payload);
        setAlertMessage({
          type: 'success',
          text: `Project "${res.data.name}" has been updated successfully.`,
        });
      }

      setIsModalOpen(false);
      setIsFormDirty(false);
      await loadData();
    } catch (err: any) {
      console.error('Project submission failed:', err);
      const errMsg = err.response?.data?.message || 'Failed to save project. Please check the inputs.';
      setModalError(errMsg);
    } finally {
      setIsSubmitting(false);
    }
  };

  // Open Delete Confirmation
  const handleOpenDelete = (project: Project) => {
    setProjectToDelete(project);
    setIsDeleteModalOpen(true);
  };

  // Confirm Delete
  const handleDeleteConfirm = async () => {
    if (!projectToDelete) return;

    try {
      setIsDeleting(true);
      await api.delete(`/projects/${projectToDelete.id}`);
      setAlertMessage({
        type: 'success',
        text: `Project "${projectToDelete.name}" and its associated tasks have been safely removed.`,
      });
      setIsDeleteModalOpen(false);
      setProjectToDelete(null);
      await loadData();
    } catch (err: any) {
      console.error('Project deletion failed:', err);
      setAlertMessage({
        type: 'error',
        text: err.response?.data?.message || 'Failed to delete project. Please try again.',
      });
    } finally {
      setIsDeleting(false);
    }
  };

  // Filtered & Searched Projects list
  const filteredProjects = useMemo(() => {
    return projects.filter((p) => {
      const q = searchQuery.toLowerCase().trim();
      const matchesSearch =
        !q ||
        p.name.toLowerCase().includes(q) ||
        (p.description && p.description.toLowerCase().includes(q)) ||
        (p.clientName && p.clientName.toLowerCase().includes(q)) ||
        (p.managerName && p.managerName.toLowerCase().includes(q));

      const matchesStatus = statusFilter === 'ALL' || p.status === statusFilter;
      const matchesPriority = priorityFilter === 'ALL' || p.priority === priorityFilter;

      return matchesSearch && matchesStatus && matchesPriority;
    });
  }, [projects, searchQuery, statusFilter, priorityFilter]);

  // Overall workspace summary statistics (Independent of filters)
  const stats = useMemo(() => {
    const total = projects.length;
    const active = projects.filter((p) => p.status === 'IN_PROGRESS').length;
    const planned = projects.filter((p) => p.status === 'PLANNED').length;
    const completed = projects.filter((p) => p.status === 'COMPLETED').length;
    const onHold = projects.filter((p) => p.status === 'ON_HOLD').length;
    const cancelled = projects.filter((p) => p.status === 'CANCELLED').length;
    return { total, active, planned, completed, onHold, cancelled };
  }, [projects]);

  // Badge variants
  const getStatusBadgeVariant = (status: string): BadgeVariant => {
    switch (status) {
      case 'COMPLETED':
        return 'success';
      case 'IN_PROGRESS':
        return 'info';
      case 'PLANNED':
        return 'purple';
      case 'ON_HOLD':
        return 'warning';
      case 'CANCELLED':
        return 'danger';
      default:
        return 'neutral';
    }
  };

  const getPriorityBadgeVariant = (priority?: string): BadgeVariant => {
    switch (priority) {
      case 'CRITICAL':
        return 'danger';
      case 'HIGH':
        return 'danger';
      case 'MEDIUM':
        return 'warning';
      case 'LOW':
        return 'neutral';
      default:
        return 'neutral';
    }
  };

  const isFiltersActive = searchQuery.trim() !== '' || statusFilter !== 'ALL' || priorityFilter !== 'ALL';

  return (
    <div className="p-8 space-y-6 max-w-7xl mx-auto">
      {/* 2. Page Heading */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-2 border-b border-gray-200 dark:border-slate-800">
        <div>
          <h1 className="text-2xl font-black text-gray-900 dark:text-white tracking-tight flex items-center gap-2.5">
            <FolderKanban className="w-7 h-7 text-blue-600 dark:text-blue-400" />
            Projects
          </h1>
          <p className="text-sm text-gray-500 dark:text-slate-400 mt-1">
            Plan, organize, and monitor your projects, deadlines, and delivery progress.
          </p>
        </div>

        <Button
          id="new-project-btn"
          variant="primary"
          size="md"
          leftIcon={<Plus className="w-4 h-4" />}
          onClick={handleOpenCreate}
          aria-label="Create New Project"
          title="Create New Project"
        >
          New Project
        </Button>
      </div>

      {/* Global Alert Notification */}
      {alertMessage && (
        <Alert
          type={alertMessage.type}
          onClose={() => setAlertMessage(null)}
          title={alertMessage.type === 'success' ? 'Success' : alertMessage.type === 'error' ? 'Notice' : 'Information'}
        >
          {alertMessage.text}
        </Alert>
      )}

      {/* 3. Project Summary Cards (Workspace Data, Independent of Filter) */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
        <Card className="p-5 border-l-4 border-l-blue-500">
          <span className="text-xs font-bold text-gray-500 dark:text-slate-400 uppercase tracking-wider">
            Total Projects
          </span>
          <div className="text-2xl font-black text-gray-900 dark:text-white mt-1">{stats.total}</div>
          <span className="text-xs text-gray-500 dark:text-slate-400 mt-0.5 block">Managed in workspace</span>
        </Card>

        <Card className="p-5 border-l-4 border-l-blue-600">
          <span className="text-xs font-bold text-blue-600 dark:text-blue-400 uppercase tracking-wider">
            Active Execution
          </span>
          <div className="text-2xl font-black text-blue-600 dark:text-blue-400 mt-1">{stats.active}</div>
          <span className="text-xs text-gray-500 dark:text-slate-400 mt-0.5 block">Currently in progress</span>
        </Card>

        <Card className="p-5 border-l-4 border-l-purple-500">
          <span className="text-xs font-bold text-purple-600 dark:text-purple-400 uppercase tracking-wider">
            Planning Phase
          </span>
          <div className="text-2xl font-black text-purple-600 dark:text-purple-400 mt-1">{stats.planned}</div>
          <span className="text-xs text-gray-500 dark:text-slate-400 mt-0.5 block">Planned, not yet started</span>
        </Card>

        <Card className="p-5 border-l-4 border-l-emerald-500">
          <span className="text-xs font-bold text-emerald-600 dark:text-emerald-400 uppercase tracking-wider">
            Completed
          </span>
          <div className="text-2xl font-black text-emerald-600 dark:text-emerald-400 mt-1">{stats.completed}</div>
          <span className="text-xs text-gray-500 dark:text-slate-400 mt-0.5 block">Delivery complete</span>
        </Card>
      </div>

      {/* Filter and Search Bar */}
      <Card className="p-4">
        <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-4">
          <div className="relative flex-1 max-w-md">
            <Input
              id="projects-search-input"
              placeholder="Search projects by name, client, or manager..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              leftIcon={<Search className="w-4 h-4" />}
            />
          </div>

          <div className="flex flex-wrap items-center gap-3">
            {/* Status Pills */}
            <div className="flex items-center gap-1.5 overflow-x-auto pb-1 sm:pb-0">
              {[
                { key: 'ALL', label: 'All', count: stats.total },
                { key: 'PLANNED', label: 'Planned', count: stats.planned },
                { key: 'IN_PROGRESS', label: 'In Progress', count: stats.active },
                { key: 'ON_HOLD', label: 'On Hold', count: stats.onHold },
                { key: 'COMPLETED', label: 'Completed', count: stats.completed },
                { key: 'CANCELLED', label: 'Cancelled', count: stats.cancelled },
              ].map((st) => (
                <button
                  key={st.key}
                  onClick={() => setStatusFilter(st.key)}
                  className={`px-3 py-1.5 rounded-xl text-xs font-bold transition-all shrink-0 flex items-center gap-1.5 ${
                    statusFilter === st.key
                      ? 'bg-blue-600 text-white shadow-2xs'
                      : 'bg-gray-100 dark:bg-slate-800 text-gray-600 dark:text-slate-300 hover:bg-gray-200 dark:hover:bg-slate-700'
                  }`}
                >
                  <span>{st.label}</span>
                  <span
                    className={`text-[10px] px-1.5 py-0.2 rounded-full ${
                      statusFilter === st.key
                        ? 'bg-blue-700 text-white'
                        : 'bg-gray-200 dark:bg-slate-700 text-gray-700 dark:text-slate-300'
                    }`}
                  >
                    {st.count}
                  </span>
                </button>
              ))}
            </div>

            {/* Priority Selector */}
            <div className="w-36">
              <Select
                value={priorityFilter}
                onChange={(e) => setPriorityFilter(e.target.value)}
                className="text-xs py-1.5"
              >
                <option value="ALL">All Priorities</option>
                <option value="CRITICAL">Critical Priority</option>
                <option value="HIGH">High Priority</option>
                <option value="MEDIUM">Medium Priority</option>
                <option value="LOW">Low Priority</option>
              </Select>
            </div>

            {/* Reset Filters button */}
            {isFiltersActive && (
              <button
                onClick={() => {
                  setSearchQuery('');
                  setStatusFilter('ALL');
                  setPriorityFilter('ALL');
                }}
                className="p-1.5 text-xs text-gray-500 dark:text-slate-400 hover:text-gray-900 dark:hover:text-white flex items-center gap-1 hover:bg-gray-100 dark:hover:bg-slate-800 rounded-lg transition"
                title="Reset filters"
              >
                <RotateCcw className="w-3.5 h-3.5" />
                <span>Reset</span>
              </button>
            )}
          </div>
        </div>
      </Card>

      {/* Projects Table View */}
      {loading ? (
        <LoadingSpinner label="Loading projects catalog..." size="lg" />
      ) : filteredProjects.length === 0 ? (
        <EmptyState
          icon={<FolderKanban className="w-10 h-10 text-gray-400" />}
          title={isFiltersActive ? 'No matching projects found' : 'No projects created yet'}
          description={
            isFiltersActive
              ? 'No projects match your current search criteria or active filters. Try resetting the filters.'
              : 'Create your first project to begin scheduling tasks, tracking milestones, and coordinating team resources.'
          }
          actionLabel={isFiltersActive ? 'Clear Filters' : 'Create First Project'}
          onAction={
            isFiltersActive
              ? () => {
                  setSearchQuery('');
                  setStatusFilter('ALL');
                  setPriorityFilter('ALL');
                }
              : handleOpenCreate
          }
        />
      ) : (
        <Card className="overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm text-gray-600 dark:text-slate-300">
              <thead className="bg-gray-50/80 dark:bg-slate-800/80 text-xs font-bold text-gray-500 dark:text-slate-400 uppercase border-b border-gray-200 dark:border-slate-800">
                <tr>
                  <th className="px-6 py-4">Project & Scope</th>
                  <th className="px-6 py-4">Status & Priority</th>
                  <th className="px-6 py-4">Timeline Dates</th>
                  <th className="px-6 py-4">Progress</th>
                  <th className="px-6 py-4 text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100 dark:divide-slate-800">
                {filteredProjects.map((p) => (
                  <tr key={p.id} className="hover:bg-gray-50/80 dark:hover:bg-slate-800/50 transition-colors">
                    {/* Project & Scope */}
                    <td className="px-6 py-4">
                      <div className="space-y-1">
                        <Link
                          to={`/schedule?projectId=${p.id}`}
                          className="font-bold text-gray-900 dark:text-white hover:text-blue-600 dark:hover:text-blue-400 transition flex items-center gap-1.5 group"
                          title="Open in Schedule & Gantt"
                        >
                          <span className="text-base font-semibold">{p.name}</span>
                          <ArrowRight className="w-4 h-4 opacity-0 group-hover:opacity-100 transition-opacity text-blue-600 dark:text-blue-400" />
                        </Link>

                        {p.description && (
                          <p className="text-xs text-gray-500 dark:text-slate-400 line-clamp-1 max-w-md">
                            {p.description}
                          </p>
                        )}

                        <div className="text-xs text-gray-500 dark:text-slate-400 flex flex-wrap items-center gap-3 pt-0.5">
                          {p.clientName ? (
                            <span className="inline-flex items-center gap-1 text-gray-700 dark:text-slate-300 font-medium">
                              <Building2 className="w-3.5 h-3.5 text-blue-500" />
                              {p.clientName}
                            </span>
                          ) : (
                            <span className="italic text-gray-400">Internal Project</span>
                          )}

                          {p.managerName && (
                            <span className="inline-flex items-center gap-1 text-gray-600 dark:text-slate-400">
                              <UserIcon className="w-3.5 h-3.5 text-purple-500" />
                              <span>Manager: {p.managerName}</span>
                            </span>
                          )}
                        </div>
                      </div>
                    </td>

                    {/* Status & Priority */}
                    <td className="px-6 py-4 whitespace-nowrap">
                      <div className="flex flex-col items-start gap-1.5">
                        <Badge variant={getStatusBadgeVariant(p.status)} size="sm" dot>
                          {p.status.replace('_', ' ')}
                        </Badge>
                        {p.priority && (
                          <Badge variant={getPriorityBadgeVariant(p.priority)} size="sm">
                            {p.priority}
                          </Badge>
                        )}
                      </div>
                    </td>

                    {/* Timeline Dates */}
                    <td className="px-6 py-4 whitespace-nowrap text-xs">
                      <div className="space-y-1.5">
                        <div className="flex items-center gap-1.5 text-gray-600 dark:text-slate-300">
                          <Calendar className="w-3.5 h-3.5 text-gray-400" />
                          <span>Start: {p.startDate || 'Not scheduled'}</span>
                        </div>
                        <div className="flex items-center gap-1.5 text-gray-800 dark:text-slate-200 font-semibold">
                          <Clock className="w-3.5 h-3.5 text-gray-400" />
                          <span>Deadline: {p.deadline || 'No deadline'}</span>
                        </div>
                      </div>
                    </td>

                    {/* Progress Bar */}
                    <td className="px-6 py-4 whitespace-nowrap">
                      <div className="w-36 space-y-1.5">
                        <div className="flex justify-between text-xs font-bold text-gray-700 dark:text-slate-300">
                          <span>{p.progress || 0}%</span>
                          {p.status === 'COMPLETED' && (
                            <CheckCircle2 className="w-3.5 h-3.5 text-emerald-500 inline ml-1" />
                          )}
                        </div>
                        <div className="w-full bg-gray-200 dark:bg-slate-700 h-2 rounded-full overflow-hidden">
                          <div
                            className={`h-full rounded-full transition-all duration-300 ${
                              p.status === 'COMPLETED'
                                ? 'bg-emerald-500'
                                : p.status === 'CANCELLED'
                                ? 'bg-rose-500'
                                : 'bg-blue-600'
                            }`}
                            style={{ width: `${Math.min(100, Math.max(0, p.progress || 0))}%` }}
                          />
                        </div>
                      </div>
                    </td>

                    {/* Actions */}
                    <td className="px-6 py-4 text-right whitespace-nowrap">
                      <div className="flex items-center justify-end gap-1">
                        <Link
                          to={`/schedule?projectId=${p.id}`}
                          title={`Schedule & Gantt: ${p.name}`}
                          aria-label={`Open interactive schedule and Gantt chart for ${p.name}`}
                          className="p-1.5 text-gray-500 dark:text-slate-400 hover:text-blue-600 dark:hover:text-blue-400 hover:bg-blue-50 dark:hover:bg-blue-950/50 rounded-lg transition focus:outline-none focus-visible:ring-2 focus-visible:ring-blue-500"
                        >
                          <BarChart3 className="w-4 h-4" />
                        </Link>
                        <Link
                          to={`/tasks?projectId=${p.id}`}
                          title={`Task Board: ${p.name}`}
                          aria-label={`View and manage tasks for ${p.name}`}
                          className="p-1.5 text-gray-500 dark:text-slate-400 hover:text-emerald-600 dark:hover:text-emerald-400 hover:bg-emerald-50 dark:hover:bg-emerald-950/50 rounded-lg transition focus:outline-none focus-visible:ring-2 focus-visible:ring-emerald-500"
                        >
                          <ListTodo className="w-4 h-4" />
                        </Link>
                        <Link
                          to={`/deliverables?projectId=${p.id}`}
                          title={`Deliverables & Approvals: ${p.name}`}
                          aria-label={`View stage-gate deliverables and approvals for ${p.name}`}
                          className="p-1.5 text-gray-500 dark:text-slate-400 hover:text-purple-600 dark:hover:text-purple-400 hover:bg-purple-50 dark:hover:bg-purple-950/50 rounded-lg transition focus:outline-none focus-visible:ring-2 focus-visible:ring-purple-500"
                        >
                          <FileCheck className="w-4 h-4" />
                        </Link>
                        <button
                          type="button"
                          onClick={() => handleOpenEdit(p)}
                          title={`Edit Project: ${p.name}`}
                          aria-label={`Edit delivery parameters for ${p.name}`}
                          className="p-1.5 text-gray-500 dark:text-slate-400 hover:text-blue-600 dark:hover:text-blue-400 hover:bg-gray-100 dark:hover:bg-slate-800 rounded-lg transition focus:outline-none focus-visible:ring-2 focus-visible:ring-blue-500"
                        >
                          <Edit2 className="w-4 h-4" />
                        </button>
                        <button
                          type="button"
                          onClick={() => handleOpenDelete(p)}
                          title={`Delete Project: ${p.name}`}
                          aria-label={`Delete project ${p.name}`}
                          className="p-1.5 text-gray-500 dark:text-slate-400 hover:text-rose-600 dark:hover:text-rose-400 hover:bg-rose-50 dark:hover:bg-rose-950/50 rounded-lg transition focus:outline-none focus-visible:ring-2 focus-visible:ring-rose-500"
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

      {/* 4 & 5. Project Creation / Edit Modal */}
      <Modal
        isOpen={isModalOpen}
        onClose={handleCloseModal}
        title={modalMode === 'create' ? 'Create New Project' : 'Edit Project Details'}
        description={
          modalMode === 'create'
            ? `Initialize a new delivery project under active workspace "${currentOrg?.name || 'Workspace'}".`
            : `Update delivery parameters, dates, and assignments for "${selectedProject?.name}".`
        }
        maxWidth="lg"
      >
        <form onSubmit={handleSubmit} className="space-y-4">
          {modalError && (
            <Alert type="error" onClose={() => setModalError(null)}>
              {modalError}
            </Alert>
          )}

          {/* Project Name */}
          <Input
            id="modal-project-name"
            label="Project Name"
            placeholder="e.g. Cloud Infrastructure Migration Phase 2"
            value={formData.name}
            onChange={(e) => handleInputChange('name', e.target.value)}
            error={formErrors.name}
            required
            maxLength={100}
          />

          {/* Description */}
          <div>
            <div className="flex justify-between items-center mb-1.5">
              <label className="block text-xs font-bold text-gray-700 dark:text-slate-300 tracking-tight">
                Description / Scope
              </label>
              <span className="text-[10px] text-gray-400">
                {formData.description.length}/1000
              </span>
            </div>
            <textarea
              id="modal-project-description"
              className={`w-full px-3.5 py-2 text-sm bg-white dark:bg-slate-900 border rounded-xl text-gray-900 dark:text-white placeholder:text-gray-400 transition-colors focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 ${
                formErrors.description ? 'border-rose-400' : 'border-gray-300 dark:border-slate-700'
              }`}
              rows={3}
              placeholder="Outline project objectives, architectural scope, deliverable boundaries, or client milestones..."
              value={formData.description}
              onChange={(e) => handleInputChange('description', e.target.value)}
              maxLength={1000}
            />
            {formErrors.description && (
              <p className="text-xs text-rose-600 mt-1">{formErrors.description}</p>
            )}
          </div>

          {/* Client & Project Manager */}
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <Select
              id="modal-project-client"
              label="Client"
              value={formData.clientId}
              onChange={(e) => handleInputChange('clientId', e.target.value)}
              error={formErrors.clientId}
              helperText="Assign client account or designate as internal project"
            >
              <option value="">No Client (Internal Project)</option>
              {clients.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.name} {c.company ? `(${c.company})` : ''}
                </option>
              ))}
            </Select>

            <Select
              id="modal-project-manager"
              label="Project Manager"
              value={formData.managerId}
              onChange={(e) => handleInputChange('managerId', e.target.value)}
              error={formErrors.managerId}
              helperText="Authorized workspace project lead"
            >
              <option value="">Unassigned (No Manager)</option>
              {members.map((m) => (
                <option key={m.userId} value={m.userId}>
                  {m.fullName ? `${m.fullName} (${m.username})` : m.username} — {m.role}
                </option>
              ))}
            </Select>
          </div>

          {/* Priority & Status */}
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <Select
              id="modal-project-priority"
              label="Priority"
              value={formData.priority}
              onChange={(e) => handleInputChange('priority', e.target.value)}
            >
              <option value="LOW">Low Priority</option>
              <option value="MEDIUM">Medium Priority</option>
              <option value="HIGH">High Priority</option>
              <option value="CRITICAL">Critical Priority</option>
            </Select>

            <Select
              id="modal-project-status"
              label="Project Status"
              value={formData.status}
              onChange={(e) => handleInputChange('status', e.target.value)}
            >
              <option value="PLANNED">Planned</option>
              <option value="IN_PROGRESS">In Progress</option>
              <option value="ON_HOLD">On Hold</option>
              <option value="COMPLETED">Completed</option>
              <option value="CANCELLED">Cancelled</option>
            </Select>
          </div>

          {/* Dates */}
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <Input
              id="modal-project-start-date"
              type="date"
              label="Start Date"
              value={formData.startDate}
              onChange={(e) => handleInputChange('startDate', e.target.value)}
              error={formErrors.startDate}
            />

            <Input
              id="modal-project-deadline"
              type="date"
              label="Contract Deadline"
              value={formData.deadline}
              onChange={(e) => handleInputChange('deadline', e.target.value)}
              error={formErrors.deadline}
            />
          </div>

          {/* Progress % */}
          <div>
            <div className="flex justify-between items-center mb-1">
              <label className="block text-xs font-bold text-gray-700 dark:text-slate-300 tracking-tight">
                Progress: {formData.progress}%
              </label>
              <span className="text-[11px] text-gray-500">
                {formData.status === 'COMPLETED' ? 'Completed projects must be 100%' : 'Execution completion'}
              </span>
            </div>
            <div className="flex items-center gap-4">
              <input
                type="range"
                min="0"
                max="100"
                value={formData.progress}
                onChange={(e) => handleInputChange('progress', Number(e.target.value))}
                className="w-full accent-blue-600 cursor-pointer"
              />
              <Input
                type="number"
                min="0"
                max="100"
                className="w-24 text-center font-bold"
                value={formData.progress}
                onChange={(e) => handleInputChange('progress', Number(e.target.value))}
                error={formErrors.progress}
              />
            </div>
          </div>

          {/* Modal Actions */}
          <div className="flex items-center justify-end gap-3 pt-4 border-t border-gray-100 dark:border-slate-800">
            <Button
              type="button"
              variant="outline"
              size="md"
              onClick={handleCloseModal}
              disabled={isSubmitting}
            >
              Cancel
            </Button>
            <Button
              type="submit"
              variant="primary"
              size="md"
              isLoading={isSubmitting}
            >
              {modalMode === 'create' ? 'Create Project' : 'Save Changes'}
            </Button>
          </div>
        </form>
      </Modal>

      {/* 6. Delete Confirmation Dialog */}
      <ConfirmDialog
        isOpen={isDeleteModalOpen}
        onClose={() => setIsDeleteModalOpen(false)}
        onConfirm={handleDeleteConfirm}
        title="Delete Project?"
        description={`Are you sure you want to delete "${projectToDelete?.name}"? All associated tasks, dependencies, and schedules will be safely deleted. This action cannot be undone.`}
        confirmLabel="Delete Project"
        variant="danger"
        isLoading={isDeleting}
      />
    </div>
  );
}
