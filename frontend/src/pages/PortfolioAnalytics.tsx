import React, { useEffect, useState, useMemo } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../services/api';
import { useAuth } from '../context/AuthContext';
import { useOrganization } from '../context/OrganizationContext';
import {
  BarChart3,
  TrendingUp,
  AlertTriangle,
  Clock,
  CheckCircle2,
  Calendar,
  Layers,
  Users,
  Truck,
  Download,
  Filter,
  RefreshCw,
  ExternalLink,
  ShieldAlert,
  Info,
  ChevronRight,
  Activity,
  ArrowUpRight,
  ArrowDownRight,
  Minus,
  Sparkles
} from 'lucide-react';

interface PortfolioKpiSummary {
  totalProjects: number;
  activeProjects: number;
  completedProjects: number;
  onHoldProjects: number;
  cancelledProjects: number;
  projectsForecastToMissDeadlines: number;
  projectsMeetingDeadlines: number;
  historicalOnTimeDeliveryRatePercent: number | null;
  averageScheduleVarianceDays: number | null;
  totalOverdueTasks: number;
  resourceUtilizationPercent: number | null;
  vendorDeliveryReliabilityPercent: number | null;
  reportingPeriodStart: string | null;
  reportingPeriodEnd: string | null;
  metricFormulas: Record<string, string>;
}

interface ProjectPerformance {
  projectId: number;
  projectName: string;
  projectCode: string;
  clientName: string;
  managerName: string;
  status: string;
  priority: string;
  plannedStartDate: string | null;
  plannedFinishDate: string | null;
  expectedCompletionDate: string | null;
  actualCompletionDate: string | null;
  deadline: string | null;
  scheduleVarianceDays: number | null;
  delayStatus: string;
  totalTasks: number;
  completedTasks: number;
  overdueTasks: number;
  progressPercent: number;
  hasActiveConflict: boolean;
  hasCriticalPathDelay: boolean;
  hasVendorDelay: boolean;
  mlDelayRiskScore: number | null;
}

interface WorkerUtilization {
  workerId: number;
  workerName: string;
  role: string;
  email: string;
  assignedTasksCount: number;
  assignedWorkingHours: number;
  availableWorkingHours: number;
  utilizationPercent: number | null;
  utilizationCategory: string;
  activeConflictsCount: number;
}

interface ResourceUtilizationSummary {
  reportingPeriodStart: string | null;
  reportingPeriodEnd: string | null;
  totalTrackedWorkers: number;
  totalAssignedHours: number;
  totalAvailableHours: number;
  portfolioUtilizationPercent: number | null;
  overallocatedWorkersCount: number;
  underutilizedWorkersCount: number;
  optimalWorkersCount: number;
  idleWorkersCount: number;
  workerUtilizations: WorkerUtilization[];
  formulaDefinition: string;
}

interface VendorDeliveryPerformance {
  vendorId: number;
  vendorName: string;
  contactEmail: string;
  totalRecordedDeliveries: number;
  onTimeDeliveries: number;
  lateDeliveries: number;
  reliabilityRatePercent: number | null;
  averageDeliveryDelayDays: number | null;
  outstandingCommitmentsCount: number;
  sampleSizeReliable: boolean;
}

interface VendorAnalyticsSummary {
  totalVendorsTracked: number;
  totalRecordedDeliveries: number;
  totalOnTimeDeliveries: number;
  totalLateDeliveries: number;
  portfolioReliabilityRatePercent: number | null;
  averageDeliveryVarianceDays: number | null;
  onTimeDeliveriesBucketCount: number;
  minorDelayBucketCount: number;
  moderateDelayBucketCount: number;
  severeDelayBucketCount: number;
  vendorPerformances: VendorDeliveryPerformance[];
  formulaDefinition: string;
}

interface PortfolioDeadlineRiskSummary {
  totalProjects: number;
  meetingDeadlinesCount: number;
  forecastMissingDeadlinesCount: number;
  unresolvedConflictsProjectCount: number;
  criticalPathDelayedProjectCount: number;
  vendorDelayedProjectCount: number;
  attentionRequiredProjectCount: number;
  attentionRequiredProjects: Array<{
    projectId: number;
    projectName: string;
    clientName: string;
    deadline: string | null;
    varianceDays: number | null;
    reasons: string[];
  }>;
}

interface AnalyticsTrends {
  monthlyProjectCompletions: Array<{
    monthKey: string;
    totalCompleted: number;
    deliveredOnTime: number;
    deliveredLate: number;
    averageVarianceDays: number;
  }>;
  monthlyTaskCompletions: Array<{
    monthKey: string;
    completedTasksCount: number;
  }>;
  delayReasonsBreakdown: Record<string, number>;
}

