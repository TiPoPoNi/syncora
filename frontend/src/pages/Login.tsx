import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { api } from '../services/api';
import { 
  Building2, 
  Sparkles, 
  ShieldCheck, 
  Calendar, 
  Lock, 
  User, 
  ArrowRight,
  Zap,
  Eye,
  EyeOff,
  KeyRound,
  PlusCircle,
  X,
  Info,
  ExternalLink,
  CheckCircle2
} from 'lucide-react';
import { Button, Alert } from '../components/ui';

export function Login() {
  const { login } = useAuth();
  const navigate = useNavigate();

  // Enable demo credentials quick-fill for seamless testing
  const isDemoMode = true;

  const DEV_DEMO_ACCOUNTS = isDemoMode ? [
    { 
      id: 'ACM-EMP-0001', 
      username: 'admin', 
      org: 'ACM', 
      roleName: 'Organization Owner / Admin', 
      badge: 'Owner / Admin',
      badgeColor: 'bg-purple-100 text-purple-700 border-purple-200', 
      desc: 'Acme Admin (Full Access)' 
    },
    { 
      id: 'ACM-EMP-0002', 
      username: 'manager', 
      org: 'ACM', 
      roleName: 'Project Manager', 
      badge: 'Project Manager',
      badgeColor: 'bg-blue-100 text-blue-700 border-blue-200', 
      desc: 'Acme Project Manager' 
    },
    { 
      id: 'ACM-EMP-0003', 
      username: 'worker', 
      org: 'ACM', 
      roleName: 'Worker / Employee', 
      badge: 'Worker',
      badgeColor: 'bg-emerald-100 text-emerald-700 border-emerald-200', 
      desc: 'Acme Worker (Tasks & CPM)' 
    },
    { 
      id: 'ACM-EMP-0004', 
      username: 'client', 
      org: 'ACM', 
      roleName: 'Client Stakeholder', 
      badge: 'Client',
      badgeColor: 'bg-amber-100 text-amber-700 border-amber-200', 
      desc: 'Acme Client (Reviews & Approvals)' 
    },
    { 
      id: 'ACM-EMP-0005', 
      username: 'vendor', 
      org: 'ACM', 
      roleName: 'Vendor Partner', 
      badge: 'Vendor',
      badgeColor: 'bg-orange-100 text-orange-700 border-orange-200', 
      desc: 'Acme External Vendor Partner' 
    },
    { 
      id: 'STK-EMP-0001', 
      username: 'admin', 
      org: 'STK', 
      roleName: 'Secondary Tenant Owner', 
      badge: 'Tenant Admin',
      badgeColor: 'bg-indigo-100 text-indigo-700 border-indigo-200', 
      desc: 'Stark Industries Admin' 
    },
  ] : [];

  // Primary Login State
  const [identifier, setIdentifier] = useState(() => localStorage.getItem('prs_remember_ident') || '');
  const [orgCode, setOrgCode] = useState(() => localStorage.getItem('prs_remember_org') || '');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [rememberMe, setRememberMe] = useState(() => localStorage.getItem('prs_remember_pref') === 'true');
  const [error, setError] = useState('');
  const [sessionNotice, setSessionNotice] = useState('');
  const [loading, setLoading] = useState(false);
  const [devHelperNote, setDevHelperNote] = useState('');
  const [activeShortcut, setActiveShortcut] = useState<string | null>(null);

  // Truthful backend health status
  const [healthStatus, setHealthStatus] = useState<'CHECKING' | 'ONLINE' | 'STANDALONE'>('CHECKING');

  // Modals
  const [showOrgModal, setShowOrgModal] = useState(false);
  const [showResetModal, setShowResetModal] = useState(false);
  const [showDevCredentials, setShowDevCredentials] = useState(true);

  // Org Onboarding Form State
  const [onboardName, setOnboardName] = useState('');
  const [onboardSlug, setOnboardSlug] = useState('');
  const [onboardCode, setOnboardCode] = useState('');
  const [onboardEmail, setOnboardEmail] = useState('');
  const [onboardUsername, setOnboardUsername] = useState('');
  const [onboardPassword, setOnboardPassword] = useState('');
  const [onboardConfirmPassword, setOnboardConfirmPassword] = useState('');
  const [showOnboardPassword, setShowOnboardPassword] = useState(false);
  const [showOnboardConfirmPassword, setShowOnboardConfirmPassword] = useState(false);
  const [onboardFullName, setOnboardFullName] = useState('');
  const [onboardLoading, setOnboardLoading] = useState(false);
  const [onboardError, setOnboardError] = useState('');

  // Password Reset Form State
  const [resetStep, setResetStep] = useState<'REQUEST' | 'SENT'>('REQUEST');
  const [resetIdent, setResetIdent] = useState('');
  const [resetMsg, setResetMsg] = useState('');
  const [resetError, setResetError] = useState('');
  const [resetLoading, setResetLoading] = useState(false);
  const [demoResetToken, setDemoResetToken] = useState('');
  const [demoResetLink, setDemoResetLink] = useState('');

  useEffect(() => {
    document.title = 'SYNCORA — Plan. Coordinate. Deliver.';

    // Check for session expired query parameter
    const params = new URLSearchParams(window.location.search);
    if (params.get('session_expired') === 'true') {
      setSessionNotice('Your session has expired. Please sign in to resume your workspace session.');
    }

    // Truthful backend health check for the status indicator
    api.get('/health')
      .then((res) => {
        if (res.data?.status === 'UP') {
          setHealthStatus('ONLINE');
        } else {
          setHealthStatus('STANDALONE');
        }
      })
      .catch(() => {
        setHealthStatus('STANDALONE');
      });
  }, []);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setSessionNotice('');
    setDevHelperNote('');
    const cleanIdent = identifier.trim();
    if (!cleanIdent || !password) {
      setError('Please provide your Employee ID or Work Email, and your password.');
      return;
    }

    setLoading(true);
    try {
      const res = await api.post('/auth/login', { 
        username: cleanIdent,
        organizationCode: orgCode.trim() || undefined,
        password 
      });

      if (rememberMe) {
        localStorage.setItem('prs_remember_ident', cleanIdent);
        localStorage.setItem('prs_remember_org', orgCode.trim());
        localStorage.setItem('prs_remember_pref', 'true');
      } else {
        localStorage.removeItem('prs_remember_ident');
        localStorage.removeItem('prs_remember_org');
        localStorage.removeItem('prs_remember_pref');
      }

      login(res.data);
    } catch (err: any) {
      if (!err.response) {
        setError('Network Error: Unable to connect to the authentication server. Please check your network connection.');
      } else if (err.response.status === 429) {
        setError(err.response.data?.message || 'Authentication Locked: Too many failed login attempts. Please try again in 15 minutes.');
      } else {
        setError(err.response?.data?.message || 'Authentication Failed: Invalid credentials or account is suspended.');
      }
    } finally {
      setLoading(false);
    }
  };

  const handleFillDevAccount = (acc: typeof DEV_DEMO_ACCOUNTS[0]) => {
    setIdentifier(acc.id);
    setOrgCode(acc.org);
    setActiveShortcut(acc.id + '_' + acc.org);
    setError('');
    setSessionNotice('');
    // In dev mode, auto-fill default password 'admin'
    if (isDemoMode) {
      setPassword('admin');
      setDevHelperNote(`Loaded ${acc.roleName} (${acc.id} in ${acc.org}). Password populated for local development testing.`);
    } else {
      setDevHelperNote(`Loaded ${acc.roleName} (${acc.id} in ${acc.org}). Please enter password.`);
    }
  };

  const handleOnboardOrg = async (e: React.FormEvent) => {
    e.preventDefault();
    setOnboardError('');

    if (onboardPassword.length < 12) {
      setOnboardError('Password must be at least 12 characters in length.');
      return;
    }
    if (onboardPassword !== onboardConfirmPassword) {
      setOnboardError('Passwords do not match. Please re-enter.');
      return;
    }

    setOnboardLoading(true);
    try {
      const res = await api.post('/auth/register-organization', {
        organizationName: onboardName.trim(),
        organizationSlug: onboardSlug.trim() || undefined,
        organizationCode: onboardCode.trim().toUpperCase() || undefined,
        ownerEmail: onboardEmail.trim(),
        ownerUsername: onboardUsername.trim(),
        ownerPassword: onboardPassword,
        ownerConfirmPassword: onboardConfirmPassword,
        ownerFullName: onboardFullName.trim() || undefined,
      });

      setShowOrgModal(false);
      login(res.data);
    } catch (err: any) {
      setOnboardError(err.response?.data?.message || 'Organization registration failed. Please review the details.');
    } finally {
      setOnboardLoading(false);
    }
  };

  const handleRequestReset = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!resetIdent.trim()) return;
    setResetError('');
    setResetMsg('');
    setResetLoading(true);
    try {
      const res = await api.post('/auth/password-reset/request', {
        identifier: resetIdent.trim()
      });
      setResetMsg(res.data.message || 'If an account exists, reset instructions have been dispatched.');
      if (res.data.demoResetLink) {
        setDemoResetLink(res.data.demoResetLink);
      }
      if (res.data.demoResetToken) {
        setDemoResetToken(res.data.demoResetToken);
      }
      setResetStep('SENT');
    } catch (err: any) {
      setResetError(err.response?.data?.message || 'Failed to request reset instructions.');
    } finally {
      setResetLoading(false);
    }
  };

  return (
    <div className="min-h-screen flex bg-slate-950 text-slate-100 font-sans">
      {/* Left Feature Showcase Banner (Preserved Split-Screen Navy-Blue Branding) */}
      <div className="hidden lg:flex lg:w-1/2 flex-col justify-between p-12 bg-gradient-to-br from-slate-900 via-blue-950 to-slate-900 border-r border-slate-800 relative overflow-hidden">
        {/* Background Decorative Rings */}
        <div className="absolute top-0 right-0 -mr-20 -mt-20 w-96 h-96 rounded-full bg-blue-500/10 blur-3xl pointer-events-none" />
        <div className="absolute bottom-0 left-0 -ml-20 -mb-20 w-96 h-96 rounded-full bg-indigo-500/10 blur-3xl pointer-events-none" />

        {/* Official Brand Header */}
        <div className="flex items-center gap-3.5 z-10">
          <div className="w-11 h-11 rounded-xl bg-blue-600 flex items-center justify-center text-white shadow-lg shadow-blue-500/30">
            <Zap className="w-6 h-6 fill-white" />
          </div>
          <div>
            <span className="text-2xl font-black tracking-tight text-white block">SYNCORA</span>
            <span className="block text-xs font-bold text-blue-300 tracking-wide uppercase">Plan. Coordinate. Deliver.</span>
            <span className="block text-[11px] text-slate-400 mt-0.5">Project Resource Allocation & Scheduling System</span>
          </div>
        </div>

        {/* Center Pitch: Required Headline & Supporting Paragraph */}
        <div className="space-y-6 max-w-lg z-10">
          <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-blue-500/10 border border-blue-500/20 text-blue-400 text-xs font-semibold">
            <Sparkles className="w-3.5 h-3.5" />
            <span>Organization Workspace Platform</span>
          </div>
          <h1 className="text-4xl font-extrabold text-white tracking-tight leading-tight">
            Keep every project, resource, and deadline in sync.
          </h1>
          <p className="text-sm text-slate-300 leading-relaxed">
            Coordinate clients, workers, vendors, tasks, and budgets in one workspace. Track dependencies, identify conflicts, and understand how delays affect delivery.
          </p>

          <div className="grid grid-cols-2 gap-4 pt-4">
            <div className="p-4 rounded-xl bg-slate-800/60 border border-slate-700/60 backdrop-blur-sm">
              <ShieldCheck className="w-5 h-5 text-emerald-400 mb-2" />
              <div className="text-sm font-bold text-white">Identity & Governance</div>
              <p className="text-xs text-slate-400 mt-1">Organization-issued employee IDs, role scopes, and single-use activation links.</p>
            </div>
            <div className="p-4 rounded-xl bg-slate-800/60 border border-slate-700/60 backdrop-blur-sm">
              <Calendar className="w-5 h-5 text-blue-400 mb-2" />
              <div className="text-sm font-bold text-white">Interactive CPM & Gantt</div>
              <p className="text-xs text-slate-400 mt-1">Critical path forward/backward passes, float calculation, and delay propagation.</p>
            </div>
          </div>
        </div>

        {/* Truthful Status Footer */}
        <div className="flex items-center justify-between text-xs text-slate-400 border-t border-slate-800/80 pt-6 z-10">
          <div className="flex items-center gap-2">
            <div 
              className={`w-2 h-2 rounded-full ${
                healthStatus === 'ONLINE' ? 'bg-emerald-400 animate-pulse' : 'bg-blue-400'
              }`} 
            />
            <span>
              {healthStatus === 'ONLINE' 
                ? 'Project Operations Workspace — Online' 
                : 'Project Operations Workspace'}
            </span>
          </div>
          <span className="font-mono text-slate-500">SYNCORA v1.0.0</span>
        </div>
      </div>

      {/* Right Login Form Container */}
      <div className="w-full lg:w-1/2 flex items-center justify-center p-8 sm:p-12 bg-white text-gray-900 overflow-y-auto">
        <div className="max-w-md w-full space-y-6">
          <div>
            <div className="flex items-center gap-2.5 lg:hidden mb-6">
              <div className="w-9 h-9 rounded-lg bg-blue-600 flex items-center justify-center text-white shadow-md shadow-blue-500/20">
                <Zap className="w-5 h-5 fill-white" />
              </div>
              <div>
                <span className="text-xl font-black text-gray-900 tracking-tight">SYNCORA</span>
                <span className="block text-[10px] font-bold text-blue-600 uppercase tracking-wide">Plan. Coordinate. Deliver.</span>
              </div>
            </div>
            <h2 className="text-2xl font-bold tracking-tight text-gray-900">
              Sign In to Workspace
            </h2>
            <p className="text-sm text-gray-500 mt-1">
              Enter your workspace code, employee ID or work email, and password.
            </p>
          </div>

          {sessionNotice && (
            <Alert type="info" title="Session Notice">
              {sessionNotice}
            </Alert>
          )}

          {error && (
            <Alert type="error" title="Sign In Failed">
              {error}
            </Alert>
          )}

          {devHelperNote && (
            <div className="p-3 bg-blue-50 border border-blue-200 rounded-lg text-xs text-blue-800 flex items-start gap-2">
              <Info className="w-4 h-4 text-blue-600 shrink-0 mt-0.5" />
              <span>{devHelperNote}</span>
            </div>
          )}

          <form className="space-y-4" onSubmit={handleSubmit} noValidate>
            <div>
              <div className="flex items-center justify-between mb-1">
                <label 
                  htmlFor="workspace-code" 
                  className="block text-xs font-semibold text-gray-700 uppercase tracking-wider"
                >
                  Workspace Code <span className="font-normal text-gray-400 lowercase">(optional)</span>
                </label>
              </div>
              <div className="relative">
                <input
                  id="workspace-code"
                  name="workspaceCode"
                  type="text"
                  autoComplete="organization"
                  placeholder="e.g. ACM or STK"
                  value={orgCode}
                  onChange={(e) => setOrgCode(e.target.value.toUpperCase())}
                  className="w-full pl-9 pr-3 py-2 border border-gray-300 rounded-lg text-sm font-mono text-gray-900 uppercase focus:outline-none focus:ring-2 focus:ring-blue-500 transition"
                  aria-describedby="workspace-code-hint"
                />
                <Building2 className="w-4 h-4 text-gray-400 absolute left-3 top-2.5 pointer-events-none" />
              </div>
              <p id="workspace-code-hint" className="text-[11px] text-gray-500 mt-1">
                Leave empty if your employee ID or email uniquely identifies your workspace.
              </p>
            </div>

            <div>
              <label 
                htmlFor="employee-identifier" 
                className="block text-xs font-semibold text-gray-700 uppercase tracking-wider mb-1"
              >
                Employee ID or Work Email <span className="text-red-500">*</span>
              </label>
              <div className="relative">
                <input
                  id="employee-identifier"
                  name="identifier"
                  type="text"
                  required
                  autoComplete="username"
                  placeholder="e.g. ACM-EMP-0001, admin, or admin@example.com"
                  value={identifier}
                  onChange={(e) => setIdentifier(e.target.value)}
                  className="w-full pl-9 pr-3 py-2 border border-gray-300 rounded-lg text-sm text-gray-900 focus:outline-none focus:ring-2 focus:ring-blue-500 transition"
                />
                <User className="w-4 h-4 text-gray-400 absolute left-3 top-2.5 pointer-events-none" />
              </div>
            </div>

            <div>
              <label 
                htmlFor="account-password" 
                className="block text-xs font-semibold text-gray-700 uppercase tracking-wider mb-1"
              >
                Password <span className="text-red-500">*</span>
              </label>
              <div className="relative">
                <input
                  id="account-password"
                  name="password"
                  type={showPassword ? 'text' : 'password'}
                  required
                  autoComplete="current-password"
                  placeholder="••••••••"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  className="w-full pl-9 pr-10 py-2 border border-gray-300 rounded-lg text-sm text-gray-900 focus:outline-none focus:ring-2 focus:ring-blue-500 transition"
                />
                <Lock className="w-4 h-4 text-gray-400 absolute left-3 top-2.5 pointer-events-none" />
                <button
                  type="button"
                  aria-label={showPassword ? 'Hide password' : 'Show password'}
                  onClick={() => setShowPassword(!showPassword)}
                  className="absolute right-3 top-2.5 text-gray-400 hover:text-gray-600 focus:outline-none focus:text-blue-600"
                >
                  {showPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                </button>
              </div>
            </div>

            <div className="flex items-center justify-between text-xs pt-1">
              <label htmlFor="remember-me" className="flex items-center gap-2 text-gray-600 cursor-pointer select-none">
                <input 
                  id="remember-me"
                  name="rememberMe"
                  type="checkbox" 
                  checked={rememberMe} 
                  onChange={(e) => setRememberMe(e.target.checked)} 
                  className="rounded border-gray-300 text-blue-600 focus:ring-blue-500" 
                />
                <span>Remember on this device</span>
              </label>
              <button
                type="button"
                onClick={() => {
                  setResetStep('REQUEST');
                  setResetError('');
                  setResetMsg('');
                  setDemoResetLink('');
                  setDemoResetToken('');
                  setShowResetModal(true);
                }}
                className="text-blue-600 hover:text-blue-700 font-medium cursor-pointer focus:outline-none focus:underline"
              >
                Forgot password?
              </button>
            </div>

            <Button
              type="submit"
              variant="primary"
              size="lg"
              className="w-full justify-center mt-2"
              isLoading={loading}
              disabled={loading}
              rightIcon={<ArrowRight className="w-4 h-4" />}
            >
              Sign In to Workspace
            </Button>
          </form>

          {/* Secondary Action: Authorized Organization Onboarding */}
          <div className="pt-2 text-center space-y-2">
            <button
              type="button"
              onClick={() => {
                setOnboardError('');
                setShowOrgModal(true);
              }}
              className="text-sm font-semibold text-blue-600 hover:text-blue-800 transition inline-flex items-center gap-1.5 focus:outline-none focus:underline cursor-pointer"
            >
              <PlusCircle className="w-4 h-4" />
              <span>Set up your organization workspace</span>
            </button>
            <p className="text-[11px] text-gray-500 leading-normal max-w-sm mx-auto">
              Employees receive access invitations directly from their organization administrator. Workspace creation is reserved for organization owners.
            </p>
          </div>

          {/* Development Test Accounts Section — Strictly gated to Development / Demo Mode */}
          {isDemoMode && (
            <div className="pt-5 border-t border-gray-200">
              <div className="flex items-center justify-between mb-2.5">
                <div className="flex items-center gap-1.5">
                  <span className="text-[10px] font-bold text-gray-500 uppercase tracking-wider">
                    Development Test Accounts
                  </span>
                  <span className="text-[9px] font-mono px-1.5 py-0.5 rounded bg-blue-50 border border-blue-200 text-blue-700 font-semibold">
                    Dev Seed
                  </span>
                </div>
                <button
                  type="button"
                  onClick={() => setShowDevCredentials(!showDevCredentials)}
                  className="text-[11px] text-blue-600 hover:underline font-medium"
                >
                  {showDevCredentials ? 'Hide' : 'Show'}
                </button>
              </div>

              {showDevCredentials && (
                <div className="space-y-2">
                  <p className="text-[11px] text-gray-500">
                    Click any shortcut to auto-populate the test account credentials:
                  </p>
                  <div className="grid grid-cols-2 gap-2">
                    {DEV_DEMO_ACCOUNTS.map((acc) => {
                      const isSelected = activeShortcut === acc.id + '_' + acc.org;
                      return (
                        <button
                          key={acc.id + '_' + acc.org}
                          type="button"
                          onClick={() => handleFillDevAccount(acc)}
                          className={`p-2 rounded-lg border text-left transition flex flex-col justify-between ${
                            isSelected 
                              ? 'bg-blue-50/80 border-blue-400 ring-1 ring-blue-400' 
                              : 'bg-slate-50 hover:bg-slate-100 border-slate-200'
                          }`}
                        >
                          <div className="flex items-center justify-between gap-1 mb-1">
                            <span className={`text-[9px] font-semibold px-1.5 py-0.5 rounded border ${acc.badgeColor}`}>
                              {acc.badge}
                            </span>
                            <span className="text-[9px] font-mono text-slate-500 font-bold">
                              {acc.org}
                            </span>
                          </div>
                          <div>
                            <div className="text-xs font-mono font-semibold text-slate-900 truncate">
                              {acc.id}
                            </div>
                            <div className="text-[10px] text-slate-500 truncate">
                              username: <span className="font-mono text-slate-700">{acc.username}</span>
                            </div>
                          </div>
                        </button>
                      );
                    })}
                  </div>
                </div>
              )}
            </div>
          )}
        </div>
      </div>

      {/* Organization Onboarding Modal */}
      {showOrgModal && (
        <div 
          className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm animate-in fade-in"
          role="dialog"
          aria-modal="true"
          aria-labelledby="org-modal-title"
        >
          <div className="bg-white text-gray-900 rounded-2xl max-w-md w-full p-6 shadow-2xl border border-gray-100 space-y-5 max-h-[90vh] overflow-y-auto">
            <div className="flex items-center justify-between pb-3 border-b border-gray-100">
              <div className="flex items-center gap-2">
                <Building2 className="w-5 h-5 text-blue-600" />
                <h3 id="org-modal-title" className="text-lg font-bold">Set Up Organization Workspace</h3>
              </div>
              <button 
                type="button" 
                aria-label="Close modal"
                onClick={() => setShowOrgModal(false)}
                className="text-gray-400 hover:text-gray-600 p-1 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            {onboardError && (
              <Alert type="error" title="Onboarding Failed">
                {onboardError}
              </Alert>
            )}

            <p className="text-xs text-gray-600 leading-relaxed">
              Creates a dedicated tenant workspace with an isolated data boundary and an initial organization owner account.
            </p>

            <form onSubmit={handleOnboardOrg} className="space-y-3.5 text-xs">
              <div>
                <label htmlFor="onboard-name" className="block font-semibold text-gray-700 mb-1">
                  Organization Name <span className="text-red-500">*</span>
                </label>
                <input
                  id="onboard-name"
                  type="text"
                  required
                  placeholder="e.g. Apex Global Engineering"
                  value={onboardName}
                  onChange={(e) => setOnboardName(e.target.value)}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm text-gray-900 focus:ring-2 focus:ring-blue-500 focus:outline-none"
                />
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label htmlFor="onboard-code" className="block font-semibold text-gray-700 mb-1">
                    Workspace Code <span className="text-red-500">*</span>
                  </label>
                  <input
                    id="onboard-code"
                    type="text"
                    required
                    placeholder="e.g. APX"
                    value={onboardCode}
                    onChange={(e) => setOnboardCode(e.target.value.toUpperCase())}
                    className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm font-mono text-gray-900 uppercase focus:ring-2 focus:ring-blue-500 focus:outline-none"
                  />
                </div>
                <div>
                  <label htmlFor="onboard-slug" className="block font-semibold text-gray-700 mb-1">
                    Slug / URL Name
                  </label>
                  <input
                    id="onboard-slug"
                    type="text"
                    placeholder="e.g. apex-global"
                    value={onboardSlug}
                    onChange={(e) => setOnboardSlug(e.target.value.toLowerCase())}
                    className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm font-mono text-gray-900 lowercase focus:ring-2 focus:ring-blue-500 focus:outline-none"
                  />
                </div>
              </div>

              <div className="pt-2 border-t border-gray-100">
                <span className="font-bold text-gray-900 block mb-2">Initial Organization Owner Account</span>
              </div>

              <div>
                <label htmlFor="onboard-email" className="block font-semibold text-gray-700 mb-1">
                  Owner Work Email <span className="text-red-500">*</span>
                </label>
                <input
                  id="onboard-email"
                  type="email"
                  required
                  placeholder="owner@apex.com"
                  value={onboardEmail}
                  onChange={(e) => setOnboardEmail(e.target.value)}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm text-gray-900 focus:ring-2 focus:ring-blue-500 focus:outline-none"
                />
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div className="col-span-2">
                  <label htmlFor="onboard-username" className="block font-semibold text-gray-700 mb-1">
                    Globally Unique Username <span className="text-red-500">*</span>
                  </label>
                  <input
                    id="onboard-username"
                    type="text"
                    required
                    placeholder="e.g. apex_owner"
                    value={onboardUsername}
                    onChange={(e) => setOnboardUsername(e.target.value)}
                    className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm text-gray-900 focus:ring-2 focus:ring-blue-500 focus:outline-none"
                  />
                  <p className="text-[11px] text-gray-500 mt-0.5">Usernames are unique across the entire SYNCORA platform.</p>
                </div>

                <div className="col-span-2 sm:col-span-1">
                  <div className="flex items-center justify-between mb-1">
                    <label htmlFor="onboard-password" className="block font-semibold text-gray-700">
                      Password <span className="text-red-500">*</span>
                    </label>
                    <span className="text-[10px] text-gray-500">Min 12 chars</span>
                  </div>
                  <div className="relative">
                    <input
                      id="onboard-password"
                      type={showOnboardPassword ? 'text' : 'password'}
                      required
                      placeholder="Min 12 chars"
                      value={onboardPassword}
                      onChange={(e) => setOnboardPassword(e.target.value)}
                      className="w-full pl-3 pr-8 py-2 border border-gray-300 rounded-lg text-sm text-gray-900 focus:ring-2 focus:ring-blue-500 focus:outline-none"
                    />
                    <button
                      type="button"
                      onClick={() => setShowOnboardPassword(!showOnboardPassword)}
                      className="absolute right-2.5 top-2.5 text-gray-400 hover:text-gray-600"
                    >
                      {showOnboardPassword ? <EyeOff className="w-3.5 h-3.5" /> : <Eye className="w-3.5 h-3.5" />}
                    </button>
                  </div>
                </div>

                <div className="col-span-2 sm:col-span-1">
                  <label htmlFor="onboard-confirm-password" className="block font-semibold text-gray-700 mb-1">
                    Confirm Password <span className="text-red-500">*</span>
                  </label>
                  <div className="relative">
                    <input
                      id="onboard-confirm-password"
                      type={showOnboardConfirmPassword ? 'text' : 'password'}
                      required
                      placeholder="Confirm password"
                      value={onboardConfirmPassword}
                      onChange={(e) => setOnboardConfirmPassword(e.target.value)}
                      className={`w-full pl-3 pr-8 py-2 border rounded-lg text-sm text-gray-900 focus:ring-2 focus:outline-none ${
                        onboardConfirmPassword && onboardConfirmPassword !== onboardPassword
                          ? 'border-red-400 focus:ring-red-400'
                          : 'border-gray-300 focus:ring-blue-500'
                      }`}
                    />
                    <button
                      type="button"
                      onClick={() => setShowOnboardConfirmPassword(!showOnboardConfirmPassword)}
                      className="absolute right-2.5 top-2.5 text-gray-400 hover:text-gray-600"
                    >
                      {showOnboardConfirmPassword ? <EyeOff className="w-3.5 h-3.5" /> : <Eye className="w-3.5 h-3.5" />}
                    </button>
                  </div>
                </div>
              </div>

              <div>
                <label htmlFor="onboard-fullname" className="block font-semibold text-gray-700 mb-1">
                  Owner Full Name (optional)
                </label>
                <input
                  id="onboard-fullname"
                  type="text"
                  placeholder="e.g. Alex Mercer"
                  value={onboardFullName}
                  onChange={(e) => setOnboardFullName(e.target.value)}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm text-gray-900 focus:ring-2 focus:ring-blue-500 focus:outline-none"
                />
              </div>

              <div className="pt-3 flex gap-2">
                <Button
                  type="button"
                  variant="outline"
                  className="flex-1 justify-center"
                  onClick={() => setShowOrgModal(false)}
                >
                  Cancel
                </Button>
                <Button
                  type="submit"
                  variant="primary"
                  className="flex-1 justify-center"
                  isLoading={onboardLoading}
                  disabled={onboardLoading || onboardPassword.length < 12 || onboardPassword !== onboardConfirmPassword}
                >
                  Create & Launch Workspace
                </Button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Forgot Password Modal */}
      {showResetModal && (
        <div 
          className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm animate-in fade-in"
          role="dialog"
          aria-modal="true"
          aria-labelledby="reset-modal-title"
        >
          <div className="bg-white text-gray-900 rounded-2xl max-w-md w-full p-6 shadow-2xl border border-gray-100 space-y-4">
            <div className="flex items-center justify-between pb-3 border-b border-gray-100">
              <div className="flex items-center gap-2">
                <KeyRound className="w-5 h-5 text-blue-600" />
                <h3 id="reset-modal-title" className="text-lg font-bold">Password Recovery</h3>
              </div>
              <button 
                type="button" 
                aria-label="Close recovery modal"
                onClick={() => setShowResetModal(false)}
                className="text-gray-400 hover:text-gray-600 p-1 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            {resetError && (
              <Alert type="error" title="Error">
                {resetError}
              </Alert>
            )}

            {resetMsg && (
              <Alert type="success" title="Notice">
                {resetMsg}
              </Alert>
            )}

            {resetStep === 'REQUEST' ? (
              <form onSubmit={handleRequestReset} className="space-y-4 text-xs">
                <p className="text-gray-600 leading-relaxed">
                  Enter your work email address or username. If an account exists, a time-limited reset link will be dispatched.
                </p>
                <div>
                  <label htmlFor="reset-identifier" className="block font-semibold text-gray-700 mb-1">
                    Work Email or Username <span className="text-red-500">*</span>
                  </label>
                  <input
                    id="reset-identifier"
                    type="text"
                    required
                    placeholder="e.g. admin@example.com or admin"
                    value={resetIdent}
                    onChange={(e) => setResetIdent(e.target.value)}
                    className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm text-gray-900 focus:ring-2 focus:ring-blue-500 focus:outline-none"
                  />
                </div>

                <div className="flex gap-2 pt-2">
                  <Button
                    type="button"
                    variant="outline"
                    className="flex-1 justify-center"
                    onClick={() => setShowResetModal(false)}
                  >
                    Cancel
                  </Button>
                  <Button
                    type="submit"
                    variant="primary"
                    className="flex-1 justify-center"
                    isLoading={resetLoading}
                    disabled={resetLoading}
                  >
                    Send Reset Link
                  </Button>
                </div>
              </form>
            ) : (
              <div className="space-y-4 text-xs">
                {demoResetLink && isDemoMode ? (
                  <div className="p-4 bg-blue-50 border border-blue-200 rounded-xl space-y-3">
                    <div className="flex items-center gap-2 text-blue-900 font-bold">
                      <ExternalLink className="w-4 h-4 text-blue-600" />
                      <span>Development Test Recovery Link Available</span>
                    </div>
                    <p className="text-gray-600">
                      In this local development environment without active SMTP delivery, you can navigate directly to the token-authenticated reset page:
                    </p>
                    <Button
                      type="button"
                      variant="primary"
                      className="w-full justify-center"
                      onClick={() => {
                        setShowResetModal(false);
                        navigate(demoResetLink);
                      }}
                      rightIcon={<ArrowRight className="w-4 h-4" />}
                    >
                      Open Password Reset Link Directly
                    </Button>
                  </div>
                ) : (
                  <div className="p-4 bg-slate-50 border border-slate-200 rounded-xl space-y-2">
                    <p className="text-gray-600 leading-relaxed">
                      Please check your inbox for instructions to reset your password. The link is valid for 1 hour.
                    </p>
                  </div>
                )}

                <div className="flex justify-end pt-2">
                  <Button
                    type="button"
                    variant="outline"
                    className="justify-center"
                    onClick={() => setShowResetModal(false)}
                  >
                    Close
                  </Button>
                </div>
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
