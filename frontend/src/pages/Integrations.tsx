import React, { useState, useEffect } from 'react';
import { 
  Share2, Calendar, Webhook, Key, ShieldCheck, RefreshCw, Plus, 
  ExternalLink, CheckCircle2, AlertTriangle, XCircle, Copy, Check,
  Trash2, RotateCw, Send, Lock, Eye, Download, Info
} from 'lucide-react';
import { api } from '../services/api';
import { useOrganization } from '../context/OrganizationContext';

interface CalendarStatus {
  provider: 'GOOGLE_CALENDAR' | 'OUTLOOK_CALENDAR';
  providerName: string;
  configured: boolean;
  status: 'NOT_CONFIGURED' | 'CONFIGURED' | 'CONNECTED' | 'SYNCING' | 'SYNCED' | 'FAILED' | 'DISCONNECTED';
  accountEmail: string | null;
  calendarId: string | null;
  lastSyncAt: string | null;
  lastError: string | null;
  mappedEventCount: number;
  authorizationUrl: string | null;
}

interface WebhookSubscription {
  id: number;
  organizationId: number;
  name: string;
  destinationUrl: string;
  secretMasked: string;
  isEnabled: boolean;
  subscribedEvents: string[];
  createdAt: string;
  lastDeliveredAt: string | null;
  failureCount: number;
  status: string;
}

interface WebhookDelivery {
  id: number;
  subscriptionId: number;
  eventId: string;
  eventType: string;
  payload: string;
  responseStatusCode: number | null;
  responseBody: string | null;
  durationMs: number | null;
  status: 'SUCCESS' | 'FAILED' | 'PENDING_RETRY';
  attemptCount: number;
  errorMessage: string | null;
  createdAt: string;
}

interface ApiKeyItem {
  id: number;
  name: string;
  keyPrefix: string;
  scopes: string[];
  rawKey?: string | null;
  expiresAt: string | null;
  revokedAt: string | null;
  lastUsedAt: string | null;
  createdAt: string;
  valid: boolean;
}

interface ProjectSummary {
  id: number;
  name: string;
}

const AVAILABLE_EVENTS = [
  { id: 'project.created', label: 'Project Created', desc: 'New project created in organization' },
  { id: 'project.updated', label: 'Project Updated', desc: 'Project deadline or scope modified' },
  { id: 'task.created', label: 'Task Created', desc: 'New work breakdown task added' },
  { id: 'task.completed', label: 'Task Completed', desc: 'Task marked finished' },
  { id: 'task.deadline_changed', label: 'Deadline Changed', desc: 'Task planned finish date adjusted' },
  { id: 'schedule.recalculated', label: 'Schedule Recalculated', desc: 'CPM baseline or dates updated' },
  { id: 'deliverable.approved', label: 'Deliverable Approved', desc: 'Formal review decision approved' },
  { id: 'budget.revised', label: 'Budget Revised', desc: 'Budget revision approved by finance' },
  { id: 'resource.conflict_detected', label: 'Resource Conflict', desc: 'Worker over-allocation detected' },
];

const AVAILABLE_SCOPES = [
  { id: 'projects:read', label: 'projects:read', desc: 'Read-only access to projects and deliverables' },
  { id: 'projects:write', label: 'projects:write', desc: 'Create and update projects and tasks' },
  { id: 'schedules:read', label: 'schedules:read', desc: 'Access schedule engine and critical path calculations' },
  { id: 'deliverables:read', label: 'deliverables:read', desc: 'Read deliverables, approvals, and documents' },
  { id: 'webhooks:manage', label: 'webhooks:manage', desc: 'Configure and test outbound webhooks' },
];

