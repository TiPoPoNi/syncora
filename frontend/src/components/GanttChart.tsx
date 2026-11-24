import React, { useState, useMemo } from 'react';
import { 
  Calendar, AlertCircle, AlertTriangle, CheckCircle2, Clock, 
  ZoomIn, ZoomOut, Info, ShieldAlert, Sparkles, Layers, ChevronRight, X
} from 'lucide-react';
import { Link } from 'react-router-dom';

export interface GanttTask {
  id: number;
  name: string;
  startDate: string;
  finishDate: string;
  duration: number;
  progress: number;
  status: string;
  critical: boolean;
  delayed: boolean;
  delayDays: number;
  totalSlack: number;
  dependencies: number[];
  assignees: string[];
}

export interface GanttChartData {
  projectId: number;
  projectName: string;
  projectStartDate: string;
  projectDeadline: string;
  expectedCompletionDate: string;
  deadlineStatus: string;
  currentDate: string;
  tasks: GanttTask[];
}

interface GanttChartProps {
  data: GanttChartData;
}

export function GanttChart({ data }: GanttChartProps) {
  const [selectedTask, setSelectedTask] = useState<GanttTask | null>(null);
  const [cellWidth, setCellWidth] = useState(38); // px per day

  // Compute timeline boundaries
  const { startDate, endDate, daysCount, dateList } = useMemo(() => {
    if (!data.tasks || data.tasks.length === 0) {
      const today = new Date();
      return { startDate: today, endDate: today, daysCount: 1, dateList: [today] };
    }

    let minDate = new Date(data.projectStartDate || data.tasks[0].startDate);
    let maxDate = new Date(data.expectedCompletionDate || data.tasks[0].finishDate);

    if (data.projectDeadline) {
      const deadline = new Date(data.projectDeadline);
      if (deadline > maxDate) maxDate = deadline;
    }

    data.tasks.forEach((t) => {
      const s = new Date(t.startDate);
      const f = new Date(t.finishDate);
      if (!isNaN(s.getTime()) && s < minDate) minDate = s;
      if (!isNaN(f.getTime()) && f > maxDate) maxDate = f;
    });

    // Add 2 days padding before and 5 days after
    const start = new Date(minDate);
    start.setDate(start.getDate() - 2);

    const end = new Date(maxDate);
    end.setDate(end.getDate() + 5);

    const dates: Date[] = [];
    const curr = new Date(start);
    while (curr <= end) {
      dates.push(new Date(curr));
      curr.setDate(curr.getDate() + 1);
    }

    return {
      startDate: start,
      endDate: end,
      daysCount: Math.max(1, dates.length),
      dateList: dates,
    };
  }, [data]);

  const getDayOffset = (dateStr: string) => {
    if (!dateStr) return 0;
    const d = new Date(dateStr);
    if (isNaN(d.getTime())) return 0;
    const diff = Math.floor((d.getTime() - startDate.getTime()) / (1000 * 60 * 60 * 24));
    return Math.max(0, diff);
  };

  const todayOffset = data.currentDate ? getDayOffset(data.currentDate) : -1;
  const deadlineOffset = data.projectDeadline ? getDayOffset(data.projectDeadline) : -1;

  const formatDateHeader = (d: Date) => {
    const day = d.getDate();
    const month = d.toLocaleDateString('en-US', { month: 'short' });
    const isWeekend = d.getDay() === 0 || d.getDay() === 6;
    return { day, month, isWeekend };
  };

  if (!data.tasks || data.tasks.length === 0) {
    return (
      <div className="bg-white rounded-xl shadow-sm border border-gray-200 p-12 text-center space-y-4">
        <div className="w-12 h-12 bg-blue-50 text-blue-600 rounded-full flex items-center justify-center mx-auto">
          <Calendar className="w-6 h-6" />
        </div>
        <div>
          <h3 className="text-base font-bold text-gray-900">No Tasks in Schedule</h3>
          <p className="text-xs text-gray-500 max-w-md mx-auto mt-1">
            This project does not have any scheduled tasks yet. Create and sequence tasks to generate the CPM schedule and interactive Gantt timeline.
          </p>
        </div>
        <Link
          to="/tasks"
          className="inline-flex items-center px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white rounded-xl text-xs font-semibold shadow-xs transition"
        >
          Go to Task Planning
        </Link>
      </div>
    );
  }

  return (
    <div className="bg-white rounded-xl shadow-sm border border-gray-200 overflow-hidden flex flex-col">
      {/* Top Gantt Toolbar: Legend & Zoom Controls */}
      <div className="p-3.5 bg-gray-50/80 border-b border-gray-200 flex flex-wrap items-center justify-between gap-3">
        {/* Legend */}
        <div className="flex flex-wrap items-center gap-3 text-xs text-gray-600">
          <span className="font-bold text-[10px] text-gray-400 uppercase tracking-wider">Legend:</span>
          
          <span className="flex items-center gap-1.5 bg-white px-2 py-1 rounded-md border border-gray-200 shadow-2xs">
            <span className="w-3 h-3 rounded-xs bg-rose-600 inline-flex items-center justify-center text-[7px] font-black text-white">
              C
            </span>
            <span className="font-semibold text-gray-800 text-[11px]">Critical Path (CPM)</span>
          </span>

          <span className="flex items-center gap-1.5 bg-white px-2 py-1 rounded-md border border-gray-200 shadow-2xs">
            <span className="w-3 h-3 rounded-xs bg-blue-600 inline-block"></span>
            <span className="font-semibold text-gray-800 text-[11px]">Normal Task</span>
          </span>

          <span className="flex items-center gap-1.5 bg-white px-2 py-1 rounded-md border border-gray-200 shadow-2xs">
            <span className="w-3 h-3 rounded-xs bg-amber-500 inline-flex items-center justify-center text-[7px] font-black text-white">
              +
            </span>
            <span className="font-semibold text-gray-800 text-[11px]">Delayed</span>
          </span>

          <span className="flex items-center gap-1.5 bg-white px-2 py-1 rounded-md border border-gray-200 shadow-2xs">
            <span className="w-3 h-3 rounded-xs bg-emerald-600 inline-flex items-center justify-center text-[7px] font-black text-white">
              ✓
            </span>
            <span className="font-semibold text-gray-800 text-[11px]">Completed</span>
          </span>
        </div>

        {/* Zoom controls & Presets */}
        <div className="flex items-center space-x-2">
          {/* Preset Buttons */}
          <div className="flex items-center bg-white border border-gray-200 rounded-lg p-0.5 shadow-2xs text-[11px]">
            <button
              onClick={() => setCellWidth(26)}
              className={`px-2 py-1 rounded font-medium transition ${
                cellWidth === 26 ? 'bg-gray-100 font-bold text-blue-700' : 'text-gray-600 hover:text-gray-900'
              }`}
            >
              Compact
            </button>
            <button
              onClick={() => setCellWidth(38)}
              className={`px-2 py-1 rounded font-medium transition ${
                cellWidth === 38 ? 'bg-gray-100 font-bold text-blue-700' : 'text-gray-600 hover:text-gray-900'
              }`}
            >
              Normal
            </button>
            <button
              onClick={() => setCellWidth(52)}
              className={`px-2 py-1 rounded font-medium transition ${
                cellWidth === 52 ? 'bg-gray-100 font-bold text-blue-700' : 'text-gray-600 hover:text-gray-900'
              }`}
            >
              Wide
            </button>
          </div>

          {/* Stepper Buttons */}
          <div className="flex items-center space-x-1 bg-white border border-gray-200 rounded-lg p-1 shadow-2xs">
            <button
              onClick={() => setCellWidth((prev) => Math.max(20, prev - 4))}
              className="p-1 hover:bg-gray-100 rounded text-gray-600 transition"
              title="Zoom Out"
              aria-label="Zoom Out"
            >
              <ZoomOut className="w-3.5 h-3.5" />
            </button>
            <span className="text-[11px] font-semibold text-gray-500 px-1">{cellWidth}px</span>
            <button
              onClick={() => setCellWidth((prev) => Math.min(68, prev + 4))}
              className="p-1 hover:bg-gray-100 rounded text-gray-600 transition"
              title="Zoom In"
              aria-label="Zoom In"
            >
              <ZoomIn className="w-3.5 h-3.5" />
            </button>
          </div>
        </div>
      </div>

      {/* Gantt Matrix Grid */}
      <div className="flex-1 overflow-x-auto overflow-y-auto max-h-[600px] relative">
        <div className="min-w-fit flex">
          {/* Left Fixed Task Column */}
          <div className="w-80 flex-shrink-0 bg-white border-r border-gray-200 sticky left-0 z-20 shadow-sm">
            {/* Header must be sticky top-0 AND z-30 to stay above rows and timeline header */}
            <div className="h-12 border-b border-gray-200 bg-gray-100 px-4 flex items-center justify-between font-bold text-xs uppercase tracking-wider text-gray-700 sticky top-0 z-30">
              <span>Task & Schedule</span>
              <span className="text-[10px] text-gray-400 font-normal">Float / Slack</span>
            </div>
            
            {data.tasks.map((task) => {
              const isSelected = selectedTask?.id === task.id;
              return (
                <div
                  key={task.id}
                  onClick={() => setSelectedTask(task)}
                  className={`h-12 border-b border-gray-100 px-4 flex items-center justify-between cursor-pointer transition-colors ${
                    isSelected ? 'bg-blue-50/90 border-l-4 border-l-blue-600' : 'hover:bg-gray-50'
                  }`}
                  title={`${task.name} (${task.duration}d) [${task.startDate} to ${task.finishDate}] - Float: ${task.totalSlack}d${task.critical ? ' [CRITICAL]' : ''}`}
                >
                  <div className="truncate pr-2">
                    <div className="text-xs font-semibold text-gray-800 truncate flex items-center gap-1.5">
                      {task.critical && (
                        <span 
                          className="px-1 py-0.2 rounded text-[9px] font-black bg-rose-600 text-white flex-shrink-0 shadow-2xs" 
                          title="Critical Path Task (Zero Float)"
                        >
                          CPM
                        </span>
                      )}
                      <span className="truncate">{task.name}</span>
                    </div>
                    <div className="text-[10px] text-gray-400 mt-0.5">
                      {task.duration}d duration • {task.startDate}
                    </div>
                  </div>

                  <div className="flex items-center space-x-1 flex-shrink-0">
                    <span 
                      className={`px-1.5 py-0.5 rounded text-[10px] font-bold ${
                        task.critical 
                          ? 'bg-rose-100 text-rose-800' 
                          : 'bg-emerald-100 text-emerald-800'
                      }`}
                      title={task.critical ? 'Zero Float (Critical)' : `${task.totalSlack} days buffer`}
                    >
                      {task.totalSlack}d
                    </span>

                    {task.delayed && (
                      <span 
                        className="px-1.5 py-0.5 rounded text-[10px] font-bold bg-amber-100 text-amber-800"
                        title={`Delayed by ${task.delayDays} day(s)`}
                      >
                        +{task.delayDays}d
                      </span>
                    )}

                    {task.status === 'COMPLETED' && (
                      <span className="px-1.5 py-0.5 rounded text-[10px] font-bold bg-emerald-100 text-emerald-800">
                        Done
                      </span>
                    )}
                  </div>
                </div>
              );
            })}
          </div>

          {/* Right Scrollable Timeline Canvas */}
          <div className="relative flex-1">
            {/* Timeline Header (Days) - sticky top-0 */}
            <div className="h-12 border-b border-gray-200 bg-gray-100 flex sticky top-0 z-10">
              {dateList.map((d, idx) => {
                const { day, month, isWeekend } = formatDateHeader(d);
                return (
                  <div
                    key={idx}
                    style={{ width: `${cellWidth}px` }}
                    className={`flex-shrink-0 border-r border-gray-200 flex flex-col items-center justify-center text-[10px] ${
                      isWeekend ? 'bg-gray-200/50 font-semibold text-gray-400' : 'text-gray-600'
                    }`}
                  >
                    <span className="font-bold">{day}</span>
                    <span className="text-[9px] uppercase tracking-tighter">{month}</span>
                  </div>
                );
              })}
            </div>

            {/* Vertical Guide Lines & Markers Canvas */}
            <div className="relative">
              {/* Vertical Day Grid Lines Background */}
              <div className="absolute inset-0 flex pointer-events-none">
                {dateList.map((d, idx) => {
                  const isWeekend = d.getDay() === 0 || d.getDay() === 6;
                  return (
                    <div
                      key={idx}
                      style={{ width: `${cellWidth}px` }}
                      className={`flex-shrink-0 border-r border-gray-100 h-full ${
                        isWeekend ? 'bg-gray-50/50' : ''
                      }`}
                    />
                  );
                })}
              </div>

              {/* "Today" Vertical Marker Line */}
              {todayOffset >= 0 && todayOffset < daysCount && (
                <div
                  style={{ left: `${todayOffset * cellWidth + cellWidth / 2}px` }}
                  className="absolute top-0 bottom-0 w-0.5 bg-blue-500 z-10 pointer-events-none shadow-sm"
                >
                  <span className="absolute -top-3.5 -translate-x-1/2 bg-blue-600 text-white text-[9px] font-bold px-1.5 py-0.5 rounded shadow">
                    Today
                  </span>
                </div>
              )}

              {/* "Project Deadline" Vertical Marker Line */}
              {deadlineOffset >= 0 && deadlineOffset < daysCount && (
                <div
                  style={{ left: `${deadlineOffset * cellWidth + cellWidth / 2}px` }}
                  className="absolute top-0 bottom-0 w-0.5 border-l-2 border-dashed border-rose-600 z-10 pointer-events-none shadow"
                >
                  <span className="absolute -top-3.5 -translate-x-1/2 bg-rose-600 text-white text-[9px] font-bold px-1.5 py-0.5 rounded shadow">
                    Deadline
                  </span>
                </div>
              )}

              {/* Task Bars Row by Row */}
              {data.tasks.map((task) => {
                const startOffset = getDayOffset(task.startDate);
                const endOffset = getDayOffset(task.finishDate);
                const barWidth = Math.max(cellWidth, (endOffset - startOffset + 1) * cellWidth - 4);
                const leftPos = startOffset * cellWidth + 2;

                const isSelected = selectedTask?.id === task.id;

                let barBg = 'bg-blue-600 border-blue-700';
                if (task.status === 'COMPLETED') {
                  barBg = 'bg-emerald-600 border-emerald-700';
                } else if (task.critical) {
                  barBg = 'bg-rose-600 border-rose-700 ring-1 ring-rose-300';
                } else if (task.delayed) {
                  barBg = 'bg-amber-500 border-amber-600';
                }

                return (
                  <div
                    key={task.id}
                    className="h-12 border-b border-gray-100 relative flex items-center"
                    style={{ width: `${daysCount * cellWidth}px` }}
                  >
                    <div
                      onClick={() => setSelectedTask(task)}
                      style={{ left: `${leftPos}px`, width: `${barWidth}px` }}
                      className={`absolute h-7 rounded-md cursor-pointer text-white shadow-xs flex items-center px-2 border transition-all hover:scale-[1.01] ${barBg} ${
                        isSelected ? 'ring-2 ring-offset-1 ring-blue-500 z-10' : ''
                      }`}
                      title={`${task.name} (${task.duration}d) [${task.startDate} to ${task.finishDate}] - Float: ${task.totalSlack}d${task.critical ? ' [CRITICAL]' : ''}`}
                    >
                      {/* Progress bar fill */}
                      {task.progress > 0 && (
                        <div
                          style={{ width: `${task.progress}%` }}
                          className="absolute left-0 top-0 bottom-0 bg-white/20 rounded-l-md pointer-events-none"
                        />
                      )}

                      <div className="relative z-10 text-[11px] font-bold truncate flex items-center gap-1.5 drop-shadow-xs">
                        {task.critical && (
                          <span className="px-1 py-0.2 rounded text-[8px] font-black bg-white/25 text-white flex-shrink-0">
                            CPM
                          </span>
                        )}
                        {task.status === 'COMPLETED' && (
                          <span className="text-[10px]">✓</span>
                        )}
                        <span className="truncate">{task.name}</span>
                      </div>
                    </div>
                  </div>
                );
              })}
            </div>
          </div>
        </div>
      </div>

      {/* Selected Task Details Drawer/Footer */}
      {selectedTask && (
        <div className="p-4 bg-gray-50 border-t border-gray-200 flex flex-wrap items-center justify-between gap-4 text-xs animate-fadeIn">
          <div className="flex flex-wrap items-center gap-3">
            <div
              className={`w-3 h-3 rounded-full flex-shrink-0 ${
                selectedTask.critical ? 'bg-rose-600 ring-4 ring-rose-100' : 'bg-blue-500'
              }`}
            />
            <div>
              <span className="font-bold text-gray-900 text-sm">{selectedTask.name}</span>
              <span className="text-gray-500 ml-2">
                [{selectedTask.startDate} → {selectedTask.finishDate}]
              </span>
            </div>
            {selectedTask.critical && (
              <span className="px-2 py-0.5 rounded text-[11px] font-bold bg-rose-100 text-rose-800 border border-rose-200">
                Critical Path (Zero Float)
              </span>
            )}
            {selectedTask.delayed && (
              <span className="px-2 py-0.5 rounded text-[11px] font-bold bg-amber-100 text-amber-800 border border-amber-200">
                Delayed +{selectedTask.delayDays}d
              </span>
            )}
            {selectedTask.status === 'COMPLETED' && (
              <span className="px-2 py-0.5 rounded text-[11px] font-bold bg-emerald-100 text-emerald-800 border border-emerald-200">
                Completed
              </span>
            )}
          </div>

          <div className="flex flex-wrap items-center gap-5 text-gray-600">
            <div>
              <span className="text-gray-400">Duration:</span>{' '}
              <span className="font-semibold text-gray-800">{selectedTask.duration} working days</span>
            </div>
            <div>
              <span className="text-gray-400">Total Slack (Float):</span>{' '}
              <span className="font-semibold text-gray-800">{selectedTask.totalSlack} days</span>
            </div>
            <div>
              <span className="text-gray-400">Predecessors:</span>{' '}
              <span className="font-semibold text-gray-800">
                {selectedTask.dependencies.length > 0 ? `${selectedTask.dependencies.length} tasks` : 'None'}
              </span>
            </div>
            <div>
              <span className="text-gray-400">Assignees:</span>{' '}
              <span className="font-semibold text-gray-800">
                {selectedTask.assignees.length > 0 ? selectedTask.assignees.join(', ') : 'Unassigned'}
              </span>
            </div>
            <button
              onClick={() => setSelectedTask(null)}
              className="text-gray-400 hover:text-gray-700 p-1 rounded transition"
              aria-label="Close task details"
            >
              <X className="w-4 h-4" />
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
