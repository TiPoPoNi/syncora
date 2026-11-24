import React, { useState, useEffect, useCallback } from 'react';
import { api } from '../services/api';
import {
  Sparkles,
  CheckCircle,
  XCircle,
  ArrowRight,
  TrendingUp,
  AlertTriangle,
  Clock,
  UserCheck,
  RefreshCw,
  Sliders,
  History,
  ShieldCheck,
  Building,
  User,
  Zap,
} from 'lucide-react';

export interface OptimizationRun {
  id: number;
  projectId: number;
  projectName: string;
  initiatedBy: string;
  selectedObjective: string;
  status: string;
  candidatesEvaluated: number;
  feasibleCandidatesCount: number;
  inputScheduleVersion: string;
  createdAt: string;
}

export interface ScheduleAlternative {
  id: number;
  optimizationRunId: number;
  alternativeRank: number;
  title: string;
  summary: string;
  proposedReassignments: string;
  proposedScheduleChanges: string;
  projectCompletionForecast: string;
  deadlineStatus: string;
  workingDaysSaved: number;
  conflictsResolved: number;
  remainingConflicts: number;
  disruptionCount: number;
  objectiveScore: number;
  constraintStatus: string;
  tradeOffs: string;
}

export interface OptimizationRecommendation {
  id: number;
  projectId: number;
  optimizationRunId: number;
  alternativeId: number;
  category: string;
  summary: string;
  explanation: string;
  estimatedImpact: string;
  status: 'PROPOSED' | 'APPROVED' | 'REJECTED' | 'APPLIED' | 'EXPIRED';
  approvedBy?: string;
  approvedAt?: string;
  appliedAt?: string;
  createdAt: string;
  alternative?: ScheduleAlternative;
}

export interface AuditLog {
  id: number;
  action: string;
  performedBy: string;
  details: string;
  createdAt: string;
}

export interface ResourceCandidate {
  id: number;
  name: string;
  type: string;
  skillsOrServices: string;
  experience?: number;
  dailyCapacity?: number;
  available: boolean;
  activeAssignmentsCount: number;
  hasRequiredSkills: boolean;
  suitabilityScore: number;
  recommendationReason: string;
}

export interface TaskResourceRecommendations {
  taskId: number;
  taskName: string;
  durationDays: number;
  requiredSkills: string;
  plannedStartDate: string;
  plannedFinishDate: string;
  currentAssignee?: string;
  currentAssigneeType?: string;
  candidateWorkers: ResourceCandidate[];
  candidateVendors: ResourceCandidate[];
}

interface OptimizationDashboardProps {
  projectId: number;
  tasks: { id: number; name: string }[];
  onScheduleUpdated: () => void;
}

