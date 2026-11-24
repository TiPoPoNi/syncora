import React, { useState, useEffect } from 'react';
import { api } from '../services/api';
import {
  TrendingUp,
  AlertTriangle,
  CheckCircle2,
  Clock,
  BarChart3,
  Brain,
  ShieldAlert,
  Info,
  Calendar,
  User,
  Sparkles,
  ArrowRight
} from 'lucide-react';

interface HistoricalOutcomes {
  totalCompletedTasks: number;
  onTimeTasks: number;
  delayedTasks: number;
  onTimeRatePercent: number;
  averageVarianceDays: number;
  delayReasonsDistribution: Record<string, number>;
  workerReliability: Array<{
    workerName: string;
    totalTasks: number;
    delayedTasks: number;
    onTimeRatePercent: number;
  }>;
  recentOutcomes: Array<{
    id: number;
    taskId: number;
    taskName: string;
    projectName: string;
    plannedFinishDate: string;
    actualFinishDate: string;
    varianceWorkingDays: number;
    delayed: boolean;
    delayReason?: string;
    workerName: string;
  }>;
}

interface TaskDelayRisk {
  taskId: number;
  taskName: string;
  projectId: number;
  deterministicScheduleStatus: string;
  deterministicSlackDays: number;
  isOnCriticalPath: boolean;
  mlPredictionStatus: string;
  delayRiskScore: number | null;
  riskLevel: string;
  modelVersion: string;
  contributingFactors: string[];
  limitations: string;
}

interface Props {
  projectId: number;
  projectName?: string;
  onTaskUpdated?: () => Promise<void> | void;
}

