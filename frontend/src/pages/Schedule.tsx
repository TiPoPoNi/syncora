import React, { useState, useEffect, useCallback, useRef } from 'react';
import { useSearchParams, useNavigate, Link } from 'react-router-dom';
import { api } from '../services/api';
import { GanttChart, GanttChartData } from '../components/GanttChart';
import { CriticalPathView, CriticalPathData } from '../components/CriticalPathView';
import { DelayImpactPanel, DelayImpactData } from '../components/DelayImpactPanel';
import { ScheduleDependenciesView } from '../components/ScheduleDependenciesView';
import { ScheduleResourcesView } from '../components/ScheduleResourcesView';
import { OptimizationDashboard } from '../components/OptimizationDashboard';
import { useOrganization } from '../context/OrganizationContext';
import {
  Calendar,
  GitBranch,
  Layers,
  AlertTriangle,
  AlertCircle,
  CheckCircle2,
  RefreshCw,
  Users,
  Download,
  ChevronDown,
  X,
  FileDown,
  Sparkles,
} from 'lucide-react';

interface Project {
  id: number;
  name: string;
  startDate?: string;
  deadline?: string;
  status?: string;
}

interface Conflict {
  resource: string;
  conflictingTask1: string;
  conflictingTask2: string;
  overlapStart: string;
  overlapEnd: string;
}

interface ScheduleTaskDto {
  id: number;
  name: string;
  estimatedDuration?: number;
  plannedStartDate?: string;
  plannedFinishDate?: string;
  status?: string;
  assignments?: Array<{
    id: number;
    taskId: number;
    assignmentType: string;
    workerId?: number;
    workerName?: string;
    vendorId?: number;
    vendorName?: string;
  }>;
  predecessorTaskIds?: number[];
}

export type ScheduleTabType =
  | 'gantt'
  | 'cpm'
  | 'dependencies'
  | 'delays'
  | 'resources'
  | 'optimization';

