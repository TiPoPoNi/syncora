import React, { useState, useEffect } from 'react';
import { 
  FileCheck, FileText, UploadCloud, CheckCircle2, AlertCircle, Clock, 
  ShieldAlert, Layers, Lock, Unlock, Download, Eye, Plus, Search, Filter, 
  ArrowUpRight, UserCheck, RefreshCw, FileWarning, Check, X, Building2,
  Calendar, AlertTriangle, ShieldCheck, ChevronRight
} from 'lucide-react';
import { api } from '../services/api';
import { useAuth } from '../context/AuthContext';
import { useOrganization } from '../context/OrganizationContext';

export interface DeliverableDto {
  id: number;
  projectId: number;
  projectName: string;
  taskId?: number;
  taskName?: string;
  name: string;
  description?: string;
  deliverableType: string;
  responsibleUserId?: number;
  responsibleUsername?: string;
  assignedReviewerId?: number;
  assignedReviewerUsername?: string;
  requiredSubmissionDate?: string;
  actualSubmissionDate?: string;
  requestedDecisionDate?: string;
  actualDecisionDate?: string;
  status: 'PLANNED' | 'IN_PROGRESS' | 'SUBMITTED' | 'UNDER_REVIEW' | 'APPROVED' | 'CHANGES_REQUESTED' | 'REJECTED' | 'CANCELLED';
  currentVersionNumber: number;
  isClientVisible: boolean;
  isConfidential: boolean;
  isOverdue: boolean;
  documentsCount: number;
  approvalsCount: number;
  createdAt?: string;
}

export interface DeliverableDocumentDto {
  id: number;
  deliverableId: number;
  versionNumber: number;
  originalFilename: string;
  storageKey: string;
  fileSize: number;
  contentType: string;
  checksum: string;
  versionNotes?: string;
  submissionStatus: string;
  uploadedBy: string;
  uploadedAt: string;
}

export interface DeliverableApprovalDto {
  id: number;
  deliverableId: number;
  documentId?: number;
  documentVersionNumber?: number;
  reviewerId: number;
  reviewerUsername: string;
  decision: 'APPROVED' | 'CHANGES_REQUESTED' | 'REJECTED';
  decisionDate: string;
  comments?: string;
  previousStatus: string;
  resultingStatus: string;
  turnaroundHours?: number;
}

export interface TaskApprovalGateDto {
  id: number;
  taskId: number;
  taskName: string;
  deliverableId: number;
  deliverableName: string;
  deliverableStatus: string;
  gateType: 'COMPLETION_GATE' | 'START_GATE';
  isMandatory: boolean;
  isSatisfied: boolean;
  createdAt?: string;
}

export interface ApprovalDelayImpactDto {
  deliverableId: number;
  deliverableName: string;
  requestedDecisionDate?: string;
  isOverdue: boolean;
  daysOverdue: number;
  blockedTasksCount: number;
  blockedTaskNames: string[];
  impactsCriticalPath: boolean;
  potentialScheduleSlipDays: number;
}

export interface ProjectOption {
  id: number;
  name: string;
  status: string;
}

export interface UserOption {
  id: number;
  username: string;
  role: string;
}

export interface TaskOption {
  id: number;
  name: string;
  status: string;
}

