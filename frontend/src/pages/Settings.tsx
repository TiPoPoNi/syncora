import React, { useState, useEffect } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import {
  User, Sun, Moon, Monitor, Globe, Bell, Eye, Shield, Check,
  AlertCircle, Clock, CheckCircle2, XCircle, AlertTriangle, ArrowRight, RefreshCw, Sparkles, Send
} from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import { useTheme, ThemeMode, TextSize, DisplayDensity, UserPreferences } from '../context/ThemeContext';
import { Button, Input, Alert, Modal, Badge } from '../components/ui';

interface UserProfileData {
  userId: number;
  username: string;
  email: string;
  fullName: string | null;
  phone: string | null;
  jobTitle: string | null;
  department: string | null;
  avatarUrl: string | null;
  employeeId: string | null;
  organizationId: number | null;
  organizationName: string | null;
  role: string | null;
  hasPendingChangeRequest: boolean;
  pendingChangeRequestId: number | null;
}

interface ProfileChangeRequest {
  id: number;
  userId: number;
  username: string;
  employeeName: string;
  employeeId: string;
  organizationId: number;
  organizationName: string;
  proposedFullName: string | null;
  proposedPhone: string | null;
  proposedJobTitle: string | null;
  proposedDepartment: string | null;
  proposedWorkEmail: string | null;
  proposedAvatarUrl: string | null;
  existingFullName: string | null;
  existingPhone: string | null;
  existingJobTitle: string | null;
  existingDepartment: string | null;
  existingWorkEmail: string | null;
  existingAvatarUrl: string | null;
  status: 'PENDING' | 'APPROVED' | 'REJECTED' | 'WITHDRAWN' | 'STALE';
  emailVerified: boolean;
  submissionComment: string | null;
  reviewComment: string | null;
  reviewedByUserId: number | null;
  reviewedByUsername: string | null;
  createdAt: string;
  reviewedAt: string | null;
}