export const HistoricalAnalyticsPanel: React.FC<Props> = ({ projectId }) => {
  const [outcomes, setOutcomes] = useState<HistoricalOutcomes | null>(null);
  const [risks, setRisks] = useState<TaskDelayRisk[]>([]);
  const [loading, setLoading] = useState(true);
  const [completingTaskId, setCompletingTaskId] = useState<number | null>(null);
  const [actualFinishDate, setActualFinishDate] = useState<string>(new Date().toISOString().split('T')[0]);
  const [delayReason, setDelayReason] = useState<string>('');
  const [submitting, setSubmitting] = useState(false);

  const fetchData = async () => {
    setLoading(true);
    try {
      const [outcomesRes, risksRes] = await Promise.all([
        api.get(`/analytics/historical-outcomes?projectId=${projectId}`),
        api.get(`/projects/${projectId}/delay-risk`)
      ]);
      setOutcomes(outcomesRes.data);
      setRisks(risksRes.data);
    } catch (e) {
      console.error('Failed to load historical analytics:', e);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchData();
  }, [projectId]);

  const handleCompleteTask = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!completingTaskId) return;
    setSubmitting(true);
    try {
      await api.post(`/tasks/${completingTaskId}/complete`, {
        actualFinishDate,
        primaryDelayReason: delayReason || null
      });
      setCompletingTaskId(null);
      setDelayReason('');
      await fetchData();
    } catch (err: any) {
      alert('Error completing task: ' + (err.response?.data?.message || err.message));
    } finally {
      setSubmitting(false);
    }
  };

  if (loading) {
    return (
      <div className="p-8 text-center text-slate-500 flex items-center justify-center gap-2">
        <Clock className="w-5 h-5 animate-spin" />
        Loading historical outcome analytics and risk assessments...
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {/* 1. Governance & Data Gate Alert */}
      <div className="bg-amber-50 border border-amber-200 rounded-xl p-4 text-amber-900 flex items-start gap-3">
        <ShieldAlert className="w-5 h-5 text-amber-600 mt-0.5 shrink-0" />
        <div className="text-sm">
          <div className="font-semibold flex items-center gap-2">
            Data-Governance & ML Evaluation Gate: RESEARCH / EVALUATION
            <span className="bg-amber-200 text-amber-800 text-xs px-2 py-0.5 rounded-full font-mono">
              Sample Gate: {outcomes?.totalCompletedTasks || 0} / 100 Completed Tasks
            </span>
          </div>
          <p className="mt-1 text-amber-800">
            In accordance with sound data engineering practices, probabilistic ML delay predictions are marked as{' '}
            <strong>NOT READY</strong> until the organization accumulates ≥ 100 verified historical task outcomes.
            Deterministic CPM schedule analysis remains authoritative, and the Scikit-learn training pipeline is validated on test fixtures in <code className="bg-amber-100 px-1 py-0.5 rounded">ai-service/app/ml/</code>.
          </p>
        </div>
      </div>

      {/* 2. Top-Level Historical Outcome KPIs */}
      <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
        <div className="bg-white p-5 rounded-xl border border-slate-200 shadow-sm">
          <div className="flex items-center justify-between text-slate-500 text-sm">
            <span>Completed Tasks</span>
            <CheckCircle2 className="w-4 h-4 text-emerald-500" />
          </div>
          <div className="text-2xl font-bold text-slate-900 mt-2">{outcomes?.totalCompletedTasks || 0}</div>
          <div className="text-xs text-slate-400 mt-1">Archived outcome records</div>
        </div>

        <div className="bg-white p-5 rounded-xl border border-slate-200 shadow-sm">
          <div className="flex items-center justify-between text-slate-500 text-sm">
            <span>On-Time Rate</span>
            <TrendingUp className="w-4 h-4 text-blue-500" />
          </div>
          <div className="text-2xl font-bold text-blue-600 mt-2">
            {outcomes?.onTimeRatePercent !== undefined ? `${outcomes.onTimeRatePercent}%` : '0%'}
          </div>
          <div className="w-full bg-slate-100 h-1.5 rounded-full mt-2 overflow-hidden">
            <div
              className="bg-blue-600 h-full rounded-full transition-all"
              style={{ width: `${outcomes?.onTimeRatePercent || 0}%` }}
            />
          </div>
        </div>

        <div className="bg-white p-5 rounded-xl border border-slate-200 shadow-sm">
          <div className="flex items-center justify-between text-slate-500 text-sm">
            <span>Delayed Tasks</span>
            <AlertTriangle className="w-4 h-4 text-rose-500" />
          </div>
          <div className="text-2xl font-bold text-rose-600 mt-2">{outcomes?.delayedTasks || 0}</div>
          <div className="text-xs text-rose-500 mt-1">Finished past planned finish date</div>
        </div>

        <div className="bg-white p-5 rounded-xl border border-slate-200 shadow-sm">
          <div className="flex items-center justify-between text-slate-500 text-sm">
            <span>Avg Schedule Variance</span>
            <Clock className="w-4 h-4 text-purple-500" />
          </div>
          <div className="text-2xl font-bold text-slate-900 mt-2">
            {outcomes?.averageVarianceDays !== undefined
              ? `${outcomes.averageVarianceDays > 0 ? '+' : ''}${outcomes.averageVarianceDays}d`
              : '0d'}
          </div>
          <div className="text-xs text-slate-400 mt-1">Working days deviation from baseline</div>
        </div>
      </div>

      {/* 3. Delay Reasons Distribution & Worker Reliability */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <div className="bg-white p-6 rounded-xl border border-slate-200 shadow-sm">
          <h3 className="font-semibold text-slate-900 text-base flex items-center gap-2 mb-4">
            <BarChart3 className="w-4 h-4 text-indigo-600" />
            Historical Delay Reasons Breakdown
          </h3>
          {outcomes?.delayReasonsDistribution && Object.keys(outcomes.delayReasonsDistribution).length > 0 ? (
            <div className="space-y-3">
              {Object.entries(outcomes.delayReasonsDistribution).map(([reason, count]) => (
                <div key={reason} className="flex items-center justify-between text-sm">
                  <span className="text-slate-700">{reason}</span>
                  <span className="bg-rose-50 text-rose-700 px-2.5 py-0.5 rounded-full font-medium text-xs border border-rose-100">
                    {count} occurrence{count > 1 ? 's' : ''}
                  </span>
                </div>
              ))}
            </div>
          ) : (
            <div className="text-sm text-slate-400 italic py-4 text-center">
              No historical delays recorded in current dataset.
            </div>
          )}
        </div>

        <div className="bg-white p-6 rounded-xl border border-slate-200 shadow-sm">
          <h3 className="font-semibold text-slate-900 text-base flex items-center gap-2 mb-4">
            <User className="w-4 h-4 text-emerald-600" />
            Team Member Delivery Reliability
          </h3>
          {outcomes?.workerReliability && outcomes.workerReliability.length > 0 ? (
            <div className="space-y-3">
              {outcomes.workerReliability.map((w) => (
                <div key={w.workerName} className="flex items-center justify-between text-sm border-b border-slate-100 pb-2">
                  <div>
                    <div className="font-medium text-slate-800">{w.workerName}</div>
                    <div className="text-xs text-slate-400">
                      {w.totalTasks} total tasks ({w.delayedTasks} delayed)
                    </div>
                  </div>
                  <div className="text-right">
                    <span
                      className={`text-xs px-2 py-0.5 rounded-full font-semibold ${
                        w.onTimeRatePercent >= 80
                          ? 'bg-emerald-50 text-emerald-700 border border-emerald-100'
                          : 'bg-amber-50 text-amber-700 border border-amber-100'
                      }`}
                    >
                      {w.onTimeRatePercent}% On-Time
                    </span>
                  </div>
                </div>
              ))}
            </div>
          ) : (
            <div className="text-sm text-slate-400 italic py-4 text-center">
              No worker completion history available yet.
            </div>
          )}
        </div>
      </div>

      {/* 4. Active Tasks: Deterministic Status vs. ML Risk Assessment */}
      <div className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
        <div className="p-5 border-b border-slate-200 bg-slate-50 flex items-center justify-between">
          <div>
            <h3 className="font-semibold text-slate-900 text-base flex items-center gap-2">
              <Brain className="w-5 h-5 text-indigo-600" />
              Active Tasks: Deterministic Schedule vs. Advisory ML Delay Risk
            </h3>
            <p className="text-xs text-slate-500 mt-1">
              Strict architectural separation: Deterministic CPM calculations are authoritative. ML estimates serve as early advisory signals.
            </p>
          </div>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm text-slate-600">
            <thead className="bg-slate-100 text-slate-700 uppercase font-semibold text-xs border-b border-slate-200">
              <tr>
                <th className="py-3 px-4">Task Name</th>
                <th className="py-3 px-4">Deterministic Status</th>
                <th className="py-3 px-4">CPM Float (Slack)</th>
                <th className="py-3 px-4">ML Risk Model</th>
                <th className="py-3 px-4">Advisory Factors</th>
                <th className="py-3 px-4 text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {risks.map((t) => (
                <tr key={t.taskId} className="hover:bg-slate-50 transition-colors">
                  <td className="py-3.5 px-4 font-medium text-slate-900">
                    <div className="flex items-center gap-2">
                      {t.taskName}
                      {t.isOnCriticalPath && (
                        <span className="bg-rose-100 text-rose-700 text-xs px-2 py-0.5 rounded font-mono font-semibold">
                          CRITICAL
                        </span>
                      )}
                    </div>
                  </td>
                  <td className="py-3.5 px-4">
                    <span
                      className={`text-xs px-2.5 py-1 rounded-full font-medium ${
                        t.deterministicScheduleStatus === 'PROJECTED_DELAY'
                          ? 'bg-rose-50 text-rose-700 border border-rose-200'
                          : t.deterministicScheduleStatus === 'CRITICAL_BOTTLENECK'
                          ? 'bg-amber-50 text-amber-700 border border-amber-200'
                          : 'bg-emerald-50 text-emerald-700 border border-emerald-200'
                      }`}
                    >
                      {t.deterministicScheduleStatus}
                    </span>
                  </td>
                  <td className="py-3.5 px-4 font-mono text-xs">
                    {t.deterministicSlackDays} working day(s)
                  </td>
                  <td className="py-3.5 px-4">
                    <div className="flex flex-col">
                      <span className="text-xs font-semibold text-slate-700">
                        Status: <span className="text-amber-600">{t.mlPredictionStatus}</span>
                      </span>
                      <span className="text-xs text-slate-400">Level: {t.riskLevel}</span>
                    </div>
                  </td>
                  <td className="py-3.5 px-4 text-xs text-slate-500 max-w-xs">
                    {t.contributingFactors.join('; ')}
                  </td>
                  <td className="py-3.5 px-4 text-right">
                    <button
                      onClick={() => setCompletingTaskId(t.taskId)}
                      className="text-xs bg-slate-100 hover:bg-slate-200 text-slate-800 font-medium px-2.5 py-1.5 rounded border border-slate-300 transition-colors"
                    >
                      Record Completion
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      {/* 5. Modal: Record Task Completion */}
      {completingTaskId && (
        <div className="fixed inset-0 bg-slate-900/40 backdrop-blur-sm flex items-center justify-center p-4 z-50">
          <div className="bg-white rounded-xl shadow-xl max-w-md w-full p-6 border border-slate-200 animate-in fade-in zoom-in duration-150">
            <h3 className="text-lg font-bold text-slate-900 mb-1">Record Task Completion</h3>
            <p className="text-xs text-slate-500 mb-4">
              Persist actual finish date to calculate variance and archive an immutable historical outcome record.
            </p>

            <form onSubmit={handleCompleteTask} className="space-y-4">
              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1">
                  Actual Finish Date *
                </label>
                <input
                  type="date"
                  required
                  value={actualFinishDate}
                  onChange={(e) => setActualFinishDate(e.target.value)}
                  className="w-full text-sm px-3 py-2 border border-slate-300 rounded-lg focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1">
                  Primary Delay Reason (Optional if delayed)
                </label>
                <input
                  type="text"
                  placeholder="e.g. Supplier delivery lag, API schema revision"
                  value={delayReason}
                  onChange={(e) => setDelayReason(e.target.value)}
                  className="w-full text-sm px-3 py-2 border border-slate-300 rounded-lg focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
                />
              </div>

              <div className="flex items-center justify-end gap-2 pt-3 border-t border-slate-100">
                <button
                  type="button"
                  onClick={() => setCompletingTaskId(null)}
                  className="px-4 py-2 text-sm text-slate-600 hover:bg-slate-100 rounded-lg"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={submitting}
                  className="px-4 py-2 text-sm bg-indigo-600 hover:bg-indigo-700 text-white font-medium rounded-lg shadow-sm disabled:opacity-50"
                >
                  {submitting ? 'Archiving...' : 'Save Outcome'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};
