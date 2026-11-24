import React, { useState, useEffect, useCallback } from 'react';
import { api } from '../services/api';
import { 
  Layers, AlertTriangle, AlertCircle, CheckCircle2, Plus, Trash2, 
  ArrowRight, ShieldAlert, Link as LinkIcon, RefreshCw, X, ChevronRight, Info
} from 'lucide-react';

export interface DependencyTask {
  id: number;
  name: string;
  status?: string;
  duration?: number;
}

interface PredecessorDetail {
  dependencyId: number;
  taskId: number;
  taskName: string;
  status: string;
  progress: number;
  isBlocking: boolean;
  estimatedDuration?: number;
  plannedStartDate?: string;
  plannedFinishDate?: string;
}

interface SuccessorDetail {
  dependencyId: number;
  taskId: number;
  taskName: string;
  status: string;
  progress: number;
}

interface ApprovalGateDetail {
  gateId: number;
  deliverableId: number;
  deliverableName: string;
  deliverableStatus: string;
  gateType: string;
  isMandatory: boolean;
  isSatisfied: boolean;
}

interface TaskDependencyDetails {
  taskId: number;
  taskName: string;
  status: string;
  progress: number;
  isBlocked: boolean;
  blockingReasons: string[];
  predecessors: PredecessorDetail[];
  successors: SuccessorDetail[];
  approvalGates: ApprovalGateDetail[];
}

interface ScheduleDependenciesViewProps {
  projectId: number;
  tasks: DependencyTask[];
  onDependenciesChanged: () => void;
}