export function Settings() {
  const { t, i18n } = useTranslation();
  const { user, token } = useAuth();
  const { preferences, updatePreferences, restoreDefaults, resolvedTheme } = useTheme();
  const location = useLocation();
  const navigate = useNavigate();

  // Tab navigation
  const queryParams = new URLSearchParams(location.search);
  const initialTab = queryParams.get('tab') || 'profile';
  const [activeTab, setActiveTab] = useState<string>(initialTab);

  // Profile data & form state
  const [profile, setProfile] = useState<UserProfileData | null>(null);
  const [profileLoading, setProfileLoading] = useState(true);
  const [profileError, setProfileError] = useState<string | null>(null);
  const [profileSuccess, setProfileSuccess] = useState<string | null>(null);

  // Form edit fields
  const [formData, setFormData] = useState({
    fullName: '',
    phone: '',
    jobTitle: '',
    department: '',
    workEmail: '',
    avatarUrl: '',
    submissionComment: '',
  });

  // Review modal state
  const [showReviewModal, setShowReviewModal] = useState(false);
  const [submittingRequest, setSubmittingRequest] = useState(false);

  // Request history
  const [requests, setRequests] = useState<ProfileChangeRequest[]>([]);
  const [loadingRequests, setLoadingRequests] = useState(false);

  // Email verification helper state (development & token flow)
  const [verifyTokenInput, setVerifyTokenInput] = useState('');
  const [verifyingRequestId, setVerifyingRequestId] = useState<number | null>(null);
  const [verifyError, setVerifyError] = useState<string | null>(null);
  const [verifySuccess, setVerifySuccess] = useState<string | null>(null);

  // Preferences local editing state (for unsaved preview)
  const [prefForm, setPrefForm] = useState<UserPreferences>(preferences);
  const [prefSaving, setPrefSaving] = useState(false);
  const [prefSuccess, setPrefSuccess] = useState(false);

  // Account security (change password) state
  const [securityForm, setSecurityForm] = useState({
    currentPassword: '',
    newPassword: '',
    confirmPassword: '',
  });
  const [securityLoading, setSecurityLoading] = useState(false);
  const [securitySuccess, setSecuritySuccess] = useState<string | null>(null);
  const [securityError, setSecurityError] = useState<string | null>(null);

  useEffect(() => {
    setPrefForm(preferences);
  }, [preferences]);

  // Sync tab with URL
  useEffect(() => {
    const tab = queryParams.get('tab');
    if (tab && tab !== activeTab) {
      setActiveTab(tab);
    }
  }, [location.search]);

  const handleTabChange = (tab: string) => {
    setActiveTab(tab);
    navigate(`/settings?tab=${tab}`, { replace: true });
  };

  // Fetch official profile
  const fetchProfile = async () => {
    if (!token) return;
    setProfileLoading(true);
    try {
      const res = await fetch('/api/users/me/profile', {
        headers: { Authorization: `Bearer ${token}` },
      });
      if (res.ok) {
        const data: UserProfileData = await res.json();
        setProfile(data);
        setFormData({
          fullName: data.fullName || '',
          phone: data.phone || '',
          jobTitle: data.jobTitle || '',
          department: data.department || '',
          workEmail: data.email || '',
          avatarUrl: data.avatarUrl || '',
          submissionComment: '',
        });
      } else {
        setProfileError('Failed to load user profile.');
      }
    } catch (err) {
      setProfileError('An unexpected error occurred while loading profile.');
    } finally {
      setProfileLoading(false);
    }
  };

  // Fetch change request history
  const fetchRequests = async () => {
    if (!token) return;
    setLoadingRequests(true);
    try {
      const res = await fetch('/api/users/me/profile/change-requests', {
        headers: { Authorization: `Bearer ${token}` },
      });
      if (res.ok) {
        const data = await res.json();
        setRequests(data);
      }
    } catch (err) {
      console.error('Failed to load profile change requests:', err);
    } finally {
      setLoadingRequests(false);
    }
  };

  useEffect(() => {
    fetchProfile();
    fetchRequests();
  }, [token]);

  // Detect differences
  const getDifferences = () => {
    if (!profile) return [];
    const diffs: { field: string; label: string; current: string; proposed: string }[] = [];

    if (formData.fullName.trim() !== (profile.fullName || '').trim()) {
      diffs.push({
        field: 'fullName',
        label: t('settings.profile.fullName'),
        current: profile.fullName || '—',
        proposed: formData.fullName.trim() || '—',
      });
    }
    if (formData.phone.trim() !== (profile.phone || '').trim()) {
      diffs.push({
        field: 'phone',
        label: t('settings.profile.phone'),
        current: profile.phone || '—',
        proposed: formData.phone.trim() || '—',
      });
    }
    if (formData.jobTitle.trim() !== (profile.jobTitle || '').trim()) {
      diffs.push({
        field: 'jobTitle',
        label: t('settings.profile.jobTitle'),
        current: profile.jobTitle || '—',
        proposed: formData.jobTitle.trim() || '—',
      });
    }
    if (formData.department.trim() !== (profile.department || '').trim()) {
      diffs.push({
        field: 'department',
        label: t('settings.profile.department'),
        current: profile.department || '—',
        proposed: formData.department.trim() || '—',
      });
    }
    if (formData.workEmail.trim() !== (profile.email || '').trim()) {
      diffs.push({
        field: 'workEmail',
        label: t('settings.profile.workEmail'),
        current: profile.email || '—',
        proposed: formData.workEmail.trim() || '—',
      });
    }
    if (formData.avatarUrl.trim() !== (profile.avatarUrl || '').trim()) {
      diffs.push({
        field: 'avatarUrl',
        label: 'Avatar URL',
        current: profile.avatarUrl || '—',
        proposed: formData.avatarUrl.trim() || '—',
      });
    }
    return diffs;
  };

  const diffs = getDifferences();
  const hasChanges = diffs.length > 0;

  // Submit profile change request
  const handleSubmitProfileChanges = async () => {
    if (!token || !hasChanges) return;
    setSubmittingRequest(true);
    setProfileError(null);
    setProfileSuccess(null);

    try {
      const res = await fetch('/api/users/me/profile/change-requests', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${token}`,
        },
        body: JSON.stringify({
          proposedFullName: formData.fullName.trim() || null,
          proposedPhone: formData.phone.trim() || null,
          proposedJobTitle: formData.jobTitle.trim() || null,
          proposedDepartment: formData.department.trim() || null,
          proposedWorkEmail: formData.workEmail.trim() || null,
          proposedAvatarUrl: formData.avatarUrl.trim() || null,
          submissionComment: formData.submissionComment.trim() || null,
        }),
      });

      const data = await res.json();
      if (res.ok) {
        setProfileSuccess(t('settings.profile.successSubmitted'));
        setShowReviewModal(false);
        fetchProfile();
        fetchRequests();
      } else {
        setProfileError(data.message || 'Failed to submit profile changes.');
      }
    } catch (err) {
      setProfileError('Network error while submitting change request.');
    } finally {
      setSubmittingRequest(false);
    }
  };

  // Verify email token helper
  const handleVerifyEmail = async (requestId: number) => {
    if (!verifyTokenInput.trim() || !token) return;
    setVerifyError(null);
    setVerifySuccess(null);
    try {
      const res = await fetch(`/api/profile-change-requests/${requestId}/verify-email`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${token}`,
        },
        body: JSON.stringify({ token: verifyTokenInput.trim() }),
      });
      const data = await res.json();
      if (res.ok) {
        setVerifySuccess('Email address verified successfully. It can now be approved by the organization administrator.');
        setVerifyTokenInput('');
        fetchRequests();
      } else {
        setVerifyError(data.message || 'Verification token invalid or expired.');
      }
    } catch (err) {
      setVerifyError('Error submitting verification token.');
    }
  };

  // Save general preferences
  const handleSavePreferences = async (sectionUpdates?: Partial<UserPreferences>) => {
    const toSave = sectionUpdates || prefForm;
    setPrefSaving(true);
    setPrefSuccess(false);
    try {
      await updatePreferences(toSave);
      setPrefSuccess(true);
      setTimeout(() => setPrefSuccess(false), 3000);
    } catch (err) {
      console.error('Error saving preferences:', err);
    } finally {
      setPrefSaving(false);
    }
  };

  // Handle Password Change
  const handleChangePassword = async (e: React.FormEvent) => {
    e.preventDefault();
    setSecurityError(null);
    setSecuritySuccess(null);

    if (securityForm.newPassword !== securityForm.confirmPassword) {
      setSecurityError('New password and confirm password do not match.');
      return;
    }

    setSecurityLoading(true);
    try {
      const res = await fetch('/api/auth/change-password', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${token}`,
        },
        body: JSON.stringify(securityForm),
      });
      const data = await res.json();
      if (res.ok) {
        setSecuritySuccess(data.message || 'Password changed successfully.');
        setSecurityForm({ currentPassword: '', newPassword: '', confirmPassword: '' });
      } else {
        setSecurityError(data.message || 'Failed to change password.');
      }
    } catch (err) {
      setSecurityError('Network error while updating password.');
    } finally {
      setSecurityLoading(false);
    }
  };

  const getStatusBadge = (status: string) => {
    switch (status) {
      case 'PENDING':
        return <Badge variant="warning"><Clock className="w-3.5 h-3.5 inline mr-1" />{t('status.pending')}</Badge>;
      case 'APPROVED':
        return <Badge variant="success"><CheckCircle2 className="w-3.5 h-3.5 inline mr-1" />{t('status.approved')}</Badge>;
      case 'REJECTED':
        return <Badge variant="danger"><XCircle className="w-3.5 h-3.5 inline mr-1" />{t('status.rejected')}</Badge>;
      case 'STALE':
        return <Badge variant="neutral"><AlertTriangle className="w-3.5 h-3.5 inline mr-1" />{t('status.stale')}</Badge>;
      default:
        return <Badge variant="neutral">{status}</Badge>;
    }
  };

  return (
    <div className="max-w-6xl mx-auto space-y-6">
      {/* Header */}
      <div className="bg-white dark:bg-slate-900 rounded-xl p-6 shadow-sm border border-gray-200 dark:border-slate-800 flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-gray-900 dark:text-white flex items-center gap-2">
            <Sparkles className="w-6 h-6 text-blue-600 dark:text-blue-400" />
            {t('settings.title')}
          </h1>
          <p className="text-sm text-gray-500 dark:text-slate-400 mt-1">
            {t('settings.subtitle')}
          </p>
        </div>

        {profile && (
          <div className="flex items-center gap-3 bg-gray-50 dark:bg-slate-800/60 px-4 py-2 rounded-lg border border-gray-200/60 dark:border-slate-700/60">
            <div className="w-10 h-10 rounded-full bg-blue-600 text-white font-bold flex items-center justify-center text-sm shadow-sm">
              {profile.fullName ? profile.fullName.charAt(0).toUpperCase() : profile.username.charAt(0).toUpperCase()}
            </div>
            <div>
              <div className="text-sm font-semibold text-gray-900 dark:text-white">
                {profile.fullName || profile.username}
              </div>
              <div className="text-xs text-gray-500 dark:text-slate-400 flex items-center gap-2">
                <span>{profile.role || 'Member'}</span>
                {profile.employeeId && (
                  <span className="font-mono bg-blue-100 dark:bg-blue-900/40 text-blue-700 dark:text-blue-300 px-1.5 py-0.5 rounded text-[10px]">
                    {profile.employeeId}
                  </span>
                )}
              </div>
            </div>
          </div>
        )}
      </div>

      {/* Main Grid: Sidebar Tabs + Content */}
      <div className="grid grid-cols-1 md:grid-cols-4 gap-6">
        {/* Navigation Tabs */}
        <div className="md:col-span-1 space-y-1">
          <div className="bg-white dark:bg-slate-900 rounded-xl p-2 shadow-sm border border-gray-200 dark:border-slate-800 space-y-1">
            <button
              onClick={() => handleTabChange('profile')}
              className={`w-full flex items-center gap-3 px-3.5 py-2.5 rounded-lg text-sm font-medium transition-colors ${
                activeTab === 'profile'
                  ? 'bg-blue-50 dark:bg-blue-950/40 text-blue-600 dark:text-blue-400 font-semibold'
                  : 'text-gray-600 dark:text-slate-300 hover:bg-gray-50 dark:hover:bg-slate-800/60'
              }`}
            >
              <User className="w-4 h-4" />
              <span>{t('settings.tabs.profile')}</span>
              {profile?.hasPendingChangeRequest && (
                <span className="ml-auto w-2 h-2 rounded-full bg-amber-500 animate-pulse" />
              )}
            </button>

            <button
              onClick={() => handleTabChange('appearance')}
              className={`w-full flex items-center gap-3 px-3.5 py-2.5 rounded-lg text-sm font-medium transition-colors ${
                activeTab === 'appearance'
                  ? 'bg-blue-50 dark:bg-blue-950/40 text-blue-600 dark:text-blue-400 font-semibold'
                  : 'text-gray-600 dark:text-slate-300 hover:bg-gray-50 dark:hover:bg-slate-800/60'
              }`}
            >
              <Sun className="w-4 h-4" />
              <span>{t('settings.tabs.appearance')}</span>
            </button>

            <button
              onClick={() => handleTabChange('localization')}
              className={`w-full flex items-center gap-3 px-3.5 py-2.5 rounded-lg text-sm font-medium transition-colors ${
                activeTab === 'localization'
                  ? 'bg-blue-50 dark:bg-blue-950/40 text-blue-600 dark:text-blue-400 font-semibold'
                  : 'text-gray-600 dark:text-slate-300 hover:bg-gray-50 dark:hover:bg-slate-800/60'
              }`}
            >
              <Globe className="w-4 h-4" />
              <span>{t('settings.tabs.localization')}</span>
            </button>

            <button
              onClick={() => handleTabChange('notifications')}
              className={`w-full flex items-center gap-3 px-3.5 py-2.5 rounded-lg text-sm font-medium transition-colors ${
                activeTab === 'notifications'
                  ? 'bg-blue-50 dark:bg-blue-950/40 text-blue-600 dark:text-blue-400 font-semibold'
                  : 'text-gray-600 dark:text-slate-300 hover:bg-gray-50 dark:hover:bg-slate-800/60'
              }`}
            >
              <Bell className="w-4 h-4" />
              <span>{t('settings.tabs.notifications')}</span>
            </button>

            <button
              onClick={() => handleTabChange('accessibility')}
              className={`w-full flex items-center gap-3 px-3.5 py-2.5 rounded-lg text-sm font-medium transition-colors ${
                activeTab === 'accessibility'
                  ? 'bg-blue-50 dark:bg-blue-950/40 text-blue-600 dark:text-blue-400 font-semibold'
                  : 'text-gray-600 dark:text-slate-300 hover:bg-gray-50 dark:hover:bg-slate-800/60'
              }`}
            >
              <Eye className="w-4 h-4" />
              <span>{t('settings.tabs.accessibility')}</span>
            </button>

            <button
              onClick={() => handleTabChange('security')}
              className={`w-full flex items-center gap-3 px-3.5 py-2.5 rounded-lg text-sm font-medium transition-colors ${
                activeTab === 'security'
                  ? 'bg-blue-50 dark:bg-blue-950/40 text-blue-600 dark:text-blue-400 font-semibold'
                  : 'text-gray-600 dark:text-slate-300 hover:bg-gray-50 dark:hover:bg-slate-800/60'
              }`}
            >
              <Shield className="w-4 h-4" />
              <span>{t('settings.tabs.security')}</span>
            </button>
          </div>

          <div className="bg-gray-50 dark:bg-slate-900/60 p-4 rounded-xl border border-gray-200/80 dark:border-slate-800 text-xs text-gray-500 dark:text-slate-400 space-y-2">
            <div className="font-semibold text-gray-700 dark:text-slate-300">Organization Security Policy</div>
            <p>
              Official employee details require administrative review. Personal theme, localization, and density settings apply immediately without approval.
            </p>
          </div>
        </div>

        {/* Tab Content Area */}
        <div className="md:col-span-3">
          {/* TAB 1: MY PROFILE */}
          {activeTab === 'profile' && (
            <div className="space-y-6">
              <div className="bg-white dark:bg-slate-900 rounded-xl p-6 shadow-sm border border-gray-200 dark:border-slate-800 space-y-6">
                <div>
                  <h2 className="text-lg font-bold text-gray-900 dark:text-white flex items-center justify-between">
                    <span>{t('settings.profile.title')}</span>
                    {profile?.hasPendingChangeRequest && (
                      <Badge variant="warning">
                        <Clock className="w-3.5 h-3.5 inline mr-1" />
                        {t('settings.profile.pendingNotice')}
                      </Badge>
                    )}
                  </h2>
                  <p className="text-sm text-gray-500 dark:text-slate-400 mt-1">
                    {t('settings.profile.desc')}
                  </p>
                </div>

                {profileSuccess && (
                  <Alert type="success" title="Success" onClose={() => setProfileSuccess(null)}>
                    {profileSuccess}
                  </Alert>
                )}

                {profileError && (
                  <Alert type="error" title="Error" onClose={() => setProfileError(null)}>
                    {profileError}
                  </Alert>
                )}

                {profileLoading ? (
                  <div className="py-12 flex items-center justify-center text-gray-500">
                    <RefreshCw className="w-6 h-6 animate-spin text-blue-600" />
                  </div>
                ) : (
                  <div className="space-y-6">
                    {/* Administrator-controlled read-only fields banner */}
                    <div className="bg-blue-50/60 dark:bg-blue-950/20 border border-blue-200/60 dark:border-blue-900/40 rounded-lg p-4 grid grid-cols-1 md:grid-cols-3 gap-4 text-xs">
                      <div>
                        <span className="text-gray-500 dark:text-slate-400 block font-medium">
                          {t('settings.profile.employeeId')}
                        </span>
                        <span className="font-mono text-sm font-bold text-blue-900 dark:text-blue-300">
                          {profile?.employeeId || 'NOT_ASSIGNED'}
                        </span>
                      </div>
                      <div>
                        <span className="text-gray-500 dark:text-slate-400 block font-medium">
                          {t('settings.profile.organization')}
                        </span>
                        <span className="text-sm font-semibold text-gray-800 dark:text-slate-200">
                          {profile?.organizationName || 'Default Workspace'}
                        </span>
                      </div>
                      <div>
                        <span className="text-gray-500 dark:text-slate-400 block font-medium">
                          {t('settings.profile.systemRole')}
                        </span>
                        <span className="text-sm font-semibold text-gray-800 dark:text-slate-200">
                          {profile?.role || 'WORKER'}
                        </span>
                      </div>
                    </div>

                    {/* Editable fields */}
                    <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                      <Input
                        label={t('settings.profile.fullName')}
                        value={formData.fullName}
                        onChange={(e) => setFormData({ ...formData, fullName: e.target.value })}
                        placeholder="e.g. John Doe"
                      />

                      <Input
                        label={t('settings.profile.workEmail')}
                        type="email"
                        value={formData.workEmail}
                        onChange={(e) => setFormData({ ...formData, workEmail: e.target.value })}
                        placeholder="john.doe@organization.com"
                        helperText="Modifying work email requires token verification before administrator approval."
                      />

                      <Input
                        label={t('settings.profile.phone')}
                        value={formData.phone}
                        onChange={(e) => setFormData({ ...formData, phone: e.target.value })}
                        placeholder="+1 (555) 000-0000"
                      />

                      <Input
                        label={t('settings.profile.jobTitle')}
                        value={formData.jobTitle}
                        onChange={(e) => setFormData({ ...formData, jobTitle: e.target.value })}
                        placeholder="e.g. Senior Project Manager"
                      />

                      <Input
                        label={t('settings.profile.department')}
                        value={formData.department}
                        onChange={(e) => setFormData({ ...formData, department: e.target.value })}
                        placeholder="e.g. Engineering & Operations"
                      />

                      <Input
                        label="Avatar Image URL (Optional)"
                        value={formData.avatarUrl}
                        onChange={(e) => setFormData({ ...formData, avatarUrl: e.target.value })}
                        placeholder="https://images.example.com/avatar.jpg"
                      />
                    </div>

                    {/* Pending Request Status Bar if active */}
                    {profile?.hasPendingChangeRequest && (
                      <div className="bg-amber-50 dark:bg-amber-950/30 border border-amber-200 dark:border-amber-900/50 rounded-lg p-4 flex items-start gap-3">
                        <AlertCircle className="w-5 h-5 text-amber-600 dark:text-amber-400 shrink-0 mt-0.5" />
                        <div className="text-xs text-amber-800 dark:text-amber-300">
                          <span className="font-semibold block">Pending Approval Active</span>
                          You already have a profile change request pending review (#{profile.pendingChangeRequestId}). Official values will only update after organization administrator approval.
                        </div>
                      </div>
                    )}

                    {/* Action buttons */}
                    <div className="flex items-center justify-between pt-4 border-t border-gray-200 dark:border-slate-800">
                      <div className="text-xs text-gray-500 dark:text-slate-400">
                        {hasChanges ? (
                          <span className="text-blue-600 dark:text-blue-400 font-medium">
                            {diffs.length} field(s) modified
                          </span>
                        ) : (
                          t('settings.profile.noChanges')
                        )}
                      </div>

                      <div className="flex items-center gap-3">
                        <Button
                          variant="outline"
                          disabled={!hasChanges}
                          onClick={() => {
                            if (profile) {
                              setFormData({
                                fullName: profile.fullName || '',
                                phone: profile.phone || '',
                                jobTitle: profile.jobTitle || '',
                                department: profile.department || '',
                                workEmail: profile.email || '',
                                avatarUrl: profile.avatarUrl || '',
                                submissionComment: '',
                              });
                            }
                          }}
                        >
                          {t('settings.actions.cancel')}
                        </Button>

                        <Button
                          variant="primary"
                          disabled={!hasChanges || profile?.hasPendingChangeRequest}
                          onClick={() => setShowReviewModal(true)}
                          leftIcon={<Send className="w-4 h-4" />}
                        >
                          {t('settings.profile.submitForApproval')}
                        </Button>
                      </div>
                    </div>
                  </div>
                )}
              </div>

              {/* Request History Section */}
              <div className="bg-white dark:bg-slate-900 rounded-xl p-6 shadow-sm border border-gray-200 dark:border-slate-800 space-y-4">
                <div className="flex items-center justify-between">
                  <h3 className="text-base font-bold text-gray-900 dark:text-white flex items-center gap-2">
                    <Clock className="w-5 h-5 text-gray-400" />
                    {t('settings.profile.requestHistoryTitle')}
                  </h3>
                  <Button variant="ghost" size="sm" onClick={fetchRequests} leftIcon={<RefreshCw className="w-4 h-4" />}>
                    {t('common.refresh')}
                  </Button>
                </div>

                {loadingRequests ? (
                  <div className="py-8 text-center text-gray-400">Loading history...</div>
                ) : requests.length === 0 ? (
                  <div className="text-center py-6 text-sm text-gray-400">
                    No profile change requests found.
                  </div>
                ) : (
                  <div className="overflow-x-auto">
                    <table className="w-full text-left text-xs border-collapse">
                      <thead>
                        <tr className="border-b border-gray-200 dark:border-slate-800 text-gray-400 font-medium">
                          <th className="py-2.5 px-3">Req #</th>
                          <th className="py-2.5 px-3">Submitted</th>
                          <th className="py-2.5 px-3">Proposed Changes</th>
                          <th className="py-2.5 px-3">Status</th>
                          <th className="py-2.5 px-3">Reviewer Details</th>
                          <th className="py-2.5 px-3 text-right">Actions</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-gray-100 dark:divide-slate-800/60">
                        {requests.map((req) => (
                          <tr key={req.id} className="hover:bg-gray-50/50 dark:hover:bg-slate-800/40">
                            <td className="py-3 px-3 font-mono font-medium text-gray-700 dark:text-slate-300">
                              #{req.id}
                            </td>
                            <td className="py-3 px-3 text-gray-500 dark:text-slate-400 whitespace-nowrap">
                              {new Date(req.createdAt).toLocaleDateString()}
                            </td>
                            <td className="py-3 px-3">
                              <div className="space-y-0.5">
                                {req.proposedFullName && req.proposedFullName !== req.existingFullName && (
                                  <div><span className="text-gray-400">Name:</span> <span className="font-medium text-gray-800 dark:text-slate-200">{req.proposedFullName}</span></div>
                                )}
                                {req.proposedJobTitle && req.proposedJobTitle !== req.existingJobTitle && (
                                  <div><span className="text-gray-400">Title:</span> <span className="font-medium text-gray-800 dark:text-slate-200">{req.proposedJobTitle}</span></div>
                                )}
                                {req.proposedDepartment && req.proposedDepartment !== req.existingDepartment && (
                                  <div><span className="text-gray-400">Dept:</span> <span className="font-medium text-gray-800 dark:text-slate-200">{req.proposedDepartment}</span></div>
                                )}
                                {req.proposedPhone && req.proposedPhone !== req.existingPhone && (
                                  <div><span className="text-gray-400">Phone:</span> <span className="font-medium text-gray-800 dark:text-slate-200">{req.proposedPhone}</span></div>
                                )}
                                {req.proposedWorkEmail && req.proposedWorkEmail !== req.existingWorkEmail && (
                                  <div className="flex items-center gap-1.5">
                                    <span className="text-gray-400">Email:</span>
                                    <span className="font-medium text-gray-800 dark:text-slate-200">{req.proposedWorkEmail}</span>
                                    {req.emailVerified ? (
                                      <span className="text-[10px] bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-300 px-1 rounded">Verified</span>
                                    ) : (
                                      <span className="text-[10px] bg-amber-100 text-amber-700 dark:bg-amber-900/30 dark:text-amber-300 px-1 rounded">Pending Verify</span>
                                    )}
                                  </div>
                                )}
                              </div>
                            </td>
                            <td className="py-3 px-3 whitespace-nowrap">
                              {getStatusBadge(req.status)}
                            </td>
                            <td className="py-3 px-3">
                              {req.reviewedByUsername ? (
                                <div>
                                  <div className="text-gray-700 dark:text-slate-300 font-medium">{req.reviewedByUsername}</div>
                                  {req.reviewComment && (
                                    <div className="text-[11px] text-gray-500 italic mt-0.5">"{req.reviewComment}"</div>
                                  )}
                                </div>
                              ) : (
                                <span className="text-gray-400 italic">Awaiting review</span>
                              )}
                            </td>
                            <td className="py-3 px-3 text-right">
                              {req.proposedWorkEmail && !req.emailVerified && req.status === 'PENDING' && (
                                <Button
                                  size="sm"
                                  variant="outline"
                                  onClick={() => setVerifyingRequestId(req.id)}
                                >
                                  Verify Email
                                </Button>
                              )}
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
              </div>
            </div>
          )}

          {/* TAB 2: APPEARANCE */}
          {activeTab === 'appearance' && (
            <div className="bg-white dark:bg-slate-900 rounded-xl p-6 shadow-sm border border-gray-200 dark:border-slate-800 space-y-6">
              <div>
                <h2 className="text-lg font-bold text-gray-900 dark:text-white">
                  {t('settings.appearance.title')}
                </h2>
                <p className="text-sm text-gray-500 dark:text-slate-400 mt-1">
                  {t('settings.appearance.desc')}
                </p>
              </div>

              {prefSuccess && (
                <Alert type="success" title="Saved">
                  {t('settings.actions.savedSuccess')}
                </Alert>
              )}

              <div className="space-y-4">
                <label className="text-sm font-semibold text-gray-700 dark:text-slate-300 block">
                  {t('settings.appearance.themeMode')}
                </label>

                <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                  {/* LIGHT */}
                  <div
                    onClick={() => handleSavePreferences({ theme: 'LIGHT' })}
                    className={`cursor-pointer rounded-xl p-5 border-2 transition-all ${
                      preferences.theme === 'LIGHT'
                        ? 'border-blue-600 bg-blue-50/40 dark:bg-blue-950/20 shadow-sm'
                        : 'border-gray-200 dark:border-slate-800 hover:border-gray-300 dark:hover:border-slate-700'
                    }`}
                  >
                    <div className="flex items-center justify-between mb-3">
                      <Sun className="w-6 h-6 text-amber-500" />
                      {preferences.theme === 'LIGHT' && <Check className="w-5 h-5 text-blue-600" />}
                    </div>
                    <div className="font-bold text-sm text-gray-900 dark:text-white">
                      {t('settings.appearance.light')}
                    </div>
                    <div className="text-xs text-gray-500 dark:text-slate-400 mt-1">
                      {t('settings.appearance.lightDesc')}
                    </div>
                  </div>

                  {/* DARK */}
                  <div
                    onClick={() => handleSavePreferences({ theme: 'DARK' })}
                    className={`cursor-pointer rounded-xl p-5 border-2 transition-all ${
                      preferences.theme === 'DARK'
                        ? 'border-blue-600 bg-blue-50/40 dark:bg-blue-950/20 shadow-sm'
                        : 'border-gray-200 dark:border-slate-800 hover:border-gray-300 dark:hover:border-slate-700'
                    }`}
                  >
                    <div className="flex items-center justify-between mb-3">
                      <Moon className="w-6 h-6 text-indigo-400" />
                      {preferences.theme === 'DARK' && <Check className="w-5 h-5 text-blue-600" />}
                    </div>
                    <div className="font-bold text-sm text-gray-900 dark:text-white">
                      {t('settings.appearance.dark')}
                    </div>
                    <div className="text-xs text-gray-500 dark:text-slate-400 mt-1">
                      {t('settings.appearance.darkDesc')}
                    </div>
                  </div>

                  {/* SYSTEM */}
                  <div
                    onClick={() => handleSavePreferences({ theme: 'SYSTEM' })}
                    className={`cursor-pointer rounded-xl p-5 border-2 transition-all ${
                      preferences.theme === 'SYSTEM'
                        ? 'border-blue-600 bg-blue-50/40 dark:bg-blue-950/20 shadow-sm'
                        : 'border-gray-200 dark:border-slate-800 hover:border-gray-300 dark:hover:border-slate-700'
                    }`}
                  >
                    <div className="flex items-center justify-between mb-3">
                      <Monitor className="w-6 h-6 text-slate-500" />
                      {preferences.theme === 'SYSTEM' && <Check className="w-5 h-5 text-blue-600" />}
                    </div>
                    <div className="font-bold text-sm text-gray-900 dark:text-white">
                      {t('settings.appearance.system')}
                    </div>
                    <div className="text-xs text-gray-500 dark:text-slate-400 mt-1">
                      {t('settings.appearance.systemDesc')}
                    </div>
                  </div>
                </div>
              </div>

              <div className="pt-4 border-t border-gray-200 dark:border-slate-800 flex justify-between items-center text-xs text-gray-500">
                <span>Active theme applied: <strong className="uppercase">{resolvedTheme}</strong></span>
                <Button variant="outline" size="sm" onClick={restoreDefaults}>
                  {t('settings.actions.restoreDefaults')}
                </Button>
              </div>
            </div>
          )}

          {/* TAB 3: LANGUAGE & REGIONAL */}
          {activeTab === 'localization' && (
            <div className="bg-white dark:bg-slate-900 rounded-xl p-6 shadow-sm border border-gray-200 dark:border-slate-800 space-y-6">
              <div>
                <h2 className="text-lg font-bold text-gray-900 dark:text-white">
                  {t('settings.localization.title')}
                </h2>
                <p className="text-sm text-gray-500 dark:text-slate-400 mt-1">
                  {t('settings.localization.desc')}
                </p>
              </div>

              {prefSuccess && (
                <Alert type="success" title="Saved">
                  {t('settings.actions.savedSuccess')}
                </Alert>
              )}

              <div className="grid grid-cols-1 md:grid-cols-2 gap-5">
                <div>
                  <label className="text-xs font-semibold text-gray-700 dark:text-slate-300 block mb-1.5">
                    {t('settings.localization.language')}
                  </label>
                  <select
                    value={preferences.language}
                    onChange={(e) => handleSavePreferences({ language: e.target.value })}
                    className="w-full px-3 py-2 bg-white dark:bg-slate-800 border border-gray-300 dark:border-slate-700 rounded-lg text-sm text-gray-900 dark:text-white focus:ring-2 focus:ring-blue-500"
                  >
                    <option value="en">English (English)</option>
                    <option value="hi">हिंदी (Hindi)</option>
                  </select>
                </div>

                <div>
                  <label className="text-xs font-semibold text-gray-700 dark:text-slate-300 block mb-1.5">
                    {t('settings.localization.timezone')}
                  </label>
                  <select
                    value={preferences.timezone}
                    onChange={(e) => handleSavePreferences({ timezone: e.target.value })}
                    className="w-full px-3 py-2 bg-white dark:bg-slate-800 border border-gray-300 dark:border-slate-700 rounded-lg text-sm text-gray-900 dark:text-white focus:ring-2 focus:ring-blue-500"
                  >
                    <option value="UTC">UTC (Universal Coordinated Time)</option>
                    <option value="America/New_York">America/New_York (EST/EDT)</option>
                    <option value="America/Los_Angeles">America/Los_Angeles (PST/PDT)</option>
                    <option value="Europe/London">Europe/London (GMT/BST)</option>
                    <option value="Asia/Kolkata">Asia/Kolkata (IST - +05:30)</option>
                    <option value="Asia/Tokyo">Asia/Tokyo (JST)</option>
                  </select>
                </div>

                <div>
                  <label className="text-xs font-semibold text-gray-700 dark:text-slate-300 block mb-1.5">
                    {t('settings.localization.dateFormat')}
                  </label>
                  <select
                    value={preferences.dateFormat}
                    onChange={(e) => handleSavePreferences({ dateFormat: e.target.value })}
                    className="w-full px-3 py-2 bg-white dark:bg-slate-800 border border-gray-300 dark:border-slate-700 rounded-lg text-sm text-gray-900 dark:text-white focus:ring-2 focus:ring-blue-500"
                  >
                    <option value="YYYY-MM-DD">YYYY-MM-DD (2026-10-22)</option>
                    <option value="DD/MM/YYYY">DD/MM/YYYY (22/10/2026)</option>
                    <option value="MM/DD/YYYY">MM/DD/YYYY (10/22/2026)</option>
                  </select>
                </div>

                <div>
                  <label className="text-xs font-semibold text-gray-700 dark:text-slate-300 block mb-1.5">
                    {t('settings.localization.firstDayOfWeek')}
                  </label>
                  <select
                    value={preferences.firstDayOfWeek}
                    onChange={(e) => handleSavePreferences({ firstDayOfWeek: e.target.value })}
                    className="w-full px-3 py-2 bg-white dark:bg-slate-800 border border-gray-300 dark:border-slate-700 rounded-lg text-sm text-gray-900 dark:text-white focus:ring-2 focus:ring-blue-500"
                  >
                    <option value="MONDAY">{t('settings.localization.monday')}</option>
                    <option value="SUNDAY">{t('settings.localization.sunday')}</option>
                    <option value="SATURDAY">{t('settings.localization.saturday')}</option>
                  </select>
                </div>
              </div>

              <div className="pt-4 border-t border-gray-200 dark:border-slate-800 flex justify-end">
                <Button variant="outline" size="sm" onClick={restoreDefaults}>
                  {t('settings.actions.restoreDefaults')}
                </Button>
              </div>
            </div>
          )}

          {/* TAB 4: NOTIFICATIONS */}
          {activeTab === 'notifications' && (
            <div className="bg-white dark:bg-slate-900 rounded-xl p-6 shadow-sm border border-gray-200 dark:border-slate-800 space-y-6">
              <div>
                <h2 className="text-lg font-bold text-gray-900 dark:text-white">
                  {t('settings.notifications.title')}
                </h2>
                <p className="text-sm text-gray-500 dark:text-slate-400 mt-1">
                  {t('settings.notifications.desc')}
                </p>
              </div>

              {prefSuccess && (
                <Alert type="success" title="Saved">
                  {t('settings.actions.savedSuccess')}
                </Alert>
              )}

              <div className="space-y-4">
                <div className="flex items-center justify-between p-4 bg-gray-50 dark:bg-slate-800/50 rounded-lg border border-gray-200/60 dark:border-slate-800">
                  <div>
                    <div className="font-semibold text-sm text-gray-900 dark:text-white">
                      {t('settings.notifications.inApp')}
                    </div>
                    <div className="text-xs text-gray-500 dark:text-slate-400 mt-0.5">
                      {t('settings.notifications.inAppDesc')}
                    </div>
                  </div>
                  <input
                    type="checkbox"
                    checked={preferences.inAppNotifications}
                    onChange={(e) => handleSavePreferences({ inAppNotifications: e.target.checked })}
                    className="w-4 h-4 text-blue-600 rounded border-gray-300 dark:border-slate-700 focus:ring-blue-500"
                  >
                  </input>
                </div>

                <div className="flex items-center justify-between p-4 bg-gray-50 dark:bg-slate-800/50 rounded-lg border border-gray-200/60 dark:border-slate-800">
                  <div>
                    <div className="font-semibold text-sm text-gray-900 dark:text-white">
                      {t('settings.notifications.email')}
                    </div>
                    <div className="text-xs text-gray-500 dark:text-slate-400 mt-0.5">
                      {t('settings.notifications.emailDesc')}
                    </div>
                  </div>
                  <input
                    type="checkbox"
                    checked={preferences.emailNotifications}
                    onChange={(e) => handleSavePreferences({ emailNotifications: e.target.checked })}
                    className="w-4 h-4 text-blue-600 rounded border-gray-300 dark:border-slate-700 focus:ring-blue-500"
                  >
                  </input>
                </div>

                <div className="flex items-center justify-between p-4 bg-gray-50 dark:bg-slate-800/50 rounded-lg border border-gray-200/60 dark:border-slate-800">
                  <div>
                    <div className="font-semibold text-sm text-gray-900 dark:text-white">
                      {t('settings.notifications.approvals')}
                    </div>
                    <div className="text-xs text-gray-500 dark:text-slate-400 mt-0.5">
                      Notify me when profile changes or stage-gate deliverables are submitted or decided.
                    </div>
                  </div>
                  <input
                    type="checkbox"
                    checked={preferences.approvalNotifications}
                    onChange={(e) => handleSavePreferences({ approvalNotifications: e.target.checked })}
                    className="w-4 h-4 text-blue-600 rounded border-gray-300 dark:border-slate-700 focus:ring-blue-500"
                  >
                  </input>
                </div>

                <div className="flex items-center justify-between p-4 bg-gray-50 dark:bg-slate-800/50 rounded-lg border border-gray-200/60 dark:border-slate-800">
                  <div>
                    <div className="font-semibold text-sm text-gray-900 dark:text-white">
                      {t('settings.notifications.taskUpdates')}
                    </div>
                    <div className="text-xs text-gray-500 dark:text-slate-400 mt-0.5">
                      Notify me when I am assigned to new tasks or reassignments occur.
                    </div>
                  </div>
                  <input
                    type="checkbox"
                    checked={preferences.taskUpdatesNotifications}
                    onChange={(e) => handleSavePreferences({ taskUpdatesNotifications: e.target.checked })}
                    className="w-4 h-4 text-blue-600 rounded border-gray-300 dark:border-slate-700 focus:ring-blue-500"
                  >
                  </input>
                </div>

                <div className="flex items-center justify-between p-4 bg-gray-50 dark:bg-slate-800/50 rounded-lg border border-gray-200/60 dark:border-slate-800">
                  <div>
                    <div className="font-semibold text-sm text-gray-900 dark:text-white">
                      {t('settings.notifications.deadlines')}
                    </div>
                    <div className="text-xs text-gray-500 dark:text-slate-400 mt-0.5">
                      Alert me 48 hours before task deadlines or when milestones become overdue.
                    </div>
                  </div>
                  <input
                    type="checkbox"
                    checked={preferences.deadlineRemindersNotifications}
                    onChange={(e) => handleSavePreferences({ deadlineRemindersNotifications: e.target.checked })}
                    className="w-4 h-4 text-blue-600 rounded border-gray-300 dark:border-slate-700 focus:ring-blue-500"
                  >
                  </input>
                </div>
              </div>
            </div>
          )}

          {/* TAB 5: ACCESSIBILITY & DISPLAY */}
          {activeTab === 'accessibility' && (
            <div className="bg-white dark:bg-slate-900 rounded-xl p-6 shadow-sm border border-gray-200 dark:border-slate-800 space-y-6">
              <div>
                <h2 className="text-lg font-bold text-gray-900 dark:text-white">
                  {t('settings.accessibility.title')}
                </h2>
                <p className="text-sm text-gray-500 dark:text-slate-400 mt-1">
                  {t('settings.accessibility.desc')}
                </p>
              </div>

              {prefSuccess && (
                <Alert type="success" title="Saved">
                  {t('settings.actions.savedSuccess')}
                </Alert>
              )}

              <div className="space-y-5">
                <div>
                  <label className="text-xs font-semibold text-gray-700 dark:text-slate-300 block mb-1.5">
                    {t('settings.accessibility.textSize')}
                  </label>
                  <div className="grid grid-cols-2 gap-4">
                    <button
                      type="button"
                      onClick={() => handleSavePreferences({ textSize: 'DEFAULT' })}
                      className={`p-3.5 rounded-lg border text-left text-xs font-medium transition-all ${
                        preferences.textSize === 'DEFAULT'
                          ? 'border-blue-600 bg-blue-50/50 dark:bg-blue-950/30 text-blue-700 dark:text-blue-300'
                          : 'border-gray-200 dark:border-slate-700 text-gray-700 dark:text-slate-300'
                      }`}
                    >
                      <div className="font-bold text-sm mb-1">{t('settings.accessibility.defaultSize')}</div>
                      Standard UI font scaling suitable for high-density monitors.
                    </button>
                    <button
                      type="button"
                      onClick={() => handleSavePreferences({ textSize: 'LARGE' })}
                      className={`p-3.5 rounded-lg border text-left text-xs font-medium transition-all ${
                        preferences.textSize === 'LARGE'
                          ? 'border-blue-600 bg-blue-50/50 dark:bg-blue-950/30 text-blue-700 dark:text-blue-300'
                          : 'border-gray-200 dark:border-slate-700 text-gray-700 dark:text-slate-300'
                      }`}
                    >
                      <div className="font-bold text-sm mb-1">{t('settings.accessibility.largeSize')}</div>
                      Larger font scaling with enhanced legibility.
                    </button>
                  </div>
                </div>

                <div>
                  <label className="text-xs font-semibold text-gray-700 dark:text-slate-300 block mb-1.5">
                    {t('settings.accessibility.density')}
                  </label>
                  <div className="grid grid-cols-2 gap-4">
                    <button
                      type="button"
                      onClick={() => handleSavePreferences({ displayDensity: 'COMFORTABLE' })}
                      className={`p-3.5 rounded-lg border text-left text-xs font-medium transition-all ${
                        preferences.displayDensity === 'COMFORTABLE'
                          ? 'border-blue-600 bg-blue-50/50 dark:bg-blue-950/30 text-blue-700 dark:text-blue-300'
                          : 'border-gray-200 dark:border-slate-700 text-gray-700 dark:text-slate-300'
                      }`}
                    >
                      <div className="font-bold text-sm mb-1">{t('settings.accessibility.comfortable')}</div>
                      Relaxed spatial padding across tables and dashboards.
                    </button>
                    <button
                      type="button"
                      onClick={() => handleSavePreferences({ displayDensity: 'COMPACT' })}
                      className={`p-3.5 rounded-lg border text-left text-xs font-medium transition-all ${
                        preferences.displayDensity === 'COMPACT'
                          ? 'border-blue-600 bg-blue-50/50 dark:bg-blue-950/30 text-blue-700 dark:text-blue-300'
                          : 'border-gray-200 dark:border-slate-700 text-gray-700 dark:text-slate-300'
                      }`}
                    >
                      <div className="font-bold text-sm mb-1">{t('settings.accessibility.compact')}</div>
                      High density data display for maximum screen utilization.
                    </button>
                  </div>
                </div>

                <div className="flex items-center justify-between p-4 bg-gray-50 dark:bg-slate-800/50 rounded-lg border border-gray-200/60 dark:border-slate-800">
                  <div>
                    <div className="font-semibold text-sm text-gray-900 dark:text-white">
                      {t('settings.accessibility.reducedMotion')}
                    </div>
                    <div className="text-xs text-gray-500 dark:text-slate-400 mt-0.5">
                      {t('settings.accessibility.reducedMotionDesc')}
                    </div>
                  </div>
                  <input
                    type="checkbox"
                    checked={preferences.reducedMotion}
                    onChange={(e) => handleSavePreferences({ reducedMotion: e.target.checked })}
                    className="w-4 h-4 text-blue-600 rounded border-gray-300 dark:border-slate-700 focus:ring-blue-500"
                  >
                  </input>
                </div>

                <div className="flex items-center justify-between p-4 bg-gray-50 dark:bg-slate-800/50 rounded-lg border border-gray-200/60 dark:border-slate-800">
                  <div>
                    <div className="font-semibold text-sm text-gray-900 dark:text-white">
                      {t('settings.accessibility.highContrastFocus')}
                    </div>
                    <div className="text-xs text-gray-500 dark:text-slate-400 mt-0.5">
                      {t('settings.accessibility.highContrastFocusDesc')}
                    </div>
                  </div>
                  <input
                    type="checkbox"
                    checked={preferences.highContrastFocus}
                    onChange={(e) => handleSavePreferences({ highContrastFocus: e.target.checked })}
                    className="w-4 h-4 text-blue-600 rounded border-gray-300 dark:border-slate-700 focus:ring-blue-500"
                  >
                  </input>
                </div>
              </div>
            </div>
          )}

          {/* TAB 6: ACCOUNT SECURITY */}
          {activeTab === 'security' && (
            <div className="bg-white dark:bg-slate-900 rounded-xl p-6 shadow-sm border border-gray-200 dark:border-slate-800 space-y-6">
              <div>
                <h2 className="text-lg font-bold text-gray-900 dark:text-white">
                  {t('settings.security.title')}
                </h2>
                <p className="text-sm text-gray-500 dark:text-slate-400 mt-1">
                  {t('settings.security.desc')}
                </p>
              </div>

              {securitySuccess && (
                <Alert type="success" title="Success" onClose={() => setSecuritySuccess(null)}>
                  {securitySuccess}
                </Alert>
              )}

              {securityError && (
                <Alert type="error" title="Error" onClose={() => setSecurityError(null)}>
                  {securityError}
                </Alert>
              )}

              <form onSubmit={handleChangePassword} className="space-y-4 max-w-md">
                <Input
                  label={t('settings.security.currentPassword')}
                  type="password"
                  required
                  value={securityForm.currentPassword}
                  onChange={(e) => setSecurityForm({ ...securityForm, currentPassword: e.target.value })}
                  placeholder="••••••••"
                />

                <Input
                  label={t('settings.security.newPassword')}
                  type="password"
                  required
                  value={securityForm.newPassword}
                  onChange={(e) => setSecurityForm({ ...securityForm, newPassword: e.target.value })}
                  placeholder="At least 8 characters..."
                  helperText="Must be at least 8 characters with letters, numbers, and symbols."
                />

                <Input
                  label={t('settings.security.confirmPassword')}
                  type="password"
                  required
                  value={securityForm.confirmPassword}
                  onChange={(e) => setSecurityForm({ ...securityForm, confirmPassword: e.target.value })}
                  placeholder="Repeat new password..."
                />

                <div className="pt-2">
                  <Button
                    type="submit"
                    variant="primary"
                    isLoading={securityLoading}
                  >
                    {t('settings.security.changePasswordBtn')}
                  </Button>
                </div>
              </form>

              <div className="text-xs text-gray-500 dark:text-slate-400 p-4 bg-gray-50 dark:bg-slate-800/40 rounded-lg border border-gray-200/60 dark:border-slate-800">
                {t('settings.security.passwordNotice')}
              </div>
            </div>
          )}
        </div>
      </div>

      {/* Review Proposed Changes Modal */}
      <Modal
        isOpen={showReviewModal}
        onClose={() => setShowReviewModal(false)}
        title="Review Proposed Profile Changes"
        description="Verify your requested updates before submitting them to your organization administrator for review."
        maxWidth="lg"
      >
        <div className="space-y-4">
          <div className="border border-gray-200 dark:border-slate-800 rounded-lg overflow-hidden">
            <table className="w-full text-left text-xs">
              <thead className="bg-gray-50 dark:bg-slate-800/70 border-b border-gray-200 dark:border-slate-800">
                <tr>
                  <th className="py-2 px-3 font-semibold text-gray-700 dark:text-slate-300">Field</th>
                  <th className="py-2 px-3 font-semibold text-gray-500 dark:text-slate-400">Current Official Value</th>
                  <th className="py-2 px-3 font-semibold text-blue-600 dark:text-blue-400">Proposed New Value</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100 dark:divide-slate-800">
                {diffs.map((d) => (
                  <tr key={d.field}>
                    <td className="py-2.5 px-3 font-medium text-gray-900 dark:text-white">{d.label}</td>
                    <td className="py-2.5 px-3 text-gray-500 dark:text-slate-400">{d.current}</td>
                    <td className="py-2.5 px-3 font-semibold text-blue-700 dark:text-blue-300 flex items-center gap-1.5">
                      <ArrowRight className="w-3.5 h-3.5 text-blue-500" />
                      {d.proposed}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <div>
            <label className="text-xs font-semibold text-gray-700 dark:text-slate-300 block mb-1">
              {t('settings.profile.submissionComment')}
            </label>
            <textarea
              rows={3}
              value={formData.submissionComment}
              onChange={(e) => setFormData({ ...formData, submissionComment: e.target.value })}
              placeholder={t('settings.profile.submissionCommentPlaceholder')}
              className="w-full px-3 py-2 text-sm bg-white dark:bg-slate-800 border border-gray-300 dark:border-slate-700 rounded-lg focus:ring-2 focus:ring-blue-500"
            />
          </div>

          {diffs.some((d) => d.field === 'workEmail') && (
            <div className="bg-blue-50 dark:bg-blue-950/30 p-3 rounded-lg border border-blue-200 dark:border-blue-900 text-xs text-blue-800 dark:text-blue-300">
              <strong>Notice:</strong> Your proposed work email address requires token verification before administrator approval will become authoritative.
            </div>
          )}

          <div className="flex justify-end gap-3 pt-3 border-t border-gray-200 dark:border-slate-800">
            <Button variant="outline" onClick={() => setShowReviewModal(false)}>
              Back to Edit
            </Button>
            <Button
              variant="primary"
              isLoading={submittingRequest}
              onClick={handleSubmitProfileChanges}
            >
              Submit for Approval
            </Button>
          </div>
        </div>
      </Modal>

      {/* Email Verification Helper Modal */}
      <Modal
        isOpen={verifyingRequestId !== null}
        onClose={() => {
          setVerifyingRequestId(null);
          setVerifyError(null);
          setVerifySuccess(null);
        }}
        title="Verify Work Email Address"
        description="Enter the single-use verification token sent to your proposed work email."
        maxWidth="md"
      >
        <div className="space-y-4">
          {verifySuccess && (
            <Alert type="success" title="Verified">
              {verifySuccess}
            </Alert>
          )}

          {verifyError && (
            <Alert type="error" title="Verification Failed">
              {verifyError}
            </Alert>
          )}

          <div className="space-y-3">
            <Input
              label="Verification Token"
              placeholder="e.g. 4d7bf2e1-8f55-4428-a47f-1d89e248b1aa"
              value={verifyTokenInput}
              onChange={(e) => setVerifyTokenInput(e.target.value)}
              helperText="In development environment, check application logs for generated token."
            />
          </div>

          <div className="flex justify-end gap-3 pt-3 border-t border-gray-200 dark:border-slate-800">
            <Button
              variant="outline"
              onClick={() => {
                setVerifyingRequestId(null);
                setVerifyError(null);
                setVerifySuccess(null);
              }}
            >
              Close
            </Button>
            <Button
              variant="primary"
              onClick={() => verifyingRequestId && handleVerifyEmail(verifyingRequestId)}
            >
              Confirm Verification
            </Button>
          </div>
        </div>
      </Modal>
    </div>
  );
}