export function DeliverablesManagement() {
  const { user } = useAuth();
  const { currentOrg } = useOrganization();

  // State
  const [projects, setProjects] = useState<ProjectOption[]>([]);
  const [selectedProjectId, setSelectedProjectId] = useState<number | null>(null);
  const [deliverables, setDeliverables] = useState<DeliverableDto[]>([]);
  const [users, setUsers] = useState<UserOption[]>([]);
  const [projectTasks, setProjectTasks] = useState<TaskOption[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [successMsg, setSuccessMsg] = useState<string | null>(null);

  // Filters & Tabs
  const [activeTab, setActiveTab] = useState<'deliverables' | 'gates' | 'client_portal'>('deliverables');
  const [searchQuery, setSearchQuery] = useState('');
  const [statusFilter, setStatusFilter] = useState<string>('ALL');
  const [typeFilter, setTypeFilter] = useState<string>('ALL');

  // Modals
  const [createModalOpen, setCreateModalOpen] = useState(false);
  const [uploadModalOpen, setUploadModalOpen] = useState(false);
  const [reviewModalOpen, setReviewModalOpen] = useState(false);
  const [assignModalOpen, setAssignModalOpen] = useState(false);
  const [historyModalOpen, setHistoryModalOpen] = useState(false);
  const [gateModalOpen, setGateModalOpen] = useState(false);
  const [impactModalOpen, setImpactModalOpen] = useState(false);

  // Selected for modals
  const [selectedDeliverable, setSelectedDeliverable] = useState<DeliverableDto | null>(null);
  const [documents, setDocuments] = useState<DeliverableDocumentDto[]>([]);
  const [approvalHistory, setApprovalHistory] = useState<DeliverableApprovalDto[]>([]);
  const [taskGates, setTaskGates] = useState<TaskApprovalGateDto[]>([]);
  const [delayImpact, setDelayImpact] = useState<ApprovalDelayImpactDto | null>(null);

  // Form states
  const [newDeliverable, setNewDeliverable] = useState({
    name: '',
    description: '',
    deliverableType: 'TECHNICAL_DOCUMENT',
    taskId: '',
    responsibleUserId: '',
    requiredSubmissionDate: '',
    isClientVisible: true,
    isConfidential: false
  });

  const [uploadFile, setUploadFile] = useState<File | null>(null);
  const [versionNotes, setVersionNotes] = useState('');
  const [uploading, setUploading] = useState(false);

  const [reviewDecision, setReviewDecision] = useState<'APPROVED' | 'CHANGES_REQUESTED' | 'REJECTED'>('APPROVED');
  const [reviewComments, setReviewComments] = useState('');
  const [submittingDecision, setSubmittingDecision] = useState(false);

  const [assigneeId, setAssigneeId] = useState<string>('');
  const [targetDecisionDate, setTargetDecisionDate] = useState<string>('');

  const [gateConfig, setGateConfig] = useState({
    taskId: '',
    deliverableId: '',
    gateType: 'COMPLETION_GATE',
    isMandatory: true
  });

  // Load projects & users on mount or workspace change
  useEffect(() => {
    fetchProjects();
    fetchUsers();
  }, [currentOrg]);

  // Load deliverables when selected project changes
  useEffect(() => {
    if (selectedProjectId) {
      fetchDeliverables(selectedProjectId);
      fetchProjectTasks(selectedProjectId);
    } else {
      setDeliverables([]);
      setProjectTasks([]);
    }
  }, [selectedProjectId]);

  const fetchProjects = async () => {
    try {
      setLoading(true);
      const res = await api.get('/projects');
      setProjects(res.data);
      if (res.data.length > 0 && !selectedProjectId) {
        setSelectedProjectId(res.data[0].id);
      }
    } catch (err: any) {
      setError(err.response?.data?.message || 'Failed to fetch projects');
    } finally {
      setLoading(false);
    }
  };

  const fetchUsers = async () => {
    try {
      const res = await api.get('/users');
      setUsers(res.data);
    } catch (err) {
      console.warn('Could not load users list');
    }
  };

  const fetchProjectTasks = async (projectId: number) => {
    try {
      const res = await api.get(`/tasks?projectId=${projectId}`);
      setProjectTasks(res.data);
      // Also fetch gates for tasks
      fetchAllGatesForProject(res.data);
    } catch (err) {
      console.warn('Could not load tasks for project');
    }
  };

  const fetchAllGatesForProject = async (tasks: TaskOption[]) => {
    try {
      const allGates: TaskApprovalGateDto[] = [];
      for (const t of tasks) {
        try {
          const gRes = await api.get(`/tasks/${t.id}/approval-gates`);
          allGates.push(...gRes.data);
        } catch (e) {
          // ignore individual task gate errors
        }
      }
      setTaskGates(allGates);
    } catch (err) {
      console.warn('Could not fetch task gates');
    }
  };

  const fetchDeliverables = async (projectId: number) => {
    try {
      setLoading(true);
      setError(null);
      const res = await api.get(`/projects/${projectId}/deliverables`);
      setDeliverables(res.data);
    } catch (err: any) {
      setError(err.response?.data?.message || 'Failed to fetch deliverables');
    } finally {
      setLoading(false);
    }
  };

  const showSuccess = (msg: string) => {
    setSuccessMsg(msg);
    setTimeout(() => setSuccessMsg(null), 4000);
  };

  // Create Deliverable
  const handleCreateDeliverable = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedProjectId) return;
    try {
      setLoading(true);
      const payload = {
        name: newDeliverable.name,
        description: newDeliverable.description || null,
        deliverableType: newDeliverable.deliverableType,
        taskId: newDeliverable.taskId ? Number(newDeliverable.taskId) : null,
        responsibleUserId: newDeliverable.responsibleUserId ? Number(newDeliverable.responsibleUserId) : null,
        requiredSubmissionDate: newDeliverable.requiredSubmissionDate || null,
        isClientVisible: newDeliverable.isClientVisible,
        isConfidential: newDeliverable.isConfidential
      };
      await api.post(`/projects/${selectedProjectId}/deliverables`, payload);
      setCreateModalOpen(false);
      setNewDeliverable({
        name: '',
        description: '',
        deliverableType: 'TECHNICAL_DOCUMENT',
        taskId: '',
        responsibleUserId: '',
        requiredSubmissionDate: '',
        isClientVisible: true,
        isConfidential: false
      });
      fetchDeliverables(selectedProjectId);
      showSuccess('Deliverable created successfully');
    } catch (err: any) {
      setError(err.response?.data?.message || 'Failed to create deliverable');
    } finally {
      setLoading(false);
    }
  };

  // Upload Document Revision
  const handleUploadDocument = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedDeliverable || !uploadFile) return;

    try {
      setUploading(true);
      const formData = new FormData();
      formData.append('file', uploadFile);
      if (versionNotes.trim()) {
        formData.append('versionNotes', versionNotes.trim());
      }

      await api.post(`/deliverables/${selectedDeliverable.id}/documents`, formData, {
        headers: { 'Content-Type': 'multipart/form-data' }
      });

      setUploadModalOpen(false);
      setUploadFile(null);
      setVersionNotes('');
      if (selectedProjectId) fetchDeliverables(selectedProjectId);
      showSuccess('Document version uploaded successfully');
    } catch (err: any) {
      setError(err.response?.data?.message || 'Upload failed. Ensure file is within 25MB and supported type.');
    } finally {
      setUploading(false);
    }
  };

  // Submit Deliverable for Review
  const handleSubmitDeliverable = async (deliv: DeliverableDto) => {
    try {
      setLoading(true);
      await api.post(`/deliverables/${deliv.id}/submit`);
      if (selectedProjectId) fetchDeliverables(selectedProjectId);
      showSuccess(`Deliverable '${deliv.name}' submitted for review`);
    } catch (err: any) {
      setError(err.response?.data?.message || 'Submission failed. Deliverable requires at least one document version.');
    } finally {
      setLoading(false);
    }
  };

  // Assign Reviewer
  const handleAssignReviewer = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedDeliverable || !assigneeId) return;

    try {
      setLoading(true);
      await api.post(`/deliverables/${selectedDeliverable.id}/reviews`, {
        reviewerId: Number(assigneeId),
        requestedDecisionDate: targetDecisionDate || null
      });
      setAssignModalOpen(false);
      setAssigneeId('');
      setTargetDecisionDate('');
      if (selectedProjectId) fetchDeliverables(selectedProjectId);
      showSuccess('Reviewer assigned and target decision date set');
    } catch (err: any) {
      setError(err.response?.data?.message || 'Failed to assign reviewer');
    } finally {
      setLoading(false);
    }
  };

  // Record Approval Decision
  const handleRecordDecision = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedDeliverable) return;

    try {
      setSubmittingDecision(true);
      await api.post(`/deliverables/${selectedDeliverable.id}/reviews/decision`, {
        decision: reviewDecision,
        comments: reviewComments.trim()
      });
      setReviewModalOpen(false);
      setReviewComments('');
      if (selectedProjectId) {
        fetchDeliverables(selectedProjectId);
        fetchProjectTasks(selectedProjectId);
      }
      showSuccess(`Decision '${reviewDecision}' recorded successfully`);
    } catch (err: any) {
      setError(err.response?.data?.message || 'Failed to record decision');
    } finally {
      setSubmittingDecision(false);
    }
  };

  // Open History & Document Versions Modal
  const handleOpenHistory = async (deliv: DeliverableDto) => {
    setSelectedDeliverable(deliv);
    setHistoryModalOpen(true);
    try {
      const [docsRes, approvalsRes] = await Promise.all([
        api.get(`/deliverables/${deliv.id}/versions`),
        api.get(`/deliverables/${deliv.id}/history`)
      ]);
      setDocuments(docsRes.data);
      setApprovalHistory(approvalsRes.data);
    } catch (err: any) {
      setError(err.response?.data?.message || 'Failed to load deliverable history');
    }
  };

  // Download Document
  const handleDownload = async (delivId: number, docId: number, filename: string) => {
    try {
      const response = await api.get(`/deliverables/${delivId}/documents/${docId}/download`, {
        responseType: 'blob'
      });
      const url = window.URL.createObjectURL(new Blob([response.data]));
      const link = document.createElement('a');
      link.href = url;
      link.setAttribute('download', filename);
      document.body.appendChild(link);
      link.click();
      link.remove();
    } catch (err: any) {
      setError(err.response?.data?.message || 'Failed to download document. Re-authentication may be required.');
    }
  };

  // View Delay Impact Analysis
  const handleViewDelayImpact = async (deliv: DeliverableDto) => {
    setSelectedDeliverable(deliv);
    setImpactModalOpen(true);
    try {
      const res = await api.get(`/deliverables/${deliv.id}/delay-impact`);
      setDelayImpact(res.data);
    } catch (err: any) {
      setError(err.response?.data?.message || 'Failed to analyze delay impact');
    }
  };

  // Configure Task Completion Gate
  const handleConfigureGate = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!gateConfig.taskId || !gateConfig.deliverableId) return;

    try {
      setLoading(true);
      await api.post(`/tasks/${gateConfig.taskId}/approval-gates`, {
        deliverableId: Number(gateConfig.deliverableId),
        gateType: gateConfig.gateType,
        isMandatory: gateConfig.isMandatory
      });
      setGateModalOpen(false);
      setGateConfig({
        taskId: '',
        deliverableId: '',
        gateType: 'COMPLETION_GATE',
        isMandatory: true
      });
      if (selectedProjectId) fetchProjectTasks(selectedProjectId);
      showSuccess('Task completion gate configured successfully');
    } catch (err: any) {
      setError(err.response?.data?.message || 'Failed to configure task approval gate');
    } finally {
      setLoading(false);
    }
  };

  // Filter Deliverables
  const filteredDeliverables = deliverables.filter((d) => {
    const matchesSearch = d.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
      (d.taskName && d.taskName.toLowerCase().includes(searchQuery.toLowerCase())) ||
      (d.description && d.description.toLowerCase().includes(searchQuery.toLowerCase()));
    const matchesStatus = statusFilter === 'ALL' || d.status === statusFilter;
    const matchesType = typeFilter === 'ALL' || d.deliverableType === typeFilter;
    if (activeTab === 'client_portal') {
      return matchesSearch && matchesStatus && matchesType && d.isClientVisible && !d.isConfidential;
    }
    return matchesSearch && matchesStatus && matchesType;
  });

  // KPI Metrics
  const totalCount = deliverables.length;
  const pendingSubmission = deliverables.filter(d => d.status === 'PLANNED' || d.status === 'IN_PROGRESS').length;
  const underReview = deliverables.filter(d => d.status === 'SUBMITTED' || d.status === 'UNDER_REVIEW').length;
  const approvedCount = deliverables.filter(d => d.status === 'APPROVED').length;
  const changesCount = deliverables.filter(d => d.status === 'CHANGES_REQUESTED').length;
  const overdueCount = deliverables.filter(d => d.isOverdue).length;

  const getStatusBadge = (status: DeliverableDto['status']) => {
    switch (status) {
      case 'APPROVED':
        return <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-xs font-semibold bg-emerald-100 text-emerald-800"><CheckCircle2 className="w-3 h-3" /> Approved</span>;
      case 'UNDER_REVIEW':
        return <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-xs font-semibold bg-purple-100 text-purple-800"><Clock className="w-3 h-3" /> Under Review</span>;
      case 'SUBMITTED':
        return <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-xs font-semibold bg-amber-100 text-amber-800"><UploadCloud className="w-3 h-3" /> Submitted</span>;
      case 'IN_PROGRESS':
        return <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-xs font-semibold bg-blue-100 text-blue-800"><RefreshCw className="w-3 h-3" /> In Progress</span>;
      case 'CHANGES_REQUESTED':
        return <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-xs font-semibold bg-orange-100 text-orange-800"><AlertTriangle className="w-3 h-3" /> Changes Requested</span>;
      case 'REJECTED':
        return <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-xs font-semibold bg-red-100 text-red-800"><AlertCircle className="w-3 h-3" /> Rejected</span>;
      case 'CANCELLED':
        return <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-xs font-semibold bg-gray-100 text-gray-700">Cancelled</span>;
      default:
        return <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-xs font-semibold bg-slate-100 text-slate-800">Planned</span>;
    }
  };

  return (
    <div className="p-8 max-w-7xl mx-auto space-y-6">
      {/* Header Banner */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 bg-white p-6 rounded-xl border border-gray-200 shadow-sm">
        <div>
          <div className="flex items-center gap-3">
            <div className="p-2.5 bg-blue-50 text-blue-600 rounded-lg">
              <FileCheck className="w-6 h-6" />
            </div>
            <div>
              <h1 className="text-2xl font-bold text-gray-900">Deliverables & Approval Workflows</h1>
              <p className="text-sm text-gray-500">
                Manage deliverables, document versions, client review gates, and schedule impact.
              </p>
            </div>
          </div>
        </div>

        {/* Project Selector & Actions */}
        <div className="flex items-center gap-3 flex-wrap">
          <div className="flex items-center gap-2">
            <span className="text-xs font-medium text-gray-500 uppercase">Project:</span>
            <select
              value={selectedProjectId || ''}
              onChange={(e) => setSelectedProjectId(Number(e.target.value))}
              className="px-3 py-2 bg-gray-50 border border-gray-300 rounded-lg text-sm font-semibold text-gray-800 focus:outline-none focus:ring-2 focus:ring-blue-500"
            >
              {projects.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.name} ({p.status})
                </option>
              ))}
            </select>
          </div>

          <button
            onClick={() => setCreateModalOpen(true)}
            className="flex items-center gap-2 px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white rounded-lg text-sm font-medium shadow-sm transition"
          >
            <Plus className="w-4 h-4" /> New Deliverable
          </button>

          <button
            onClick={() => setGateModalOpen(true)}
            className="flex items-center gap-2 px-3.5 py-2 bg-gray-100 hover:bg-gray-200 text-gray-700 rounded-lg text-sm font-medium transition"
          >
            <Lock className="w-4 h-4 text-purple-600" /> Configure Gate
          </button>
        </div>
      </div>

      {/* Notifications / Alerts */}
      {error && (
        <div className="flex items-center justify-between p-4 bg-red-50 border border-red-200 text-red-800 rounded-lg text-sm">
          <div className="flex items-center gap-2">
            <AlertCircle className="w-5 h-5 flex-shrink-0" />
            <span>{error}</span>
          </div>
          <button onClick={() => setError(null)} className="text-red-600 hover:text-red-800">
            <X className="w-4 h-4" />
          </button>
        </div>
      )}

      {successMsg && (
        <div className="flex items-center justify-between p-4 bg-emerald-50 border border-emerald-200 text-emerald-800 rounded-lg text-sm">
          <div className="flex items-center gap-2">
            <CheckCircle2 className="w-5 h-5 flex-shrink-0" />
            <span>{successMsg}</span>
          </div>
          <button onClick={() => setSuccessMsg(null)} className="text-emerald-600 hover:text-emerald-800">
            <X className="w-4 h-4" />
          </button>
        </div>
      )}

      {/* KPI Metrics Ribbon */}
      <div className="grid grid-cols-2 md:grid-cols-6 gap-3">
        <div className="bg-white p-4 rounded-xl border border-gray-200 shadow-sm">
          <div className="text-xs font-medium text-gray-500 uppercase">Total Deliverables</div>
          <div className="text-2xl font-bold text-gray-900 mt-1">{totalCount}</div>
        </div>
        <div className="bg-white p-4 rounded-xl border border-gray-200 shadow-sm">
          <div className="text-xs font-medium text-blue-600 uppercase">Pending Submission</div>
          <div className="text-2xl font-bold text-blue-700 mt-1">{pendingSubmission}</div>
        </div>
        <div className="bg-white p-4 rounded-xl border border-gray-200 shadow-sm">
          <div className="text-xs font-medium text-purple-600 uppercase">Under Review</div>
          <div className="text-2xl font-bold text-purple-700 mt-1">{underReview}</div>
        </div>
        <div className="bg-white p-4 rounded-xl border border-gray-200 shadow-sm">
          <div className="text-xs font-medium text-emerald-600 uppercase">Approved</div>
          <div className="text-2xl font-bold text-emerald-700 mt-1">{approvedCount}</div>
        </div>
        <div className="bg-white p-4 rounded-xl border border-gray-200 shadow-sm">
          <div className="text-xs font-medium text-orange-600 uppercase">Changes Req.</div>
          <div className="text-2xl font-bold text-orange-700 mt-1">{changesCount}</div>
        </div>
        <div className="bg-white p-4 rounded-xl border border-gray-200 shadow-sm">
          <div className="text-xs font-medium text-red-600 uppercase">Overdue Review</div>
          <div className="text-2xl font-bold text-red-700 mt-1">{overdueCount}</div>
        </div>
      </div>

      {/* Navigation Tabs */}
      <div className="border-b border-gray-200">
        <nav className="flex space-x-6 text-sm font-medium">
          <button
            onClick={() => setActiveTab('deliverables')}
            className={`pb-3 border-b-2 flex items-center gap-2 transition ${
              activeTab === 'deliverables'
                ? 'border-blue-600 text-blue-600 font-semibold'
                : 'border-transparent text-gray-500 hover:text-gray-700'
            }`}
          >
            <Layers className="w-4 h-4" /> All Deliverables ({deliverables.length})
          </button>
          <button
            onClick={() => setActiveTab('gates')}
            className={`pb-3 border-b-2 flex items-center gap-2 transition ${
              activeTab === 'gates'
                ? 'border-blue-600 text-blue-600 font-semibold'
                : 'border-transparent text-gray-500 hover:text-gray-700'
            }`}
          >
            <Lock className="w-4 h-4" /> Task Completion Gates ({taskGates.length})
          </button>
          <button
            onClick={() => setActiveTab('client_portal')}
            className={`pb-3 border-b-2 flex items-center gap-2 transition ${
              activeTab === 'client_portal'
                ? 'border-blue-600 text-blue-600 font-semibold'
                : 'border-transparent text-gray-500 hover:text-gray-700'
            }`}
          >
            <ShieldCheck className="w-4 h-4" /> Client Review Portal
          </button>
        </nav>
      </div>

      {/* Main Content Area */}
      {activeTab === 'gates' ? (
        /* Task Completion Gates Table */
        <div className="bg-white rounded-xl border border-gray-200 shadow-sm overflow-hidden">
          <div className="p-4 bg-gray-50 border-b border-gray-200 flex items-center justify-between">
            <div>
              <h3 className="font-semibold text-gray-800">Configured Task Approval Gates</h3>
              <p className="text-xs text-gray-500">Tasks cannot be completed until prerequisite deliverables are APPROVED.</p>
            </div>
            <button
              onClick={() => setGateModalOpen(true)}
              className="px-3 py-1.5 bg-blue-600 text-white rounded-lg text-xs font-semibold hover:bg-blue-700"
            >
              + Add Gate
            </button>
          </div>
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm text-gray-600">
              <thead className="bg-gray-100 text-xs uppercase text-gray-500 border-b border-gray-200">
                <tr>
                  <th className="px-6 py-3 font-semibold">Gated Task</th>
                  <th className="px-6 py-3 font-semibold">Prerequisite Deliverable</th>
                  <th className="px-6 py-3 font-semibold">Deliverable Status</th>
                  <th className="px-6 py-3 font-semibold">Gate Type</th>
                  <th className="px-6 py-3 font-semibold">Mandatory</th>
                  <th className="px-6 py-3 font-semibold">Gate Status</th>
                  <th className="px-6 py-3 font-semibold text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {taskGates.length === 0 ? (
                  <tr>
                    <td colSpan={7} className="px-6 py-8 text-center text-gray-400">
                      No task completion gates configured for this project.
                    </td>
                  </tr>
                ) : (
                  taskGates.map((gate) => (
                    <tr key={gate.id} className="hover:bg-gray-50 transition">
                      <td className="px-6 py-4 font-semibold text-gray-900">{gate.taskName}</td>
                      <td className="px-6 py-4">{gate.deliverableName}</td>
                      <td className="px-6 py-4">
                        <span className="font-mono text-xs px-2 py-0.5 rounded bg-gray-100">
                          {gate.deliverableStatus}
                        </span>
                      </td>
                      <td className="px-6 py-4 text-xs font-medium text-gray-500">{gate.gateType}</td>
                      <td className="px-6 py-4">
                        {gate.isMandatory ? (
                          <span className="text-xs font-semibold text-red-600">MANDATORY</span>
                        ) : (
                          <span className="text-xs text-gray-400">Optional</span>
                        )}
                      </td>
                      <td className="px-6 py-4">
                        {gate.isSatisfied ? (
                          <span className="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-xs font-semibold bg-emerald-100 text-emerald-800">
                            <Unlock className="w-3.5 h-3.5 text-emerald-600" /> Satisfied (Unblocked)
                          </span>
                        ) : (
                          <span className="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-xs font-semibold bg-red-100 text-red-800">
                            <Lock className="w-3.5 h-3.5 text-red-600" /> Blocked (Requires Approval)
                          </span>
                        )}
                      </td>
                      <td className="px-6 py-4 text-right">
                        <button
                          onClick={async () => {
                            if (!window.confirm('Delete this approval gate?')) return;
                            try {
                              await api.delete(`/approval-gates/${gate.id}`);
                              if (selectedProjectId) fetchProjectTasks(selectedProjectId);
                              showSuccess('Approval gate removed');
                            } catch (err: any) {
                              setError(err.response?.data?.message || 'Failed to remove gate');
                            }
                          }}
                          className="text-xs text-red-600 hover:text-red-800 font-medium"
                        >
                          Delete
                        </button>
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        </div>
      ) : (
        /* Deliverables List View (Standard or Client Portal) */
        <div className="space-y-4">
          {/* Search & Filter Toolbar */}
          <div className="flex flex-col md:flex-row gap-3 items-center justify-between bg-white p-4 rounded-xl border border-gray-200 shadow-sm">
            <div className="relative flex-1 w-full md:max-w-md">
              <Search className="w-4 h-4 text-gray-400 absolute left-3 top-3" />
              <input
                type="text"
                placeholder="Search deliverables, tasks, or notes..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                className="w-full pl-9 pr-4 py-2 border border-gray-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
            </div>

            <div className="flex items-center gap-3 w-full md:w-auto flex-wrap">
              <div className="flex items-center gap-1.5 text-xs text-gray-500">
                <Filter className="w-3.5 h-3.5" />
                <span>Status:</span>
              </div>
              <select
                value={statusFilter}
                onChange={(e) => setStatusFilter(e.target.value)}
                className="px-3 py-1.5 border border-gray-200 rounded-lg text-xs font-medium text-gray-700 bg-gray-50"
              >
                <option value="ALL">All Statuses</option>
                <option value="PLANNED">Planned</option>
                <option value="IN_PROGRESS">In Progress</option>
                <option value="SUBMITTED">Submitted</option>
                <option value="UNDER_REVIEW">Under Review</option>
                <option value="APPROVED">Approved</option>
                <option value="CHANGES_REQUESTED">Changes Requested</option>
                <option value="REJECTED">Rejected</option>
              </select>

              <select
                value={typeFilter}
                onChange={(e) => setTypeFilter(e.target.value)}
                className="px-3 py-1.5 border border-gray-200 rounded-lg text-xs font-medium text-gray-700 bg-gray-50"
              >
                <option value="ALL">All Output Types</option>
                <option value="TECHNICAL_DOCUMENT">Technical Document</option>
                <option value="DESIGN_SPECIFICATION">Design Specification</option>
                <option value="UI_PROTOTYPE">UI Prototype</option>
                <option value="SOURCE_CODE_PACKAGE">Source Code Package</option>
                <option value="TESTING_REPORT">Testing Report</option>
                <option value="PROJECT_REPORT">Project Report</option>
                <option value="CLIENT_HANDOVER">Client Handover</option>
                <option value="MATERIAL_DELIVERY">Material Delivery</option>
              </select>
            </div>
          </div>

          {/* Deliverables Table */}
          <div className="bg-white rounded-xl border border-gray-200 shadow-sm overflow-hidden">
            <div className="overflow-x-auto">
              <table className="w-full text-left text-sm text-gray-600">
                <thead className="bg-gray-100 text-xs uppercase text-gray-500 border-b border-gray-200">
                  <tr>
                    <th className="px-6 py-3 font-semibold">Deliverable & Type</th>
                    <th className="px-6 py-3 font-semibold">Linked Task</th>
                    <th className="px-6 py-3 font-semibold">Current Version</th>
                    <th className="px-6 py-3 font-semibold">Responsible / Reviewer</th>
                    <th className="px-6 py-3 font-semibold">Timeline & Due Date</th>
                    <th className="px-6 py-3 font-semibold">Status</th>
                    <th className="px-6 py-3 font-semibold text-right">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-100">
                  {filteredDeliverables.length === 0 ? (
                    <tr>
                      <td colSpan={7} className="px-6 py-12 text-center text-gray-400">
                        No deliverables found matching current filters.
                      </td>
                    </tr>
                  ) : (
                    filteredDeliverables.map((deliv) => {
                      const hasGate = taskGates.some(g => g.deliverableId === deliv.id);
                      return (
                        <tr key={deliv.id} className="hover:bg-gray-50 transition">
                          <td className="px-6 py-4">
                            <div className="flex items-start gap-2.5">
                              <div className="p-2 bg-blue-50 text-blue-600 rounded-lg mt-0.5">
                                <FileText className="w-4 h-4" />
                              </div>
                              <div>
                                <div className="font-semibold text-gray-900 flex items-center gap-2">
                                  {deliv.name}
                                  {deliv.isConfidential && (
                                    <span className="text-[10px] bg-red-100 text-red-700 px-1.5 py-0.2 rounded font-bold">
                                      CONFIDENTIAL
                                    </span>
                                  )}
                                  {deliv.isClientVisible && (
                                    <span className="text-[10px] bg-teal-100 text-teal-800 px-1.5 py-0.2 rounded font-medium">
                                      CLIENT VISIBLE
                                    </span>
                                  )}
                                </div>
                                <div className="text-xs text-gray-500 mt-0.5">
                                  {deliv.deliverableType.replace(/_/g, ' ')}
                                </div>
                              </div>
                            </div>
                          </td>

                          <td className="px-6 py-4">
                            {deliv.taskName ? (
                              <div className="flex items-center gap-1.5 text-xs text-gray-700">
                                {hasGate && (
                                  <span title="Task Gate Enforced">
                                    <Lock className="w-3.5 h-3.5 text-purple-600" />
                                  </span>
                                )}
                                <span className="font-medium">{deliv.taskName}</span>
                              </div>
                            ) : (
                              <span className="text-xs text-gray-400">None</span>
                            )}
                          </td>

                          <td className="px-6 py-4">
                            <button
                              onClick={() => handleOpenHistory(deliv)}
                              className="inline-flex items-center gap-1 px-2.5 py-1 bg-gray-100 hover:bg-gray-200 rounded text-xs font-mono font-semibold text-gray-800"
                            >
                              v{deliv.currentVersionNumber}
                              <ChevronRight className="w-3 h-3 text-gray-500" />
                            </button>
                            <div className="text-[11px] text-gray-400 mt-1">
                              {deliv.documentsCount} files • {deliv.approvalsCount} reviews
                            </div>
                          </td>

                          <td className="px-6 py-4 text-xs">
                            <div className="text-gray-800 font-medium">
                              Resp: {deliv.responsibleUsername || 'Unassigned'}
                            </div>
                            <div className="text-gray-500 mt-0.5">
                              Rev: {deliv.assignedReviewerUsername || 'Unassigned'}
                            </div>
                          </td>

                          <td className="px-6 py-4 text-xs">
                            <div className="flex items-center gap-1.5">
                              <Calendar className="w-3.5 h-3.5 text-gray-400" />
                              <span>Due: {deliv.requiredSubmissionDate || 'None'}</span>
                            </div>
                            {deliv.isOverdue && (
                              <button
                                onClick={() => handleViewDelayImpact(deliv)}
                                className="inline-flex items-center gap-1 text-red-600 font-semibold hover:underline mt-1"
                              >
                                <AlertTriangle className="w-3 h-3" /> Overdue (Analyze Impact)
                              </button>
                            )}
                          </td>

                          <td className="px-6 py-4">
                            {getStatusBadge(deliv.status)}
                          </td>

                          <td className="px-6 py-4 text-right">
                            <div className="flex items-center justify-end gap-2">
                              {/* Upload Doc Revision Button */}
                              <button
                                onClick={() => {
                                  setSelectedDeliverable(deliv);
                                  setUploadModalOpen(true);
                                }}
                                title="Upload Document Revision"
                                className="p-1.5 text-gray-600 hover:text-blue-600 hover:bg-blue-50 rounded transition"
                              >
                                <UploadCloud className="w-4 h-4" />
                              </button>

                              {/* Submit Button (if Planned/In Progress) */}
                              {(deliv.status === 'PLANNED' || deliv.status === 'IN_PROGRESS' || deliv.status === 'CHANGES_REQUESTED') && (
                                <button
                                  onClick={() => handleSubmitDeliverable(deliv)}
                                  title="Submit for Review"
                                  className="px-2 py-1 bg-amber-500 hover:bg-amber-600 text-white rounded text-xs font-semibold shadow-xs"
                                >
                                  Submit
                                </button>
                              )}

                              {/* Assign Reviewer */}
                              {(deliv.status === 'SUBMITTED' || deliv.status === 'UNDER_REVIEW') && !deliv.assignedReviewerId && (
                                <button
                                  onClick={() => {
                                    setSelectedDeliverable(deliv);
                                    setAssignModalOpen(true);
                                  }}
                                  title="Assign Reviewer"
                                  className="px-2 py-1 bg-purple-600 hover:bg-purple-700 text-white rounded text-xs font-semibold shadow-xs"
                                >
                                  Assign
                                </button>
                              )}

                              {/* Review & Decide Button */}
                              {(deliv.status === 'SUBMITTED' || deliv.status === 'UNDER_REVIEW') && (
                                <button
                                  onClick={() => {
                                    setSelectedDeliverable(deliv);
                                    setReviewModalOpen(true);
                                  }}
                                  title="Review & Record Decision"
                                  className="px-2.5 py-1 bg-emerald-600 hover:bg-emerald-700 text-white rounded text-xs font-semibold shadow-xs flex items-center gap-1"
                                >
                                  <UserCheck className="w-3.5 h-3.5" /> Decide
                                </button>
                              )}

                              {/* Version History Drawer */}
                              <button
                                onClick={() => handleOpenHistory(deliv)}
                                title="View History & Documents"
                                className="p-1.5 text-gray-600 hover:text-gray-900 hover:bg-gray-100 rounded transition"
                              >
                                <Eye className="w-4 h-4" />
                              </button>
                            </div>
                          </td>
                        </tr>
                      );
                    })
                  )}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      )}

      {/* ==================================================== */}
      {/* 1. Modal: Create Deliverable */}
      {/* ==================================================== */}
      {createModalOpen && (
        <div className="fixed inset-0 bg-black/50 z-50 flex items-center justify-center p-4">
          <div className="bg-white rounded-xl shadow-xl max-w-lg w-full p-6 space-y-4">
            <div className="flex items-center justify-between border-b pb-3">
              <h3 className="font-bold text-gray-900 text-lg">Define Project Deliverable</h3>
              <button onClick={() => setCreateModalOpen(false)} className="text-gray-400 hover:text-gray-600">
                <X className="w-5 h-5" />
              </button>
            </div>

            <form onSubmit={handleCreateDeliverable} className="space-y-4 text-sm">
              <div>
                <label className="block font-semibold text-gray-700 mb-1">Deliverable Name *</label>
                <input
                  type="text"
                  required
                  placeholder="e.g. Design Specification v1"
                  value={newDeliverable.name}
                  onChange={(e) => setNewDeliverable({ ...newDeliverable, name: e.target.value })}
                  className="w-full px-3 py-2 border rounded-lg focus:ring-2 focus:ring-blue-500"
                />
              </div>

              <div>
                <label className="block font-semibold text-gray-700 mb-1">Description</label>
                <textarea
                  rows={2}
                  placeholder="Summary of scope, requirements, and acceptance criteria..."
                  value={newDeliverable.description}
                  onChange={(e) => setNewDeliverable({ ...newDeliverable, description: e.target.value })}
                  className="w-full px-3 py-2 border rounded-lg focus:ring-2 focus:ring-blue-500"
                />
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block font-semibold text-gray-700 mb-1">Deliverable Type</label>
                  <select
                    value={newDeliverable.deliverableType}
                    onChange={(e) => setNewDeliverable({ ...newDeliverable, deliverableType: e.target.value })}
                    className="w-full px-3 py-2 border rounded-lg bg-gray-50"
                  >
                    <option value="TECHNICAL_DOCUMENT">Technical Document</option>
                    <option value="DESIGN_SPECIFICATION">Design Specification</option>
                    <option value="UI_PROTOTYPE">UI Prototype</option>
                    <option value="SOURCE_CODE_PACKAGE">Source Code Package</option>
                    <option value="TESTING_REPORT">Testing Report</option>
                    <option value="PROJECT_REPORT">Project Report</option>
                    <option value="CLIENT_HANDOVER">Client Handover</option>
                    <option value="MATERIAL_DELIVERY">Material Delivery</option>
                  </select>
                </div>

                <div>
                  <label className="block font-semibold text-gray-700 mb-1">Linked Task</label>
                  <select
                    value={newDeliverable.taskId}
                    onChange={(e) => setNewDeliverable({ ...newDeliverable, taskId: e.target.value })}
                    className="w-full px-3 py-2 border rounded-lg bg-gray-50"
                  >
                    <option value="">None (Project Level)</option>
                    {projectTasks.map((t) => (
                      <option key={t.id} value={t.id}>{t.name}</option>
                    ))}
                  </select>
                </div>
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block font-semibold text-gray-700 mb-1">Responsible User</label>
                  <select
                    value={newDeliverable.responsibleUserId}
                    onChange={(e) => setNewDeliverable({ ...newDeliverable, responsibleUserId: e.target.value })}
                    className="w-full px-3 py-2 border rounded-lg bg-gray-50"
                  >
                    <option value="">Select Responsible User</option>
                    {users.map((u) => (
                      <option key={u.id} value={u.id}>{u.username} ({u.role})</option>
                    ))}
                  </select>
                </div>

                <div>
                  <label className="block font-semibold text-gray-700 mb-1">Required Submission Date</label>
                  <input
                    type="date"
                    value={newDeliverable.requiredSubmissionDate}
                    onChange={(e) => setNewDeliverable({ ...newDeliverable, requiredSubmissionDate: e.target.value })}
                    className="w-full px-3 py-2 border rounded-lg bg-gray-50"
                  />
                </div>
              </div>

              <div className="pt-2 border-t space-y-2">
                <label className="flex items-center gap-2 text-gray-700 font-medium">
                  <input
                    type="checkbox"
                    checked={newDeliverable.isClientVisible}
                    onChange={(e) => setNewDeliverable({ ...newDeliverable, isClientVisible: e.target.checked })}
                    className="w-4 h-4 text-blue-600 rounded"
                  />
                  <span>Client Visible (Accessible in Client Review Portal)</span>
                </label>

                <label className="flex items-center gap-2 text-gray-700 font-medium">
                  <input
                    type="checkbox"
                    checked={newDeliverable.isConfidential}
                    onChange={(e) => setNewDeliverable({ ...newDeliverable, isConfidential: e.target.checked })}
                    className="w-4 h-4 text-red-600 rounded"
                  />
                  <span>Confidential (Restricted from external client access)</span>
                </label>
              </div>

              <div className="flex justify-end gap-3 pt-3">
                <button
                  type="button"
                  onClick={() => setCreateModalOpen(false)}
                  className="px-4 py-2 border border-gray-300 rounded-lg hover:bg-gray-50 font-medium"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={loading}
                  className="px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white rounded-lg font-medium shadow-sm disabled:opacity-50"
                >
                  {loading ? 'Creating...' : 'Create Deliverable'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* ==================================================== */}
      {/* 2. Modal: Upload Document Version */}
      {/* ==================================================== */}
      {uploadModalOpen && selectedDeliverable && (
        <div className="fixed inset-0 bg-black/50 z-50 flex items-center justify-center p-4">
          <div className="bg-white rounded-xl shadow-xl max-w-md w-full p-6 space-y-4">
            <div className="flex items-center justify-between border-b pb-3">
              <div>
                <h3 className="font-bold text-gray-900 text-lg">Upload Document Version</h3>
                <p className="text-xs text-gray-500">Deliverable: {selectedDeliverable.name}</p>
              </div>
              <button onClick={() => setUploadModalOpen(false)} className="text-gray-400 hover:text-gray-600">
                <X className="w-5 h-5" />
              </button>
            </div>

            <form onSubmit={handleUploadDocument} className="space-y-4 text-sm">
              <div>
                <label className="block font-semibold text-gray-700 mb-1">Select File *</label>
                <input
                  type="file"
                  required
                  onChange={(e) => {
                    if (e.target.files && e.target.files[0]) {
                      setUploadFile(e.target.files[0]);
                    }
                  }}
                  className="w-full text-xs text-gray-500 file:mr-3 file:py-2 file:px-3 file:rounded-md file:border-0 file:text-xs file:font-semibold file:bg-blue-50 file:text-blue-700 hover:file:bg-blue-100"
                />
                <p className="text-[11px] text-gray-400 mt-1">
                  Supported: PDF, PNG, JPG, DOCX, XLSX, ZIP, TXT, CSV, MD. Max 25 MB.
                </p>
              </div>

              <div>
                <label className="block font-semibold text-gray-700 mb-1">Version Notes / Changelog</label>
                <textarea
                  rows={2}
                  placeholder="Notes explaining revisions, changes made, or review feedback addressed..."
                  value={versionNotes}
                  onChange={(e) => setVersionNotes(e.target.value)}
                  className="w-full px-3 py-2 border rounded-lg focus:ring-2 focus:ring-blue-500"
                />
              </div>

              <div className="flex justify-end gap-3 pt-3">
                <button
                  type="button"
                  onClick={() => setUploadModalOpen(false)}
                  className="px-4 py-2 border border-gray-300 rounded-lg hover:bg-gray-50 font-medium"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={uploading || !uploadFile}
                  className="px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white rounded-lg font-medium shadow-sm disabled:opacity-50"
                >
                  {uploading ? 'Uploading & Computing Checksum...' : 'Upload Revision'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* ==================================================== */}
      {/* 3. Modal: Review & Approval Decision */}
      {/* ==================================================== */}
      {reviewModalOpen && selectedDeliverable && (
        <div className="fixed inset-0 bg-black/50 z-50 flex items-center justify-center p-4">
          <div className="bg-white rounded-xl shadow-xl max-w-lg w-full p-6 space-y-4">
            <div className="flex items-center justify-between border-b pb-3">
              <div>
                <h3 className="font-bold text-gray-900 text-lg">Record Approval Decision</h3>
                <p className="text-xs text-gray-500">Deliverable: {selectedDeliverable.name} (v{selectedDeliverable.currentVersionNumber})</p>
              </div>
              <button onClick={() => setReviewModalOpen(false)} className="text-gray-400 hover:text-gray-600">
                <X className="w-5 h-5" />
              </button>
            </div>

            <form onSubmit={handleRecordDecision} className="space-y-4 text-sm">
              {/* Anti-Self-Approval Note */}
              {selectedDeliverable.responsibleUsername === user?.username && user?.role !== 'ADMIN' && (
                <div className="p-3 bg-amber-50 border border-amber-200 rounded-lg text-amber-800 text-xs flex items-center gap-2">
                  <ShieldAlert className="w-4 h-4 text-amber-600 flex-shrink-0" />
                  <span>
                    Anti-Self-Approval Policy: You are marked as responsible for this deliverable. Another designated reviewer or Admin must approve it.
                  </span>
                </div>
              )}

              <div>
                <label className="block font-semibold text-gray-700 mb-2">Review Decision *</label>
                <div className="grid grid-cols-3 gap-3">
                  <button
                    type="button"
                    onClick={() => setReviewDecision('APPROVED')}
                    className={`py-2 px-3 rounded-lg border font-semibold flex items-center justify-center gap-1.5 transition ${
                      reviewDecision === 'APPROVED'
                        ? 'border-emerald-600 bg-emerald-50 text-emerald-800 ring-2 ring-emerald-500'
                        : 'border-gray-200 hover:bg-gray-50 text-gray-700'
                    }`}
                  >
                    <CheckCircle2 className="w-4 h-4 text-emerald-600" /> Approve
                  </button>

                  <button
                    type="button"
                    onClick={() => setReviewDecision('CHANGES_REQUESTED')}
                    className={`py-2 px-3 rounded-lg border font-semibold flex items-center justify-center gap-1.5 transition ${
                      reviewDecision === 'CHANGES_REQUESTED'
                        ? 'border-orange-600 bg-orange-50 text-orange-800 ring-2 ring-orange-500'
                        : 'border-gray-200 hover:bg-gray-50 text-gray-700'
                    }`}
                  >
                    <AlertTriangle className="w-4 h-4 text-orange-600" /> Changes
                  </button>

                  <button
                    type="button"
                    onClick={() => setReviewDecision('REJECTED')}
                    className={`py-2 px-3 rounded-lg border font-semibold flex items-center justify-center gap-1.5 transition ${
                      reviewDecision === 'REJECTED'
                        ? 'border-red-600 bg-red-50 text-red-800 ring-2 ring-red-500'
                        : 'border-gray-200 hover:bg-gray-50 text-gray-700'
                    }`}
                  >
                    <AlertCircle className="w-4 h-4 text-red-600" /> Reject
                  </button>
                </div>
              </div>

              <div>
                <label className="block font-semibold text-gray-700 mb-1">Review Comments & Feedback *</label>
                <textarea
                  rows={3}
                  required
                  placeholder="Detailed review notes, recommendations, or required changes..."
                  value={reviewComments}
                  onChange={(e) => setReviewComments(e.target.value)}
                  className="w-full px-3 py-2 border rounded-lg focus:ring-2 focus:ring-blue-500"
                />
              </div>

              <div className="flex justify-end gap-3 pt-3">
                <button
                  type="button"
                  onClick={() => setReviewModalOpen(false)}
                  className="px-4 py-2 border border-gray-300 rounded-lg hover:bg-gray-50 font-medium"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={submittingDecision || !reviewComments.trim()}
                  className="px-4 py-2 bg-emerald-600 hover:bg-emerald-700 text-white rounded-lg font-medium shadow-sm disabled:opacity-50"
                >
                  {submittingDecision ? 'Submitting...' : 'Confirm Decision'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* ==================================================== */}
      {/* 4. Modal: Assign Reviewer */}
      {/* ==================================================== */}
      {assignModalOpen && selectedDeliverable && (
        <div className="fixed inset-0 bg-black/50 z-50 flex items-center justify-center p-4">
          <div className="bg-white rounded-xl shadow-xl max-w-md w-full p-6 space-y-4">
            <div className="flex items-center justify-between border-b pb-3">
              <div>
                <h3 className="font-bold text-gray-900 text-lg">Assign Reviewer</h3>
                <p className="text-xs text-gray-500">Deliverable: {selectedDeliverable.name}</p>
              </div>
              <button onClick={() => setAssignModalOpen(false)} className="text-gray-400 hover:text-gray-600">
                <X className="w-5 h-5" />
              </button>
            </div>

            <form onSubmit={handleAssignReviewer} className="space-y-4 text-sm">
              <div>
                <label className="block font-semibold text-gray-700 mb-1">Select Reviewer *</label>
                <select
                  required
                  value={assigneeId}
                  onChange={(e) => setAssigneeId(e.target.value)}
                  className="w-full px-3 py-2 border rounded-lg bg-gray-50"
                >
                  <option value="">Select Reviewer</option>
                  {users.map((u) => (
                    <option key={u.id} value={u.id}>{u.username} ({u.role})</option>
                  ))}
                </select>
              </div>

              <div>
                <label className="block font-semibold text-gray-700 mb-1">Target Decision Date</label>
                <input
                  type="date"
                  value={targetDecisionDate}
                  onChange={(e) => setTargetDecisionDate(e.target.value)}
                  className="w-full px-3 py-2 border rounded-lg bg-gray-50"
                />
              </div>

              <div className="flex justify-end gap-3 pt-3">
                <button
                  type="button"
                  onClick={() => setAssignModalOpen(false)}
                  className="px-4 py-2 border border-gray-300 rounded-lg hover:bg-gray-50 font-medium"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={loading || !assigneeId}
                  className="px-4 py-2 bg-purple-600 hover:bg-purple-700 text-white rounded-lg font-medium shadow-sm disabled:opacity-50"
                >
                  {loading ? 'Assigning...' : 'Assign Reviewer'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* ==================================================== */}
      {/* 5. Modal: History & Document Versions Drawer */}
      {/* ==================================================== */}
      {historyModalOpen && selectedDeliverable && (
        <div className="fixed inset-0 bg-black/50 z-50 flex items-center justify-center p-4">
          <div className="bg-white rounded-xl shadow-xl max-w-2xl w-full p-6 space-y-5 max-h-[85vh] overflow-y-auto">
            <div className="flex items-center justify-between border-b pb-3">
              <div>
                <h3 className="font-bold text-gray-900 text-lg flex items-center gap-2">
                  <FileText className="w-5 h-5 text-blue-600" />
                  {selectedDeliverable.name} (History & Files)
                </h3>
                <p className="text-xs text-gray-500">Status: {selectedDeliverable.status} • Current Version: v{selectedDeliverable.currentVersionNumber}</p>
              </div>
              <button onClick={() => setHistoryModalOpen(false)} className="text-gray-400 hover:text-gray-600">
                <X className="w-5 h-5" />
              </button>
            </div>

            {/* Document Versions */}
            <div className="space-y-3">
              <h4 className="font-semibold text-sm text-gray-800 flex items-center gap-1.5">
                <Layers className="w-4 h-4 text-blue-600" /> Document Versions ({documents.length})
              </h4>
              <div className="border rounded-lg divide-y divide-gray-100 overflow-hidden">
                {documents.length === 0 ? (
                  <div className="p-4 text-center text-xs text-gray-400">No documents uploaded yet.</div>
                ) : (
                  documents.map((doc) => (
                    <div key={doc.id} className="p-3.5 flex items-center justify-between hover:bg-gray-50 transition">
                      <div className="space-y-1">
                        <div className="flex items-center gap-2">
                          <span className="font-mono text-xs font-bold px-2 py-0.5 rounded bg-blue-100 text-blue-800">
                            v{doc.versionNumber}
                          </span>
                          <span className="font-semibold text-gray-800 text-sm">{doc.originalFilename}</span>
                          <span className="text-xs text-gray-400">({(doc.fileSize / 1024).toFixed(1)} KB)</span>
                        </div>
                        {doc.versionNotes && (
                          <div className="text-xs text-gray-600 italic">Notes: "{doc.versionNotes}"</div>
                        )}
                        <div className="text-[11px] text-gray-400 flex items-center gap-2">
                          <span>Uploaded by {doc.uploadedBy} on {new Date(doc.uploadedAt).toLocaleDateString()}</span>
                          <span>•</span>
                          <span className="font-mono truncate max-w-[200px]" title={doc.checksum}>SHA: {doc.checksum}</span>
                        </div>
                      </div>

                      <button
                        onClick={() => handleDownload(selectedDeliverable.id, doc.id, doc.originalFilename)}
                        className="flex items-center gap-1.5 px-3 py-1.5 bg-gray-100 hover:bg-blue-50 hover:text-blue-600 text-gray-700 rounded-lg text-xs font-semibold transition"
                      >
                        <Download className="w-3.5 h-3.5" /> Download
                      </button>
                    </div>
                  ))
                )}
              </div>
            </div>

            {/* Approval Decision Trail */}
            <div className="space-y-3 pt-2">
              <h4 className="font-semibold text-sm text-gray-800 flex items-center gap-1.5">
                <CheckCircle2 className="w-4 h-4 text-emerald-600" /> Approval Decisions & Governance Trail ({approvalHistory.length})
              </h4>
              <div className="border rounded-lg divide-y divide-gray-100 overflow-hidden">
                {approvalHistory.length === 0 ? (
                  <div className="p-4 text-center text-xs text-gray-400">No review decisions recorded yet.</div>
                ) : (
                  approvalHistory.map((app) => (
                    <div key={app.id} className="p-3.5 space-y-1 hover:bg-gray-50 transition">
                      <div className="flex items-center justify-between">
                        <div className="flex items-center gap-2">
                          <span className={`px-2 py-0.5 text-xs font-bold rounded ${
                            app.decision === 'APPROVED'
                              ? 'bg-emerald-100 text-emerald-800'
                              : app.decision === 'CHANGES_REQUESTED'
                              ? 'bg-orange-100 text-orange-800'
                              : 'bg-red-100 text-red-800'
                          }`}>
                            {app.decision}
                          </span>
                          <span className="text-xs font-medium text-gray-700">
                            Version evaluated: v{app.documentVersionNumber || '1'}
                          </span>
                        </div>
                        <span className="text-xs text-gray-400">
                          {new Date(app.decisionDate).toLocaleString()}
                        </span>
                      </div>
                      {app.comments && (
                        <p className="text-xs text-gray-700 bg-gray-50 p-2 rounded border border-gray-100 mt-1">
                          "{app.comments}"
                        </p>
                      )}
                      <div className="text-[11px] text-gray-400 flex items-center gap-3 pt-1">
                        <span>Reviewer: <strong>{app.reviewerUsername}</strong></span>
                        {app.turnaroundHours != null && (
                          <span>Turnaround: {app.turnaroundHours} hrs</span>
                        )}
                        <span>Transition: {app.previousStatus} → {app.resultingStatus}</span>
                      </div>
                    </div>
                  ))
                )}
              </div>
            </div>

            <div className="flex justify-end pt-2">
              <button
                onClick={() => setHistoryModalOpen(false)}
                className="px-4 py-2 border rounded-lg text-sm font-medium hover:bg-gray-50"
              >
                Close
              </button>
            </div>
          </div>
        </div>
      )}

      {/* ==================================================== */}
      {/* 6. Modal: Configure Task Completion Gate */}
      {/* ==================================================== */}
      {gateModalOpen && (
        <div className="fixed inset-0 bg-black/50 z-50 flex items-center justify-center p-4">
          <div className="bg-white rounded-xl shadow-xl max-w-md w-full p-6 space-y-4">
            <div className="flex items-center justify-between border-b pb-3">
              <div>
                <h3 className="font-bold text-gray-900 text-lg">Configure Task Completion Gate</h3>
                <p className="text-xs text-gray-500">Prevent task completion until prerequisite deliverable is APPROVED.</p>
              </div>
              <button onClick={() => setGateModalOpen(false)} className="text-gray-400 hover:text-gray-600">
                <X className="w-5 h-5" />
              </button>
            </div>

            <form onSubmit={handleConfigureGate} className="space-y-4 text-sm">
              <div>
                <label className="block font-semibold text-gray-700 mb-1">Select Gated Task *</label>
                <select
                  required
                  value={gateConfig.taskId}
                  onChange={(e) => setGateConfig({ ...gateConfig, taskId: e.target.value })}
                  className="w-full px-3 py-2 border rounded-lg bg-gray-50"
                >
                  <option value="">Select Task to Gate</option>
                  {projectTasks.map((t) => (
                    <option key={t.id} value={t.id}>{t.name} ({t.status})</option>
                  ))}
                </select>
              </div>

              <div>
                <label className="block font-semibold text-gray-700 mb-1">Prerequisite Deliverable *</label>
                <select
                  required
                  value={gateConfig.deliverableId}
                  onChange={(e) => setGateConfig({ ...gateConfig, deliverableId: e.target.value })}
                  className="w-full px-3 py-2 border rounded-lg bg-gray-50"
                >
                  <option value="">Select Required Deliverable</option>
                  {deliverables.map((d) => (
                    <option key={d.id} value={d.id}>{d.name} ({d.status})</option>
                  ))}
                </select>
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block font-semibold text-gray-700 mb-1">Gate Type</label>
                  <select
                    value={gateConfig.gateType}
                    onChange={(e) => setGateConfig({ ...gateConfig, gateType: e.target.value as any })}
                    className="w-full px-3 py-2 border rounded-lg bg-gray-50"
                  >
                    <option value="COMPLETION_GATE">Completion Gate</option>
                    <option value="START_GATE">Start Gate</option>
                  </select>
                </div>

                <div className="flex items-center pt-6">
                  <label className="flex items-center gap-2 text-gray-700 font-medium">
                    <input
                      type="checkbox"
                      checked={gateConfig.isMandatory}
                      onChange={(e) => setGateConfig({ ...gateConfig, isMandatory: e.target.checked })}
                      className="w-4 h-4 text-blue-600 rounded"
                    />
                    <span>Mandatory Gate</span>
                  </label>
                </div>
              </div>

              <div className="flex justify-end gap-3 pt-3">
                <button
                  type="button"
                  onClick={() => setGateModalOpen(false)}
                  className="px-4 py-2 border border-gray-300 rounded-lg hover:bg-gray-50 font-medium"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={loading || !gateConfig.taskId || !gateConfig.deliverableId}
                  className="px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white rounded-lg font-medium shadow-sm disabled:opacity-50"
                >
                  {loading ? 'Configuring...' : 'Set Gate'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* ==================================================== */}
      {/* 7. Modal: Overdue Approval Delay Impact Analysis */}
      {/* ==================================================== */}
      {impactModalOpen && selectedDeliverable && delayImpact && (
        <div className="fixed inset-0 bg-black/50 z-50 flex items-center justify-center p-4">
          <div className="bg-white rounded-xl shadow-xl max-w-md w-full p-6 space-y-4">
            <div className="flex items-center justify-between border-b pb-3">
              <div>
                <h3 className="font-bold text-gray-900 text-lg flex items-center gap-2">
                  <AlertTriangle className="w-5 h-5 text-red-600" />
                  Approval Delay Impact Analysis
                </h3>
                <p className="text-xs text-gray-500">Deliverable: {delayImpact.deliverableName}</p>
              </div>
              <button onClick={() => setImpactModalOpen(false)} className="text-gray-400 hover:text-gray-600">
                <X className="w-5 h-5" />
              </button>
            </div>

            <div className="space-y-3 text-sm">
              <div className="grid grid-cols-2 gap-3 bg-red-50 p-3 rounded-lg border border-red-100">
                <div>
                  <div className="text-xs text-red-600 uppercase font-semibold">Days Overdue</div>
                  <div className="text-2xl font-bold text-red-800 mt-0.5">{delayImpact.daysOverdue} days</div>
                </div>
                <div>
                  <div className="text-xs text-red-600 uppercase font-semibold">Potential Slip</div>
                  <div className="text-2xl font-bold text-red-800 mt-0.5">+{delayImpact.potentialScheduleSlipDays} days</div>
                </div>
              </div>

              <div className="border rounded-lg p-3 space-y-2">
                <div className="flex items-center justify-between text-xs font-semibold">
                  <span className="text-gray-600">Critical Path Impact:</span>
                  {delayImpact.impactsCriticalPath ? (
                    <span className="text-red-600 font-bold flex items-center gap-1">
                      <AlertCircle className="w-3.5 h-3.5" /> YES (On Critical Path)
                    </span>
                  ) : (
                    <span className="text-emerald-600 font-bold">NO (Float Available)</span>
                  )}
                </div>

                <div className="flex items-center justify-between text-xs font-semibold">
                  <span className="text-gray-600">Blocked Tasks Count:</span>
                  <span className="text-gray-900">{delayImpact.blockedTasksCount} task(s)</span>
                </div>

                {delayImpact.blockedTaskNames.length > 0 && (
                  <div className="pt-2 border-t text-xs">
                    <span className="font-semibold text-gray-700">Blocked Dependent Tasks:</span>
                    <ul className="list-disc pl-5 mt-1 space-y-0.5 text-gray-600">
                      {delayImpact.blockedTaskNames.map((name, i) => (
                        <li key={i}>{name}</li>
                      ))}
                    </ul>
                  </div>
                )}
              </div>
            </div>

            <div className="flex justify-end pt-2">
              <button
                onClick={() => setImpactModalOpen(false)}
                className="px-4 py-2 border rounded-lg text-sm font-medium hover:bg-gray-50"
              >
                Close
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
export default DeliverablesManagement;
