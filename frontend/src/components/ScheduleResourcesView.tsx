import React, { useState } from 'react';
import { Link } from 'react-router-dom';
import { 
  Users, AlertCircle, AlertTriangle, CheckCircle2, Calendar, 
  ExternalLink, UserCheck, ShieldAlert, ArrowRight, X, Clock, Briefcase
} from 'lucide-react';

export interface ResourceConflictItem {
  resource: string;
  conflictingTask1: string;
  conflictingTask2: string;
  overlapStart: string;
  overlapEnd: string;
}

export interface TaskAssignmentItem {
  id: number;
  taskId: number;
  assignmentType: string;
  workerId?: number;
  workerName?: string;
  vendorId?: number;
  vendorName?: string;
}

export interface ScheduledTaskItem {
  id: number;
  name: string;
  estimatedDuration?: number;
  plannedStartDate?: string;
  plannedFinishDate?: string;
  status?: string;
  assignments?: TaskAssignmentItem[];
}

interface ScheduleResourcesViewProps {
  projectId: number;
  projectName: string;
  tasks: ScheduledTaskItem[];
  conflicts: ResourceConflictItem[];
  onRefresh: () => void;
}

export function ScheduleResourcesView({
  projectId,
  projectName,
  tasks,
  conflicts,
  onRefresh,
}: ScheduleResourcesViewProps) {
  const [selectedConflict, setSelectedConflict] = useState<ResourceConflictItem | null>(null);

  // Flatten assignments across all project tasks
  const assignmentRows: Array<{
    key: string;
    resourceName: string;
    resourceType: 'Worker' | 'Vendor';
    resourceId?: number;
    taskId: number;
    taskName: string;
    taskStatus: string;
    plannedStart: string;
    plannedFinish: string;
    duration: number;
    hasConflict: boolean;
    conflictDetail?: ResourceConflictItem;
  }> = [];

  tasks.forEach((task) => {
    if (task.assignments && task.assignments.length > 0) {
      task.assignments.forEach((a) => {
        const isWorker = !!a.workerName || !!a.workerId;
        const name = a.workerName || a.vendorName || 'Unassigned Resource';
        const type: 'Worker' | 'Vendor' = isWorker ? 'Worker' : 'Vendor';
        const id = a.workerId || a.vendorId;

        // Check if this resource has a conflict on this task
        const conflict = conflicts.find(
          (c) =>
            c.resource === name &&
            (c.conflictingTask1 === task.name || c.conflictingTask2 === task.name)
        );

        assignmentRows.push({
          key: `${task.id}-${a.id}`,
          resourceName: name,
          resourceType: type,
          resourceId: id,
          taskId: task.id,
          taskName: task.name,
          taskStatus: task.status || 'TODO',
          plannedStart: task.plannedStartDate || 'Unscheduled',
          plannedFinish: task.plannedFinishDate || 'Unscheduled',
          duration: task.estimatedDuration || 1,
          hasConflict: !!conflict,
          conflictDetail: conflict,
        });
      });
    }
  });

  // Calculate unique resources count
  const uniqueResources = new Set(assignmentRows.map((r) => r.resourceName)).size;

  return (
    <div className="space-y-6">
      {/* Header & Conflict Banner */}
      <div className="bg-white p-5 rounded-xl border border-gray-200 shadow-sm flex flex-wrap items-center justify-between gap-4">
        <div>
          <h3 className="text-base font-bold text-gray-900 flex items-center">
            <Users className="w-5 h-5 mr-2 text-indigo-600" />
            Resource Assignments & Overlap Feasibility
          </h3>
          <p className="text-xs text-gray-500 mt-1">
            Deterministic detection of workers or vendors scheduled to multiple concurrent tasks within overlapping timeframes.
          </p>
        </div>
        <div className="flex items-center gap-2">
          <span className="text-xs font-semibold px-2.5 py-1 rounded-full bg-slate-100 text-slate-700">
            {uniqueResources} Resources
          </span>
          <span className="text-xs font-semibold px-2.5 py-1 rounded-full bg-slate-100 text-slate-700">
            {assignmentRows.length} Total Assignments
          </span>
          <span
            className={`text-xs font-bold px-2.5 py-1 rounded-full ${
              conflicts.length > 0
                ? 'bg-rose-100 text-rose-800 border border-rose-200'
                : 'bg-emerald-100 text-emerald-800'
            }`}
          >
            {conflicts.length} Overlap Conflict{conflicts.length === 1 ? '' : 's'}
          </span>
        </div>
      </div>

      {/* Active Resource Overlaps Alert List */}
      {conflicts.length > 0 ? (
        <div className="space-y-3">
          <div className="flex items-center justify-between">
            <h4 className="text-xs font-bold uppercase tracking-wider text-rose-800 flex items-center">
              <ShieldAlert className="w-4 h-4 mr-1.5 text-rose-600" />
              Detected Schedule Overlaps ({conflicts.length})
            </h4>
            <span className="text-[11px] text-gray-500">Requires date shifting or worker reassignment</span>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
            {conflicts.map((c, idx) => (
              <div
                key={idx}
                className="p-4 bg-rose-50/70 border border-rose-200 rounded-xl flex flex-col justify-between space-y-3 shadow-2xs"
              >
                <div className="flex items-start justify-between gap-2">
                  <div className="flex items-start space-x-2.5">
                    <AlertCircle className="w-4 h-4 text-rose-600 flex-shrink-0 mt-0.5" />
                    <div>
                      <span className="font-bold text-gray-900 text-xs">{c.resource}</span>
                      <p className="text-[11px] text-gray-600 mt-0.5">
                        Double-booked across concurrent tasks
                      </p>
                    </div>
                  </div>
                  <button
                    onClick={() => setSelectedConflict(c)}
                    className="text-[11px] font-semibold text-rose-700 hover:text-rose-900 bg-white px-2.5 py-1 rounded-lg border border-rose-200 hover:bg-rose-100 transition shadow-2xs"
                  >
                    Inspect Conflict
                  </button>
                </div>

                <div className="bg-white/80 p-2.5 rounded-lg border border-rose-100 text-xs space-y-1">
                  <div className="flex items-center justify-between text-[11px]">
                    <span className="text-gray-500 truncate mr-2">1. {c.conflictingTask1}</span>
                    <span className="text-rose-700 font-bold whitespace-nowrap">Overlaps</span>
                  </div>
                  <div className="flex items-center justify-between text-[11px]">
                    <span className="text-gray-500 truncate mr-2">2. {c.conflictingTask2}</span>
                    <span className="text-gray-700 font-semibold">{c.overlapStart} → {c.overlapEnd}</span>
                  </div>
                </div>
              </div>
            ))}
          </div>
        </div>
      ) : (
        <div className="p-4 bg-emerald-50/60 border border-emerald-200 rounded-xl flex items-center justify-between text-xs text-emerald-800">
          <div className="flex items-center space-x-2.5">
            <CheckCircle2 className="w-4 h-4 text-emerald-600 flex-shrink-0" />
            <span>
              <strong>Feasible Resource Schedule:</strong> No simultaneous overlapping allocations detected for assigned personnel or vendors.
            </span>
          </div>
        </div>
      )}

      {/* Resource Allocation Roster Table */}
      <div className="bg-white rounded-xl border border-gray-200 shadow-sm overflow-hidden">
        <div className="p-4 bg-gray-50 border-b border-gray-200 flex items-center justify-between">
          <div>
            <h4 className="font-bold text-gray-900 text-xs uppercase tracking-wider">
              Project Resource Allocation Roster
            </h4>
            <p className="text-[11px] text-gray-500 mt-0.5">
              Scheduled tasks, assignment windows, and feasibility verification
            </p>
          </div>
          <Link
            to="/tasks"
            className="text-xs text-blue-600 hover:text-blue-700 font-semibold flex items-center gap-1"
          >
            <span>Manage Tasks & Assignments</span>
            <ExternalLink className="w-3.5 h-3.5" />
          </Link>
        </div>

        {assignmentRows.length === 0 ? (
          <div className="p-12 text-center text-xs text-gray-400 italic">
            No resource assignments found for this project. Assign workers or vendors to tasks in the Tasks section to view allocations.
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs">
              <thead className="bg-gray-100 text-gray-600 uppercase font-bold tracking-wider border-b border-gray-200 text-[10px]">
                <tr>
                  <th className="py-3 px-4">Resource</th>
                  <th className="py-3 px-3">Type</th>
                  <th className="py-3 px-4">Assigned Task</th>
                  <th className="py-3 px-3">Task Status</th>
                  <th className="py-3 px-4">Scheduled Window</th>
                  <th className="py-3 px-3 text-center">Duration</th>
                  <th className="py-3 px-4 text-center">Feasibility Status</th>
                  <th className="py-3 px-3 text-right">Action</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {assignmentRows.map((row) => (
                  <tr key={row.key} className="hover:bg-gray-50 transition-colors">
                    <td className="py-3 px-4 font-bold text-gray-900 flex items-center gap-2">
                      <div className="w-6 h-6 rounded-full bg-blue-100 text-blue-700 font-bold text-[10px] flex items-center justify-center flex-shrink-0">
                        {row.resourceName.charAt(0)}
                      </div>
                      <span>{row.resourceName}</span>
                    </td>
                    <td className="py-3 px-3 text-gray-600">
                      <span className="px-2 py-0.5 rounded text-[10px] font-semibold bg-gray-100 text-gray-700">
                        {row.resourceType}
                      </span>
                    </td>
                    <td className="py-3 px-4 font-medium text-gray-800">
                      {row.taskName}
                    </td>
                    <td className="py-3 px-3">
                      <span className={`px-2 py-0.5 rounded text-[10px] font-bold ${
                        row.taskStatus === 'COMPLETED'
                          ? 'bg-emerald-100 text-emerald-800'
                          : row.taskStatus === 'IN_PROGRESS'
                          ? 'bg-blue-100 text-blue-800'
                          : 'bg-gray-100 text-gray-700'
                      }`}>
                        {row.taskStatus}
                      </span>
                    </td>
                    <td className="py-3 px-4 text-gray-600 whitespace-nowrap">
                      {row.plannedStart} → {row.plannedFinish}
                    </td>
                    <td className="py-3 px-3 text-center text-gray-600">
                      {row.duration}d
                    </td>
                    <td className="py-3 px-4 text-center">
                      {row.hasConflict ? (
                        <button
                          onClick={() => row.conflictDetail && setSelectedConflict(row.conflictDetail)}
                          className="px-2 py-0.5 rounded-full text-[10px] font-bold bg-rose-100 text-rose-800 border border-rose-200 hover:bg-rose-200 transition cursor-pointer"
                          title="Click to inspect conflict details"
                        >
                          Overlap Conflict
                        </button>
                      ) : (
                        <span className="px-2 py-0.5 rounded-full text-[10px] font-bold bg-emerald-100 text-emerald-800">
                          Clear / Feasible
                        </span>
                      )}
                    </td>
                    <td className="py-3 px-3 text-right">
                      <Link
                        to={`/tasks?search=${encodeURIComponent(row.taskName)}`}
                        className="text-gray-400 hover:text-blue-600 p-1 inline-block"
                        title="View and edit task assignment in Tasks"
                      >
                        <ExternalLink className="w-3.5 h-3.5" />
                      </Link>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Inspect Conflict Modal */}
      {selectedConflict && (
        <div className="fixed inset-0 bg-slate-900/40 backdrop-blur-xs flex items-center justify-center z-50 p-4 animate-fadeIn">
          <div className="bg-white rounded-2xl shadow-xl border border-gray-200 max-w-lg w-full p-6 space-y-4">
            <div className="flex items-start justify-between pb-3 border-b border-gray-100">
              <div className="flex items-center space-x-2.5">
                <div className="p-2 bg-rose-100 text-rose-700 rounded-xl">
                  <ShieldAlert className="w-5 h-5" />
                </div>
                <div>
                  <h4 className="text-base font-bold text-gray-900">Resource Allocation Conflict</h4>
                  <p className="text-xs text-gray-500">Resource: {selectedConflict.resource}</p>
                </div>
              </div>
              <button
                onClick={() => setSelectedConflict(null)}
                className="text-gray-400 hover:text-gray-600 p-1 rounded-lg"
                aria-label="Close modal"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <div className="space-y-3 text-xs">
              <div className="p-3 bg-rose-50 border border-rose-100 rounded-xl space-y-1">
                <div className="font-bold text-rose-900">Double-Booking Window:</div>
                <div className="text-rose-700 font-semibold text-sm">
                  {selectedConflict.overlapStart} → {selectedConflict.overlapEnd}
                </div>
              </div>

              <div className="space-y-2">
                <div className="p-3 bg-gray-50 border border-gray-200 rounded-xl">
                  <span className="font-bold text-gray-500 uppercase text-[10px]">First Conflicting Task</span>
                  <div className="font-semibold text-gray-900 text-sm mt-0.5">{selectedConflict.conflictingTask1}</div>
                </div>
                <div className="p-3 bg-gray-50 border border-gray-200 rounded-xl">
                  <span className="font-bold text-gray-500 uppercase text-[10px]">Second Conflicting Task</span>
                  <div className="font-semibold text-gray-900 text-sm mt-0.5">{selectedConflict.conflictingTask2}</div>
                </div>
              </div>

              <div className="p-3 bg-slate-50 border border-slate-200 rounded-xl text-slate-700 space-y-1">
                <div className="font-bold text-slate-800">Recommended Resolution:</div>
                <p className="text-slate-600 leading-relaxed">
                  1. Shift task planned dates in Gantt or Task planning so the tasks execute sequentially.
                  <br />
                  2. Reassign one of the tasks to another available worker in the Tasks roster.
                </p>
              </div>
            </div>

            <div className="flex items-center justify-end gap-2 pt-2 border-t border-gray-100">
              <button
                onClick={() => setSelectedConflict(null)}
                className="px-4 py-2 bg-gray-100 hover:bg-gray-200 text-gray-700 rounded-xl text-xs font-semibold transition"
              >
                Close
              </button>
              <Link
                to="/tasks"
                className="px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white rounded-xl text-xs font-semibold shadow-xs transition"
              >
                Go to Tasks Page
              </Link>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