export function Schedule() {
  const [searchParams, setSearchParams] = useSearchParams();
  const navigate = useNavigate();
  const rawTab = searchParams.get('tab');

  // Handle redirects for migrated tools
  useEffect(() => {
    if (rawTab === 'assistant') {
      navigate('/ai-assistant', { replace: true });
    } else if (rawTab === 'analytics') {
      navigate('/ml-risk', { replace: true });
    }
  }, [rawTab, navigate]);

  const [projects, setProjects] = useState<Project[]>([]);
  const [selectedProjectId, setSelectedProjectId] = useState<number | null>(null);

  const [activeTab, setActiveTab] = useState<ScheduleTabType>(() => {
    const validTabs: ScheduleTabType[] = [
      'gantt',
      'cpm',
      'dependencies',
      'delays',
      'resources',
      'optimization',
    ];
    if (rawTab && (validTabs as string[]).includes(rawTab)) {
      return rawTab as ScheduleTabType;
    }
    return 'gantt';
  });

  const [ganttData, setGanttData] = useState<GanttChartData | null>(null);
  const [cpmData, setCpmData] = useState<CriticalPathData | null>(null);
  const [delayImpactData, setDelayImpactData] = useState<DelayImpactData | null>(null);
  const [scheduledTasks, setScheduledTasks] = useState<ScheduleTaskDto[]>([]);
  const [conflicts, setConflicts] = useState<Conflict[]>([]);

  const [loading, setLoading] = useState(false);
  const [recalculating, setRecalculating] = useState(false);
  const [pageError, setPageError] = useState<string | null>(null);
  const [pageSuccess, setPageSuccess] = useState<string | null>(null);

  const [exportMenuOpen, setExportMenuOpen] = useState(false);
  const exportMenuRef = useRef<HTMLDivElement>(null);

  const { currentOrg } = useOrganization();

  // Close export dropdown when clicking outside
  useEffect(() => {
    const handleOutsideClick = (e: MouseEvent) => {
      if (exportMenuRef.current && !exportMenuRef.current.contains(e.target as Node)) {
        setExportMenuOpen(false);
      }
    };
    document.addEventListener('mousedown', handleOutsideClick);
    return () => document.removeEventListener('mousedown', handleOutsideClick);
  }, []);

  // Auto-dismiss transient notifications
  useEffect(() => {
    if (pageError) {
      const t = setTimeout(() => setPageError(null), 6000);
      return () => clearTimeout(t);
    }
  }, [pageError]);

  useEffect(() => {
    if (pageSuccess) {
      const t = setTimeout(() => setPageSuccess(null), 4000);
      return () => clearTimeout(t);
    }
  }, [pageSuccess]);

  // Sync tab with URL
  const handleTabChange = (tab: ScheduleTabType) => {
    setActiveTab(tab);
    setSearchParams({ tab });
  };

  // Sync activeTab when rawTab query param changes externally (e.g. from sidebar clicks)
  useEffect(() => {
    if (rawTab && rawTab !== activeTab) {
      const validTabs: ScheduleTabType[] = [
        'gantt',
        'cpm',
        'dependencies',
        'delays',
        'resources',
        'optimization',
      ];
      if ((validTabs as string[]).includes(rawTab)) {
        setActiveTab(rawTab as ScheduleTabType);
      }
    }
  }, [rawTab, activeTab]);

  // 1. Fetch Projects for active organization
  useEffect(() => {
    let isMounted = true;
    api.get('/projects')
      .then((res) => {
        if (!isMounted) return;
        const list = res.data || [];
        setProjects(list);
        if (list.length > 0) {
          setSelectedProjectId(list[0].id);
        } else {
          setSelectedProjectId(null);
          setGanttData(null);
          setCpmData(null);
          setDelayImpactData(null);
          setScheduledTasks([]);
          setConflicts([]);
        }
      })
      .catch((err) => {
        if (!isMounted) return;
        console.error('Error fetching projects:', err);
        setPageError('Failed to load projects. Please verify organization access.');
      });
    return () => {
      isMounted = false;
    };
  }, [currentOrg?.id]);

  // 2. Fetch Project Schedule Data
  const loadProjectData = useCallback(async (projectId: number) => {
    setLoading(true);
    setPageError(null);
    try {
      const [ganttRes, cpmRes, delayRes, schedRes] = await Promise.all([
        api.get(`/projects/${projectId}/gantt`),
        api.get(`/projects/${projectId}/critical-path`),
        api.get(`/projects/${projectId}/delay-impact`),
        api.get(`/projects/${projectId}/schedule`),
      ]);

      setGanttData(ganttRes.data);
      setCpmData(cpmRes.data);
      setDelayImpactData(delayRes.data);
      setScheduledTasks(schedRes.data.tasks || []);
      setConflicts(schedRes.data.conflicts || []);
    } catch (err: any) {
      console.error('Error loading schedule data:', err);
      const msg = err.response?.data?.message || err.message || 'Failed to load schedule data.';
      setPageError(msg);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (selectedProjectId) {
      loadProjectData(selectedProjectId);
    }
  }, [selectedProjectId, loadProjectData]);

  // 3. Recalculate Schedule via Java CPM Engine
  const recalculateSchedule = async () => {
    if (!selectedProjectId) return;
    setRecalculating(true);
    setPageError(null);
    setPageSuccess(null);
    try {
      await api.post(`/projects/${selectedProjectId}/schedule/recalculate`);
      await loadProjectData(selectedProjectId);
      setPageSuccess('Schedule recalculated successfully. All dates, float values, and critical paths updated.');
    } catch (err: any) {
      const msg = err.response?.data?.message || err.message || 'Failed to recalculate schedule.';
      setPageError(msg);
    } finally {
      setRecalculating(false);
    }
  };

  // 4. Export Handlers
  const handleExport = async (type: 'calendar' | 'tasks' | 'assignments' | 'delays') => {
    if (!selectedProjectId) return;
    setExportMenuOpen(false);
    try {
      const endpoints: Record<string, { path: string; ext: string }> = {
        calendar: { path: `/projects/${selectedProjectId}/calendar.ics`, ext: 'ics' },
        tasks: { path: `/projects/${selectedProjectId}/exports/tasks.csv`, ext: 'csv' },
        assignments: { path: `/projects/${selectedProjectId}/exports/assignments.csv`, ext: 'csv' },
        delays: { path: `/projects/${selectedProjectId}/exports/delays.csv`, ext: 'csv' },
      };
      const target = endpoints[type];
      const res = await api.get(target.path, { responseType: 'blob' });
      const url = window.URL.createObjectURL(new Blob([res.data]));
      const link = document.createElement('a');
      link.href = url;
      link.setAttribute('download', `project_${selectedProjectId}_${type}.${target.ext}`);
      document.body.appendChild(link);
      link.click();
      link.remove();
      setPageSuccess(`Exported ${type} successfully.`);
    } catch (err: any) {
      const msg = err.response?.data?.message || err.message || 'Failed to export file.';
      setPageError(msg);
    }
  };

  const selectedProject = projects.find((p) => p.id === selectedProjectId);
  const tasksList = ganttData?.tasks.map((t) => ({ id: t.id, name: t.name, status: t.status, duration: t.duration })) || [];

  // Summary Metrics from live backend data
  const totalTasksCount = ganttData?.tasks?.length ?? 0;
  const criticalTasksCount = cpmData?.criticalTaskIds?.length ?? (ganttData?.tasks?.filter(t => t.critical)?.length ?? 0);
  const unresolvedConflictsCount = conflicts.length;
  const forecastCompletion = ganttData?.expectedCompletionDate || selectedProject?.deadline || 'Unscheduled';
  const deadlineStatus = ganttData?.deadlineStatus || 'ON_TRACK';

  return (
    <div className="space-y-5 animate-fadeIn pb-12">
      {/* 1. Compact Page Header */}
      <div className="bg-white p-5 rounded-2xl shadow-sm border border-gray-200 flex flex-wrap items-center justify-between gap-4">
        <div className="space-y-1">
          <div className="flex items-center gap-3">
            <h1 className="text-2xl font-black text-gray-900 tracking-tight">Schedule</h1>
            {selectedProject && (
              <span className="text-xs font-semibold px-2.5 py-0.5 rounded-full bg-blue-50 text-blue-700 border border-blue-200">
                {selectedProject.name}
              </span>
            )}
          </div>
          <p className="text-xs text-gray-500">
            {selectedProject ? (
              <span>
                Start: <strong className="text-gray-700">{selectedProject.startDate || ganttData?.projectStartDate || 'Not set'}</strong>
                {' • '}
                Deadline: <strong className="text-gray-700">{selectedProject.deadline || ganttData?.projectDeadline || 'None'}</strong>
                {' • '}
                Engine: <span className="font-semibold text-gray-700">Deterministic CPM</span>
              </span>
            ) : (
              'Plan, sequence, and track project schedules with deterministic Critical Path calculations.'
            )}
          </p>
        </div>

        {/* Controls: Project Selector, Recalculate, Export Menu */}
        <div className="flex items-center gap-3 flex-wrap">
          {/* Project Selector */}
          <div className="flex items-center gap-2 bg-gray-50 border border-gray-200 rounded-xl px-3 py-1.5 shadow-2xs">
            <label htmlFor="schedule-project-select" className="text-[11px] font-bold text-gray-500 uppercase">
              Project:
            </label>
            <select
              id="schedule-project-select"
              className="bg-transparent font-bold text-xs text-gray-800 focus:outline-none cursor-pointer max-w-[200px] truncate"
              value={selectedProjectId || ''}
              onChange={(e) => setSelectedProjectId(Number(e.target.value))}
            >
              {projects.length === 0 ? (
                <option value="">No projects found</option>
              ) : (
                projects.map((p) => (
                  <option key={p.id} value={p.id}>
                    {p.name}
                  </option>
                ))
              )}
            </select>
          </div>

          {/* Primary Action: Recalculate Schedule */}
          <button
            onClick={recalculateSchedule}
            disabled={recalculating || loading || !selectedProjectId}
            className="flex items-center bg-blue-600 hover:bg-blue-700 text-white px-4 py-2 rounded-xl font-semibold text-xs shadow-xs hover:shadow transition disabled:opacity-50"
            title="Recalculate CPM critical path, earliest/latest dates, and float"
          >
            <RefreshCw className={`w-3.5 h-3.5 mr-1.5 ${recalculating ? 'animate-spin' : ''}`} />
            {recalculating ? 'Recalculating...' : 'Recalculate Schedule'}
          </button>

          {/* Compact Export Menu */}
          <div className="relative" ref={exportMenuRef}>
            <button
              onClick={() => setExportMenuOpen(!exportMenuOpen)}
              disabled={!selectedProjectId}
              className="flex items-center gap-1.5 bg-white border border-gray-300 hover:bg-gray-50 text-gray-700 px-3 py-2 rounded-xl text-xs font-semibold shadow-2xs transition disabled:opacity-50"
              aria-haspopup="true"
              aria-expanded={exportMenuOpen}
              title="Export project schedule and assignments"
            >
              <Download className="w-3.5 h-3.5 text-gray-500" />
              <span>Export</span>
              <ChevronDown className="w-3.5 h-3.5 text-gray-400" />
            </button>

            {exportMenuOpen && (
              <div className="absolute right-0 mt-1.5 w-56 bg-white rounded-xl shadow-lg border border-gray-200 py-1.5 z-40 animate-fadeIn">
                <div className="px-3 py-1 text-[10px] font-bold text-gray-400 uppercase tracking-wider border-b border-gray-100">
                  Export Options
                </div>
                <button
                  onClick={() => handleExport('calendar')}
                  className="w-full text-left px-3 py-2 text-xs flex items-center gap-2.5 hover:bg-indigo-50 text-gray-700 hover:text-indigo-700 transition"
                >
                  <Calendar className="w-4 h-4 text-indigo-600 flex-shrink-0" />
                  <div>
                    <div className="font-semibold">Calendar (.ics)</div>
                    <div className="text-[10px] text-gray-400">iCalendar schedule sync</div>
                  </div>
                </button>
                <button
                  onClick={() => handleExport('tasks')}
                  className="w-full text-left px-3 py-2 text-xs flex items-center gap-2.5 hover:bg-emerald-50 text-gray-700 hover:text-emerald-700 transition"
                >
                  <FileDown className="w-4 h-4 text-emerald-600 flex-shrink-0" />
                  <div>
                    <div className="font-semibold">Tasks (CSV)</div>
                    <div className="text-[10px] text-gray-400">Task schedules & float</div>
                  </div>
                </button>
                <button
                  onClick={() => handleExport('assignments')}
                  className="w-full text-left px-3 py-2 text-xs flex items-center gap-2.5 hover:bg-blue-50 text-gray-700 hover:text-blue-700 transition"
                >
                  <FileDown className="w-4 h-4 text-blue-600 flex-shrink-0" />
                  <div>
                    <div className="font-semibold">Assignments (CSV)</div>
                    <div className="text-[10px] text-gray-400">Resource allocations</div>
                  </div>
                </button>
                <button
                  onClick={() => handleExport('delays')}
                  className="w-full text-left px-3 py-2 text-xs flex items-center gap-2.5 hover:bg-amber-50 text-gray-700 hover:text-amber-700 transition"
                >
                  <FileDown className="w-4 h-4 text-amber-600 flex-shrink-0" />
                  <div>
                    <div className="font-semibold">Delays (CSV)</div>
                    <div className="text-[10px] text-gray-400">Delay & variance log</div>
                  </div>
                </button>
              </div>
            )}
          </div>
        </div>
      </div>

      {/* Global Notifications */}
      {pageError && (
        <div role="alert" className="p-3.5 bg-rose-50 border border-rose-200 rounded-xl flex items-center justify-between text-rose-800 text-xs animate-fadeIn">
          <div className="flex items-center gap-2.5">
            <AlertCircle className="w-4 h-4 text-rose-600 flex-shrink-0" />
            <span>{pageError}</span>
          </div>
          <button onClick={() => setPageError(null)} className="text-rose-500 hover:text-rose-800 p-0.5 rounded">
            <X className="w-4 h-4" />
          </button>
        </div>
      )}

      {pageSuccess && (
        <div role="status" className="p-3.5 bg-emerald-50 border border-emerald-200 rounded-xl flex items-center justify-between text-emerald-800 text-xs animate-fadeIn">
          <div className="flex items-center gap-2.5">
            <CheckCircle2 className="w-4 h-4 text-emerald-600 flex-shrink-0" />
            <span>{pageSuccess}</span>
          </div>
          <button onClick={() => setPageSuccess(null)} className="text-emerald-500 hover:text-emerald-800 p-0.5 rounded">
            <X className="w-4 h-4" />
          </button>
        </div>
      )}

      {/* 2. Structured Tab Navigation: Core Scheduling & Optimization */}
      <div className="flex border-b border-gray-200 space-x-1 overflow-x-auto">
        <button
          onClick={() => handleTabChange('gantt')}
          className={`flex items-center py-2.5 px-4 border-b-2 font-bold text-xs whitespace-nowrap transition-colors ${
            activeTab === 'gantt'
              ? 'border-blue-600 text-blue-600'
              : 'border-transparent text-gray-500 hover:text-gray-800'
          }`}
        >
          <Calendar className="w-3.5 h-3.5 mr-1.5" />
          Gantt
        </button>

        <button
          onClick={() => handleTabChange('cpm')}
          className={`flex items-center py-2.5 px-4 border-b-2 font-bold text-xs whitespace-nowrap transition-colors ${
            activeTab === 'cpm'
              ? 'border-blue-600 text-blue-600'
              : 'border-transparent text-gray-500 hover:text-gray-800'
          }`}
        >
          <GitBranch className="w-3.5 h-3.5 mr-1.5" />
          Critical Path
          {criticalTasksCount > 0 && (
            <span className="ml-1.5 px-1.5 py-0.2 rounded-full text-[10px] font-bold bg-rose-100 text-rose-800">
              {criticalTasksCount}
            </span>
          )}
        </button>

        <button
          onClick={() => handleTabChange('dependencies')}
          className={`flex items-center py-2.5 px-4 border-b-2 font-bold text-xs whitespace-nowrap transition-colors ${
            activeTab === 'dependencies'
              ? 'border-blue-600 text-blue-600'
              : 'border-transparent text-gray-500 hover:text-gray-800'
          }`}
        >
          <Layers className="w-3.5 h-3.5 mr-1.5" />
          Dependencies
        </button>

        <button
          onClick={() => handleTabChange('delays')}
          className={`flex items-center py-2.5 px-4 border-b-2 font-bold text-xs whitespace-nowrap transition-colors ${
            activeTab === 'delays'
              ? 'border-blue-600 text-blue-600'
              : 'border-transparent text-gray-500 hover:text-gray-800'
          }`}
        >
          <AlertTriangle className="w-3.5 h-3.5 mr-1.5 text-amber-500" />
          Delay Impact
          {delayImpactData && delayImpactData.reportedDelays.length > 0 && (
            <span className="ml-1.5 px-1.5 py-0.2 rounded-full text-[10px] font-bold bg-amber-100 text-amber-800">
              {delayImpactData.reportedDelays.length}
            </span>
          )}
        </button>

        <button
          onClick={() => handleTabChange('resources')}
          className={`flex items-center py-2.5 px-4 border-b-2 font-bold text-xs whitespace-nowrap transition-colors ${
            activeTab === 'resources'
              ? 'border-blue-600 text-blue-600'
              : 'border-transparent text-gray-500 hover:text-gray-800'
          }`}
        >
          <Users className="w-3.5 h-3.5 mr-1.5" />
          Resources
          {unresolvedConflictsCount > 0 && (
            <span className="ml-1.5 px-1.5 py-0.2 rounded-full text-[10px] font-bold bg-rose-100 text-rose-800">
              {unresolvedConflictsCount}
            </span>
          )}
        </button>

        {/* Visual Divider */}
        <div className="h-5 w-px bg-gray-200 self-center mx-1 flex-shrink-0" />

        <button
          onClick={() => handleTabChange('optimization')}
          className={`flex items-center py-2.5 px-4 border-b-2 font-bold text-xs whitespace-nowrap transition-colors ${
            activeTab === 'optimization'
              ? 'border-indigo-600 text-indigo-600'
              : 'border-transparent text-gray-500 hover:text-gray-800'
          }`}
        >
          <Sparkles className="w-3.5 h-3.5 mr-1.5 text-indigo-600" />
          Optimization
        </button>
      </div>

      {/* 3. Schedule Summary Strip */}
      {selectedProjectId && !loading && (
        <div className="bg-white px-5 py-3 rounded-xl border border-gray-200 shadow-2xs flex flex-wrap items-center justify-between gap-4 text-xs">
          <div className="flex items-center gap-6 flex-wrap">
            <div>
              <span className="text-gray-400 text-[11px] block">Total Tasks</span>
              <span className="font-bold text-gray-800 text-sm">{totalTasksCount}</span>
            </div>

            <div className="border-l border-gray-100 pl-4">
              <span className="text-gray-400 text-[11px] block">Critical Tasks</span>
              <span className="font-bold text-rose-700 text-sm">{criticalTasksCount}</span>
            </div>

            <div className="border-l border-gray-100 pl-4">
              <span className="text-gray-400 text-[11px] block">Resource Conflicts</span>
              <span className={`text-sm font-bold ${unresolvedConflictsCount > 0 ? 'text-rose-700' : 'text-emerald-700'}`}>
                {unresolvedConflictsCount}
              </span>
            </div>

            <div className="border-l border-gray-100 pl-4">
              <span className="text-gray-400 text-[11px] block">Forecast Completion</span>
              <span className="font-bold text-gray-800 text-sm">{forecastCompletion}</span>
            </div>

            <div className="border-l border-gray-100 pl-4">
              <span className="text-gray-400 text-[11px] block">Deadline Status</span>
              <span
                className={`inline-flex items-center px-2 py-0.5 rounded text-[10px] font-bold ${
                  deadlineStatus === 'ON_TRACK'
                    ? 'bg-emerald-100 text-emerald-800'
                    : deadlineStatus === 'AT_RISK'
                    ? 'bg-amber-100 text-amber-800'
                    : deadlineStatus === 'DELAYED'
                    ? 'bg-rose-100 text-rose-800'
                    : 'bg-gray-100 text-gray-700'
                }`}
              >
                {deadlineStatus.replace(/_/g, ' ')}
              </span>
            </div>
          </div>

          <div className="text-[11px] text-gray-400">
            {cpmData?.minimumDurationWorkingDays ? (
              <span>Duration: <strong className="text-gray-700 font-semibold">{cpmData.minimumDurationWorkingDays}</strong> working days</span>
            ) : null}
          </div>
        </div>
      )}

      {/* 4. Loading State with protection */}
      {loading && (
        <div className="bg-white rounded-xl border border-gray-200 p-12 text-center text-gray-500 text-xs">
          <RefreshCw className="w-5 h-5 mx-auto mb-2 animate-spin text-blue-600" />
          Loading project schedule data...
        </div>
      )}

      {/* 5. Empty State: No Project Found */}
      {!loading && projects.length === 0 && (
        <div className="bg-white rounded-xl border border-gray-200 p-12 text-center space-y-3">
          <Calendar className="w-8 h-8 text-gray-300 mx-auto" />
          <h3 className="text-sm font-bold text-gray-900">No Projects Available</h3>
          <p className="text-xs text-gray-500 max-w-sm mx-auto">
            You don't have any projects in this workspace. Create a project to start planning and scheduling.
          </p>
          <Link
            to="/projects"
            className="inline-flex items-center px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white rounded-xl text-xs font-semibold shadow-xs"
          >
            Create New Project
          </Link>
        </div>
      )}

      {/* 6. Active Tab Content Views */}
      {!loading && selectedProjectId && (
        <div>
          {/* Tab 1: Gantt Chart */}
          {activeTab === 'gantt' && ganttData && (
            <GanttChart data={ganttData} />
          )}

          {/* Tab 2: Critical Path (CPM) */}
          {activeTab === 'cpm' && cpmData && (
            <CriticalPathView data={cpmData} conflicts={conflicts} />
          )}

          {/* Tab 3: Dependencies */}
          {activeTab === 'dependencies' && (
            <ScheduleDependenciesView
              projectId={selectedProjectId}
              tasks={tasksList}
              onDependenciesChanged={() => loadProjectData(selectedProjectId)}
            />
          )}

          {/* Tab 4: Delay Impact */}
          {activeTab === 'delays' && delayImpactData && (
            <DelayImpactPanel
              data={delayImpactData}
              tasks={tasksList}
              onRefresh={() => loadProjectData(selectedProjectId)}
            />
          )}

          {/* Tab 5: Resources */}
          {activeTab === 'resources' && (
            <ScheduleResourcesView
              projectId={selectedProjectId}
              projectName={selectedProject?.name || 'Project'}
              tasks={scheduledTasks}
              conflicts={conflicts}
              onRefresh={() => loadProjectData(selectedProjectId)}
            />
          )}

          {/* Tab 6: Schedule Optimization */}
          {activeTab === 'optimization' && (
            <OptimizationDashboard
              projectId={selectedProjectId}
              tasks={tasksList}
              onScheduleUpdated={() => loadProjectData(selectedProjectId)}
            />
          )}
        </div>
      )}
    </div>
  );
}