export function OptimizationDashboard({ projectId, tasks, onScheduleUpdated }: OptimizationDashboardProps) {
  const [runs, setRuns] = useState<OptimizationRun[]>([]);
  const [recommendations, setRecommendations] = useState<OptimizationRecommendation[]>([]);
  const [alternatives, setAlternatives] = useState<ScheduleAlternative[]>([]);
  const [auditLogs, setAuditLogs] = useState<AuditLog[]>([]);
  const [selectedObjective, setSelectedObjective] = useState<string>('BALANCED');

  const [loading, setLoading] = useState(false);
  const [optimizing, setOptimizing] = useState(false);
  const [actionLoading, setActionLoading] = useState(false);

  // Task resource recommendations modal
  const [selectedTaskId, setSelectedTaskId] = useState<number | null>(null);
  const [taskRecommendations, setTaskRecommendations] = useState<TaskResourceRecommendations | null>(null);
  const [loadingTaskRecs, setLoadingTaskRecs] = useState(false);

  // Active alternative for comparison view
  const [selectedAlt, setSelectedAlt] = useState<ScheduleAlternative | null>(null);

  const loadOptimizationData = useCallback(async () => {
    setLoading(true);
    try {
      const [runsRes, recsRes, logsRes] = await Promise.all([
        api.get(`/projects/${projectId}/optimization/runs`),
        api.get(`/projects/${projectId}/recommendations`),
        api.get(`/projects/${projectId}/optimization/audit-logs`),
      ]);

      setRuns(runsRes.data);
      setRecommendations(recsRes.data);
      setAuditLogs(logsRes.data);

      if (runsRes.data.length > 0) {
        const altsRes = await api.get(`/optimization/runs/${runsRes.data[0].id}/alternatives`);
        setAlternatives(altsRes.data);
        if (altsRes.data.length > 0) {
          setSelectedAlt(altsRes.data[0]);
        }
      }
    } catch (e) {
      console.error('Error loading optimization data:', e);
    } finally {
      setLoading(false);
    }
  }, [projectId]);

  useEffect(() => {
    loadOptimizationData();
  }, [loadOptimizationData]);

  // Run Optimization
  const handleRunOptimization = async () => {
    setOptimizing(true);
    try {
      await api.post(`/projects/${projectId}/optimization/runs`, {
        selectedObjective,
      });
      await loadOptimizationData();
      onScheduleUpdated();
    } catch (e: any) {
      alert('Optimization error: ' + (e.response?.data?.message || e.message));
    } finally {
      setOptimizing(false);
    }
  };

  // Approve Recommendation
  const handleApprove = async (recId: number) => {
    setActionLoading(true);
    try {
      await api.post(`/recommendations/${recId}/approve`);
      await loadOptimizationData();
    } catch (e: any) {
      alert('Approval error: ' + (e.response?.data?.message || e.message));
    } finally {
      setActionLoading(false);
    }
  };

  // Reject Recommendation
  const handleReject = async (recId: number) => {
    setActionLoading(true);
    try {
      await api.post(`/recommendations/${recId}/reject`);
      await loadOptimizationData();
    } catch (e: any) {
      alert('Reject error: ' + (e.response?.data?.message || e.message));
    } finally {
      setActionLoading(false);
    }
  };

  // Apply Recommendation
  const handleApply = async (recId: number) => {
    setActionLoading(true);
    try {
      await api.post(`/recommendations/${recId}/apply`);
      await loadOptimizationData();
      onScheduleUpdated();
      alert('Recommendation successfully applied to schedule!');
    } catch (e: any) {
      alert('Apply error: ' + (e.response?.data?.message || e.message));
    } finally {
      setActionLoading(false);
    }
  };

  // Fetch Task Resource Recommendations
  const openTaskResourceModal = async (taskId: number) => {
    setSelectedTaskId(taskId);
    setLoadingTaskRecs(true);
    try {
      const res = await api.get(`/tasks/${taskId}/resource-recommendations`);
      setTaskRecommendations(res.data);
    } catch (e) {
      console.error('Error loading task recommendations:', e);
    } finally {
      setLoadingTaskRecs(false);
    }
  };

  const activeRecommendation = recommendations.length > 0 ? recommendations[0] : null;

  return (
    <div className="space-y-6">
      {/* 1. Header Toolbar */}
      <div className="bg-gradient-to-r from-indigo-900 via-indigo-800 to-slate-900 rounded-2xl p-6 text-white shadow-md">
        <div className="flex flex-wrap items-center justify-between gap-4">
          <div>
            <div className="flex items-center space-x-2">
              <span className="p-2 bg-indigo-500/20 border border-indigo-400/30 rounded-xl">
                <Sparkles className="w-6 h-6 text-indigo-300" />
              </span>
              <div>
                <h2 className="text-xl font-black tracking-tight">Resource-Aware Schedule Optimizer</h2>
                <p className="text-xs text-indigo-200 mt-0.5">
                  Deterministic candidate generation, hard constraint validation, multi-objective ranking & automated recovery.
                </p>
              </div>
            </div>
          </div>

          <div className="flex items-center space-x-3">
            <div className="bg-indigo-950/60 border border-indigo-700/50 rounded-xl px-3 py-1.5 flex items-center space-x-2 text-xs">
              <Sliders className="w-3.5 h-3.5 text-indigo-300" />
              <label className="text-indigo-300 font-bold uppercase text-[10px]">Objective:</label>
              <select
                value={selectedObjective}
                onChange={(e) => setSelectedObjective(e.target.value)}
                className="bg-transparent text-white font-semibold focus:outline-hidden cursor-pointer"
              >
                <option value="BALANCED" className="bg-gray-800 text-white">Balanced (Default)</option>
                <option value="DEADLINE_FIRST" className="bg-gray-800 text-white">Deadline-First</option>
                <option value="MINIMIZE_CONFLICTS" className="bg-gray-800 text-white">Minimize Conflicts</option>
              </select>
            </div>

            <button
              onClick={handleRunOptimization}
              disabled={optimizing}
              className="flex items-center bg-indigo-500 hover:bg-indigo-600 text-white px-5 py-2 rounded-xl font-bold text-sm shadow-sm transition disabled:opacity-50"
            >
              <RefreshCw className={`w-4 h-4 mr-2 ${optimizing ? 'animate-spin' : ''}`} />
              {optimizing ? 'Optimizing...' : 'Run Optimization'}
            </button>
          </div>
        </div>

        {/* Quick Stats Bar */}
        {runs.length > 0 && (
          <div className="grid grid-cols-2 md:grid-cols-4 gap-3 mt-6 pt-5 border-t border-indigo-700/40 text-xs">
            <div className="bg-indigo-950/40 p-2.5 rounded-xl border border-indigo-800/40">
              <span className="text-indigo-300 block text-[11px]">Latest Run Status</span>
              <span className="text-sm font-black text-emerald-400 flex items-center mt-0.5">
                <CheckCircle className="w-3.5 h-3.5 mr-1" /> {runs[0].status}
              </span>
            </div>
            <div className="bg-indigo-950/40 p-2.5 rounded-xl border border-indigo-800/40">
              <span className="text-indigo-300 block text-[11px]">Candidates Evaluated</span>
              <span className="text-sm font-black text-white mt-0.5">{runs[0].candidatesEvaluated} Alternatives</span>
            </div>
            <div className="bg-indigo-950/40 p-2.5 rounded-xl border border-indigo-800/40">
              <span className="text-indigo-300 block text-[11px]">Feasible Candidates</span>
              <span className="text-sm font-black text-indigo-200 mt-0.5">{runs[0].feasibleCandidatesCount} Valid</span>
            </div>
            <div className="bg-indigo-950/40 p-2.5 rounded-xl border border-indigo-800/40">
              <span className="text-indigo-300 block text-[11px]">Initiated By</span>
              <span className="text-sm font-black text-white mt-0.5">{runs[0].initiatedBy}</span>
            </div>
          </div>
        )}
      </div>

      {/* 2. Top Recommended Alternative Action Panel */}
      {activeRecommendation && (
        <div className="bg-white rounded-2xl border border-indigo-200 shadow-sm p-6 relative overflow-hidden">
          <div className="flex flex-wrap items-center justify-between gap-4 pb-4 border-b border-gray-100">
            <div className="flex items-center space-x-3">
              <span className="p-2.5 bg-indigo-50 border border-indigo-100 rounded-xl">
                <Zap className="w-5 h-5 text-indigo-600" />
              </span>
              <div>
                <div className="flex items-center space-x-2">
                  <span className="px-2 py-0.5 bg-indigo-100 text-indigo-800 rounded-md font-black text-[10px] tracking-wider uppercase">
                    Rank #1 Recommendation
                  </span>
                  <span
                    className={`px-2 py-0.5 rounded-md font-bold text-[10px] uppercase ${
                      activeRecommendation.status === 'APPLIED'
                        ? 'bg-emerald-100 text-emerald-800'
                        : activeRecommendation.status === 'APPROVED'
                        ? 'bg-blue-100 text-blue-800'
                        : activeRecommendation.status === 'REJECTED'
                        ? 'bg-rose-100 text-rose-800'
                        : 'bg-amber-100 text-amber-800'
                    }`}
                  >
                    {activeRecommendation.status}
                  </span>
                </div>
                <h3 className="text-lg font-black text-gray-900 mt-1">{activeRecommendation.summary}</h3>
              </div>
            </div>

            {/* Actions */}
            <div className="flex items-center space-x-2">
              {activeRecommendation.status === 'PROPOSED' && (
                <>
                  <button
                    onClick={() => handleReject(activeRecommendation.id)}
                    disabled={actionLoading}
                    className="flex items-center px-3 py-1.5 bg-gray-100 hover:bg-gray-200 text-gray-700 font-semibold text-xs rounded-xl transition"
                  >
                    <XCircle className="w-3.5 h-3.5 mr-1 text-gray-500" /> Reject
                  </button>
                  <button
                    onClick={() => handleApprove(activeRecommendation.id)}
                    disabled={actionLoading}
                    className="flex items-center px-4 py-1.5 bg-indigo-600 hover:bg-indigo-700 text-white font-bold text-xs rounded-xl shadow-xs transition"
                  >
                    <CheckCircle className="w-3.5 h-3.5 mr-1" /> Approve
                  </button>
                </>
              )}

              {activeRecommendation.status === 'APPROVED' && (
                <button
                  onClick={() => handleApply(activeRecommendation.id)}
                  disabled={actionLoading}
                  className="flex items-center px-5 py-2 bg-emerald-600 hover:bg-emerald-700 text-white font-black text-xs rounded-xl shadow-sm transition"
                >
                  <Zap className="w-4 h-4 mr-1.5" /> Apply Approved Changes
                </button>
              )}

              {activeRecommendation.status === 'APPLIED' && (
                <span className="flex items-center text-xs font-bold text-emerald-700 bg-emerald-50 px-3 py-1.5 rounded-xl border border-emerald-200">
                  <CheckCircle className="w-4 h-4 mr-1 text-emerald-600" /> Applied to Active Schedule
                </span>
              )}
            </div>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-4 mt-4 text-xs">
            <div className="p-3 bg-gray-50 rounded-xl border border-gray-100">
              <span className="font-bold text-gray-700 block mb-1">Algorithmic Rationale & Facts</span>
              <p className="text-gray-600 leading-relaxed">{activeRecommendation.explanation}</p>
            </div>
            <div className="p-3 bg-indigo-50/50 rounded-xl border border-indigo-100">
              <span className="font-bold text-indigo-900 block mb-1">Estimated Impact & Solved Conflicts</span>
              <p className="text-indigo-800 leading-relaxed font-semibold">{activeRecommendation.estimatedImpact}</p>
            </div>
          </div>
        </div>
      )}

      {/* 3. Side-by-Side Comparison: Current Schedule vs Selected Alternative */}
      {selectedAlt && (
        <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-6 space-y-4">
          <div className="flex flex-wrap items-center justify-between pb-3 border-b border-gray-100 gap-2">
            <div>
              <h3 className="text-base font-black text-gray-900 flex items-center">
                <Sliders className="w-4 h-4 mr-2 text-indigo-600" />
                Schedule Comparison: Current vs Proposed Alternative (Rank #{selectedAlt.alternativeRank})
              </h3>
              <p className="text-xs text-gray-500 mt-0.5">
                Direct side-by-side comparison of completion forecasts, conflicts resolved, and required modifications.
              </p>
            </div>

            <div className="flex items-center space-x-2">
              <span className="text-xs font-bold text-gray-500">Objective Score:</span>
              <span className="px-2.5 py-1 bg-indigo-600 text-white rounded-xl text-xs font-black shadow-xs">
                {selectedAlt.objectiveScore} / 100
              </span>
            </div>
          </div>

          {/* Metrics comparison cards */}
          <div className="grid grid-cols-2 md:grid-cols-4 gap-3 text-xs">
            <div className="p-3 bg-gray-50 rounded-xl border border-gray-100">
              <span className="text-gray-400 block text-[10px] uppercase font-bold">Forecast Completion</span>
              <span className="text-sm font-black text-gray-900 mt-0.5 block">{selectedAlt.projectCompletionForecast}</span>
              <span className="text-[11px] text-emerald-600 font-semibold mt-0.5 block">
                {selectedAlt.workingDaysSaved > 0 ? `+${selectedAlt.workingDaysSaved} days saved` : 'Matches schedule'}
              </span>
            </div>

            <div className="p-3 bg-gray-50 rounded-xl border border-gray-100">
              <span className="text-gray-400 block text-[10px] uppercase font-bold">Deadline Feasibility</span>
              <span
                className={`text-sm font-black mt-0.5 block ${
                  selectedAlt.deadlineStatus === 'ON_TRACK'
                    ? 'text-emerald-700'
                    : selectedAlt.deadlineStatus === 'AT_RISK'
                    ? 'text-amber-700'
                    : 'text-rose-700'
                }`}
              >
                {selectedAlt.deadlineStatus}
              </span>
              <span className="text-[11px] text-gray-500 font-semibold mt-0.5 block">
                {selectedAlt.constraintStatus}
              </span>
            </div>

            <div className="p-3 bg-gray-50 rounded-xl border border-gray-100">
              <span className="text-gray-400 block text-[10px] uppercase font-bold">Resource Conflicts</span>
              <span className="text-sm font-black text-indigo-700 mt-0.5 block">
                {selectedAlt.conflictsResolved} Resolved
              </span>
              <span className="text-[11px] text-gray-500 font-semibold mt-0.5 block">
                {selectedAlt.remainingConflicts} remaining
              </span>
            </div>

            <div className="p-3 bg-gray-50 rounded-xl border border-gray-100">
              <span className="text-gray-400 block text-[10px] uppercase font-bold">Schedule Churn</span>
              <span className="text-sm font-black text-gray-800 mt-0.5 block">
                {selectedAlt.disruptionCount} Changes
              </span>
              <span className="text-[11px] text-gray-500 font-semibold mt-0.5 block">Low disruption</span>
            </div>
          </div>

          {/* Trade-offs Explanation */}
          <div className="p-3.5 bg-amber-50/60 border border-amber-200/80 rounded-xl text-xs text-amber-900 flex items-start space-x-2">
            <AlertTriangle className="w-4 h-4 text-amber-600 flex-shrink-0 mt-0.5" />
            <div>
              <span className="font-bold">Trade-offs & Operational Impact:</span>
              <p className="mt-0.5">{selectedAlt.tradeOffs || 'No significant trade-offs identified.'}</p>
            </div>
          </div>
        </div>
      )}

      {/* 4. Ranked Feasible Alternatives Table */}
      <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-6 space-y-4">
        <div className="flex items-center justify-between pb-3 border-b border-gray-100">
          <div>
            <h3 className="text-base font-black text-gray-900 flex items-center">
              <TrendingUp className="w-4 h-4 mr-2 text-indigo-600" />
              Evaluated Schedule Alternatives ({alternatives.length})
            </h3>
            <p className="text-xs text-gray-500 mt-0.5">
              Ranked deterministically using multi-objective scoring (deadline, duration, conflict elimination, and disruption).
            </p>
          </div>
        </div>

        {alternatives.length > 0 ? (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs">
              <thead className="bg-gray-50 border-b border-gray-200 text-gray-500 font-bold uppercase text-[10px]">
                <tr>
                  <th className="py-2.5 px-3">Rank</th>
                  <th className="py-2.5 px-3">Alternative Title</th>
                  <th className="py-2.5 px-3">Forecast Completion</th>
                  <th className="py-2.5 px-3">Conflicts Resolved</th>
                  <th className="py-2.5 px-3">Score</th>
                  <th className="py-2.5 px-3">Constraint</th>
                  <th className="py-2.5 px-3 text-right">Action</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100 text-gray-700">
                {alternatives.map((alt) => (
                  <tr
                    key={alt.id}
                    className={`hover:bg-indigo-50/40 transition cursor-pointer ${
                      selectedAlt?.id === alt.id ? 'bg-indigo-50/70 font-semibold' : ''
                    }`}
                    onClick={() => setSelectedAlt(alt)}
                  >
                    <td className="py-3 px-3">
                      <span
                        className={`w-6 h-6 rounded-full inline-flex items-center justify-center font-bold text-xs ${
                          alt.alternativeRank === 1
                            ? 'bg-indigo-600 text-white shadow-xs'
                            : 'bg-gray-100 text-gray-600'
                        }`}
                      >
                        #{alt.alternativeRank}
                      </span>
                    </td>
                    <td className="py-3 px-3">
                      <div className="font-bold text-gray-900">{alt.title}</div>
                      <div className="text-[11px] text-gray-500 truncate max-w-md">{alt.summary}</div>
                    </td>
                    <td className="py-3 px-3 font-semibold text-gray-800">
                      {alt.projectCompletionForecast}
                      <span className="block text-[10px] text-gray-400 font-normal">{alt.deadlineStatus}</span>
                    </td>
                    <td className="py-3 px-3">
                      <span className="px-2 py-0.5 rounded-full text-[10px] font-bold bg-indigo-100 text-indigo-800">
                        {alt.conflictsResolved} resolved
                      </span>
                    </td>
                    <td className="py-3 px-3">
                      <div className="flex items-center space-x-1.5">
                        <span className="font-black text-gray-900">{alt.objectiveScore}</span>
                        <div className="w-16 bg-gray-200 rounded-full h-1.5">
                          <div
                            className="bg-indigo-600 h-1.5 rounded-full"
                            style={{ width: `${Math.min(100, alt.objectiveScore)}%` }}
                          />
                        </div>
                      </div>
                    </td>
                    <td className="py-3 px-3">
                      <span
                        className={`text-[10px] font-bold px-2 py-0.5 rounded-md ${
                          alt.constraintStatus === 'VALID'
                            ? 'bg-emerald-50 text-emerald-700 border border-emerald-200'
                            : 'bg-rose-50 text-rose-700 border border-rose-200'
                        }`}
                      >
                        {alt.constraintStatus}
                      </span>
                    </td>
                    <td className="py-3 px-3 text-right">
                      <button
                        onClick={(e) => {
                          e.stopPropagation();
                          setSelectedAlt(alt);
                        }}
                        className="text-indigo-600 hover:text-indigo-800 font-bold text-xs"
                      >
                        Compare →
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <div className="p-8 text-center text-gray-400 text-sm">
            No optimization runs executed yet. Click &quot;Run Optimization&quot; to evaluate alternative schedules.
          </div>
        )}
      </div>

      {/* 5. Single Task Resource Recommendations Toolbar */}
      <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-6 space-y-4">
        <div className="flex flex-wrap items-center justify-between pb-3 border-b border-gray-100 gap-2">
          <div>
            <h3 className="text-base font-black text-gray-900 flex items-center">
              <UserCheck className="w-4 h-4 mr-2 text-indigo-600" />
              Per-Task Resource & Vendor Suitability Explorer
            </h3>
            <p className="text-xs text-gray-500 mt-0.5">
              Select any project task to rank eligible workers and vendors based on skills, capacity, and current workload.
            </p>
          </div>

          <div className="flex items-center space-x-2">
            <span className="text-xs font-bold text-gray-600">Inspect Task:</span>
            <select
              value={selectedTaskId || ''}
              onChange={(e) => {
                const id = Number(e.target.value);
                if (id) openTaskResourceModal(id);
              }}
              className="border border-gray-300 rounded-xl px-3 py-1.5 text-xs font-bold text-gray-800 focus:outline-hidden"
            >
              <option value="">Select a Task...</option>
              {tasks.map((t) => (
                <option key={t.id} value={t.id}>
                  {t.name}
                </option>
              ))}
            </select>
          </div>
        </div>

        {loadingTaskRecs && (
          <div className="p-6 text-center text-gray-400 text-xs font-semibold animate-pulse">
            Analyzing worker skills and vendor capabilities...
          </div>
        )}

        {taskRecommendations && !loadingTaskRecs && (
          <div className="space-y-4">
            <div className="p-3.5 bg-gray-50 rounded-xl border border-gray-200/80 flex flex-wrap items-center justify-between gap-3 text-xs">
              <div>
                <span className="font-black text-gray-900 text-sm">{taskRecommendations.taskName}</span>
                <span className="text-gray-500 block mt-0.5">
                  Duration: {taskRecommendations.durationDays}d | Required Skills:{' '}
                  <span className="font-semibold text-indigo-700">{taskRecommendations.requiredSkills || 'None'}</span>
                </span>
              </div>
              <div className="text-right">
                <span className="text-gray-400 block text-[10px] uppercase font-bold">Current Assignment</span>
                <span className="font-bold text-gray-800">
                  {taskRecommendations.currentAssignee || 'Unassigned'} ({taskRecommendations.currentAssigneeType || 'N/A'})
                </span>
              </div>
            </div>

            {/* Candidate Workers */}
            <div>
              <h4 className="text-xs font-black uppercase text-gray-500 tracking-wider mb-2">Qualified Worker Candidates</h4>
              <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                {taskRecommendations.candidateWorkers.map((w) => (
                  <div
                    key={w.id}
                    className="p-3.5 bg-white border border-gray-200 rounded-xl hover:border-indigo-300 transition shadow-2xs space-y-1.5 text-xs"
                  >
                    <div className="flex items-center justify-between">
                      <div className="flex items-center space-x-2">
                        <User className="w-4 h-4 text-indigo-600" />
                        <span className="font-bold text-gray-900">{w.name}</span>
                      </div>
                      <span className="px-2 py-0.5 rounded-full text-[10px] font-black bg-indigo-100 text-indigo-800">
                        {w.suitabilityScore} pts
                      </span>
                    </div>

                    <p className="text-[11px] text-gray-500">Skills: {w.skillsOrServices}</p>
                    <div className="flex items-center space-x-3 text-[10px] text-gray-600 pt-1 border-t border-gray-100">
                      <span>Exp: {w.experience || 3} yrs</span>
                      <span>Active tasks: {w.activeAssignmentsCount}</span>
                      <span className={w.available ? 'text-emerald-700 font-bold' : 'text-rose-600 font-bold'}>
                        {w.available ? 'Available' : 'Unavailable'}
                      </span>
                    </div>
                    <p className="text-[11px] text-gray-600 italic mt-1">{w.recommendationReason}</p>
                  </div>
                ))}
              </div>
            </div>

            {/* Candidate Vendors */}
            {taskRecommendations.candidateVendors.length > 0 && (
              <div>
                <h4 className="text-xs font-black uppercase text-gray-500 tracking-wider mb-2">Alternative Vendors</h4>
                <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                  {taskRecommendations.candidateVendors.map((v) => (
                    <div
                      key={v.id}
                      className="p-3 bg-white border border-gray-200 rounded-xl hover:border-purple-300 transition shadow-2xs space-y-1 text-xs"
                    >
                      <div className="flex items-center justify-between">
                        <div className="flex items-center space-x-2">
                          <Building className="w-4 h-4 text-purple-600" />
                          <span className="font-bold text-gray-900">{v.name}</span>
                        </div>
                        <span className="px-2 py-0.5 rounded-full text-[10px] font-black bg-purple-100 text-purple-800">
                          {v.suitabilityScore} pts
                        </span>
                      </div>
                      <p className="text-[11px] text-gray-500">Services: {v.skillsOrServices}</p>
                      <p className="text-[11px] text-gray-600 italic">{v.recommendationReason}</p>
                    </div>
                  ))}
                </div>
              </div>
            )}
          </div>
        )}
      </div>

      {/* 6. Audit Trail History */}
      {auditLogs.length > 0 && (
        <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-6 space-y-3">
          <h3 className="text-base font-black text-gray-900 flex items-center">
            <History className="w-4 h-4 mr-2 text-indigo-600" />
            Optimization & Decision Audit Log ({auditLogs.length})
          </h3>
          <div className="divide-y divide-gray-100 text-xs">
            {auditLogs.slice(0, 5).map((log) => (
              <div key={log.id} className="py-2.5 flex items-center justify-between">
                <div>
                  <span className="font-bold text-gray-800">{log.action}</span>
                  <span className="text-gray-500 ml-2">by {log.performedBy}</span>
                  <p className="text-gray-600 text-[11px] mt-0.5">{log.details}</p>
                </div>
                <span className="text-gray-400 text-[10px]">
                  {log.createdAt ? new Date(log.createdAt).toLocaleString() : ''}
                </span>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}
