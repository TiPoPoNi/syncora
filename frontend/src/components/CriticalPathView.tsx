import React, { useState } from 'react';
import { 
  GitBranch, Clock, AlertCircle, AlertTriangle, CheckCircle, 
  ChevronRight, ArrowRight, ShieldCheck, Info, Users, ShieldAlert
} from 'lucide-react';

export interface CpmTask {
  taskId: number;
  taskName: string;
  duration: number;
  earlyStart: number;
  earlyFinish: number;
  lateStart: number;
  lateFinish: number;
  totalSlack: number;
  critical: boolean;
  earlyStartDate: string;
  earlyFinishDate: string;
  lateStartDate: string;
  lateFinishDate: string;
  predecessorTaskIds: number[];
  successorTaskIds: number[];
}

export interface CriticalPathData {
  projectId: number;
  projectName: string;
  projectStartDate: string;
  earliestProjectedCompletion: string;
  minimumDurationWorkingDays: number;
  tasks: CpmTask[];
  criticalTaskIds: number[];
  representativeCriticalPath: number[];
  allCriticalPaths: number[][];
  criticalTaskNames: string[];
}

interface ConflictInfo {
  resource: string;
  conflictingTask1: string;
  conflictingTask2: string;
  overlapStart?: string;
  overlapEnd?: string;
}

interface CriticalPathViewProps {
  data: CriticalPathData;
  conflicts?: ConflictInfo[];
}