export function ScheduleDependenciesView({
  projectId,
  tasks,
  onDependenciesChanged,
}: ScheduleDependenciesViewProps) {
  const [selectedTaskId, setSelectedTaskId] = useState<number | null>(tasks[0]?.id || null);
  const [dependencyDetails, setDependencyDetails] = useState<TaskDependencyDetails | null>(null);
  const [loading, setLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);
  const [candidatePredecessorId, setCandidatePredecessorId] = useState<string>('');
  const [searchFilter, setSearchFilter] = useState('');

  // Auto-dismiss notifications
  useEffect(() => {
    if (errorMessage) {
      const timer = setTimeout(() => setErrorMessage(null), 6000);
      return () => clearTimeout(timer);
    }
  }, [errorMessage]);

  useEffect(() => {
    if (successMessage) {
      const timer = setTimeout(() => setSuccessMessage(null), 4000);
      return () => clearTimeout(timer);
    }
  }, [successMessage]);

  // Keep selectedTaskId valid if tasks change
  useEffect(() => {
    if (tasks.length > 0) {
      if (!selectedTaskId || !tasks.some(t => t.id === selectedTaskId)) {
        setSelectedTaskId(tasks[0].id);
      }
    } else {
      setSelectedTaskId(null);
      setDependencyDetails(null);
    }
  }, [tasks, selectedTaskId]);

  // Fetch dependency details for selected task
  const loadDependencyDetails = useCallback(async (taskId: number) => {
    setLoading(true);
    setErrorMessage(null);
    try {
      const res = await api.get(`/tasks/${taskId}/dependency-details`);
      setDependencyDetails(res.data);
    } catch (err: any) {
      console.error('Error fetching dependency details:', err);
      setErrorMessage(err.response?.data?.message || 'Failed to load task dependency details.');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (selectedTaskId) {
      loadDependencyDetails(selectedTaskId);
    }
  }, [selectedTaskId, loadDependencyDetails]);

  // Add Predecessor Dependency
  const handleAddDependency = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedTaskId || !candidatePredecessorId) return;

    setSubmitting(true);
    setErrorMessage(null);
    setSuccessMessage(null);

    const predIdNum = Number(candidatePredecessorId);

    try {
      await api.post(`/tasks/${selectedTaskId}/dependencies`, {
        predecessorTaskId: predIdNum,
      });
      setSuccessMessage('Dependency added successfully. Project schedule recalculation initiated.');
      setCandidatePredecessorId('');
      await loadDependencyDetails(selectedTaskId);
      onDependenciesChanged();
    } catch (err: any) {
      const msg = err.response?.data?.message || err.message || 'Failed to add dependency.';
      setErrorMessage(msg);
    } finally {
      setSubmitting(false);
    }
  };

  // Remove Dependency
  const handleRemoveDependency = async (dependencyId: number, predName: string) => {
    if (!window.confirm(`Are you sure you want to remove the dependency on "${predName}"?`)) {
      return;
    }

    setSubmitting(true);
    setErrorMessage(null);
    setSuccessMessage(null);

    try {
      await api.delete(`/dependencies/${dependencyId}`);
      setSuccessMessage(`Removed dependency on "${predName}".`);
      if (selectedTaskId) {
        await loadDependencyDetails(selectedTaskId);
      }
      onDependenciesChanged();
    } catch (err: any) {
      setErrorMessage(err.response?.data?.message || 'Failed to remove dependency.');
    } finally {
      setSubmitting(false);
    }
  };

  const filteredTasks = tasks.filter(t => 
    t.name.toLowerCase().includes(searchFilter.toLowerCase())
  );

  const selectedTaskObj = tasks.find(t => t.id === selectedTaskId);

  // Available candidate predecessors (excluding self and existing predecessors)
  const existingPredTaskIds = new Set(dependencyDetails?.predecessors?.map(p => p.taskId) || []);
  const availableCandidates = tasks.filter(
    t => t.id !== selectedTaskId && !existingPredTaskIds.has(t.id)
  );

  return (
    <div className="space-y-6">
      {/* Informative Header Banner */}
      <div className="bg-white p-5 rounded-xl border border-gray-200 shadow-sm flex flex-wrap items-center justify-between gap-4">
        <div>
          <h3 className="text-base font-bold text-gray-900 flex items-center">
            <Layers className="w-5 h-5 mr-2 text-indigo-600" />
            Task Dependency Architecture & Precedence Graph
          </h3>
          <p className="text-xs text-gray-500 mt-1">
            Manage strict finish-to-start precedence relationships. Cycles and circular dependencies are deterministically rejected.
          </p>
        </div>
        <div className="flex items-center gap-2">
          <span className="text-xs font-semibold px-2.5 py-1 rounded-full bg-slate-100 text-slate-700">
            {tasks.length} Total Project Tasks
          </span>
        </div>
      </div>

      {/* Global Alerts */}
      {errorMessage && (
        <div role="alert" className="p-3.5 bg-rose-50 border border-rose-200 rounded-xl flex items-start justify-between text-rose-800 text-xs animate-fadeIn">
          <div className="flex items-start gap-2.5">
            <AlertCircle className="w-4 h-4 text-rose-600 flex-shrink-0 mt-0.5" />
            <div>
              <span className="font-bold">Validation Error:</span> {errorMessage}
            </div>
          </div>
          <button 
            onClick={() => setErrorMessage(null)} 
            className="text-rose-500 hover:text-rose-800 p-0.5 rounded transition"
            aria-label="Dismiss error"
          >
            <X className="w-4 h-4" />
          </button>
        </div>
      )}

      {successMessage && (
        <div role="status" className="p-3.5 bg-emerald-50 border border-emerald-200 rounded-xl flex items-start justify-between text-emerald-800 text-xs animate-fadeIn">
          <div className="flex items-center gap-2.5">
            <CheckCircle2 className="w-4 h-4 text-emerald-600 flex-shrink-0" />
            <span>{successMessage}</span>
          </div>
          <button 
            onClick={() => setSuccessMessage(null)} 
            className="text-emerald-500 hover:text-emerald-800 p-0.5 rounded transition"
            aria-label="Dismiss message"
          >
            <X className="w-4 h-4" />
          </button>
        </div>
      )}

      {/* Main 2-Column Split: Task Selector vs Dependency Inspector */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
        {/* Left Column (5 cols): Project Task List */}
        <div className="lg:col-span-4 bg-white rounded-xl border border-gray-200 shadow-sm flex flex-col overflow-hidden max-h-[600px]">
          <div className="p-3.5 bg-gray-50 border-b border-gray-200 space-y-2">
            <div className="flex items-center justify-between">
              <span className="text-xs font-bold text-gray-700 uppercase tracking-wider">Project Tasks</span>
              <span className="text-[11px] text-gray-500">{filteredTasks.length} tasks</span>
            </div>
            <input
              type="text"
              placeholder="Search tasks..."
              value={searchFilter}
              onChange={(e) => setSearchFilter(e.target.value)}
              className="w-full text-xs px-3 py-1.5 border border-gray-300 rounded-lg focus:outline-none focus:ring-1 focus:ring-blue-500"
            />
          </div>

          <div className="flex-1 overflow-y-auto divide-y divide-gray-100">
            {filteredTasks.length === 0 ? (
              <div className="p-6 text-center text-xs text-gray-400 italic">No tasks match filter</div>
            ) : (
              filteredTasks.map((t) => {
                const isSelected = t.id === selectedTaskId;
                return (
                  <button
                    key={t.id}
                    onClick={() => setSelectedTaskId(t.id)}
                    className={`w-full text-left p-3 text-xs flex items-center justify-between transition-colors ${
                      isSelected 
                        ? 'bg-blue-50/80 font-bold border-l-4 border-l-blue-600 text-blue-900' 
                        : 'hover:bg-gray-50 text-gray-700'
                    }`}
                  >
                    <div className="truncate pr-2">
                      <div className="truncate font-semibold">{t.name}</div>
                      <div className="text-[10px] text-gray-400 mt-0.5">
                        {t.status || 'TODO'} {t.duration ? `• ${t.duration}d` : ''}
                      </div>
                    </div>
                    <ChevronRight className={`w-3.5 h-3.5 flex-shrink-0 ${isSelected ? 'text-blue-600' : 'text-gray-300'}`} />
                  </button>
                );
              })
            )}
          </div>
        </div>

        {/* Right Column (8 cols): Selected Task Dependency Details & Editor */}
        <div className="lg:col-span-8 space-y-5">
          {loading && !dependencyDetails ? (
            <div className="bg-white rounded-xl border border-gray-200 p-12 text-center text-gray-400 text-xs">
              <RefreshCw className="w-5 h-5 mx-auto mb-2 animate-spin text-blue-600" />
              Loading dependency details...
            </div>
          ) : selectedTaskObj && dependencyDetails ? (
            <div className="bg-white rounded-xl border border-gray-200 shadow-sm p-6 space-y-6">
              {/* Task Header & Blocking Status */}
              <div className="flex flex-wrap items-start justify-between gap-3 pb-4 border-b border-gray-100">
                <div>
                  <span className="text-[10px] font-bold uppercase tracking-wider text-gray-400">Inspecting Task #{dependencyDetails.taskId}</span>
                  <h4 className="text-lg font-bold text-gray-900 mt-0.5">{dependencyDetails.taskName}</h4>
                  <div className="flex items-center gap-2 mt-1.5 text-xs">
                    <span className="px-2 py-0.5 rounded text-[11px] font-semibold bg-gray-100 text-gray-700">
                      Status: {dependencyDetails.status}
                    </span>
                    <span className="px-2 py-0.5 rounded text-[11px] font-semibold bg-gray-100 text-gray-700">
                      Progress: {dependencyDetails.progress}%
                    </span>
                  </div>
                </div>

                {/* Blocked Status Badge */}
                <div>
                  {dependencyDetails.isBlocked ? (
                    <div className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-rose-50 border border-rose-200 text-rose-700 font-bold text-xs">
                      <ShieldAlert className="w-4 h-4 text-rose-600 flex-shrink-0" />
                      <span>Blocked from Starting / Completion</span>
                    </div>
                  ) : (
                    <div className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-emerald-50 border border-emerald-200 text-emerald-700 font-bold text-xs">
                      <CheckCircle2 className="w-4 h-4 text-emerald-600 flex-shrink-0" />
                      <span>Prerequisites Satisfied (Unblocked)</span>
                    </div>
                  )}
                </div>
              </div>

              {/* Blocking Reasons Breakdown */}
              {dependencyDetails.isBlocked && dependencyDetails.blockingReasons.length > 0 && (
                <div className="p-3.5 bg-rose-50/70 border border-rose-200 rounded-xl space-y-1.5">
                  <div className="text-xs font-bold text-rose-900 flex items-center">
                    <AlertTriangle className="w-4 h-4 mr-1.5 text-rose-600" />
                    Unsatisfied Prerequisites Blocking This Task:
                  </div>
                  <ul className="list-disc list-inside text-xs text-rose-800 space-y-0.5 pl-1">
                    {dependencyDetails.blockingReasons.map((reason, idx) => (
                      <li key={idx} className="leading-relaxed">{reason}</li>
                    ))}
                  </ul>
                </div>
              )}

              {/* Predecessors Section (Tasks that MUST finish before this task) */}
              <div className="space-y-3">
                <div className="flex items-center justify-between">
                  <h5 className="text-xs font-bold uppercase tracking-wider text-gray-700 flex items-center">
                    <LinkIcon className="w-3.5 h-3.5 mr-1.5 text-blue-600" />
                    Mandatory Predecessors ({dependencyDetails.predecessors.length})
                  </h5>
                  <span className="text-[11px] text-gray-400">Must finish before this task can start</span>
                </div>

                {dependencyDetails.predecessors.length === 0 ? (
                  <div className="p-4 bg-gray-50 rounded-xl text-center text-xs text-gray-400 italic">
                    No predecessor dependencies. This task can begin as soon as the project starts.
                  </div>
                ) : (
                  <div className="divide-y divide-gray-100 border border-gray-200 rounded-xl overflow-hidden">
                    {dependencyDetails.predecessors.map((pred) => (
                      <div key={pred.dependencyId} className="p-3 bg-white flex items-center justify-between text-xs hover:bg-gray-50 transition">
                        <div className="flex items-center space-x-3">
                          <button
                            onClick={() => setSelectedTaskId(pred.taskId)}
                            className="font-bold text-gray-900 hover:text-blue-600 text-left transition"
                            title="Click to inspect this predecessor task"
                          >
                            {pred.taskName}
                          </button>
                          <span className={`px-2 py-0.5 rounded text-[10px] font-bold ${
                            pred.status === 'COMPLETED' 
                              ? 'bg-emerald-100 text-emerald-800' 
                              : 'bg-amber-100 text-amber-800'
                          }`}>
                            {pred.status} ({pred.progress}%)
                          </span>
                          {pred.isBlocking && (
                            <span className="px-1.5 py-0.5 rounded text-[10px] font-bold bg-rose-100 text-rose-700">
                              Blocking
                            </span>
                          )}
                        </div>

                        <div className="flex items-center space-x-2">
                          <button
                            onClick={() => handleRemoveDependency(pred.dependencyId, pred.taskName)}
                            disabled={submitting}
                            className="p-1.5 text-gray-400 hover:text-rose-600 hover:bg-rose-50 rounded-lg transition"
                            title="Delete this dependency"
                            aria-label={`Remove dependency on ${pred.taskName}`}
                          >
                            <Trash2 className="w-3.5 h-3.5" />
                          </button>
                        </div>
                      </div>
                    ))}
                  </div>
                )}

                {/* Add New Predecessor Form */}
                <form onSubmit={handleAddDependency} className="pt-2 flex flex-wrap items-center gap-2">
                  <div className="flex-1 min-w-[200px]">
                    <select
                      value={candidatePredecessorId}
                      onChange={(e) => setCandidatePredecessorId(e.target.value)}
                      className="w-full text-xs px-3 py-2 bg-white border border-gray-300 rounded-lg focus:outline-none focus:ring-1 focus:ring-blue-500 font-medium"
                      disabled={submitting || availableCandidates.length === 0}
                    >
                      <option value="">
                        {availableCandidates.length === 0 ? 'No other project tasks available' : '+ Select Predecessor Task to Add...'}
                      </option>
                      {availableCandidates.map((c) => (
                        <option key={c.id} value={c.id}>
                          {c.name} ({c.status || 'TODO'})
                        </option>
                      ))}
                    </select>
                  </div>
                  <button
                    type="submit"
                    disabled={!candidatePredecessorId || submitting}
                    className="flex items-center px-3 py-2 bg-blue-600 hover:bg-blue-700 text-white rounded-lg text-xs font-semibold shadow-xs disabled:opacity-40 transition"
                  >
                    <Plus className="w-3.5 h-3.5 mr-1" />
                    {submitting ? 'Validating...' : 'Add Predecessor'}
                  </button>
                </form>
              </div>

              {/* Successors Section (Tasks that depend on this task) */}
              <div className="space-y-3 pt-2 border-t border-gray-100">
                <div className="flex items-center justify-between">
                  <h5 className="text-xs font-bold uppercase tracking-wider text-gray-700 flex items-center">
                    <ArrowRight className="w-3.5 h-3.5 mr-1.5 text-indigo-600" />
                    Dependent Successors ({dependencyDetails.successors.length})
                  </h5>
                  <span className="text-[11px] text-gray-400">Tasks waiting on this task to finish</span>
                </div>

                {dependencyDetails.successors.length === 0 ? (
                  <div className="p-4 bg-gray-50 rounded-xl text-center text-xs text-gray-400 italic">
                    No downstream successors. This task is a terminal deliverable milestone.
                  </div>
                ) : (
                  <div className="divide-y divide-gray-100 border border-gray-200 rounded-xl overflow-hidden">
                    {dependencyDetails.successors.map((succ) => (
                      <div key={succ.dependencyId} className="p-3 bg-white flex items-center justify-between text-xs hover:bg-gray-50 transition">
                        <button
                          onClick={() => setSelectedTaskId(succ.taskId)}
                          className="font-bold text-gray-900 hover:text-blue-600 text-left transition"
                          title="Click to inspect this successor task"
                        >
                          {succ.taskName}
                        </button>
                        <span className="px-2 py-0.5 rounded text-[10px] font-semibold bg-gray-100 text-gray-700">
                          Status: {succ.status}
                        </span>
                      </div>
                    ))}
                  </div>
                )}
              </div>

              {/* Deliverable Approval Gates Section (if any) */}
              {dependencyDetails.approvalGates && dependencyDetails.approvalGates.length > 0 && (
                <div className="space-y-3 pt-2 border-t border-gray-100">
                  <h5 className="text-xs font-bold uppercase tracking-wider text-gray-700">
                    Required Approval Gates ({dependencyDetails.approvalGates.length})
                  </h5>
                  <div className="space-y-2">
                    {dependencyDetails.approvalGates.map((gate) => (
                      <div key={gate.gateId} className="p-3 bg-slate-50 border border-slate-200 rounded-xl flex items-center justify-between text-xs">
                        <div>
                          <span className="font-semibold text-gray-800">{gate.deliverableName}</span>
                          <span className="text-gray-400 ml-2">({gate.gateType})</span>
                        </div>
                        <span className={`px-2 py-0.5 rounded text-[10px] font-bold ${
                          gate.isSatisfied ? 'bg-emerald-100 text-emerald-800' : 'bg-amber-100 text-amber-800'
                        }`}>
                          {gate.deliverableStatus}
                        </span>
                      </div>
                    ))}
                  </div>
                </div>
              )}
            </div>
          ) : (
            <div className="bg-white rounded-xl border border-gray-200 p-12 text-center text-gray-400 text-xs italic">
              Select a task from the list to view and manage dependencies.
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
