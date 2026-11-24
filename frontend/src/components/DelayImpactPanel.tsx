import React, { useState } from 'react';
import { AlertCircle, AlertTriangle, CheckCircle, PlusCircle, Bookmark, X } from 'lucide-react';
import { api } from '../services/api';

export interface TaskDelayItem {
  id: number;
  taskId: number;
  taskName: string;
  delayDurationDays: number;
  delayReason: string;
  reportedDate: string;
  updatedExpectedFinish: string;
  notes: string;
  reportedBy: string;
  status: string;
  createdAt: string;
}

export interface DelayImpactData {
  projectId: number;
  projectName: string;
  projectStartDate: string;
  baselineCompletionDate: string;
  forecastCompletionDate: string;
  clientDeadline: string;
  varianceFromBaselineWorkingDays: number;
  varianceFromDeadlineWorkingDays: number;
  deadlineStatus: string;
  reportedDelays: TaskDelayItem[];
  affectedTasks: string[];
  unaffectedTasks: string[];
  criticalTasksAffected: string[];
  primaryDelayReasons: string[];
  explanation: string;
  criticalPathChanged: boolean;
  originalCriticalPath: string[];
  revisedCriticalPath: string[];
}

interface DelayImpactPanelProps {
  data: DelayImpactData;
  tasks: { id: number; name: string }[];
  onRefresh: () => void;
}