export function CriticalPathView({ data, conflicts = [] }: CriticalPathViewProps) {
  const [inspectedTaskId, setInspectedTaskId] = useState<number | null>(
    data.representativeCriticalPath.length > 0 ? data.representativeCriticalPath[0] : null
  );

  const inspectedTask = data.tasks.find((t) => t.taskId === inspectedTaskId) || data.tasks[0];
  const criticalCount = data.criticalTaskIds.length;
  const totalTasks = data.tasks.length;
  const totalFloatAvailable = data.tasks.reduce((acc, t) => acc + t.totalSlack, 0);

  // Helper to check if a task has resource overlaps
  const getTaskConflict = (taskName: string) => {
    return conflicts.find(
      (c) => c.conflictingTask1 === taskName || c.conflictingTask2 === taskName
    );
  };

  return (
    <div className="space-y-6">
      {/* Analytical Callout: Classical CPM vs Resource-Constrained Feasibility */}
      <div className="bg-blue-50/70 border border-blue-200 rounded-xl p-4 flex items-start gap-3 text-xs text-blue-900">
        <Info className="w-5 h-5 text-blue-600 flex-shrink-0 mt-0.5" />
        <div className="space-y-1">
          <span className="font-bold">Understanding CPM vs. Resource Feasibility Analysis:</span>
          <p className="text-blue-800 leading-relaxed">
            <strong>Classical CPM (Critical Path Method)</strong> calculates the theoretical minimum project duration based purely on task durations and precedence chains under an unconstrained resource assumption.
            In contrast, <strong>Resource Feasibility</strong> verifies actual personnel and vendor concurrency limits. A task may have positive float under CPM yet still encounter schedule bottlenecks if assigned resources are over-allocated.
          </p>
        </div>
      </div>

      {/* KPI Cards */}
      <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
        <div className="bg-white p-4 rounded-xl border border-gray-200 shadow-sm flex items-center space-x-3.5">
          <div className="p-2.5 bg-rose-100 text-rose-600 rounded-lg">
            <Clock className="w-5 h-5" />
          </div>
          <div>
            <p className="text-[10px] font-bold text-gray-400 uppercase tracking-wider">Minimum Duration</p>
            <h4 className="text-xl font-black text-gray-900">
              {data.minimumDurationWorkingDays}{' '}
              <span className="text-xs font-medium text-gray-500">working days</span>
            </h4>
          </div>
        </div>

        <div className="bg-white p-4 rounded-xl border border-gray-200 shadow-sm flex items-center space-x-3.5">
          <div className="p-2.5 bg-rose-100 text-rose-600 rounded-lg">
            <GitBranch className="w-5 h-5" />
          </div>
          <div>
            <p className="text-[10px] font-bold text-gray-400 uppercase tracking-wider">Critical Tasks</p>
            <h4 className="text-xl font-black text-gray-900">
              {criticalCount}{' '}
              <span className="text-xs font-medium text-gray-500">of {totalTasks} tasks</span>
            </h4>
          </div>
        </div>

        <div className="bg-white p-4 rounded-xl border border-gray-200 shadow-sm flex items-center space-x-3.5">
          <div className="p-2.5 bg-emerald-100 text-emerald-600 rounded-lg">
            <ShieldCheck className="w-5 h-5" />
          </div>
          <div>
            <p className="text-[10px] font-bold text-gray-400 uppercase tracking-wider">Total Buffer Slack</p>
            <h4 className="text-xl font-black text-gray-900">
              {totalFloatAvailable} <span className="text-xs font-medium text-gray-500">days float</span>
            </h4>
          </div>
        </div>

        <div className="bg-white p-4 rounded-xl border border-gray-200 shadow-sm flex items-center space-x-3.5">
          <div className="p-2.5 bg-blue-100 text-blue-600 rounded-lg">
            <CheckCircle className="w-5 h-5" />
          </div>
          <div>
            <p className="text-[10px] font-bold text-gray-400 uppercase tracking-wider">Earliest Completion</p>
            <h4 className="text-lg font-black text-gray-900">{data.earliestProjectedCompletion || 'Unscheduled'}</h4>
          </div>
        </div>
      </div>

      {/* Critical Path Flowchart Sequence */}
      <div className="bg-white p-5 rounded-xl border border-gray-200 shadow-sm">
        <div className="flex items-center justify-between mb-3">
          <div>
            <h3 className="text-base font-bold text-gray-900 flex items-center">
              <span className="w-2.5 h-2.5 rounded-full bg-rose-600 mr-2" />
              Critical Path Sequence
            </h3>
            <p className="text-xs text-gray-500 mt-0.5">
              Zero-slack task chain determining project completion. Any delay on these tasks directly moves the project deadline.
            </p>
          </div>
          <span className="px-2.5 py-1 bg-rose-50 text-rose-700 text-xs font-bold rounded-full border border-rose-200">
            {data.representativeCriticalPath.length} Milestones
          </span>
        </div>

        {data.criticalTaskNames.length === 0 ? (
          <div className="p-6 bg-gray-50 rounded-lg text-center text-xs text-gray-400 italic">
            No critical path identified. Ensure project tasks are sequenced with dependencies.
          </div>
        ) : (
          <div className="flex flex-wrap items-center gap-2 p-3.5 bg-gray-50 rounded-lg border border-gray-100 overflow-x-auto">
            {data.criticalTaskNames.map((name, idx) => (
              <React.Fragment key={idx}>
                <div
                  onClick={() => {
                    const t = data.tasks.find((task) => task.taskName === name);
                    if (t) setInspectedTaskId(t.taskId);
                  }}
                  className={`px-3.5 py-2 rounded-lg border shadow-2xs cursor-pointer transition-all ${
                    inspectedTask?.taskName === name
                      ? 'bg-rose-600 text-white border-rose-700 ring-2 ring-rose-300 font-bold'
                      : 'bg-white text-gray-800 border-gray-200 hover:border-rose-400 font-semibold'
                  }`}
                >
                  <div className="text-xs flex items-center gap-1.5">
                    <span className="w-4 h-4 rounded-full bg-rose-100 text-rose-700 text-[10px] flex items-center justify-center font-bold">
                      {idx + 1}
                    </span>
                    <span>{name}</span>
                  </div>
                </div>
                {idx < data.criticalTaskNames.length - 1 && (
                  <ArrowRight className="w-3.5 h-3.5 text-gray-400 flex-shrink-0" />
                )}
              </React.Fragment>
            ))}
          </div>
        )}
      </div>

      {/* CPM Table & Task Inspection Split */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Left 2 Cols: CPM Table */}
        <div className="lg:col-span-2 bg-white rounded-xl border border-gray-200 shadow-sm overflow-hidden">
          <div className="p-3.5 bg-gray-50 border-b border-gray-200 flex items-center justify-between">
            <h4 className="font-bold text-gray-900 text-xs uppercase tracking-wider">
              Deterministic CPM Task Analysis
            </h4>
            <span className="text-[11px] text-gray-500">Click any row to inspect float, windows & predecessors</span>
          </div>

          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs">
              <thead className="bg-gray-100 text-gray-600 uppercase font-bold tracking-wider border-b border-gray-200 text-[10px]">
                <tr>
                  <th className="py-2.5 px-3">Task</th>
                  <th className="py-2.5 px-2 text-center">Dur</th>
                  <th className="py-2.5 px-2 text-center">ES</th>
                  <th className="py-2.5 px-2 text-center">EF</th>
                  <th className="py-2.5 px-2 text-center">LS</th>
                  <th className="py-2.5 px-2 text-center">LF</th>
                  <th className="py-2.5 px-2 text-center">Float</th>
                  <th className="py-2.5 px-3 text-center">Criticality</th>
                  <th className="py-2.5 px-2 text-center">Feasibility</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {data.tasks.map((task) => {
                  const isSelected = inspectedTask?.taskId === task.taskId;
                  const conflict = getTaskConflict(task.taskName);

                  return (
                    <tr
                      key={task.taskId}
                      onClick={() => setInspectedTaskId(task.taskId)}
                      className={`cursor-pointer transition-colors ${
                        isSelected ? 'bg-blue-50 font-semibold' : 'hover:bg-gray-50'
                      }`}
                    >
                      <td className="py-2.5 px-3 flex items-center font-medium text-gray-800">
                        {task.critical && (
                          <span className="w-2 h-2 rounded-full bg-rose-600 mr-2 flex-shrink-0" />
                        )}
                        <span className="truncate max-w-[200px]">{task.taskName}</span>
                      </td>
                      <td className="py-2.5 px-2 text-center text-gray-600">{task.duration}d</td>
                      <td className="py-2.5 px-2 text-center text-gray-600">{task.earlyStart}</td>
                      <td className="py-2.5 px-2 text-center text-gray-600">{task.earlyFinish}</td>
                      <td className="py-2.5 px-2 text-center text-gray-600">{task.lateStart}</td>
                      <td className="py-2.5 px-2 text-center text-gray-600">{task.lateFinish}</td>
                      <td className="py-2.5 px-2 text-center">
                        <span
                          className={`px-1.5 py-0.5 rounded font-bold text-[10px] ${
                            task.totalSlack === 0
                              ? 'bg-rose-100 text-rose-800'
                              : 'bg-emerald-100 text-emerald-800'
                          }`}
                        >
                          {task.totalSlack}d
                        </span>
                      </td>
                      <td className="py-2.5 px-3 text-center">
                        {task.critical ? (
                          <span className="px-2 py-0.5 bg-rose-600 text-white rounded text-[10px] font-bold shadow-2xs">
                            CRITICAL
                          </span>
                        ) : (
                          <span className="px-2 py-0.5 bg-gray-100 text-gray-600 rounded text-[10px] font-medium">
                            NON-CRITICAL
                          </span>
                        )}
                      </td>
                      <td className="py-2.5 px-2 text-center">
                        {conflict ? (
                          <span 
                            className="px-1.5 py-0.5 bg-amber-100 text-amber-800 border border-amber-200 rounded text-[9px] font-bold"
                            title={`Resource conflict with ${conflict.resource}`}
                          >
                            Overlap
                          </span>
                        ) : (
                          <span className="text-[10px] text-emerald-600 font-semibold">
                            OK
                          </span>
                        )}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </div>

        {/* Right 1 Col: Inspected Task Inspector */}
        <div className="bg-white rounded-xl border border-gray-200 shadow-sm p-5 flex flex-col justify-between">
          {inspectedTask ? (
            <div className="space-y-4">
              <div className="flex items-center justify-between pb-3 border-b border-gray-100">
                <div>
                  <span className="text-[10px] font-bold uppercase tracking-wider text-gray-400">Task Detail</span>
                  <h4 className="text-base font-bold text-gray-900 mt-0.5">{inspectedTask.taskName}</h4>
                </div>
                {inspectedTask.critical ? (
                  <span className="px-2 py-0.5 bg-rose-100 text-rose-800 rounded-md text-[11px] font-bold border border-rose-200">
                    Critical Path (Zero Float)
                  </span>
                ) : (
                  <span className="px-2 py-0.5 bg-emerald-100 text-emerald-800 rounded-md text-[11px] font-bold border border-emerald-200">
                    Float Buffer: {inspectedTask.totalSlack}d
                  </span>
                )}
              </div>

              {/* Resource Conflict Notice on this task (even if non-critical under CPM) */}
              {getTaskConflict(inspectedTask.taskName) && (
                <div className="p-3 bg-amber-50 border border-amber-200 rounded-lg text-xs space-y-1">
                  <div className="font-bold text-amber-900 flex items-center">
                    <AlertTriangle className="w-3.5 h-3.5 mr-1 text-amber-600" />
                    Resource Overlap Detected
                  </div>
                  <p className="text-amber-800 text-[11px]">
                    Resource <strong>{getTaskConflict(inspectedTask.taskName)?.resource}</strong> is double-booked during this task timeframe.
                  </p>
                </div>
              )}

              <div className="grid grid-cols-2 gap-2 text-xs">
                <div className="p-2.5 bg-gray-50 rounded-lg">
                  <p className="text-[10px] text-gray-400 uppercase font-semibold">Duration</p>
                  <p className="font-bold text-gray-800 text-xs mt-0.5">{inspectedTask.duration} working days</p>
                </div>
                <div className="p-2.5 bg-gray-50 rounded-lg">
                  <p className="text-[10px] text-gray-400 uppercase font-semibold">Total Float (Slack)</p>
                  <p className="font-bold text-gray-800 text-xs mt-0.5">{inspectedTask.totalSlack} days buffer</p>
                </div>
                <div className="p-2.5 bg-blue-50/60 rounded-lg">
                  <p className="text-[10px] text-blue-500 uppercase font-semibold">Earliest Window</p>
                  <p className="font-bold text-gray-800 text-[11px] mt-0.5">
                    {inspectedTask.earlyStartDate} → {inspectedTask.earlyFinishDate}
                  </p>
                </div>
                <div className="p-2.5 bg-purple-50/60 rounded-lg">
                  <p className="text-[10px] text-purple-500 uppercase font-semibold">Latest Window</p>
                  <p className="font-bold text-gray-800 text-[11px] mt-0.5">
                    {inspectedTask.lateStartDate} → {inspectedTask.lateFinishDate}
                  </p>
                </div>
              </div>

              <div>
                <h5 className="text-[10px] font-bold uppercase tracking-wider text-gray-400 mb-1.5">Predecessors</h5>
                {inspectedTask.predecessorTaskIds.length > 0 ? (
                  <div className="flex flex-wrap gap-1.5">
                    {inspectedTask.predecessorTaskIds.map((predId) => {
                      const pred = data.tasks.find((t) => t.taskId === predId);
                      return (
                        <button
                          key={predId}
                          onClick={() => setInspectedTaskId(predId)}
                          className="px-2 py-1 bg-gray-100 hover:bg-gray-200 text-gray-800 rounded text-xs font-medium transition"
                        >
                          {pred ? pred.taskName : `Task #${predId}`}
                        </button>
                      );
                    })}
                  </div>
                ) : (
                  <p className="text-xs text-gray-400 italic">None (Project Entry Task)</p>
                )}
              </div>

              <div>
                <h5 className="text-[10px] font-bold uppercase tracking-wider text-gray-400 mb-1.5">Successors</h5>
                {inspectedTask.successorTaskIds.length > 0 ? (
                  <div className="flex flex-wrap gap-1.5">
                    {inspectedTask.successorTaskIds.map((succId) => {
                      const succ = data.tasks.find((t) => t.taskId === succId);
                      return (
                        <button
                          key={succId}
                          onClick={() => setInspectedTaskId(succId)}
                          className="px-2 py-1 bg-gray-100 hover:bg-gray-200 text-gray-800 rounded text-xs font-medium transition"
                        >
                          {succ ? succ.taskName : `Task #${succId}`}
                        </button>
                      );
                    })}
                  </div>
                ) : (
                  <p className="text-xs text-gray-400 italic">None (Terminal Deliverable Task)</p>
                )}
              </div>
            </div>
          ) : (
            <div className="text-gray-400 text-xs text-center py-10 italic">Select a task from the table to inspect details</div>
          )}
        </div>
      </div>
    </div>
  );
}