export function PortfolioAnalytics() {
  const { user } = useAuth();
  const [activeTab, setActiveTab] = useState<'overview' | 'projects' | 'resources' | 'vendors' | 'risks'>('overview');
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Filters state
  const [startDate, setStartDate] = useState('');
  const [endDate, setEndDate] = useState('');
  const [statusFilter, setStatusFilter] = useState('');
  const [priorityFilter, setPriorityFilter] = useState('');
  const [searchQuery, setSearchQuery] = useState('');

  // Data state
  const [kpiSummary, setKpiSummary] = useState<PortfolioKpiSummary | null>(null);
  const [projects, setProjects] = useState<ProjectPerformance[]>([]);
  const [resources, setResources] = useState<ResourceUtilizationSummary | null>(null);
  const [vendors, setVendors] = useState<VendorAnalyticsSummary | null>(null);
  const [risks, setRisks] = useState<PortfolioDeadlineRiskSummary | null>(null);
  const [trends, setTrends] = useState<AnalyticsTrends | null>(null);

  const fetchAnalytics = async () => {
    try {
      setError(null);
      const params: Record<string, string> = {};
      if (startDate) params.startDate = startDate;
      if (endDate) params.endDate = endDate;
      if (statusFilter) params.status = statusFilter;
      if (priorityFilter) params.priority = priorityFilter;

      const [kpiRes, projRes, resRes, venRes, riskRes, trendRes] = await Promise.all([
        api.get<PortfolioKpiSummary>('/analytics/portfolio', { params }),
        api.get<ProjectPerformance[]>('/analytics/projects/performance', { params }),
        api.get<ResourceUtilizationSummary>('/analytics/resources/utilization', { params }),
        api.get<VendorAnalyticsSummary>('/analytics/vendors/performance', { params }),
        api.get<PortfolioDeadlineRiskSummary>('/analytics/projects/deadlines', { params }),
        api.get<AnalyticsTrends>('/analytics/trends', { params })
      ]);

      setKpiSummary(kpiRes.data);
      setProjects(projRes.data);
      setResources(resRes.data);
      setVendors(venRes.data);
      setRisks(riskRes.data);
      setTrends(trendRes.data);
    } catch (err: any) {
      console.error('Failed to load portfolio analytics:', err);
      setError(err?.response?.data?.message || 'Failed to load portfolio analytics data.');
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  };

  const { currentOrg } = useOrganization();

  useEffect(() => {
    fetchAnalytics();
  }, [startDate, endDate, statusFilter, priorityFilter, currentOrg?.id]);

  const handleRefresh = () => {
    setRefreshing(true);
    fetchAnalytics();
  };

  const handleResetFilters = () => {
    setStartDate('');
    setEndDate('');
    setStatusFilter('');
    setPriorityFilter('');
    setSearchQuery('');
  };

  const downloadCsv = async (reportType: string) => {
    try {
      const params = new URLSearchParams();
      params.append('reportType', reportType);
      if (startDate) params.append('startDate', startDate);
      if (endDate) params.append('endDate', endDate);
      if (statusFilter) params.append('status', statusFilter);
      if (priorityFilter) params.append('priority', priorityFilter);

      const response = await api.get(`/analytics/export/csv?${params.toString()}`, {
        responseType: 'blob'
      });

      const blob = new Blob([response.data], { type: 'text/csv;charset=utf-8;' });
      const url = window.URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.setAttribute('download', `${reportType}_analytics_${new Date().toISOString().split('T')[0]}.csv`);
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      window.URL.revokeObjectURL(url);
    } catch (err) {
      console.error('Failed to download CSV report:', err);
      alert('Failed to generate CSV export.');
    }
  };

  // Filter projects by search query
  const filteredProjects = useMemo(() => {
    if (!searchQuery.trim()) return projects;
    const q = searchQuery.toLowerCase();
    return projects.filter(
      (p) =>
        p.projectName.toLowerCase().includes(q) ||
        (p.clientName && p.clientName.toLowerCase().includes(q)) ||
        (p.managerName && p.managerName.toLowerCase().includes(q))
    );
  }, [projects, searchQuery]);

  return (
    <div className="p-8 max-w-7xl mx-auto space-y-6">
      {/* Top Header */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 border-b border-gray-200 pb-5">
        <div>
          <div className="flex items-center gap-2">
            <h1 className="text-2xl font-bold text-gray-900">Portfolio Analytics & KPI Dashboard</h1>
            <span className="px-2.5 py-0.5 rounded-full text-xs font-semibold bg-blue-100 text-blue-800">
              Phase 11 BI
            </span>
          </div>
          <p className="text-sm text-gray-500 mt-1">
            Executive oversight of multi-project performance, schedule variance, resource workload, and vendor reliability.
          </p>
        </div>

        {/* Action Buttons */}
        <div className="flex items-center gap-3">
          <button
            onClick={handleRefresh}
            disabled={refreshing}
            className="flex items-center gap-1.5 px-3 py-2 text-sm font-medium text-gray-700 bg-white border border-gray-300 rounded-lg hover:bg-gray-50 transition-colors shadow-sm disabled:opacity-50"
          >
            <RefreshCw className={`w-4 h-4 ${refreshing ? 'animate-spin text-blue-600' : 'text-gray-500'}`} />
            Refresh
          </button>

          {/* Export CSV Dropdown */}
          <div className="relative group">
            <button className="flex items-center gap-1.5 px-3 py-2 text-sm font-medium text-white bg-blue-600 rounded-lg hover:bg-blue-700 transition-colors shadow-sm">
              <Download className="w-4 h-4" />
              Export CSV
            </button>
            <div className="absolute right-0 mt-1 w-48 bg-white border border-gray-200 rounded-lg shadow-lg py-1 z-20 hidden group-hover:block">
              <button
                onClick={() => downloadCsv('portfolio')}
                className="w-full text-left px-4 py-2 text-xs font-medium text-gray-700 hover:bg-blue-50 hover:text-blue-600"
              >
                Portfolio Summary
              </button>
              <button
                onClick={() => downloadCsv('projects')}
                className="w-full text-left px-4 py-2 text-xs font-medium text-gray-700 hover:bg-blue-50 hover:text-blue-600"
              >
                Project Performance
              </button>
              {user?.role !== 'CLIENT' && (
                <>
                  <button
                    onClick={() => downloadCsv('resources')}
                    className="w-full text-left px-4 py-2 text-xs font-medium text-gray-700 hover:bg-blue-50 hover:text-blue-600"
                  >
                    Resource Utilization
                  </button>
                  <button
                    onClick={() => downloadCsv('vendors')}
                    className="w-full text-left px-4 py-2 text-xs font-medium text-gray-700 hover:bg-blue-50 hover:text-blue-600"
                  >
                    Vendor Performance
                  </button>
                </>
              )}
            </div>
          </div>
        </div>
      </div>

      {/* Filter Bar */}
      <div className="bg-white p-4 rounded-xl border border-gray-200 shadow-sm space-y-3">
        <div className="flex items-center justify-between text-xs font-semibold text-gray-500 uppercase tracking-wider">
          <div className="flex items-center gap-1.5">
            <Filter className="w-3.5 h-3.5 text-blue-600" />
            <span>Reporting Period & Dimension Filters</span>
          </div>
          {(startDate || endDate || statusFilter || priorityFilter || searchQuery) && (
            <button
              onClick={handleResetFilters}
              className="text-blue-600 hover:text-blue-800 lowercase text-xs font-normal"
            >
              Reset filters
            </button>
          )}
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-5 gap-3">
          <div>
            <label className="block text-xs font-medium text-gray-600 mb-1">From Date</label>
            <input
              type="date"
              value={startDate}
              onChange={(e) => setStartDate(e.target.value)}
              className="w-full text-xs px-2.5 py-1.5 border border-gray-300 rounded-md focus:ring-1 focus:ring-blue-500 focus:outline-none"
            />
          </div>
          <div>
            <label className="block text-xs font-medium text-gray-600 mb-1">To Date</label>
            <input
              type="date"
              value={endDate}
              onChange={(e) => setEndDate(e.target.value)}
              className="w-full text-xs px-2.5 py-1.5 border border-gray-300 rounded-md focus:ring-1 focus:ring-blue-500 focus:outline-none"
            />
          </div>
          <div>
            <label className="block text-xs font-medium text-gray-600 mb-1">Project Status</label>
            <select
              value={statusFilter}
              onChange={(e) => setStatusFilter(e.target.value)}
              className="w-full text-xs px-2.5 py-1.5 border border-gray-300 rounded-md focus:ring-1 focus:ring-blue-500 focus:outline-none bg-white"
            >
              <option value="">All Statuses</option>
              <option value="PLANNED">Planned</option>
              <option value="IN_PROGRESS">In Progress</option>
              <option value="ON_HOLD">On Hold</option>
              <option value="COMPLETED">Completed</option>
              <option value="CANCELLED">Cancelled</option>
            </select>
          </div>
          <div>
            <label className="block text-xs font-medium text-gray-600 mb-1">Priority</label>
            <select
              value={priorityFilter}
              onChange={(e) => setPriorityFilter(e.target.value)}
              className="w-full text-xs px-2.5 py-1.5 border border-gray-300 rounded-md focus:ring-1 focus:ring-blue-500 focus:outline-none bg-white"
            >
              <option value="">All Priorities</option>
              <option value="LOW">Low</option>
              <option value="MEDIUM">Medium</option>
              <option value="HIGH">High</option>
              <option value="CRITICAL">Critical</option>
            </select>
          </div>
          <div>
            <label className="block text-xs font-medium text-gray-600 mb-1">Search Projects</label>
            <input
              type="text"
              placeholder="Filter by name, client..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="w-full text-xs px-2.5 py-1.5 border border-gray-300 rounded-md focus:ring-1 focus:ring-blue-500 focus:outline-none"
            />
          </div>
        </div>
      </div>

      {/* Tabs Navigation */}
      <div className="flex border-b border-gray-200 gap-6 text-sm font-medium">
        <button
          onClick={() => setActiveTab('overview')}
          className={`pb-3 flex items-center gap-2 border-b-2 transition-colors ${
            activeTab === 'overview'
              ? 'border-blue-600 text-blue-600 font-semibold'
              : 'border-transparent text-gray-500 hover:text-gray-700'
          }`}
        >
          <BarChart3 className="w-4 h-4" />
          Portfolio Overview & KPIs
        </button>
        <button
          onClick={() => setActiveTab('projects')}
          className={`pb-3 flex items-center gap-2 border-b-2 transition-colors ${
            activeTab === 'projects'
              ? 'border-blue-600 text-blue-600 font-semibold'
              : 'border-transparent text-gray-500 hover:text-gray-700'
          }`}
        >
          <Layers className="w-4 h-4" />
          Project Performance ({projects.length})
        </button>
        {user?.role !== 'CLIENT' && (
          <>
            <button
              onClick={() => setActiveTab('resources')}
              className={`pb-3 flex items-center gap-2 border-b-2 transition-colors ${
                activeTab === 'resources'
                  ? 'border-blue-600 text-blue-600 font-semibold'
                  : 'border-transparent text-gray-500 hover:text-gray-700'
              }`}
            >
              <Users className="w-4 h-4" />
              Resource Utilization
            </button>
            <button
              onClick={() => setActiveTab('vendors')}
              className={`pb-3 flex items-center gap-2 border-b-2 transition-colors ${
                activeTab === 'vendors'
                  ? 'border-blue-600 text-blue-600 font-semibold'
                  : 'border-transparent text-gray-500 hover:text-gray-700'
              }`}
            >
              <Truck className="w-4 h-4" />
              Vendor Reliability
            </button>
          </>
        )}
        <button
          onClick={() => setActiveTab('risks')}
          className={`pb-3 flex items-center gap-2 border-b-2 transition-colors ${
            activeTab === 'risks'
              ? 'border-blue-600 text-blue-600 font-semibold'
              : 'border-transparent text-gray-500 hover:text-gray-700'
          }`}
        >
          <ShieldAlert className="w-4 h-4" />
          Deadline Risks & Trends
        </button>
      </div>

      {/* Error state */}
      {error && (
        <div className="p-4 bg-red-50 border border-red-200 rounded-xl text-red-700 text-sm flex items-center gap-2">
          <AlertTriangle className="w-5 h-5 flex-shrink-0" />
          <span>{error}</span>
        </div>
      )}

      {/* Loading state */}
      {loading ? (
        <div className="flex flex-col items-center justify-center py-20 text-gray-500 space-y-3">
          <RefreshCw className="w-8 h-8 animate-spin text-blue-600" />
          <p className="text-sm">Aggregating portfolio telemetry and schedule performance...</p>
        </div>
      ) : (
        <>
          {/* TAB 1: OVERVIEW & KPIS */}
          {activeTab === 'overview' && kpiSummary && (
            <div className="space-y-6">
              {/* Primary KPI Cards Grid */}
              <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
                {/* Total & Active Projects */}
                <div className="bg-white p-5 rounded-xl border border-gray-200 shadow-sm relative overflow-hidden">
                  <div className="flex justify-between items-start">
                    <div>
                      <p className="text-xs font-semibold text-gray-500 uppercase tracking-wider">Total Portfolio</p>
                      <h3 className="text-3xl font-bold text-gray-900 mt-1">{kpiSummary.totalProjects}</h3>
                    </div>
                    <div className="p-2.5 bg-blue-50 text-blue-600 rounded-lg">
                      <Layers className="w-5 h-5" />
                    </div>
                  </div>
                  <div className="mt-4 flex items-center gap-2 text-xs text-gray-600">
                    <span className="font-semibold text-blue-600">{kpiSummary.activeProjects} active</span>
                    <span>•</span>
                    <span className="text-emerald-600 font-medium">{kpiSummary.completedProjects} completed</span>
                    <span>•</span>
                    <span className="text-amber-600">{kpiSummary.onHoldProjects} on hold</span>
                  </div>
                </div>

                {/* Forecast Missed Deadlines */}
                <div className="bg-white p-5 rounded-xl border border-gray-200 shadow-sm relative overflow-hidden">
                  <div className="flex justify-between items-start">
                    <div>
                      <p className="text-xs font-semibold text-gray-500 uppercase tracking-wider">Forecast Missed</p>
                      <h3 className="text-3xl font-bold text-red-600 mt-1">
                        {kpiSummary.projectsForecastToMissDeadlines}
                      </h3>
                    </div>
                    <div className="p-2.5 bg-red-50 text-red-600 rounded-lg">
                      <Clock className="w-5 h-5" />
                    </div>
                  </div>
                  <div className="mt-4 flex items-center gap-2 text-xs text-gray-600">
                    <span className="text-emerald-600 font-semibold">{kpiSummary.projectsMeetingDeadlines} on track</span>
                    <span>•</span>
                    <span className="text-red-500 font-medium">{kpiSummary.totalOverdueTasks} overdue tasks</span>
                  </div>
                </div>

                {/* On-Time Delivery Rate */}
                <div className="bg-white p-5 rounded-xl border border-gray-200 shadow-sm relative overflow-hidden">
                  <div className="flex justify-between items-start">
                    <div>
                      <p className="text-xs font-semibold text-gray-500 uppercase tracking-wider">On-Time Delivery Rate</p>
                      <h3 className="text-3xl font-bold text-emerald-600 mt-1">
                        {kpiSummary.historicalOnTimeDeliveryRatePercent !== null
                          ? `${kpiSummary.historicalOnTimeDeliveryRatePercent}%`
                          : 'N/A'}
                      </h3>
                    </div>
                    <div className="p-2.5 bg-emerald-50 text-emerald-600 rounded-lg">
                      <CheckCircle2 className="w-5 h-5" />
                    </div>
                  </div>
                  <p className="mt-4 text-xs text-gray-500">
                    {kpiSummary.completedProjects > 0
                      ? `Based on ${kpiSummary.completedProjects} completed project outcomes`
                      : 'No completed projects recorded yet'}
                  </p>
                </div>

                {/* Schedule Variance */}
                <div className="bg-white p-5 rounded-xl border border-gray-200 shadow-sm relative overflow-hidden">
                  <div className="flex justify-between items-start">
                    <div>
                      <p className="text-xs font-semibold text-gray-500 uppercase tracking-wider">Avg Schedule Variance</p>
                      <h3 className={`text-3xl font-bold mt-1 ${
                        kpiSummary.averageScheduleVarianceDays === null
                          ? 'text-gray-400'
                          : kpiSummary.averageScheduleVarianceDays > 0
                          ? 'text-amber-600'
                          : 'text-emerald-600'
                      }`}>
                        {kpiSummary.averageScheduleVarianceDays !== null
                          ? `${kpiSummary.averageScheduleVarianceDays > 0 ? '+' : ''}${kpiSummary.averageScheduleVarianceDays}d`
                          : 'N/A'}
                      </h3>
                    </div>
                    <div className="p-2.5 bg-purple-50 text-purple-600 rounded-lg">
                      <TrendingUp className="w-5 h-5" />
                    </div>
                  </div>
                  <p className="mt-4 text-xs text-gray-500">
                    {kpiSummary.averageScheduleVarianceDays === null
                      ? 'No historical variance available'
                      : kpiSummary.averageScheduleVarianceDays <= 0
                      ? 'Delivered on or ahead of planned finish'
                      : 'Average days delayed across completed projects'}
                  </p>
                </div>
              </div>

              {/* Secondary Operational Metrics */}
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                {/* Resource Utilization summary card */}
                <div className="bg-white p-5 rounded-xl border border-gray-200 shadow-sm">
                  <div className="flex items-center justify-between pb-3 border-b border-gray-100">
                    <div className="flex items-center gap-2">
                      <Users className="w-4 h-4 text-blue-600" />
                      <h4 className="text-sm font-semibold text-gray-900">Portfolio Resource Utilization</h4>
                    </div>
                    <span className="text-sm font-bold text-blue-600">
                      {kpiSummary.resourceUtilizationPercent !== null
                        ? `${kpiSummary.resourceUtilizationPercent}%`
                        : 'N/A'}
                    </span>
                  </div>
                  <div className="mt-4 space-y-2">
                    <div className="w-full bg-gray-100 rounded-full h-2.5 overflow-hidden">
                      <div
                        className={`h-2.5 rounded-full transition-all ${
                          (kpiSummary.resourceUtilizationPercent || 0) > 100
                            ? 'bg-red-500'
                            : (kpiSummary.resourceUtilizationPercent || 0) >= 70
                            ? 'bg-emerald-500'
                            : 'bg-amber-500'
                        }`}
                        style={{ width: `${Math.min(kpiSummary.resourceUtilizationPercent || 0, 100)}%` }}
                      />
                    </div>
                    <div className="flex justify-between text-xs text-gray-500">
                      <span>Formula: Assigned Working Hours / Available Capacity × 100</span>
                      <span>Target: 75% – 90%</span>
                    </div>
                  </div>
                </div>

                {/* Vendor Delivery Reliability summary card */}
                <div className="bg-white p-5 rounded-xl border border-gray-200 shadow-sm">
                  <div className="flex items-center justify-between pb-3 border-b border-gray-100">
                    <div className="flex items-center gap-2">
                      <Truck className="w-4 h-4 text-emerald-600" />
                      <h4 className="text-sm font-semibold text-gray-900">Vendor Delivery Reliability</h4>
                    </div>
                    <span className="text-sm font-bold text-emerald-600">
                      {kpiSummary.vendorDeliveryReliabilityPercent !== null
                        ? `${kpiSummary.vendorDeliveryReliabilityPercent}%`
                        : 'N/A'}
                    </span>
                  </div>
                  <div className="mt-4 space-y-2">
                    <div className="w-full bg-gray-100 rounded-full h-2.5 overflow-hidden">
                      <div
                        className="h-2.5 rounded-full bg-emerald-500 transition-all"
                        style={{ width: `${Math.min(kpiSummary.vendorDeliveryReliabilityPercent || 0, 100)}%` }}
                      />
                    </div>
                    <div className="flex justify-between text-xs text-gray-500">
                      <span>Formula: On-Time Deliveries / Completed Deliveries × 100</span>
                      <span>Sample Size Disclosed</span>
                    </div>
                  </div>
                </div>
              </div>

              {/* Visual Portfolio Status Distribution Bar */}
              <div className="bg-white p-5 rounded-xl border border-gray-200 shadow-sm space-y-3">
                <h4 className="text-xs font-semibold text-gray-500 uppercase tracking-wider">
                  Portfolio Lifecycle Distribution
                </h4>
                {kpiSummary.totalProjects > 0 ? (
                  <>
                    <div className="w-full h-4 bg-gray-100 rounded-full overflow-hidden flex">
                      <div
                        className="bg-blue-500 h-full transition-all"
                        style={{ width: `${(kpiSummary.activeProjects / kpiSummary.totalProjects) * 100}%` }}
                        title={`Active: ${kpiSummary.activeProjects}`}
                      />
                      <div
                        className="bg-emerald-500 h-full transition-all"
                        style={{ width: `${(kpiSummary.completedProjects / kpiSummary.totalProjects) * 100}%` }}
                        title={`Completed: ${kpiSummary.completedProjects}`}
                      />
                      <div
                        className="bg-amber-500 h-full transition-all"
                        style={{ width: `${(kpiSummary.onHoldProjects / kpiSummary.totalProjects) * 100}%` }}
                        title={`On Hold: ${kpiSummary.onHoldProjects}`}
                      />
                      <div
                        className="bg-gray-400 h-full transition-all"
                        style={{ width: `${(kpiSummary.cancelledProjects / kpiSummary.totalProjects) * 100}%` }}
                        title={`Cancelled: ${kpiSummary.cancelledProjects}`}
                      />
                    </div>
                    <div className="flex flex-wrap items-center gap-6 text-xs text-gray-600 pt-1">
                      <span className="flex items-center gap-1.5">
                        <span className="w-3 h-3 rounded-full bg-blue-500 inline-block" />
                        Active: {kpiSummary.activeProjects} ({Math.round((kpiSummary.activeProjects / kpiSummary.totalProjects) * 100)}%)
                      </span>
                      <span className="flex items-center gap-1.5">
                        <span className="w-3 h-3 rounded-full bg-emerald-500 inline-block" />
                        Completed: {kpiSummary.completedProjects} ({Math.round((kpiSummary.completedProjects / kpiSummary.totalProjects) * 100)}%)
                      </span>
                      <span className="flex items-center gap-1.5">
                        <span className="w-3 h-3 rounded-full bg-amber-500 inline-block" />
                        On Hold: {kpiSummary.onHoldProjects} ({Math.round((kpiSummary.onHoldProjects / kpiSummary.totalProjects) * 100)}%)
                      </span>
                      {kpiSummary.cancelledProjects > 0 && (
                        <span className="flex items-center gap-1.5">
                          <span className="w-3 h-3 rounded-full bg-gray-400 inline-block" />
                          Cancelled: {kpiSummary.cancelledProjects}
                        </span>
                      )}
                    </div>
                  </>
                ) : (
                  <p className="text-xs text-gray-500 italic">No projects found for current criteria.</p>
                )}
              </div>

              {/* KPI Definitions Guide Card */}
              <div className="bg-slate-50 p-5 rounded-xl border border-slate-200">
                <div className="flex items-center gap-2 text-slate-800 text-sm font-semibold mb-2">
                  <Info className="w-4 h-4 text-blue-600" />
                  <span>Metric Definitions & Interpretation Standards (Phase 11 Compliance)</span>
                </div>
                <div className="grid grid-cols-1 md:grid-cols-2 gap-3 text-xs text-slate-600">
                  <div className="p-2.5 bg-white rounded border border-slate-200">
                    <span className="font-semibold text-slate-800">Historical On-Time Delivery Rate:</span>
                    <p className="mt-0.5">Calculated as (Completed Projects Delivered on or before Baseline Finish / Total Completed Projects) × 100. Returns N/A if zero projects are completed to prevent misleading 0% ratings.</p>
                  </div>
                  <div className="p-2.5 bg-white rounded border border-slate-200">
                    <span className="font-semibold text-slate-800">Schedule Variance (Days):</span>
                    <p className="mt-0.5">Variance = Actual/Expected Finish − Planned Baseline Finish. Positive numbers indicate delay days; zero or negative numbers indicate on-time or ahead of schedule.</p>
                  </div>
                  <div className="p-2.5 bg-white rounded border border-slate-200">
                    <span className="font-semibold text-slate-800">Resource Utilization Rate:</span>
                    <p className="mt-0.5">Total Assigned Task Hours / (Working Days in window × 8h Daily Capacity) × 100. Unavailable resources are distinguished from available but unassigned personnel.</p>
                  </div>
                  <div className="p-2.5 bg-white rounded border border-slate-200">
                    <span className="font-semibold text-slate-800">Vendor Delivery Reliability:</span>
                    <p className="mt-0.5">On-Time Deliveries / Completed Deliveries with Valid Recorded Dates × 100. Records without delivery dates are excluded from the denominator with sample size disclosed.</p>
                  </div>
                </div>
              </div>
            </div>
          )}

          {/* TAB 2: PROJECT PERFORMANCE */}
          {activeTab === 'projects' && (
            <div className="space-y-4">
              <div className="bg-white rounded-xl border border-gray-200 shadow-sm overflow-hidden">
                <div className="px-6 py-4 border-b border-gray-200 flex items-center justify-between">
                  <h3 className="text-sm font-semibold text-gray-900">
                    Detailed Project Schedule Performance ({filteredProjects.length})
                  </h3>
                  <span className="text-xs text-gray-500">
                    Planned vs Actual Dates, Variance & Risk Flags
                  </span>
                </div>

                {filteredProjects.length === 0 ? (
                  <div className="p-12 text-center text-gray-500 space-y-2">
                    <Layers className="w-8 h-8 text-gray-300 mx-auto" />
                    <p className="text-sm font-medium">No projects match the current filter criteria.</p>
                    <button
                      onClick={handleResetFilters}
                      className="text-xs text-blue-600 hover:underline"
                    >
                      Clear filters to view all projects
                    </button>
                  </div>
                ) : (
                  <div className="overflow-x-auto">
                    <table className="w-full text-left border-collapse text-xs">
                      <thead>
                        <tr className="bg-gray-50 text-gray-600 border-b border-gray-200 uppercase font-medium tracking-wider">
                          <th className="py-3 px-4">Project</th>
                          <th className="py-3 px-3">Client / Manager</th>
                          <th className="py-3 px-3">Status</th>
                          <th className="py-3 px-3">Planned Finish</th>
                          <th className="py-3 px-3">Expected/Actual</th>
                          <th className="py-3 px-3">Variance</th>
                          <th className="py-3 px-3">Progress</th>
                          <th className="py-3 px-3">Risk Flags</th>
                          <th className="py-3 px-3 text-right">Actions</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-gray-100 text-gray-700">
                        {filteredProjects.map((p) => {
                          const isDelayed = (p.scheduleVarianceDays || 0) > 0;
                          return (
                            <tr key={p.projectId} className="hover:bg-gray-50 transition-colors">
                              <td className="py-3 px-4">
                                <div className="font-semibold text-gray-900">{p.projectName}</div>
                                <div className="text-[10px] text-gray-400">{p.projectCode || `PRJ-${p.projectId}`}</div>
                              </td>
                              <td className="py-3 px-3">
                                <div>{p.clientName || 'Internal'}</div>
                                <div className="text-[10px] text-gray-400">PM: {p.managerName || 'Unassigned'}</div>
                              </td>
                              <td className="py-3 px-3">
                                <span className={`px-2 py-0.5 rounded-full text-[10px] font-semibold ${
                                  p.status === 'COMPLETED'
                                    ? 'bg-emerald-100 text-emerald-800'
                                    : p.status === 'IN_PROGRESS'
                                    ? 'bg-blue-100 text-blue-800'
                                    : p.status === 'ON_HOLD'
                                    ? 'bg-amber-100 text-amber-800'
                                    : 'bg-gray-100 text-gray-700'
                                }`}>
                                  {p.status}
                                </span>
                              </td>
                              <td className="py-3 px-3">
                                {p.plannedFinishDate || '—'}
                              </td>
                              <td className="py-3 px-3 font-medium">
                                {p.actualCompletionDate ? (
                                  <span className="text-emerald-700">{p.actualCompletionDate} (Done)</span>
                                ) : p.expectedCompletionDate ? (
                                  <span>{p.expectedCompletionDate}</span>
                                ) : (
                                  '—'
                                )}
                              </td>
                              <td className="py-3 px-3 font-semibold">
                                {p.scheduleVarianceDays !== null ? (
                                  <span className={`px-2 py-0.5 rounded text-[11px] ${
                                    isDelayed
                                      ? 'bg-red-50 text-red-700'
                                      : p.scheduleVarianceDays < 0
                                      ? 'bg-emerald-50 text-emerald-700'
                                      : 'bg-gray-100 text-gray-700'
                                  }`}>
                                    {p.scheduleVarianceDays > 0 ? `+${p.scheduleVarianceDays}d` : `${p.scheduleVarianceDays}d`}
                                  </span>
                                ) : (
                                  <span className="text-gray-400">0d</span>
                                )}
                              </td>
                              <td className="py-3 px-3 w-32">
                                <div className="flex items-center gap-2">
                                  <div className="w-16 bg-gray-200 rounded-full h-1.5 overflow-hidden">
                                    <div
                                      className="bg-blue-600 h-1.5 rounded-full"
                                      style={{ width: `${p.progressPercent}%` }}
                                    />
                                  </div>
                                  <span className="text-[11px] font-medium">{p.progressPercent}%</span>
                                </div>
                                <span className="text-[10px] text-gray-400">
                                  {p.completedTasks}/{p.totalTasks} tasks
                                </span>
                              </td>
                              <td className="py-3 px-3">
                                <div className="flex items-center gap-1.5">
                                  {p.hasCriticalPathDelay && (
                                    <span className="px-1.5 py-0.5 bg-red-100 text-red-700 rounded text-[9px] font-bold" title="Critical Path Delayed">
                                      CPM
                                    </span>
                                  )}
                                  {p.hasActiveConflict && (
                                    <span className="px-1.5 py-0.5 bg-amber-100 text-amber-800 rounded text-[9px] font-bold" title="Active Resource Conflict">
                                      Conflict
                                    </span>
                                  )}
                                  {p.hasVendorDelay && (
                                    <span className="px-1.5 py-0.5 bg-purple-100 text-purple-700 rounded text-[9px] font-bold" title="Vendor Delay Reported">
                                      Vendor
                                    </span>
                                  )}
                                  {!p.hasCriticalPathDelay && !p.hasActiveConflict && !p.hasVendorDelay && (
                                    <span className="text-emerald-600 text-[10px]">Healthy</span>
                                  )}
                                </div>
                              </td>
                              <td className="py-3 px-3 text-right">
                                <Link
                                  to={`/schedule?tab=gantt`}
                                  className="inline-flex items-center gap-1 text-blue-600 hover:text-blue-800 font-medium text-[11px]"
                                >
                                  Schedule
                                  <ExternalLink className="w-3 h-3" />
                                </Link>
                              </td>
                            </tr>
                          );
                        })}
                      </tbody>
                    </table>
                  </div>
                )}
              </div>
            </div>
          )}

          {/* TAB 3: RESOURCE UTILIZATION */}
          {activeTab === 'resources' && resources && (
            <div className="space-y-6">
              {/* Summary KPIs */}
              <div className="grid grid-cols-1 sm:grid-cols-4 gap-4">
                <div className="bg-white p-4 rounded-xl border border-gray-200 shadow-sm">
                  <p className="text-xs text-gray-500 uppercase font-semibold">Tracked Workers</p>
                  <p className="text-2xl font-bold text-gray-900 mt-1">{resources.totalTrackedWorkers}</p>
                  <p className="text-xs text-gray-400 mt-1">{resources.totalAssignedHours} assigned hours</p>
                </div>
                <div className="bg-white p-4 rounded-xl border border-gray-200 shadow-sm">
                  <p className="text-xs text-gray-500 uppercase font-semibold">Overall Utilization</p>
                  <p className="text-2xl font-bold text-blue-600 mt-1">
                    {resources.portfolioUtilizationPercent !== null ? `${resources.portfolioUtilizationPercent}%` : 'N/A'}
                  </p>
                  <p className="text-xs text-gray-400 mt-1">Target: 70% – 100%</p>
                </div>
                <div className="bg-white p-4 rounded-xl border border-gray-200 shadow-sm">
                  <p className="text-xs text-gray-500 uppercase font-semibold">Overallocated</p>
                  <p className="text-2xl font-bold text-red-600 mt-1">{resources.overallocatedWorkersCount}</p>
                  <p className="text-xs text-gray-400 mt-1">&gt; 100% capacity assigned</p>
                </div>
                <div className="bg-white p-4 rounded-xl border border-gray-200 shadow-sm">
                  <p className="text-xs text-gray-500 uppercase font-semibold">Optimal Capacity</p>
                  <p className="text-2xl font-bold text-emerald-600 mt-1">{resources.optimalWorkersCount}</p>
                  <p className="text-xs text-gray-400 mt-1">70% – 100% assigned</p>
                </div>
              </div>

              {/* Workers Detail Table */}
              <div className="bg-white rounded-xl border border-gray-200 shadow-sm overflow-hidden">
                <div className="px-6 py-4 border-b border-gray-200 flex items-center justify-between">
                  <h3 className="text-sm font-semibold text-gray-900">
                    Worker Workload & Allocation Ratios ({resources.workerUtilizations.length})
                  </h3>
                  <span className="text-xs text-gray-500">
                    Capacity Basis: 8 Working Hours / Day
                  </span>
                </div>

                <div className="overflow-x-auto">
                  <table className="w-full text-left border-collapse text-xs">
                    <thead>
                      <tr className="bg-gray-50 text-gray-600 border-b border-gray-200 uppercase font-medium tracking-wider">
                        <th className="py-3 px-4">Worker</th>
                        <th className="py-3 px-3">Role</th>
                        <th className="py-3 px-3">Assigned Tasks</th>
                        <th className="py-3 px-3">Hours (Assigned / Available)</th>
                        <th className="py-3 px-3">Utilization %</th>
                        <th className="py-3 px-3">Workload Status</th>
                        <th className="py-3 px-3">Conflicts</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-gray-100 text-gray-700">
                      {resources.workerUtilizations.map((w) => (
                        <tr key={w.workerId} className="hover:bg-gray-50 transition-colors">
                          <td className="py-3 px-4 font-semibold text-gray-900">
                            {w.workerName}
                            <div className="text-[10px] text-gray-400 font-normal">{w.email}</div>
                          </td>
                          <td className="py-3 px-3">{w.role || 'Staff'}</td>
                          <td className="py-3 px-3 font-medium">{w.assignedTasksCount} tasks</td>
                          <td className="py-3 px-3">
                            {w.assignedWorkingHours}h / {w.availableWorkingHours}h
                          </td>
                          <td className="py-3 px-3 w-36">
                            <div className="flex items-center gap-2">
                              <div className="w-16 bg-gray-200 rounded-full h-1.5 overflow-hidden">
                                <div
                                  className={`h-1.5 rounded-full ${
                                    (w.utilizationPercent || 0) > 100
                                      ? 'bg-red-500'
                                      : (w.utilizationPercent || 0) >= 70
                                      ? 'bg-emerald-500'
                                      : 'bg-amber-500'
                                  }`}
                                  style={{ width: `${Math.min(w.utilizationPercent || 0, 100)}%` }}
                                />
                              </div>
                              <span className="text-[11px] font-semibold">
                                {w.utilizationPercent !== null ? `${w.utilizationPercent}%` : '0%'}
                              </span>
                            </div>
                          </td>
                          <td className="py-3 px-3">
                            <span className={`px-2 py-0.5 rounded-full text-[10px] font-semibold ${
                              w.utilizationCategory === 'OVERALLOCATED'
                                ? 'bg-red-100 text-red-800'
                                : w.utilizationCategory === 'OPTIMAL'
                                ? 'bg-emerald-100 text-emerald-800'
                                : w.utilizationCategory === 'UNDERUTILIZED'
                                ? 'bg-amber-100 text-amber-800'
                                : w.utilizationCategory === 'UNAVAILABLE'
                                ? 'bg-gray-200 text-gray-700'
                                : 'bg-blue-50 text-blue-700'
                            }`}>
                              {w.utilizationCategory}
                            </span>
                          </td>
                          <td className="py-3 px-3">
                            {w.activeConflictsCount > 0 ? (
                              <span className="px-2 py-0.5 bg-red-100 text-red-700 rounded text-[10px] font-bold">
                                {w.activeConflictsCount} Conflict{w.activeConflictsCount > 1 ? 's' : ''}
                              </span>
                            ) : (
                              <span className="text-gray-400 text-[11px]">None</span>
                            )}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>
            </div>
          )}

          {/* TAB 4: VENDOR RELIABILITY */}
          {activeTab === 'vendors' && vendors && (
            <div className="space-y-6">
              {/* Summary Metrics */}
              <div className="grid grid-cols-1 sm:grid-cols-4 gap-4">
                <div className="bg-white p-4 rounded-xl border border-gray-200 shadow-sm">
                  <p className="text-xs text-gray-500 uppercase font-semibold">Tracked Vendors</p>
                  <p className="text-2xl font-bold text-gray-900 mt-1">{vendors.totalVendorsTracked}</p>
                  <p className="text-xs text-gray-400 mt-1">{vendors.totalRecordedDeliveries} recorded deliveries</p>
                </div>
                <div className="bg-white p-4 rounded-xl border border-gray-200 shadow-sm">
                  <p className="text-xs text-gray-500 uppercase font-semibold">Reliability Rate</p>
                  <p className="text-2xl font-bold text-emerald-600 mt-1">
                    {vendors.portfolioReliabilityRatePercent !== null ? `${vendors.portfolioReliabilityRatePercent}%` : 'N/A'}
                  </p>
                  <p className="text-xs text-gray-400 mt-1">{vendors.totalOnTimeDeliveries} on-time completed</p>
                </div>
                <div className="bg-white p-4 rounded-xl border border-gray-200 shadow-sm">
                  <p className="text-xs text-gray-500 uppercase font-semibold">Avg Delay Days</p>
                  <p className="text-2xl font-bold text-amber-600 mt-1">
                    {vendors.averageDeliveryVarianceDays !== null ? `${vendors.averageDeliveryVarianceDays}d` : '0d'}
                  </p>
                  <p className="text-xs text-gray-400 mt-1">Late deliveries average</p>
                </div>
                <div className="bg-white p-4 rounded-xl border border-gray-200 shadow-sm">
                  <p className="text-xs text-gray-500 uppercase font-semibold">Late Deliveries</p>
                  <p className="text-2xl font-bold text-red-600 mt-1">{vendors.totalLateDeliveries}</p>
                  <p className="text-xs text-gray-400 mt-1">Delivered past commitment</p>
                </div>
              </div>

              {/* Delivery Delay Distribution Buckets */}
              <div className="bg-white p-5 rounded-xl border border-gray-200 shadow-sm space-y-3">
                <h4 className="text-xs font-semibold text-gray-500 uppercase tracking-wider">
                  Delivery Delay Distribution (Sample Size Disclosed)
                </h4>
                <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 text-center">
                  <div className="p-3 bg-emerald-50 rounded-lg border border-emerald-100">
                    <span className="text-xs text-emerald-700 font-medium">On-Time (≤ 0d)</span>
                    <p className="text-xl font-bold text-emerald-800 mt-1">{vendors.onTimeDeliveriesBucketCount}</p>
                  </div>
                  <div className="p-3 bg-blue-50 rounded-lg border border-blue-100">
                    <span className="text-xs text-blue-700 font-medium">Minor Delay (1-3d)</span>
                    <p className="text-xl font-bold text-blue-800 mt-1">{vendors.minorDelayBucketCount}</p>
                  </div>
                  <div className="p-3 bg-amber-50 rounded-lg border border-amber-100">
                    <span className="text-xs text-amber-700 font-medium">Moderate (4-7d)</span>
                    <p className="text-xl font-bold text-amber-800 mt-1">{vendors.moderateDelayBucketCount}</p>
                  </div>
                  <div className="p-3 bg-red-50 rounded-lg border border-red-100">
                    <span className="text-xs text-red-700 font-medium">Severe (&gt; 7d)</span>
                    <p className="text-xl font-bold text-red-800 mt-1">{vendors.severeDelayBucketCount}</p>
                  </div>
                </div>
              </div>

              {/* Vendor Performance Table */}
              <div className="bg-white rounded-xl border border-gray-200 shadow-sm overflow-hidden">
                <div className="px-6 py-4 border-b border-gray-200 flex items-center justify-between">
                  <h3 className="text-sm font-semibold text-gray-900">
                    Vendor Delivery Reliability Scorecard ({vendors.vendorPerformances.length})
                  </h3>
                  <span className="text-xs text-gray-500">
                    Note: Sample size disclosure applied.
                  </span>
                </div>

                <div className="overflow-x-auto">
                  <table className="w-full text-left border-collapse text-xs">
                    <thead>
                      <tr className="bg-gray-50 text-gray-600 border-b border-gray-200 uppercase font-medium tracking-wider">
                        <th className="py-3 px-4">Vendor</th>
                        <th className="py-3 px-3">Total Deliveries</th>
                        <th className="py-3 px-3">On-Time</th>
                        <th className="py-3 px-3">Late</th>
                        <th className="py-3 px-3">Reliability Rate</th>
                        <th className="py-3 px-3">Avg Delay</th>
                        <th className="py-3 px-3">Outstanding Commitments</th>
                        <th className="py-3 px-3">Sample Reliability</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-gray-100 text-gray-700">
                      {vendors.vendorPerformances.map((v) => (
                        <tr key={v.vendorId} className="hover:bg-gray-50 transition-colors">
                          <td className="py-3 px-4 font-semibold text-gray-900">
                            {v.vendorName}
                            <div className="text-[10px] text-gray-400 font-normal">{v.contactEmail}</div>
                          </td>
                          <td className="py-3 px-3 font-medium">{v.totalRecordedDeliveries}</td>
                          <td className="py-3 px-3 text-emerald-600 font-semibold">{v.onTimeDeliveries}</td>
                          <td className="py-3 px-3 text-red-600 font-semibold">{v.lateDeliveries}</td>
                          <td className="py-3 px-3 font-bold text-blue-600">
                            {v.reliabilityRatePercent !== null ? `${v.reliabilityRatePercent}%` : 'N/A'}
                          </td>
                          <td className="py-3 px-3">
                            {v.averageDeliveryDelayDays !== null ? `${v.averageDeliveryDelayDays}d` : '0d'}
                          </td>
                          <td className="py-3 px-3 font-medium text-amber-700">
                            {v.outstandingCommitmentsCount} active
                          </td>
                          <td className="py-3 px-3">
                            {v.sampleSizeReliable ? (
                              <span className="px-2 py-0.5 bg-emerald-100 text-emerald-800 rounded-full text-[10px] font-semibold">
                                Reliable (N ≥ 3)
                              </span>
                            ) : (
                              <span className="px-2 py-0.5 bg-gray-100 text-gray-600 rounded-full text-[10px]">
                                Low Sample (N &lt; 3)
                              </span>
                            )}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>
            </div>
          )}

          {/* TAB 5: RISKS & TRENDS */}
          {activeTab === 'risks' && (
            <div className="space-y-6">
              {/* Attention Required Banner */}
              {risks && (
                <div className="bg-white p-5 rounded-xl border border-gray-200 shadow-sm space-y-4">
                  <div className="flex items-center justify-between">
                    <div className="flex items-center gap-2">
                      <ShieldAlert className="w-5 h-5 text-red-600" />
                      <h3 className="text-base font-bold text-gray-900">
                        Management Attention Radar ({risks.attentionRequiredProjectCount} Projects)
                      </h3>
                    </div>
                    <span className="text-xs text-gray-500">
                      Unresolved Conflicts, Critical Path Delays & Deadline Slippages
                    </span>
                  </div>

                  {risks.attentionRequiredProjects.length === 0 ? (
                    <div className="p-6 bg-emerald-50 rounded-lg border border-emerald-200 text-emerald-800 text-xs flex items-center gap-2">
                      <CheckCircle2 className="w-4 h-4 text-emerald-600 flex-shrink-0" />
                      <span>All portfolio projects are currently healthy with zero unresolved conflicts or critical path delays!</span>
                    </div>
                  ) : (
                    <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                      {risks.attentionRequiredProjects.map((item) => (
                        <div key={item.projectId} className="p-3.5 bg-red-50/60 border border-red-200 rounded-lg space-y-2">
                          <div className="flex items-center justify-between">
                            <span className="font-bold text-gray-900 text-xs">{item.projectName}</span>
                            <Link
                              to={`/schedule?tab=gantt`}
                              className="text-[10px] font-semibold text-blue-600 hover:underline flex items-center gap-0.5"
                            >
                              Resolve in Gantt
                              <ExternalLink className="w-2.5 h-2.5" />
                            </Link>
                          </div>
                          <div className="flex flex-wrap gap-1.5">
                            {item.reasons.map((r, i) => (
                              <span key={i} className="px-2 py-0.5 bg-red-100 text-red-800 rounded text-[10px] font-medium">
                                {r}
                              </span>
                            ))}
                          </div>
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              )}

              {/* Historical Trend Charts */}
              {trends && (
                <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
                  {/* Monthly Completions */}
                  <div className="bg-white p-5 rounded-xl border border-gray-200 shadow-sm space-y-4">
                    <h4 className="text-xs font-semibold text-gray-500 uppercase tracking-wider">
                      Monthly Project Delivery Outcomes
                    </h4>
                    {trends.monthlyProjectCompletions.length === 0 ? (
                      <p className="text-xs text-gray-400 italic">No historical monthly data recorded yet.</p>
                    ) : (
                      <div className="space-y-3">
                        {trends.monthlyProjectCompletions.map((m) => (
                          <div key={m.monthKey} className="space-y-1">
                            <div className="flex justify-between text-xs font-medium">
                              <span className="text-gray-700">{m.monthKey}</span>
                              <span className="text-gray-500">
                                {m.deliveredOnTime} On-Time / {m.totalCompleted} Total (Avg var: {m.averageVarianceDays}d)
                              </span>
                            </div>
                            <div className="w-full bg-gray-100 rounded-full h-2.5 flex overflow-hidden">
                              <div
                                className="bg-emerald-500 h-full"
                                style={{ width: `${(m.deliveredOnTime / (m.totalCompleted || 1)) * 100}%` }}
                                title={`On-Time: ${m.deliveredOnTime}`}
                              />
                              <div
                                className="bg-red-400 h-full"
                                style={{ width: `${(m.deliveredLate / (m.totalCompleted || 1)) * 100}%` }}
                                title={`Late: ${m.deliveredLate}`}
                              />
                            </div>
                          </div>
                        ))}
                      </div>
                    )}
                  </div>

                  {/* Delay Reasons Breakdown */}
                  <div className="bg-white p-5 rounded-xl border border-gray-200 shadow-sm space-y-4">
                    <h4 className="text-xs font-semibold text-gray-500 uppercase tracking-wider">
                      Reported Project Delay Causes
                    </h4>
                    {Object.keys(trends.delayReasonsBreakdown).length === 0 ? (
                      <p className="text-xs text-gray-400 italic">No delay reports logged in this timeframe.</p>
                    ) : (
                      <div className="space-y-2">
                        {Object.entries(trends.delayReasonsBreakdown).map(([reason, count]) => (
                          <div key={reason} className="flex items-center justify-between p-2.5 bg-gray-50 rounded-lg text-xs">
                            <span className="font-medium text-gray-800">{reason}</span>
                            <span className="px-2 py-0.5 bg-amber-100 text-amber-800 rounded font-bold">
                              {count} incident{count > 1 ? 's' : ''}
                            </span>
                          </div>
                        ))}
                      </div>
                    )}
                  </div>
                </div>
              )}
            </div>
          )}
        </>
      )}
    </div>
  );
}