export function DelayImpactPanel({ data, tasks, onRefresh }: DelayImpactPanelProps) {
  const [showDelayModal, setShowDelayModal] = useState(false);
  const [showBaselineModal, setShowBaselineModal] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  // Form states
  const [selectedTaskId, setSelectedTaskId] = useState<number>(tasks[0]?.id || 1);
  const [delayDays, setDelayDays] = useState<number>(2);
  const [delayReason, setDelayReason] = useState<string>('Vendor delivery delay');
  const [notes, setNotes] = useState<string>('');
  const [baselineName, setBaselineName] = useState<string>('Approved Plan v1');

  const handleReportDelay = async (e: React.FormEvent) => {
    e.preventDefault();
    setSubmitting(true);
    try {
      await api.post(`/tasks/${selectedTaskId}/delays`, {
        delayDurationDays: Number(delayDays),
        delayReason,
        notes,
      });
      setShowDelayModal(false);
      onRefresh();
    } catch (err: any) {
      alert('Error reporting delay: ' + (err.response?.data?.message || err.message));
    } finally {
      setSubmitting(false);
    }
  };

  const handleCreateBaseline = async (e: React.FormEvent) => {
    e.preventDefault();
    setSubmitting(true);
    try {
      await api.post(`/projects/${data.projectId}/baseline`, {
        versionName: baselineName,
      });
      setShowBaselineModal(false);
      onRefresh();
    } catch (err: any) {
      alert('Error creating baseline: ' + (err.response?.data?.message || err.message));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="space-y-6">
      {/* Top Action Bar */}
      <div className="flex flex-wrap items-center justify-between gap-4 p-4 bg-white rounded-xl border border-gray-200 shadow-sm">
        <div>
          <h3 className="text-lg font-bold text-gray-900">Schedule Variance & Delay Propagation</h3>
          <p className="text-xs text-gray-500">
            Compare forecast against approved baseline & analyze ripple effects through task dependency chains.
          </p>
        </div>
        <div className="flex items-center space-x-3">
          <button
            onClick={() => setShowBaselineModal(true)}
            className="flex items-center px-4 py-2 bg-indigo-50 hover:bg-indigo-100 text-indigo-700 text-sm font-semibold rounded-lg border border-indigo-200 transition shadow-xs"
          >
            <Bookmark className="w-4 h-4 mr-2 text-indigo-600" />
            Snapshot Approved Baseline
          </button>
          <button
            onClick={() => setShowDelayModal(true)}
            className="flex items-center px-4 py-2 bg-rose-600 hover:bg-rose-700 text-white text-sm font-semibold rounded-lg shadow-sm transition"
          >
            <PlusCircle className="w-4 h-4 mr-2" />
            Report Task Delay
          </button>
        </div>
      </div>

      {/* KPI Comparison Banner */}
      <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
        <div className="bg-white p-5 rounded-xl border border-gray-200 shadow-sm">
          <p className="text-xs font-semibold text-gray-400 uppercase tracking-wider">Approved Baseline</p>
          <p className="text-xl font-bold text-gray-900 mt-1">
            {data.baselineCompletionDate || 'Not Established'}
          </p>
          <span className="text-[11px] text-gray-500">Master schedule reference</span>
        </div>

        <div className="bg-white p-5 rounded-xl border border-gray-200 shadow-sm">
          <p className="text-xs font-semibold text-gray-400 uppercase tracking-wider">Forecast Completion</p>
          <p className="text-xl font-bold text-gray-900 mt-1">{data.forecastCompletionDate}</p>
          <span className="text-[11px] text-gray-500">Calculated from delays & CPM</span>
        </div>

        <div className="bg-white p-5 rounded-xl border border-gray-200 shadow-sm">
          <p className="text-xs font-semibold text-gray-400 uppercase tracking-wider">Variance vs Baseline</p>
          <p
            className={`text-xl font-bold mt-1 ${
              data.varianceFromBaselineWorkingDays > 0
                ? 'text-rose-600'
                : data.varianceFromBaselineWorkingDays < 0
                ? 'text-emerald-600'
                : 'text-gray-900'
            }`}
          >
            {data.varianceFromBaselineWorkingDays > 0 ? `+${data.varianceFromBaselineWorkingDays}` : data.varianceFromBaselineWorkingDays}{' '}
            <span className="text-sm font-medium">working days</span>
          </p>
          <span className="text-[11px] text-gray-500">
            {data.varianceFromBaselineWorkingDays > 0 ? 'Projected schedule slip' : 'On or ahead of target'}
          </span>
        </div>

        <div className="bg-white p-5 rounded-xl border border-gray-200 shadow-sm">
          <p className="text-xs font-semibold text-gray-400 uppercase tracking-wider">Deadline Health</p>
          <div className="mt-1 flex items-center">
            {data.deadlineStatus === 'ON_TRACK' && (
              <span className="inline-flex items-center px-3 py-1 rounded-full text-xs font-bold bg-emerald-100 text-emerald-800">
                <CheckCircle className="w-3.5 h-3.5 mr-1" />
                ON TRACK
              </span>
            )}
            {data.deadlineStatus === 'AT_RISK' && (
              <span className="inline-flex items-center px-3 py-1 rounded-full text-xs font-bold bg-amber-100 text-amber-800">
                <AlertTriangle className="w-3.5 h-3.5 mr-1" />
                AT RISK
              </span>
            )}
            {data.deadlineStatus === 'DELAYED' && (
              <span className="inline-flex items-center px-3 py-1 rounded-full text-xs font-bold bg-rose-100 text-rose-800">
                <AlertCircle className="w-3.5 h-3.5 mr-1" />
                DELAYED
              </span>
            )}
          </div>
          <span className="text-[11px] text-gray-500 mt-1 block">Deadline: {data.clientDeadline || 'N/A'}</span>
        </div>
      </div>

      {/* Explanation Card */}
      <div className="bg-blue-50/70 border border-blue-200 p-4 rounded-xl text-blue-900 text-sm shadow-xs flex items-start space-x-3">
        <AlertCircle className="w-5 h-5 text-blue-600 flex-shrink-0 mt-0.5" />
        <div>
          <h5 className="font-bold text-blue-900 mb-0.5">Automated Schedule Impact Analysis</h5>
          <p className="text-xs leading-relaxed text-blue-800">{data.explanation}</p>
        </div>
      </div>

      {/* Two-Column Grid: Affected vs Unaffected (Slack Absorption) */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
        {/* Affected Tasks */}
        <div className="bg-white rounded-xl border border-gray-200 shadow-sm p-5">
          <div className="flex items-center justify-between mb-4 pb-2 border-b border-gray-100">
            <h4 className="font-bold text-gray-900 text-sm flex items-center">
              <span className="w-2.5 h-2.5 rounded-full bg-rose-600 mr-2" />
              Affected Downstream Tasks ({data.affectedTasks.length})
            </h4>
            <span className="text-[11px] text-gray-400">Finish dates pushed forward</span>
          </div>

          {data.affectedTasks.length > 0 ? (
            <ul className="space-y-2">
              {data.affectedTasks.map((taskName, idx) => {
                const isCrit = data.criticalTasksAffected.includes(taskName);
                return (
                  <li
                    key={idx}
                    className="flex items-center justify-between p-2.5 bg-rose-50/60 rounded-lg border border-rose-100 text-xs font-medium text-gray-800"
                  >
                    <span className="flex items-center">
                      <span className="text-rose-600 font-bold mr-2">#{idx + 1}</span>
                      {taskName}
                    </span>
                    {isCrit && (
                      <span className="px-2 py-0.5 rounded text-[10px] font-bold bg-rose-600 text-white shadow-xs">
                        Critical Task
                      </span>
                    )}
                  </li>
                );
              })}
            </ul>
          ) : (
            <p className="text-xs text-gray-400 italic py-4 text-center">
              No downstream tasks shifted. Schedule is fully on track.
            </p>
          )}
        </div>

        {/* Unaffected Tasks (Slack Absorption) */}
        <div className="bg-white rounded-xl border border-gray-200 shadow-sm p-5">
          <div className="flex items-center justify-between mb-4 pb-2 border-b border-gray-100">
            <h4 className="font-bold text-gray-900 text-sm flex items-center">
              <span className="w-2.5 h-2.5 rounded-full bg-emerald-600 mr-2" />
              Unaffected Tasks ({data.unaffectedTasks.length})
            </h4>
            <span className="text-[11px] text-gray-400">Absorbed by slack buffer or parallel paths</span>
          </div>

          {data.unaffectedTasks.length > 0 ? (
            <div className="flex flex-wrap gap-2">
              {data.unaffectedTasks.map((taskName, idx) => (
                <span
                  key={idx}
                  className="px-3 py-1.5 bg-emerald-50 text-emerald-800 rounded-lg border border-emerald-100 text-xs font-medium"
                >
                  {taskName}
                </span>
              ))}
            </div>
          ) : (
            <p className="text-xs text-gray-400 italic py-4 text-center">All project tasks shifted.</p>
          )}
        </div>
      </div>

      {/* Reported Delays Table */}
      <div className="bg-white rounded-xl border border-gray-200 shadow-sm overflow-hidden">
        <div className="p-4 bg-gray-50 border-b border-gray-200 flex items-center justify-between">
          <h4 className="font-bold text-gray-900 text-sm">Reported Delay Records ({data.reportedDelays.length})</h4>
          <span className="text-xs text-gray-500">Historical delay log with root causes</span>
        </div>

        {data.reportedDelays.length > 0 ? (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs">
              <thead className="bg-gray-100 text-gray-600 uppercase font-bold tracking-wider border-b border-gray-200">
                <tr>
                  <th className="py-3 px-4">Task</th>
                  <th className="py-3 px-3">Delay Duration</th>
                  <th className="py-3 px-4">Reason</th>
                  <th className="py-3 px-3">Reported Date</th>
                  <th className="py-3 px-3">Reported By</th>
                  <th className="py-3 px-4">Status</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {data.reportedDelays.map((delay) => (
                  <tr key={delay.id} className="hover:bg-gray-50">
                    <td className="py-3 px-4 font-semibold text-gray-900">{delay.taskName}</td>
                    <td className="py-3 px-3">
                      <span className="px-2 py-0.5 rounded font-bold bg-amber-100 text-amber-800">
                        +{delay.delayDurationDays} working days
                      </span>
                    </td>
                    <td className="py-3 px-4 text-gray-700">{delay.delayReason}</td>
                    <td className="py-3 px-3 text-gray-500">{delay.reportedDate}</td>
                    <td className="py-3 px-3 text-gray-600">{delay.reportedBy || 'admin'}</td>
                    <td className="py-3 px-4">
                      <span className="px-2 py-0.5 rounded text-[10px] font-bold bg-gray-100 text-gray-700 border">
                        {delay.status}
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <div className="p-8 text-center text-gray-400 text-sm italic">
            No delays have been recorded for this project yet. Use "Report Task Delay" above to test delay propagation.
          </div>
        )}
      </div>

      {/* Report Delay Modal */}
      {showDelayModal && (
        <div className="fixed inset-0 bg-black/50 backdrop-blur-xs flex items-center justify-center z-50 p-4">
          <div className="bg-white rounded-2xl shadow-xl max-w-md w-full p-6 animate-scaleIn">
            <div className="flex items-center justify-between pb-3 border-b border-gray-100">
              <h3 className="text-lg font-bold text-gray-900">Report Task Delay</h3>
              <button onClick={() => setShowDelayModal(false)} className="text-gray-400 hover:text-gray-600">
                <X className="w-5 h-5" />
              </button>
            </div>

            <form onSubmit={handleReportDelay} className="mt-4 space-y-4">
              <div>
                <label className="block text-xs font-bold text-gray-700 uppercase mb-1">Select Task</label>
                <select
                  value={selectedTaskId}
                  onChange={(e) => setSelectedTaskId(Number(e.target.value))}
                  className="w-full border border-gray-300 rounded-lg p-2.5 text-sm focus:ring-2 focus:ring-rose-500"
                >
                  {tasks.map((t) => (
                    <option key={t.id} value={t.id}>
                      {t.name} (ID: {t.id})
                    </option>
                  ))}
                </select>
              </div>

              <div>
                <label className="block text-xs font-bold text-gray-700 uppercase mb-1">
                  Delay Duration (Working Days)
                </label>
                <input
                  type="number"
                  min="1"
                  max="30"
                  value={delayDays}
                  onChange={(e) => setDelayDays(Number(e.target.value))}
                  className="w-full border border-gray-300 rounded-lg p-2.5 text-sm focus:ring-2 focus:ring-rose-500"
                  required
                />
              </div>

              <div>
                <label className="block text-xs font-bold text-gray-700 uppercase mb-1">Delay Reason</label>
                <input
                  type="text"
                  value={delayReason}
                  onChange={(e) => setDelayReason(e.target.value)}
                  placeholder="e.g. Vendor delivery delay, Scope creep, Technical blocker"
                  className="w-full border border-gray-300 rounded-lg p-2.5 text-sm focus:ring-2 focus:ring-rose-500"
                  required
                />
              </div>

              <div>
                <label className="block text-xs font-bold text-gray-700 uppercase mb-1">Notes / Context</label>
                <textarea
                  value={notes}
                  onChange={(e) => setNotes(e.target.value)}
                  placeholder="Provide context on mitigation or client alignment..."
                  rows={3}
                  className="w-full border border-gray-300 rounded-lg p-2.5 text-sm focus:ring-2 focus:ring-rose-500"
                />
              </div>

              <div className="flex justify-end space-x-3 pt-3 border-t border-gray-100">
                <button
                  type="button"
                  onClick={() => setShowDelayModal(false)}
                  className="px-4 py-2 border rounded-lg text-sm text-gray-600 hover:bg-gray-50"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={submitting}
                  className="px-4 py-2 bg-rose-600 hover:bg-rose-700 text-white rounded-lg text-sm font-semibold shadow transition disabled:opacity-50"
                >
                  {submitting ? 'Reporting...' : 'Submit Delay & Recalculate'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Snapshot Baseline Modal */}
      {showBaselineModal && (
        <div className="fixed inset-0 bg-black/50 backdrop-blur-xs flex items-center justify-center z-50 p-4">
          <div className="bg-white rounded-2xl shadow-xl max-w-md w-full p-6 animate-scaleIn">
            <div className="flex items-center justify-between pb-3 border-b border-gray-100">
              <h3 className="text-lg font-bold text-gray-900">Snapshot Approved Baseline</h3>
              <button onClick={() => setShowBaselineModal(false)} className="text-gray-400 hover:text-gray-600">
                <X className="w-5 h-5" />
              </button>
            </div>

            <form onSubmit={handleCreateBaseline} className="mt-4 space-y-4">
              <div>
                <label className="block text-xs font-bold text-gray-700 uppercase mb-1">
                  Baseline Version Name
                </label>
                <input
                  type="text"
                  value={baselineName}
                  onChange={(e) => setBaselineName(e.target.value)}
                  placeholder="e.g. Approved Plan v1, Contract Signoff"
                  className="w-full border border-gray-300 rounded-lg p-2.5 text-sm focus:ring-2 focus:ring-indigo-500"
                  required
                />
                <p className="text-xs text-gray-400 mt-1">
                  This freezes the current schedule dates as the official baseline for future variance comparisons.
                </p>
              </div>

              <div className="flex justify-end space-x-3 pt-3 border-t border-gray-100">
                <button
                  type="button"
                  onClick={() => setShowBaselineModal(false)}
                  className="px-4 py-2 border rounded-lg text-sm text-gray-600 hover:bg-gray-50"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={submitting}
                  className="px-4 py-2 bg-indigo-600 hover:bg-indigo-700 text-white rounded-lg text-sm font-semibold shadow transition disabled:opacity-50"
                >
                  {submitting ? 'Saving...' : 'Save Approved Baseline'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