export function Integrations() {
  const { currentOrg } = useOrganization();
  const [activeTab, setActiveTab] = useState<'calendar' | 'webhooks' | 'apikeys' | 'docs'>('calendar');

  // Calendar State
  const [calendars, setCalendars] = useState<CalendarStatus[]>([]);
  const [projects, setProjects] = useState<ProjectSummary[]>([]);
  const [selectedProjectId, setSelectedProjectId] = useState<number | ''>('');
  const [syncingCalendar, setSyncingCalendar] = useState(false);
  const [syncResult, setSyncResult] = useState<any>(null);
  const [showConnectModal, setShowConnectModal] = useState<CalendarStatus | null>(null);
  const [connectEmail, setConnectEmail] = useState('');
  const [connectCalId, setConnectCalId] = useState('primary');

  // Webhooks State
  const [webhooks, setWebhooks] = useState<WebhookSubscription[]>([]);
  const [showWebhookModal, setShowWebhookModal] = useState(false);
  const [editingWebhook, setEditingWebhook] = useState<WebhookSubscription | null>(null);
  const [webhookName, setWebhookName] = useState('');
  const [webhookUrl, setWebhookUrl] = useState('');
  const [webhookSecret, setWebhookSecret] = useState('');
  const [selectedEvents, setSelectedEvents] = useState<string[]>(['project.created', 'task.completed']);
  const [savingWebhook, setSavingWebhook] = useState(false);
  const [deliveriesSub, setDeliveriesSub] = useState<WebhookSubscription | null>(null);
  const [deliveries, setDeliveries] = useState<WebhookDelivery[]>([]);
  const [loadingDeliveries, setLoadingDeliveries] = useState(false);
  const [testingPingId, setTestingPingId] = useState<number | null>(null);

  // API Keys State
  const [apiKeys, setApiKeys] = useState<ApiKeyItem[]>([]);
  const [showKeyModal, setShowKeyModal] = useState(false);
  const [keyName, setKeyName] = useState('');
  const [selectedScopes, setSelectedScopes] = useState<string[]>(['projects:read', 'schedules:read']);
  const [keyExpiryDays, setKeyExpiryDays] = useState<number>(90);
  const [generatedKey, setGeneratedKey] = useState<string | null>(null);
  const [copiedKey, setCopiedKey] = useState(false);
  const [generatingKey, setGeneratingKey] = useState(false);

  // General State
  const [loading, setLoading] = useState(true);
  const [feedback, setFeedback] = useState<{ type: 'success' | 'error'; message: string } | null>(null);

  useEffect(() => {
    loadAllData();
  }, [currentOrg]);

  const loadAllData = async () => {
    try {
      setLoading(true);
      const [calRes, whRes, keyRes, projRes] = await Promise.allSettled([
        api.get('/integrations/calendars'),
        api.get('/integrations/webhooks'),
        api.get('/integrations/api-keys'),
        api.get('/projects'),
      ]);

      if (calRes.status === 'fulfilled') setCalendars(calRes.value.data);
      if (whRes.status === 'fulfilled') setWebhooks(whRes.value.data);
      if (keyRes.status === 'fulfilled') setApiKeys(keyRes.value.data);
      if (projRes.status === 'fulfilled') {
        const pList = projRes.value.data || [];
        setProjects(pList);
        if (pList.length > 0 && !selectedProjectId) {
          setSelectedProjectId(pList[0].id);
        }
      }
    } catch (err: any) {
      console.error('Error loading integrations:', err);
    } finally {
      setLoading(false);
    }
  };

  const showMsg = (type: 'success' | 'error', message: string) => {
    setFeedback({ type, message });
    setTimeout(() => setFeedback(null), 5000);
  };

  // =========================================================================
  // Calendar Handlers
  // =========================================================================

  const handleConnectCalendar = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!showConnectModal) return;

    try {
      await api.post(`/integrations/calendars/${showConnectModal.provider}/connect`, {
        accountEmail: connectEmail.trim() || undefined,
        calendarId: connectCalId.trim() || 'primary',
      });
      showMsg('success', `${showConnectModal.providerName} connected successfully.`);
      setShowConnectModal(null);
      setConnectEmail('');
      loadAllData();
    } catch (err: any) {
      showMsg('error', err.response?.data?.message || 'Failed to connect calendar provider.');
    }
  };

  const handleDisconnectCalendar = async (provider: string) => {
    if (!window.confirm(`Are you sure you want to disconnect ${provider}?`)) return;
    try {
      await api.post(`/integrations/calendars/${provider}/disconnect`);
      showMsg('success', 'Calendar disconnected.');
      loadAllData();
    } catch (err: any) {
      showMsg('error', err.response?.data?.message || 'Failed to disconnect calendar.');
    }
  };

  const handleSyncProjectCalendar = async (provider: string) => {
    if (!selectedProjectId) {
      showMsg('error', 'Please select a project to synchronize.');
      return;
    }
    try {
      setSyncingCalendar(true);
      const res = await api.post(`/projects/${selectedProjectId}/calendar/sync?provider=${provider}`);
      setSyncResult(res.data);
      showMsg('success', res.data.message || 'Calendar synchronized successfully.');
      loadAllData();
    } catch (err: any) {
      showMsg('error', err.response?.data?.message || 'Synchronization failed.');
    } finally {
      setSyncingCalendar(false);
    }
  };

  // =========================================================================
  // Webhook Handlers
  // =========================================================================

  const handleSaveWebhook = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!webhookName.trim() || !webhookUrl.trim()) return;

    try {
      setSavingWebhook(true);
      const payload = {
        name: webhookName.trim(),
        destinationUrl: webhookUrl.trim(),
        secret: webhookSecret.trim() || undefined,
        subscribedEvents: selectedEvents,
        isEnabled: true,
      };

      if (editingWebhook) {
        await api.put(`/integrations/webhooks/${editingWebhook.id}`, payload);
        showMsg('success', 'Webhook subscription updated.');
      } else {
        await api.post('/integrations/webhooks', payload);
        showMsg('success', 'Webhook subscription created.');
      }

      setShowWebhookModal(false);
      setEditingWebhook(null);
      setWebhookName('');
      setWebhookUrl('');
      setWebhookSecret('');
      loadAllData();
    } catch (err: any) {
      showMsg('error', err.response?.data?.message || 'Failed to save webhook subscription.');
    } finally {
      setSavingWebhook(false);
    }
  };

  const handleDeleteWebhook = async (id: number) => {
    if (!window.confirm('Delete this webhook endpoint permanently?')) return;
    try {
      await api.delete(`/integrations/webhooks/${id}`);
      showMsg('success', 'Webhook removed.');
      loadAllData();
    } catch (err: any) {
      showMsg('error', err.response?.data?.message || 'Failed to delete webhook.');
    }
  };

  const handleRotateSecret = async (id: number) => {
    if (!window.confirm('Rotate signing secret? You will need to update your receiving service.')) return;
    try {
      await api.post(`/integrations/webhooks/${id}/rotate-secret`);
      showMsg('success', 'Signing secret rotated successfully.');
      loadAllData();
    } catch (err: any) {
      showMsg('error', err.response?.data?.message || 'Failed to rotate secret.');
    }
  };

  const handleTestPing = async (id: number) => {
    try {
      setTestingPingId(id);
      const res = await api.post(`/integrations/webhooks/${id}/test`);
      const status = res.data.status;
      if (status === 'SUCCESS') {
        showMsg('success', `Ping delivered successfully (HTTP ${res.data.responseStatusCode}) in ${res.data.durationMs}ms`);
      } else {
        showMsg('error', `Ping delivery failed: ${res.data.errorMessage || 'HTTP Error'}`);
      }
      loadAllData();
    } catch (err: any) {
      showMsg('error', err.response?.data?.message || 'Ping dispatch failed.');
    } finally {
      setTestingPingId(null);
    }
  };

  const handleOpenDeliveries = async (sub: WebhookSubscription) => {
    setDeliveriesSub(sub);
    try {
      setLoadingDeliveries(true);
      const res = await api.get(`/integrations/webhooks/${sub.id}/deliveries`);
      setDeliveries(res.data);
    } catch (err: any) {
      showMsg('error', 'Failed to load delivery history.');
    } finally {
      setLoadingDeliveries(false);
    }
  };

  const handleRetryDelivery = async (deliveryId: number) => {
    if (!deliveriesSub) return;
    try {
      const res = await api.post(`/integrations/webhooks/${deliveriesSub.id}/deliveries/${deliveryId}/retry`);
      showMsg('success', `Redelivery attempt recorded (${res.data.status}).`);
      handleOpenDeliveries(deliveriesSub);
    } catch (err: any) {
      showMsg('error', err.response?.data?.message || 'Redelivery attempt failed.');
    }
  };

  // =========================================================================
  // API Key Handlers
  // =========================================================================

  const handleGenerateKey = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!keyName.trim()) return;

    try {
      setGeneratingKey(true);
      const res = await api.post('/integrations/api-keys', {
        name: keyName.trim(),
        scopes: selectedScopes,
        expiresInDays: keyExpiryDays > 0 ? keyExpiryDays : null,
      });

      setGeneratedKey(res.data.rawKey);
      setKeyName('');
      loadAllData();
    } catch (err: any) {
      showMsg('error', err.response?.data?.message || 'Failed to generate API key.');
    } finally {
      setGeneratingKey(false);
    }
  };

  const handleRevokeKey = async (id: number) => {
    if (!window.confirm('Revoke this API Key immediately? All integrations using it will fail.')) return;
    try {
      await api.delete(`/integrations/api-keys/${id}`);
      showMsg('success', 'API Key revoked.');
      loadAllData();
    } catch (err: any) {
      showMsg('error', err.response?.data?.message || 'Failed to revoke key.');
    }
  };

  const copyToClipboard = (text: string) => {
    navigator.clipboard.writeText(text);
    setCopiedKey(true);
    setTimeout(() => setCopiedKey(false), 2500);
  };

  return (
    <div className="p-8 max-w-7xl mx-auto space-y-6">
      {/* Header Banner */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 bg-white p-6 rounded-xl border border-gray-200 shadow-sm">
        <div>
          <div className="flex items-center gap-3">
            <div className="p-2.5 bg-blue-50 text-blue-600 rounded-lg">
              <Share2 className="w-6 h-6" />
            </div>
            <div>
              <h1 className="text-2xl font-bold text-gray-900">External Integrations & Interoperability</h1>
              <p className="text-sm text-gray-500">
                Workspace: <span className="font-semibold text-gray-700">{currentOrg?.name || 'Acme Global Solutions'}</span> • Calendar synchronization, secure webhooks, and scoped API keys
              </p>
            </div>
          </div>
        </div>
        <div className="flex items-center gap-2">
          <button
            onClick={loadAllData}
            className="flex items-center gap-2 px-3.5 py-2 text-sm font-medium text-gray-700 bg-gray-100 hover:bg-gray-200 rounded-lg transition-colors"
          >
            <RefreshCw className="w-4 h-4" /> Refresh
          </button>
        </div>
      </div>

      {/* Global Feedback Alert */}
      {feedback && (
        <div className={`p-4 rounded-lg flex items-center justify-between text-sm ${
          feedback.type === 'success' ? 'bg-emerald-50 text-emerald-800 border border-emerald-200' : 'bg-red-50 text-red-800 border border-red-200'
        }`}>
          <div className="flex items-center gap-2">
            {feedback.type === 'success' ? <CheckCircle2 className="w-5 h-5 text-emerald-600" /> : <XCircle className="w-5 h-5 text-red-600" />}
            <span>{feedback.message}</span>
          </div>
          <button onClick={() => setFeedback(null)} className="text-gray-400 hover:text-gray-600">×</button>
        </div>
      )}

      {/* Navigation Tabs */}
      <div className="flex border-b border-gray-200 bg-white px-6 rounded-t-xl">
        <button
          onClick={() => setActiveTab('calendar')}
          className={`flex items-center gap-2 py-4 px-4 text-sm font-medium border-b-2 transition-colors ${
            activeTab === 'calendar' ? 'border-blue-600 text-blue-600' : 'border-transparent text-gray-500 hover:text-gray-700'
          }`}
        >
          <Calendar className="w-4 h-4" /> Calendar Synchronization
        </button>
        <button
          onClick={() => setActiveTab('webhooks')}
          className={`flex items-center gap-2 py-4 px-4 text-sm font-medium border-b-2 transition-colors ${
            activeTab === 'webhooks' ? 'border-blue-600 text-blue-600' : 'border-transparent text-gray-500 hover:text-gray-700'
          }`}
        >
          <Webhook className="w-4 h-4" /> Webhook Subscriptions ({webhooks.length})
        </button>
        <button
          onClick={() => setActiveTab('apikeys')}
          className={`flex items-center gap-2 py-4 px-4 text-sm font-medium border-b-2 transition-colors ${
            activeTab === 'apikeys' ? 'border-blue-600 text-blue-600' : 'border-transparent text-gray-500 hover:text-gray-700'
          }`}
        >
          <Key className="w-4 h-4" /> Scoped API Keys ({apiKeys.length})
        </button>
        <button
          onClick={() => setActiveTab('docs')}
          className={`flex items-center gap-2 py-4 px-4 text-sm font-medium border-b-2 transition-colors ${
            activeTab === 'docs' ? 'border-blue-600 text-blue-600' : 'border-transparent text-gray-500 hover:text-gray-700'
          }`}
        >
          <ExternalLink className="w-4 h-4" /> API Specs & Interoperability
        </button>
      </div>

      {/* TAB 1: CALENDAR SYNCHRONIZATION */}
      {activeTab === 'calendar' && (
        <div className="space-y-6">
          {/* Information & Architecture Notice */}
          <div className="bg-blue-50 border border-blue-200 rounded-xl p-4 flex gap-3 text-sm text-blue-900">
            <Info className="w-5 h-5 text-blue-600 flex-shrink-0 mt-0.5" />
            <div>
              <p className="font-semibold">Authoritative Schedule & Synchronization Strategy</p>
              <p className="text-blue-800 text-xs mt-1">
                Internal CPM scheduling engine remains 100% authoritative. Synchronization is one-way from SYNCORA to external calendars to ensure that external edits never silently corrupt critical path or resource allocation constraints. SHA-256 event fingerprinting prevents duplicate entries during re-sync.
              </p>
            </div>
          </div>

          {/* Sync Trigger Card */}
          <div className="bg-white p-6 rounded-xl border border-gray-200 shadow-sm flex flex-col md:flex-row md:items-center justify-between gap-4">
            <div>
              <h3 className="text-base font-semibold text-gray-900">Push Project Dates to External Calendar</h3>
              <p className="text-xs text-gray-500">
                Synchronizes project milestones, task planned dates, and deliverable deadlines with external event mapping.
              </p>
            </div>
            <div className="flex items-center gap-3">
              <select
                value={selectedProjectId}
                onChange={(e) => setSelectedProjectId(Number(e.target.value))}
                className="px-3 py-2 text-sm border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 outline-none"
              >
                {projects.map((p) => (
                  <option key={p.id} value={p.id}>{p.name}</option>
                ))}
              </select>
              <button
                disabled={syncingCalendar || !selectedProjectId}
                onClick={() => handleSyncProjectCalendar('GOOGLE_CALENDAR')}
                className="flex items-center gap-2 px-4 py-2 text-sm font-medium text-white bg-blue-600 hover:bg-blue-700 rounded-lg disabled:opacity-50 transition-colors shadow-sm"
              >
                <RefreshCw className={`w-4 h-4 ${syncingCalendar ? 'animate-spin' : ''}`} />
                {syncingCalendar ? 'Synchronizing...' : 'Sync Now'}
              </button>
              {selectedProjectId && (
                <a
                  href={`http://localhost:8080/api/projects/${selectedProjectId}/export/calendar.ics`}
                  download
                  className="flex items-center gap-2 px-4 py-2 text-sm font-medium text-gray-700 bg-gray-100 hover:bg-gray-200 rounded-lg transition-colors border border-gray-300"
                >
                  <Download className="w-4 h-4" /> Download .ics
                </a>
              )}
            </div>
          </div>

          {/* Sync Result Details */}
          {syncResult && (
            <div className="bg-white p-5 rounded-xl border border-gray-200 shadow-sm">
              <div className="flex items-center justify-between mb-3">
                <h4 className="text-sm font-bold text-gray-800 flex items-center gap-2">
                  <CheckCircle2 className="w-4 h-4 text-emerald-600" /> Synchronization Result Summary
                </h4>
                <span className="text-xs font-semibold px-2.5 py-1 bg-emerald-100 text-emerald-800 rounded-full">
                  {syncResult.eventsCreated} Created • {syncResult.eventsUpdated} Updated • {syncResult.eventsSkippedUnchanged} Unchanged
                </span>
              </div>
              <ul className="text-xs text-gray-600 space-y-1 bg-gray-50 p-3 rounded-lg border border-gray-100 max-h-40 overflow-y-auto">
                {syncResult.details?.map((d: string, idx: number) => (
                  <li key={idx}>• {d}</li>
                ))}
              </ul>
            </div>
          )}

          {/* Provider Grid */}
          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
            {calendars.map((cal) => {
              const isConnected = cal.status === 'CONNECTED' || cal.status === 'SYNCED';
              return (
                <div key={cal.provider} className="bg-white p-6 rounded-xl border border-gray-200 shadow-sm flex flex-col justify-between">
                  <div>
                    <div className="flex items-center justify-between mb-4">
                      <div className="flex items-center gap-3">
                        <div className="p-3 bg-gray-50 text-blue-600 rounded-xl border border-gray-100">
                          <Calendar className="w-6 h-6" />
                        </div>
                        <div>
                          <h3 className="font-bold text-gray-900">{cal.providerName}</h3>
                          <p className="text-xs text-gray-500">
                            {cal.provider === 'GOOGLE_CALENDAR' ? 'Google Calendar API v3' : 'Microsoft Graph Calendar v1.0'}
                          </p>
                        </div>
                      </div>
                      <span className={`px-2.5 py-1 text-xs font-semibold rounded-full ${
                        isConnected ? 'bg-emerald-100 text-emerald-800' :
                        cal.status === 'CONFIGURED' ? 'bg-blue-100 text-blue-800' :
                        cal.status === 'SYNCING' ? 'bg-purple-100 text-purple-800 animate-pulse' :
                        cal.status === 'FAILED' ? 'bg-red-100 text-red-800' :
                        'bg-gray-100 text-gray-600'
                      }`}>
                        {cal.status.replace('_', ' ')}
                      </span>
                    </div>

                    <div className="space-y-2 text-xs text-gray-600 mb-6 bg-gray-50 p-3.5 rounded-lg border border-gray-100">
                      <div className="flex justify-between">
                        <span className="text-gray-500">Connected Account:</span>
                        <span className="font-medium text-gray-900">{cal.accountEmail || 'None'}</span>
                      </div>
                      <div className="flex justify-between">
                        <span className="text-gray-500">External Calendar:</span>
                        <span className="font-medium text-gray-900">{cal.calendarId || 'primary'}</span>
                      </div>
                      <div className="flex justify-between">
                        <span className="text-gray-500">Mapped Internal Events:</span>
                        <span className="font-medium text-gray-900">{cal.mappedEventCount}</span>
                      </div>
                      <div className="flex justify-between">
                        <span className="text-gray-500">Last Synced:</span>
                        <span className="font-medium text-gray-900">{cal.lastSyncAt ? new Date(cal.lastSyncAt).toLocaleString() : 'Never'}</span>
                      </div>
                    </div>
                  </div>

                  <div className="flex items-center gap-2 pt-4 border-t border-gray-100">
                    {isConnected ? (
                      <button
                        onClick={() => handleDisconnectCalendar(cal.provider)}
                        className="w-full py-2 text-xs font-semibold text-red-600 bg-red-50 hover:bg-red-100 rounded-lg transition-colors border border-red-200"
                      >
                        Disconnect Provider
                      </button>
                    ) : (
                      <button
                        onClick={() => {
                          setShowConnectModal(cal);
                          setConnectEmail('dev@acme.com');
                        }}
                        className="w-full py-2 text-xs font-semibold text-white bg-blue-600 hover:bg-blue-700 rounded-lg transition-colors shadow-sm"
                      >
                        Connect {cal.providerName}
                      </button>
                    )}
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      )}

      {/* TAB 2: WEBHOOK SUBSCRIPTIONS */}
      {activeTab === 'webhooks' && (
        <div className="space-y-6">
          {/* SSRF & Security Advisory */}
          <div className="bg-emerald-50 border border-emerald-200 rounded-xl p-4 flex gap-3 text-sm text-emerald-900">
            <ShieldCheck className="w-5 h-5 text-emerald-600 flex-shrink-0 mt-0.5" />
            <div>
              <p className="font-semibold">Outbound Webhook Security & SSRF Protection</p>
              <p className="text-emerald-800 text-xs mt-1">
                Outbound webhooks enforce strict destination verification: private RFC 1918 subnets, loopback addresses, and cloud metadata (169.254.169.254) are rejected in production. All JSON payloads include constant-time HMAC-SHA256 signature header (<code className="bg-emerald-100 px-1 py-0.5 rounded">X-PRS-Signature: sha256=...</code>).
              </p>
            </div>
          </div>

          {/* Action Header */}
          <div className="flex justify-between items-center">
            <h3 className="text-base font-semibold text-gray-900">Configured Outbound Webhooks</h3>
            <button
              onClick={() => {
                setEditingWebhook(null);
                setWebhookName('');
                setWebhookUrl('https://');
                setWebhookSecret('');
                setSelectedEvents(['project.created', 'task.completed']);
                setShowWebhookModal(true);
              }}
              className="flex items-center gap-2 px-4 py-2 text-sm font-medium text-white bg-blue-600 hover:bg-blue-700 rounded-lg shadow-sm transition-colors"
            >
              <Plus className="w-4 h-4" /> Add Webhook Endpoint
            </button>
          </div>

          {/* Webhooks Table */}
          <div className="bg-white rounded-xl border border-gray-200 shadow-sm overflow-hidden">
            <table className="w-full text-left text-sm text-gray-600">
              <thead className="bg-gray-50 text-xs uppercase font-semibold text-gray-500 border-b border-gray-200">
                <tr>
                  <th className="px-6 py-3">Webhook Name</th>
                  <th className="px-6 py-3">Destination Endpoint</th>
                  <th className="px-6 py-3">Subscribed Events</th>
                  <th className="px-6 py-3">HMAC Secret</th>
                  <th className="px-6 py-3">Status</th>
                  <th className="px-6 py-3 text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {webhooks.length === 0 ? (
                  <tr>
                    <td colSpan={6} className="px-6 py-8 text-center text-gray-400">
                      No webhook endpoints configured yet. Click "Add Webhook Endpoint" to receive real-time business events.
                    </td>
                  </tr>
                ) : (
                  webhooks.map((w) => (
                    <tr key={w.id} className="hover:bg-gray-50 transition-colors">
                      <td className="px-6 py-4 font-semibold text-gray-900">{w.name}</td>
                      <td className="px-6 py-4">
                        <div className="font-mono text-xs text-blue-600 max-w-xs truncate" title={w.destinationUrl}>
                          {w.destinationUrl}
                        </div>
                      </td>
                      <td className="px-6 py-4">
                        <div className="flex flex-wrap gap-1">
                          {w.subscribedEvents?.slice(0, 3).map((ev) => (
                            <span key={ev} className="px-2 py-0.5 text-xs bg-gray-100 text-gray-700 rounded">
                              {ev}
                            </span>
                          ))}
                          {w.subscribedEvents?.length > 3 && (
                            <span className="px-2 py-0.5 text-xs bg-gray-200 text-gray-600 rounded">
                              +{w.subscribedEvents.length - 3} more
                            </span>
                          )}
                        </div>
                      </td>
                      <td className="px-6 py-4">
                        <span className="font-mono text-xs text-gray-400">{w.secretMasked}</span>
                      </td>
                      <td className="px-6 py-4">
                        <span className={`px-2 py-0.5 text-xs font-semibold rounded-full ${
                          w.status === 'ACTIVE' ? 'bg-emerald-100 text-emerald-800' : 'bg-amber-100 text-amber-800'
                        }`}>
                          {w.status}
                        </span>
                      </td>
                      <td className="px-6 py-4 text-right space-x-2">
                        <button
                          onClick={() => handleTestPing(w.id)}
                          disabled={testingPingId === w.id}
                          className="px-2.5 py-1 text-xs font-medium text-blue-600 bg-blue-50 hover:bg-blue-100 rounded border border-blue-200 transition-colors"
                          title="Send test event"
                        >
                          {testingPingId === w.id ? 'Pinging...' : 'Test Ping'}
                        </button>
                        <button
                          onClick={() => handleOpenDeliveries(w)}
                          className="px-2.5 py-1 text-xs font-medium text-gray-700 bg-gray-100 hover:bg-gray-200 rounded border border-gray-300 transition-colors"
                          title="View Delivery Outbox"
                        >
                          Deliveries
                        </button>
                        <button
                          onClick={() => handleRotateSecret(w.id)}
                          className="p-1 text-gray-400 hover:text-gray-600 rounded"
                          title="Rotate Secret"
                        >
                          <RotateCw className="w-3.5 h-3.5" />
                        </button>
                        <button
                          onClick={() => handleDeleteWebhook(w.id)}
                          className="p-1 text-red-400 hover:text-red-600 rounded"
                          title="Delete Webhook"
                        >
                          <Trash2 className="w-3.5 h-3.5" />
                        </button>
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* TAB 3: SCOPED API KEYS */}
      {activeTab === 'apikeys' && (
        <div className="space-y-6">
          <div className="bg-purple-50 border border-purple-200 rounded-xl p-4 flex gap-3 text-sm text-purple-900">
            <Lock className="w-5 h-5 text-purple-600 flex-shrink-0 mt-0.5" />
            <div>
              <p className="font-semibold">Granular Scopes & Raw Secret Protection</p>
              <p className="text-purple-800 text-xs mt-1">
                API keys are hashed with SHA-256 before storage. The raw secret is returned only once at creation time and never stored in plain text. External requests authenticate via <code className="bg-purple-100 px-1 py-0.5 rounded">X-API-Key: prs_live_...</code> and are strictly isolated to this organization's workspace.
              </p>
            </div>
          </div>

          {/* Reveal Key Alert */}
          {generatedKey && (
            <div className="bg-amber-50 border-2 border-amber-300 rounded-xl p-5 shadow-sm space-y-3">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2 text-amber-900 font-bold text-sm">
                  <AlertTriangle className="w-5 h-5 text-amber-600" />
                  Please copy your API Key now. For security, it will never be displayed again.
                </div>
                <button
                  onClick={() => setGeneratedKey(null)}
                  className="text-xs font-semibold text-gray-500 hover:text-gray-700"
                >
                  Dismiss
                </button>
              </div>
              <div className="flex items-center gap-2 bg-white p-3 rounded-lg border border-amber-200">
                <code className="font-mono text-sm font-semibold text-gray-800 flex-1 truncate">{generatedKey}</code>
                <button
                  onClick={() => copyToClipboard(generatedKey)}
                  className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-semibold text-white bg-blue-600 hover:bg-blue-700 rounded-md shadow-sm"
                >
                  {copiedKey ? <Check className="w-4 h-4" /> : <Copy className="w-4 h-4" />}
                  {copiedKey ? 'Copied!' : 'Copy Key'}
                </button>
              </div>
            </div>
          )}

          {/* Action Header */}
          <div className="flex justify-between items-center">
            <h3 className="text-base font-semibold text-gray-900">Active Organization API Keys</h3>
            <button
              onClick={() => {
                setKeyName('');
                setSelectedScopes(['projects:read', 'schedules:read']);
                setKeyExpiryDays(90);
                setShowKeyModal(true);
              }}
              className="flex items-center gap-2 px-4 py-2 text-sm font-medium text-white bg-blue-600 hover:bg-blue-700 rounded-lg shadow-sm transition-colors"
            >
              <Plus className="w-4 h-4" /> Generate New API Key
            </button>
          </div>

          {/* Keys Table */}
          <div className="bg-white rounded-xl border border-gray-200 shadow-sm overflow-hidden">
            <table className="w-full text-left text-sm text-gray-600">
              <thead className="bg-gray-50 text-xs uppercase font-semibold text-gray-500 border-b border-gray-200">
                <tr>
                  <th className="px-6 py-3">Key Label</th>
                  <th className="px-6 py-3">Prefix Identifier</th>
                  <th className="px-6 py-3">Granted Scopes</th>
                  <th className="px-6 py-3">Created / Expires</th>
                  <th className="px-6 py-3">Status</th>
                  <th className="px-6 py-3 text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {apiKeys.length === 0 ? (
                  <tr>
                    <td colSpan={6} className="px-6 py-8 text-center text-gray-400">
                      No external API keys generated yet. Click "Generate New API Key" to integrate external CI/CD or reporting systems.
                    </td>
                  </tr>
                ) : (
                  apiKeys.map((k) => (
                    <tr key={k.id} className="hover:bg-gray-50 transition-colors">
                      <td className="px-6 py-4 font-semibold text-gray-900">{k.name}</td>
                      <td className="px-6 py-4 font-mono text-xs text-gray-600">{k.keyPrefix}...</td>
                      <td className="px-6 py-4">
                        <div className="flex flex-wrap gap-1">
                          {k.scopes?.map((s) => (
                            <span key={s} className="px-2 py-0.5 text-xs bg-purple-50 text-purple-700 rounded border border-purple-200">
                              {s}
                            </span>
                          ))}
                        </div>
                      </td>
                      <td className="px-6 py-4 text-xs">
                        <div>Created: {new Date(k.createdAt).toLocaleDateString()}</div>
                        <div className="text-gray-400">
                          {k.expiresAt ? `Expires: ${new Date(k.expiresAt).toLocaleDateString()}` : 'Never expires'}
                        </div>
                      </td>
                      <td className="px-6 py-4">
                        <span className={`px-2.5 py-0.5 text-xs font-semibold rounded-full ${
                          k.valid ? 'bg-emerald-100 text-emerald-800' : 'bg-red-100 text-red-800'
                        }`}>
                          {k.valid ? 'Active' : 'Revoked'}
                        </span>
                      </td>
                      <td className="px-6 py-4 text-right">
                        {k.valid && (
                          <button
                            onClick={() => handleRevokeKey(k.id)}
                            className="px-2.5 py-1 text-xs font-medium text-red-600 bg-red-50 hover:bg-red-100 rounded border border-red-200 transition-colors"
                          >
                            Revoke Key
                          </button>
                        )}
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* TAB 4: API DOCS & INTEROPERABILITY */}
      {activeTab === 'docs' && (
        <div className="bg-white p-8 rounded-xl border border-gray-200 shadow-sm space-y-6">
          <div>
            <h2 className="text-xl font-bold text-gray-900">SYNCORA REST API & Interoperability Specification</h2>
            <p className="text-sm text-gray-500 mt-1">
              External systems can query and manipulate project schedules, CPM calculations, and deliverables using standard JSON REST APIs.
            </p>
          </div>

          <div className="space-y-4">
            <h3 className="text-sm font-bold uppercase tracking-wider text-gray-600">1. Authentication & Tenant Scope</h3>
            <p className="text-xs text-gray-600 leading-relaxed">
              Include your scoped API key in the <code className="bg-gray-100 px-1 py-0.5 rounded text-gray-800">X-API-Key</code> request header. All requests automatically inherit the organization workspace attached to the key.
            </p>
            <div className="bg-gray-900 text-gray-100 p-4 rounded-lg font-mono text-xs overflow-x-auto">
              curl -X GET "http://localhost:8080/api/projects" \<br />
              &nbsp;&nbsp;-H "X-API-Key: prs_live_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx" \<br />
              &nbsp;&nbsp;-H "Accept: application/json"
            </div>
          </div>

          <div className="space-y-4">
            <h3 className="text-sm font-bold uppercase tracking-wider text-gray-600">2. Core Supported Endpoints</h3>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4 text-xs">
              <div className="p-4 bg-gray-50 rounded-lg border border-gray-200">
                <span className="font-mono font-bold text-emerald-600">GET /api/projects</span>
                <p className="text-gray-500 mt-1">List all active projects and budgets in organization.</p>
              </div>
              <div className="p-4 bg-gray-50 rounded-lg border border-gray-200">
                <span className="font-mono font-bold text-blue-600">GET /api/projects/&#123;id&#125;/schedule</span>
                <p className="text-gray-500 mt-1">Retrieve CPM critical path, early/late dates, and task slacks.</p>
              </div>
              <div className="p-4 bg-gray-50 rounded-lg border border-gray-200">
                <span className="font-mono font-bold text-purple-600">GET /api/projects/&#123;id&#125;/deliverables</span>
                <p className="text-gray-500 mt-1">List project work deliverables and approval audit history.</p>
              </div>
              <div className="p-4 bg-gray-50 rounded-lg border border-gray-200">
                <span className="font-mono font-bold text-amber-600">POST /api/integrations/webhooks</span>
                <p className="text-gray-500 mt-1">Programmatically register outbound webhook event listeners.</p>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* MODAL: Connect Calendar */}
      {showConnectModal && (
        <div className="fixed inset-0 z-50 bg-black/40 flex items-center justify-center p-4">
          <div className="bg-white rounded-xl shadow-xl max-w-md w-full p-6 space-y-4">
            <h3 className="text-lg font-bold text-gray-900">Connect {showConnectModal.providerName}</h3>
            <p className="text-xs text-gray-500">
              Provide your external account details to establish a synchronization channel.
            </p>
            <form onSubmit={handleConnectCalendar} className="space-y-4">
              <div>
                <label className="block text-xs font-semibold text-gray-700 mb-1">Account Email</label>
                <input
                  type="email"
                  required
                  value={connectEmail}
                  onChange={(e) => setConnectEmail(e.target.value)}
                  placeholder="e.g. team@acme.com"
                  className="w-full px-3 py-2 text-sm border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 outline-none"
                />
              </div>
              <div>
                <label className="block text-xs font-semibold text-gray-700 mb-1">Calendar Identifier</label>
                <input
                  type="text"
                  required
                  value={connectCalId}
                  onChange={(e) => setConnectCalId(e.target.value)}
                  placeholder="primary"
                  className="w-full px-3 py-2 text-sm border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 outline-none"
                />
              </div>
              <div className="flex justify-end gap-2 pt-2">
                <button
                  type="button"
                  onClick={() => setShowConnectModal(null)}
                  className="px-4 py-2 text-sm font-medium text-gray-700 bg-gray-100 hover:bg-gray-200 rounded-lg"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="px-4 py-2 text-sm font-medium text-white bg-blue-600 hover:bg-blue-700 rounded-lg shadow-sm"
                >
                  Save Connection
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* MODAL: Create Webhook */}
      {showWebhookModal && (
        <div className="fixed inset-0 z-50 bg-black/40 flex items-center justify-center p-4">
          <div className="bg-white rounded-xl shadow-xl max-w-lg w-full p-6 space-y-4 max-h-[90vh] overflow-y-auto">
            <h3 className="text-lg font-bold text-gray-900">Add Webhook Subscription</h3>
            <p className="text-xs text-gray-500">
              Deliver real-time HTTP POST notifications to your destination endpoint when domain events occur.
            </p>
            <form onSubmit={handleSaveWebhook} className="space-y-4">
              <div>
                <label className="block text-xs font-semibold text-gray-700 mb-1">Webhook Name</label>
                <input
                  type="text"
                  required
                  value={webhookName}
                  onChange={(e) => setWebhookName(e.target.value)}
                  placeholder="e.g. Production CI/CD Pipeline"
                  className="w-full px-3 py-2 text-sm border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 outline-none"
                />
              </div>
              <div>
                <label className="block text-xs font-semibold text-gray-700 mb-1">Destination URL</label>
                <input
                  type="url"
                  required
                  value={webhookUrl}
                  onChange={(e) => setWebhookUrl(e.target.value)}
                  placeholder="https://your-domain.com/api/prs-webhook"
                  className="w-full px-3 py-2 text-sm border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 outline-none"
                />
                <p className="text-[11px] text-gray-400 mt-1">Must be HTTPS in production. Loopback and internal IPs are rejected.</p>
              </div>
              <div>
                <label className="block text-xs font-semibold text-gray-700 mb-1">Signing Secret (Optional)</label>
                <input
                  type="text"
                  value={webhookSecret}
                  onChange={(e) => setWebhookSecret(e.target.value)}
                  placeholder="Leave blank to auto-generate secure 32-byte secret"
                  className="w-full px-3 py-2 text-sm border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 outline-none"
                />
              </div>
              <div>
                <label className="block text-xs font-semibold text-gray-700 mb-2">Subscribed Event Types</label>
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-2 max-h-48 overflow-y-auto p-2 bg-gray-50 rounded-lg border border-gray-200">
                  {AVAILABLE_EVENTS.map((ev) => {
                    const checked = selectedEvents.includes(ev.id);
                    return (
                      <label key={ev.id} className="flex items-start gap-2 text-xs text-gray-700 cursor-pointer">
                        <input
                          type="checkbox"
                          checked={checked}
                          onChange={(e) => {
                            if (e.target.checked) setSelectedEvents([...selectedEvents, ev.id]);
                            else setSelectedEvents(selectedEvents.filter((id) => id !== ev.id));
                          }}
                          className="mt-0.5 rounded text-blue-600"
                        />
                        <div>
                          <div className="font-semibold">{ev.label}</div>
                          <div className="text-[11px] text-gray-400">{ev.id}</div>
                        </div>
                      </label>
                    );
                  })}
                </div>
              </div>
              <div className="flex justify-end gap-2 pt-2">
                <button
                  type="button"
                  onClick={() => setShowWebhookModal(false)}
                  className="px-4 py-2 text-sm font-medium text-gray-700 bg-gray-100 hover:bg-gray-200 rounded-lg"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={savingWebhook}
                  className="px-4 py-2 text-sm font-medium text-white bg-blue-600 hover:bg-blue-700 rounded-lg shadow-sm"
                >
                  {savingWebhook ? 'Saving...' : 'Save Webhook'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* MODAL: Generate API Key */}
      {showKeyModal && (
        <div className="fixed inset-0 z-50 bg-black/40 flex items-center justify-center p-4">
          <div className="bg-white rounded-xl shadow-xl max-w-md w-full p-6 space-y-4">
            <h3 className="text-lg font-bold text-gray-900">Generate Scoped API Key</h3>
            <p className="text-xs text-gray-500">
              Create an authenticated key for external tools with strict permission scopes and automatic expiration.
            </p>
            <form onSubmit={handleGenerateKey} className="space-y-4">
              <div>
                <label className="block text-xs font-semibold text-gray-700 mb-1">Key Label</label>
                <input
                  type="text"
                  required
                  value={keyName}
                  onChange={(e) => setKeyName(e.target.value)}
                  placeholder="e.g. Jenkins Automation Service"
                  className="w-full px-3 py-2 text-sm border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 outline-none"
                />
              </div>
              <div>
                <label className="block text-xs font-semibold text-gray-700 mb-1">Key Expiration</label>
                <select
                  value={keyExpiryDays}
                  onChange={(e) => setKeyExpiryDays(Number(e.target.value))}
                  className="w-full px-3 py-2 text-sm border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 outline-none"
                >
                  <option value={30}>30 Days</option>
                  <option value={60}>60 Days</option>
                  <option value={90}>90 Days</option>
                  <option value={365}>1 Year</option>
                  <option value={0}>Never Expires</option>
                </select>
              </div>
              <div>
                <label className="block text-xs font-semibold text-gray-700 mb-2">Granted Scopes</label>
                <div className="space-y-2 p-3 bg-gray-50 rounded-lg border border-gray-200">
                  {AVAILABLE_SCOPES.map((sc) => {
                    const checked = selectedScopes.includes(sc.id);
                    return (
                      <label key={sc.id} className="flex items-start gap-2 text-xs text-gray-700 cursor-pointer">
                        <input
                          type="checkbox"
                          checked={checked}
                          onChange={(e) => {
                            if (e.target.checked) setSelectedScopes([...selectedScopes, sc.id]);
                            else setSelectedScopes(selectedScopes.filter((id) => id !== sc.id));
                          }}
                          className="mt-0.5 rounded text-blue-600"
                        />
                        <div>
                          <div className="font-semibold">{sc.label}</div>
                          <div className="text-[11px] text-gray-400">{sc.desc}</div>
                        </div>
                      </label>
                    );
                  })}
                </div>
              </div>
              <div className="flex justify-end gap-2 pt-2">
                <button
                  type="button"
                  onClick={() => setShowKeyModal(false)}
                  className="px-4 py-2 text-sm font-medium text-gray-700 bg-gray-100 hover:bg-gray-200 rounded-lg"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={generatingKey}
                  className="px-4 py-2 text-sm font-medium text-white bg-blue-600 hover:bg-blue-700 rounded-lg shadow-sm"
                >
                  {generatingKey ? 'Generating...' : 'Create Key'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* DRAWER: Webhook Delivery History */}
      {deliveriesSub && (
        <div className="fixed inset-0 z-50 bg-black/40 flex justify-end">
          <div className="bg-white w-full max-w-xl h-full shadow-2xl p-6 overflow-y-auto space-y-4">
            <div className="flex items-center justify-between border-b pb-3">
              <div>
                <h3 className="text-base font-bold text-gray-900">Delivery Outbox: {deliveriesSub.name}</h3>
                <p className="text-xs text-gray-500 truncate max-w-md">{deliveriesSub.destinationUrl}</p>
              </div>
              <button
                onClick={() => setDeliveriesSub(null)}
                className="text-gray-400 hover:text-gray-600 text-lg font-bold"
              >
                ✕
              </button>
            </div>

            {loadingDeliveries ? (
              <p className="text-xs text-gray-400 py-6 text-center">Loading delivery records...</p>
            ) : deliveries.length === 0 ? (
              <p className="text-xs text-gray-400 py-6 text-center">No outbound events delivered yet.</p>
            ) : (
              <div className="space-y-3">
                {deliveries.map((del) => (
                  <div key={del.id} className="p-4 bg-gray-50 rounded-xl border border-gray-200 text-xs space-y-2">
                    <div className="flex items-center justify-between">
                      <span className={`px-2 py-0.5 font-bold rounded ${
                        del.status === 'SUCCESS' ? 'bg-emerald-100 text-emerald-800' : 'bg-red-100 text-red-800'
                      }`}>
                        {del.status} {del.responseStatusCode ? `(HTTP ${del.responseStatusCode})` : ''}
                      </span>
                      <span className="text-gray-400">{new Date(del.createdAt).toLocaleString()}</span>
                    </div>

                    <div className="grid grid-cols-2 gap-2 text-gray-600">
                      <div><span className="font-semibold">Event:</span> {del.eventType}</div>
                      <div><span className="font-semibold">Duration:</span> {del.durationMs}ms</div>
                      <div><span className="font-semibold">Attempt:</span> #{del.attemptCount}</div>
                      <div><span className="font-semibold">Event ID:</span> <span className="font-mono text-[10px]">{del.eventId.substring(0, 8)}...</span></div>
                    </div>

                    {del.errorMessage && (
                      <p className="text-red-600 bg-red-50 p-2 rounded border border-red-200">
                        Error: {del.errorMessage}
                      </p>
                    )}

                    <div className="flex justify-end pt-2">
                      <button
                        onClick={() => handleRetryDelivery(del.id)}
                        className="px-3 py-1 font-semibold text-blue-600 bg-white hover:bg-blue-50 border border-blue-200 rounded shadow-sm"
                      >
                        Retry Delivery
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
export default Integrations;
