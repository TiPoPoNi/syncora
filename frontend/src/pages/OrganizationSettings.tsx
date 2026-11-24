import React, { useState, useEffect } from 'react';
import { api } from '../services/api';
import { useOrganization, OrganizationDto } from '../context/OrganizationContext';
import { useAuth } from '../context/AuthContext';
import { 
  Building2, Users, ShieldAlert, Plus, CheckCircle, AlertTriangle, 
  Trash2, UserPlus, Save, RefreshCw, KeyRound, Search, Filter,
  Copy, Check, Send, Ban, ExternalLink, ShieldCheck, Mail,
  ArrowRight, Clock, CheckCircle2, XCircle, AlertCircle
} from 'lucide-react';

interface MemberDto {
  id: number;
  membershipId?: number;
  userId: number;
  username: string;
  email: string;
  role: string;
  status: string;
  joinedAt?: string;
  createdAt?: string;
  employeeId?: string;
  invitationStatus?: string;
  invitationExpiresAt?: string;
  activatedAt?: string;
  activationLink?: string;
}

interface ProfileChangeRequestDto {
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

export function OrganizationSettings() {
  const { currentOrg, refreshOrganizations, switchOrganization } = useOrganization();
  const { user, token } = useAuth();
  
  const [activeMainTab, setActiveMainTab] = useState<'members' | 'requests'>('members');

  const [members, setMembers] = useState<MemberDto[]>([]);
  const [loadingMembers, setLoadingMembers] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);

  // Profile Change Requests State
  const [profileRequests, setProfileRequests] = useState<ProfileChangeRequestDto[]>([]);
  const [loadingProfileRequests, setLoadingProfileRequests] = useState(false);
  const [requestStatusFilter, setRequestStatusFilter] = useState<string>('ALL');
  const [reviewComments, setReviewComments] = useState<Record<number, string>>({});
  const [processingRequestId, setProcessingRequestId] = useState<number | null>(null);

  // Search & Filter State
  const [searchTerm, setSearchTerm] = useState('');
  const [roleFilter, setRoleFilter] = useState('ALL');
  const [statusFilter, setStatusFilter] = useState('ALL');
  const [invitationFilter, setInvitationFilter] = useState('ALL');

  // Edit Org State
  const [orgName, setOrgName] = useState('');
  const [orgSlug, setOrgSlug] = useState('');
  const [orgCode, setOrgCode] = useState('');
  const [orgStatus, setOrgStatus] = useState('ACTIVE');
  const [savingOrg, setSavingOrg] = useState(false);

  // Invite Member Modal State
  const [showInviteModal, setShowInviteModal] = useState(false);
  const [inviteEmail, setInviteEmail] = useState('');
  const [inviteFullName, setInviteFullName] = useState('');
  const [inviteRole, setInviteRole] = useState('WORKER');
  const [inviteCustomEmpId, setInviteCustomEmpId] = useState('');
  const [inviting, setInviting] = useState(false);
  const [newInviteResult, setNewInviteResult] = useState<MemberDto | null>(null);
  const [copiedLink, setCopiedLink] = useState(false);

  useEffect(() => {
    document.title = 'SYNCORA | Organization Settings';
    if (currentOrg) {
      setOrgName(currentOrg.name);
      setOrgSlug(currentOrg.slug);
      setOrgCode(currentOrg.orgCode || '');
      setOrgStatus(currentOrg.status);
      loadMembers(currentOrg.id);
    }
  }, [currentOrg]);

  const loadMembers = async (orgId: number) => {
    try {
      setLoadingMembers(true);
      setError(null);
      const params = new URLSearchParams();
      if (searchTerm.trim()) params.append('search', searchTerm.trim());
      if (roleFilter !== 'ALL') params.append('role', roleFilter);
      if (statusFilter !== 'ALL') params.append('status', statusFilter);
      if (invitationFilter !== 'ALL') params.append('invitationStatus', invitationFilter);

      const res = await api.get<MemberDto[]>(`/organizations/${orgId}/members?${params.toString()}`);
      setMembers(res.data || []);
    } catch (err: any) {
      setError(err.response?.data?.message || 'Failed to load organization members');
    } finally {
      setLoadingMembers(false);
    }
  };

  const loadProfileRequests = async (orgId: number) => {
    try {
      setLoadingProfileRequests(true);
      setError(null);
      const url = requestStatusFilter !== 'ALL'
        ? `/organizations/${orgId}/profile-change-requests?status=${requestStatusFilter}`
        : `/organizations/${orgId}/profile-change-requests`;
      const res = await api.get<ProfileChangeRequestDto[]>(url);
      setProfileRequests(res.data || []);
    } catch (err: any) {
      setError(err.response?.data?.message || 'Failed to load profile change requests');
    } finally {
      setLoadingProfileRequests(false);
    }
  };

  useEffect(() => {
    if (currentOrg) {
      if (activeMainTab === 'requests') {
        loadProfileRequests(currentOrg.id);
      } else {
        loadMembers(currentOrg.id);
      }
    }
  }, [currentOrg, activeMainTab, requestStatusFilter]);

  const handleApproveProfileRequest = async (reqId: number) => {
    if (!currentOrg) return;
    try {
      setProcessingRequestId(reqId);
      setError(null);
      setSuccess(null);
      const comment = reviewComments[reqId] || '';
      await api.post(`/profile-change-requests/${reqId}/approve`, { comment });
      setSuccess(`Profile change request #${reqId} approved successfully.`);
      loadProfileRequests(currentOrg.id);
      loadMembers(currentOrg.id);
    } catch (err: any) {
      setError(err.response?.data?.message || 'Failed to approve profile change request');
    } finally {
      setProcessingRequestId(null);
    }
  };

  const handleRejectProfileRequest = async (reqId: number) => {
    if (!currentOrg) return;
    try {
      setProcessingRequestId(reqId);
      setError(null);
      setSuccess(null);
      const comment = reviewComments[reqId] || 'Rejected by organization administrator.';
      await api.post(`/profile-change-requests/${reqId}/reject`, { comment });
      setSuccess(`Profile change request #${reqId} rejected.`);
      loadProfileRequests(currentOrg.id);
    } catch (err: any) {
      setError(err.response?.data?.message || 'Failed to reject profile change request');
    } finally {
      setProcessingRequestId(null);
    }
  };

  const handleUpdateOrg = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!currentOrg) return;
    try {
      setSavingOrg(true);
      setError(null);
      setSuccess(null);
      const res = await api.put<OrganizationDto>(`/organizations/${currentOrg.id}`, {
        name: orgName.trim(),
        slug: orgSlug.trim(),
        orgCode: orgCode.trim().toUpperCase(),
        status: orgStatus,
      });
      setSuccess('Organization details updated successfully.');
      await refreshOrganizations();
      switchOrganization(res.data);
    } catch (err: any) {
      setError(err.response?.data?.message || 'Failed to update organization details');
    } finally {
      setSavingOrg(false);
    }
  };

  const handleRoleChange = async (memberId: number, targetRole: string) => {
    if (!currentOrg) return;
    try {
      setError(null);
      setSuccess(null);
      await api.put(`/organizations/${currentOrg.id}/members/${memberId}`, {
        role: targetRole,
      });
      setSuccess('Member role updated successfully.');
      loadMembers(currentOrg.id);
    } catch (err: any) {
      setError(err.response?.data?.message || 'Failed to update member role');
    }
  };

  const handleToggleStatus = async (member: MemberDto) => {
    if (!currentOrg) return;
    const newStatus = member.status === 'ACTIVE' ? 'SUSPENDED' : 'ACTIVE';
    const actionLabel = newStatus === 'SUSPENDED' ? 'suspend' : 'activate';
    if (!window.confirm(`Are you sure you want to ${actionLabel} membership for ${member.username || member.email}?`)) return;

    try {
      setError(null);
      setSuccess(null);
      await api.put(`/organizations/${currentOrg.id}/members/${member.id}`, {
        status: newStatus,
      });
      setSuccess(`Membership status updated to ${newStatus}.`);
      loadMembers(currentOrg.id);
    } catch (err: any) {
      setError(err.response?.data?.message || `Failed to ${actionLabel} member`);
    }
  };

  const handleRemoveMember = async (memberId: number) => {
    if (!currentOrg) return;
    if (!window.confirm('Are you sure you want to permanently remove this member from the organization?')) return;
    try {
      setError(null);
      setSuccess(null);
      await api.delete(`/organizations/${currentOrg.id}/members/${memberId}`);
      setSuccess('Member removed from organization.');
      loadMembers(currentOrg.id);
    } catch (err: any) {
      setError(err.response?.data?.message || 'Failed to remove member');
    }
  };

  const handleInviteMember = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!currentOrg || !inviteEmail.trim()) return;
    try {
      setInviting(true);
      setError(null);
      setSuccess(null);
      const res = await api.post<MemberDto>(`/organizations/${currentOrg.id}/members/invite`, {
        email: inviteEmail.trim(),
        fullName: inviteFullName.trim() || undefined,
        role: inviteRole,
        customEmployeeId: inviteCustomEmpId.trim() || undefined,
      });

      setNewInviteResult(res.data);
      setSuccess(`Invitation issued for ${inviteEmail.trim()}! Assigned ID: ${res.data.employeeId}`);
      loadMembers(currentOrg.id);
    } catch (err: any) {
      setError(err.response?.data?.message || 'Failed to invite member');
    } finally {
      setInviting(false);
    }
  };

  const handleResendInvite = async (memberId: number) => {
    if (!currentOrg) return;
    try {
      setError(null);
      setSuccess(null);
      const res = await api.post<MemberDto>(`/organizations/${currentOrg.id}/members/${memberId}/resend-invite`);
      setSuccess(`Invitation resent. New activation link generated.`);
      loadMembers(currentOrg.id);
    } catch (err: any) {
      setError(err.response?.data?.message || 'Failed to resend invitation');
    }
  };

  const handleRevokeInvite = async (memberId: number) => {
    if (!currentOrg) return;
    if (!window.confirm('Are you sure you want to revoke this pending invitation?')) return;
    try {
      setError(null);
      setSuccess(null);
      await api.post(`/organizations/${currentOrg.id}/members/${memberId}/revoke-invite`);
      setSuccess('Invitation revoked successfully.');
      loadMembers(currentOrg.id);
    } catch (err: any) {
      setError(err.response?.data?.message || 'Failed to revoke invitation');
    }
  };

  const copyToClipboard = (text: string) => {
    const fullUrl = window.location.origin + text;
    navigator.clipboard.writeText(fullUrl);
    setCopiedLink(true);
    setTimeout(() => setCopiedLink(false), 2000);
  };

  if (!currentOrg) {
    return (
      <div className="p-8 text-center text-gray-500">
        <Building2 className="w-12 h-12 mx-auto text-gray-300 mb-3" />
        <h3 className="text-base font-semibold text-gray-800">No Organization Selected</h3>
        <p className="text-xs text-gray-500 mt-1">Please select or create an organization workspace.</p>
      </div>
    );
  }

  return (
    <div className="space-y-6 max-w-7xl mx-auto pb-12">
      {/* Header Banner */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-4 border-b border-gray-200">
        <div>
          <div className="flex items-center gap-2 text-xs font-semibold uppercase tracking-wider text-blue-600 mb-1">
            <Building2 className="w-4 h-4" />
            <span>Workspace Governance</span>
          </div>
          <h1 className="text-2xl font-black text-gray-900 tracking-tight flex items-center gap-2">
            {currentOrg.name}
            <span className="text-xs px-2.5 py-0.5 rounded-full bg-blue-100 text-blue-800 font-mono font-medium">
              {currentOrg.orgCode || 'SYN'}
            </span>
          </h1>
          <p className="text-xs text-gray-500 mt-0.5">
            Manage organization identities, employee IDs, access roles, and account lifecycle.
          </p>
        </div>

        <button
          onClick={() => {
            setNewInviteResult(null);
            setInviteEmail('');
            setInviteFullName('');
            setInviteRole('WORKER');
            setInviteCustomEmpId('');
            setShowInviteModal(true);
          }}
          className="px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white rounded-xl text-sm font-semibold shadow-sm shadow-blue-500/20 flex items-center gap-2 transition"
        >
          <UserPlus className="w-4 h-4" />
          <span>Provision Employee Account</span>
        </button>
      </div>

      {error && (
        <div className="p-4 rounded-xl bg-red-50 border border-red-200 text-red-800 text-xs flex items-center justify-between">
          <div className="flex items-center gap-2">
            <AlertTriangle className="w-4 h-4 text-red-600 shrink-0" />
            <span>{error}</span>
          </div>
          <button onClick={() => setError(null)} className="text-red-500 hover:text-red-700 font-bold ml-2">✕</button>
        </div>
      )}

      {success && (
        <div className="p-4 rounded-xl bg-emerald-50 border border-emerald-200 text-emerald-800 text-xs flex items-center justify-between">
          <div className="flex items-center gap-2">
            <CheckCircle className="w-4 h-4 text-emerald-600 shrink-0" />
            <span>{success}</span>
          </div>
          <button onClick={() => setSuccess(null)} className="text-emerald-500 hover:text-emerald-700 font-bold ml-2">✕</button>
        </div>
      )}

      {/* Tab Switcher */}
      <div className="flex border-b border-gray-200 gap-4 text-sm font-semibold">
        <button
          onClick={() => setActiveMainTab('members')}
          className={`pb-3 px-1 border-b-2 flex items-center gap-2 transition ${
            activeMainTab === 'members'
              ? 'border-blue-600 text-blue-600'
              : 'border-transparent text-gray-500 hover:text-gray-700'
          }`}
        >
          <Users className="w-4 h-4" />
          <span>Members & Access Control</span>
        </button>

        <button
          onClick={() => setActiveMainTab('requests')}
          className={`pb-3 px-1 border-b-2 flex items-center gap-2 transition ${
            activeMainTab === 'requests'
              ? 'border-blue-600 text-blue-600'
              : 'border-transparent text-gray-500 hover:text-gray-700'
          }`}
        >
          <ShieldAlert className="w-4 h-4" />
          <span>Profile Change Requests</span>
          {profileRequests.filter((r) => r.status === 'PENDING').length > 0 && (
            <span className="bg-amber-100 text-amber-800 text-[10px] font-bold px-2 py-0.5 rounded-full">
              {profileRequests.filter((r) => r.status === 'PENDING').length}
            </span>
          )}
        </button>
      </div>

      {activeMainTab === 'members' ? (
        <>
          {/* Organization Members Ledger Card */}
          <div className="bg-white rounded-2xl shadow-sm border border-gray-200 overflow-hidden">
        <div className="p-5 border-b border-gray-100 flex flex-col md:flex-row md:items-center justify-between gap-4 bg-slate-50/50">
          <div>
            <h2 className="text-base font-bold text-gray-900 flex items-center gap-2">
              <Users className="w-4 h-4 text-blue-600" />
              <span>Employee Directory & Role-Based Access Control</span>
            </h2>
            <p className="text-xs text-gray-500 mt-0.5">
              Organization administrators control identities, assign scoped roles, and monitor activation status.
            </p>
          </div>

          {/* Search & Filters */}
          <div className="flex flex-wrap items-center gap-2 text-xs">
            <div className="relative">
              <input
                type="text"
                placeholder="Search employee, email, ID..."
                value={searchTerm}
                onChange={(e) => setSearchTerm(e.target.value)}
                onKeyDown={(e) => e.key === 'Enter' && loadMembers(currentOrg.id)}
                className="pl-8 pr-3 py-1.5 border border-gray-300 rounded-lg bg-white text-gray-900 focus:outline-none focus:ring-1 focus:ring-blue-500 w-48 sm:w-56"
              />
              <Search className="w-3.5 h-3.5 text-gray-400 absolute left-2.5 top-2.5" />
            </div>

            <select
              value={roleFilter}
              onChange={(e) => {
                setRoleFilter(e.target.value);
              }}
              className="px-2.5 py-1.5 border border-gray-300 rounded-lg bg-white text-gray-700 font-medium focus:outline-none"
            >
              <option value="ALL">All Roles</option>
              <option value="ORGANIZATION_OWNER">Owner</option>
              <option value="ORGANIZATION_ADMIN">Admin</option>
              <option value="PROJECT_MANAGER">Project Manager</option>
              <option value="WORKER">Worker</option>
              <option value="CLIENT">Client</option>
              <option value="VENDOR">Vendor</option>
            </select>

            <select
              value={statusFilter}
              onChange={(e) => setStatusFilter(e.target.value)}
              className="px-2.5 py-1.5 border border-gray-300 rounded-lg bg-white text-gray-700 font-medium focus:outline-none"
            >
              <option value="ALL">All Status</option>
              <option value="ACTIVE">Active</option>
              <option value="SUSPENDED">Suspended</option>
            </select>

            <button
              onClick={() => loadMembers(currentOrg.id)}
              className="p-1.5 rounded-lg border border-gray-300 bg-white hover:bg-gray-50 text-gray-600 transition"
              title="Apply filters"
            >
              <Filter className="w-3.5 h-3.5" />
            </button>
          </div>
        </div>

        {loadingMembers ? (
          <div className="py-16 text-center text-gray-500 text-xs">
            <RefreshCw className="w-6 h-6 animate-spin mx-auto mb-2 text-blue-500" />
            <span>Loading organization roster...</span>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs border-collapse">
              <thead>
                <tr className="border-b border-gray-200 font-semibold text-gray-500 uppercase tracking-wider bg-gray-50/80">
                  <th className="py-3 px-4">Employee</th>
                  <th className="py-3 px-4">Employee ID</th>
                  <th className="py-3 px-4">Assigned Role</th>
                  <th className="py-3 px-4">Account Status</th>
                  <th className="py-3 px-4">Invitation Lifecycle</th>
                  <th className="py-3 px-4 text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100 text-gray-700">
                {members.length === 0 ? (
                  <tr>
                    <td colSpan={6} className="py-12 text-center text-gray-400">
                      No members matching the current filters.
                    </td>
                  </tr>
                ) : (
                  members.map((m) => (
                    <tr key={m.id} className="hover:bg-slate-50/80 transition">
                      {/* Employee Details */}
                      <td className="py-3 px-4">
                        <div className="flex items-center gap-2.5">
                          <div className="w-8 h-8 rounded-full bg-blue-100 text-blue-700 font-bold flex items-center justify-center text-xs shrink-0">
                            {(m.username || m.email || 'U').charAt(0).toUpperCase()}
                          </div>
                          <div>
                            <span className="font-semibold text-gray-900 block">{m.username}</span>
                            <span className="text-[11px] text-gray-500 flex items-center gap-1 font-mono">
                              <Mail className="w-3 h-3 text-gray-400" />
                              {m.email || '—'}
                            </span>
                          </div>
                        </div>
                      </td>

                      {/* Employee ID */}
                      <td className="py-3 px-4 font-mono">
                        <span className="px-2 py-0.5 rounded bg-slate-100 border border-slate-200 font-bold text-slate-800 text-[11px]">
                          {m.employeeId || '—'}
                        </span>
                      </td>

                      {/* Organization Role */}
                      <td className="py-3 px-4">
                        <select
                          value={m.role}
                          onChange={(e) => handleRoleChange(m.id, e.target.value)}
                          className="px-2 py-1 rounded-lg border border-gray-200 bg-white font-medium text-gray-800 text-xs focus:ring-1 focus:ring-blue-500"
                        >
                          <option value="ORGANIZATION_OWNER">ORGANIZATION_OWNER</option>
                          <option value="ORGANIZATION_ADMIN">ORGANIZATION_ADMIN</option>
                          <option value="PROJECT_MANAGER">PROJECT_MANAGER</option>
                          <option value="WORKER">WORKER</option>
                          <option value="CLIENT">CLIENT</option>
                          <option value="VENDOR">VENDOR</option>
                        </select>
                      </td>

                      {/* Status */}
                      <td className="py-3 px-4">
                        <span
                          className={`inline-flex items-center px-2 py-0.5 rounded-full text-[10px] font-semibold tracking-wide ${
                            m.status === 'ACTIVE' 
                              ? 'bg-emerald-50 text-emerald-700 border border-emerald-200' 
                              : 'bg-red-50 text-red-700 border border-red-200'
                          }`}
                        >
                          {m.status}
                        </span>
                      </td>

                      {/* Invitation Lifecycle */}
                      <td className="py-3 px-4">
                        <div className="space-y-1">
                          <span
                            className={`inline-flex items-center px-2 py-0.5 rounded text-[10px] font-medium ${
                              m.invitationStatus === 'ACCEPTED'
                                ? 'bg-green-50 text-green-700'
                                : m.invitationStatus === 'PENDING'
                                ? 'bg-amber-50 text-amber-700 border border-amber-200'
                                : 'bg-gray-100 text-gray-600'
                            }`}
                          >
                            {m.invitationStatus || 'ACCEPTED'}
                          </span>

                          {m.invitationStatus === 'PENDING' && m.activationLink && (
                            <div className="flex items-center gap-1.5 pt-0.5">
                              <button
                                onClick={() => copyToClipboard(m.activationLink!)}
                                className="text-[10px] text-blue-600 hover:underline flex items-center gap-1"
                                title="Copy direct activation URL"
                              >
                                <Copy className="w-3 h-3" />
                                <span>Copy Link</span>
                              </button>
                            </div>
                          )}
                        </div>
                      </td>

                      {/* Actions */}
                      <td className="py-3 px-4 text-right">
                        <div className="inline-flex items-center gap-1">
                          {m.invitationStatus === 'PENDING' && (
                            <>
                              <button
                                onClick={() => handleResendInvite(m.id)}
                                className="p-1 rounded hover:bg-blue-50 text-blue-600"
                                title="Resend invitation token"
                              >
                                <Send className="w-3.5 h-3.5" />
                              </button>
                              <button
                                onClick={() => handleRevokeInvite(m.id)}
                                className="p-1 rounded hover:bg-amber-50 text-amber-600"
                                title="Revoke invitation"
                              >
                                <Ban className="w-3.5 h-3.5" />
                              </button>
                            </>
                          )}

                          <button
                            onClick={() => handleToggleStatus(m)}
                            className={`px-2 py-1 rounded text-[11px] font-medium ${
                              m.status === 'ACTIVE' 
                                ? 'text-amber-700 hover:bg-amber-50' 
                                : 'text-emerald-700 hover:bg-emerald-50'
                            }`}
                          >
                            {m.status === 'ACTIVE' ? 'Suspend' : 'Activate'}
                          </button>

                          <button
                            onClick={() => handleRemoveMember(m.id)}
                            className="p-1 rounded text-red-500 hover:bg-red-50 hover:text-red-700 ml-1"
                            title="Remove member"
                          >
                            <Trash2 className="w-3.5 h-3.5" />
                          </button>
                        </div>
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Organization Workspace Details Card */}
      <div className="bg-white rounded-2xl shadow-sm border border-gray-200 p-6 space-y-4">
        <div>
          <h2 className="text-base font-bold text-gray-900 flex items-center gap-2">
            <ShieldCheck className="w-4 h-4 text-blue-600" />
            <span>Workspace Profile & Employee ID Prefix</span>
          </h2>
          <p className="text-xs text-gray-500 mt-0.5">
            Configure the workspace slug and custom employee ID sequence identifier.
          </p>
        </div>

        <form onSubmit={handleUpdateOrg} className="grid grid-cols-1 md:grid-cols-4 gap-4 text-xs">
          <div>
            <label className="block font-semibold text-gray-700 mb-1">Organization Name</label>
            <input
              type="text"
              required
              value={orgName}
              onChange={(e) => setOrgName(e.target.value)}
              className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm text-gray-900 focus:ring-1 focus:ring-blue-500"
            />
          </div>

          <div>
            <label className="block font-semibold text-gray-700 mb-1">Workspace Code (ID Prefix)</label>
            <input
              type="text"
              required
              maxLength={6}
              placeholder="e.g. ACM"
              value={orgCode}
              onChange={(e) => setOrgCode(e.target.value.toUpperCase())}
              className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm font-mono text-gray-900 uppercase focus:ring-1 focus:ring-blue-500"
            />
          </div>

          <div>
            <label className="block font-semibold text-gray-700 mb-1">Slug / URL Identifier</label>
            <input
              type="text"
              required
              value={orgSlug}
              onChange={(e) => setOrgSlug(e.target.value.toLowerCase())}
              className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm font-mono text-gray-900 lowercase focus:ring-1 focus:ring-blue-500"
            />
          </div>

          <div>
            <label className="block font-semibold text-gray-700 mb-1">Organization Status</label>
            <select
              value={orgStatus}
              onChange={(e) => setOrgStatus(e.target.value)}
              className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm text-gray-900 focus:ring-1 focus:ring-blue-500"
            >
              <option value="ACTIVE">ACTIVE (Operational)</option>
              <option value="SUSPENDED">SUSPENDED (Locked)</option>
            </select>
          </div>

          <div className="md:col-span-4 flex justify-end pt-2">
            <button
              type="submit"
              disabled={savingOrg}
              className="px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white font-medium rounded-lg text-sm shadow-sm flex items-center gap-2 transition"
            >
              {savingOrg ? <RefreshCw className="w-4 h-4 animate-spin" /> : <Save className="w-4 h-4" />}
              <span>Save Workspace Details</span>
            </button>
          </div>
        </form>
      </div>
        </>
      ) : (
        /* Profile Change Requests Console */
        <div className="space-y-6">
          <div className="bg-white rounded-2xl shadow-sm border border-gray-200 p-5 flex flex-col md:flex-row md:items-center justify-between gap-4 bg-slate-50/50">
            <div>
              <h2 className="text-base font-bold text-gray-900 flex items-center gap-2">
                <ShieldAlert className="w-4 h-4 text-blue-600" />
                <span>Employee Profile Change Approval Workflow</span>
              </h2>
              <p className="text-xs text-gray-500 mt-0.5">
                Review proposed profile modifications submitted by employees. Verify diffs, check sensitive work email verification, and record audit decisions.
              </p>
            </div>

            <div className="flex items-center gap-3">
              <select
                value={requestStatusFilter}
                onChange={(e) => setRequestStatusFilter(e.target.value)}
                className="px-3 py-1.5 border border-gray-300 rounded-lg bg-white text-xs text-gray-700 font-medium focus:outline-none"
              >
                <option value="ALL">All Statuses</option>
                <option value="PENDING">Pending Review</option>
                <option value="APPROVED">Approved</option>
                <option value="REJECTED">Rejected</option>
                <option value="STALE">Stale (Modified After Submission)</option>
              </select>

              <button
                onClick={() => loadProfileRequests(currentOrg.id)}
                className="p-1.5 rounded-lg border border-gray-300 bg-white hover:bg-gray-50 text-gray-600 transition"
                title="Refresh Requests"
              >
                <RefreshCw className={`w-4 h-4 ${loadingProfileRequests ? 'animate-spin text-blue-600' : ''}`} />
              </button>
            </div>
          </div>

          {loadingProfileRequests ? (
            <div className="py-16 text-center text-gray-500 text-xs">
              <RefreshCw className="w-6 h-6 animate-spin mx-auto mb-2 text-blue-500" />
              <span>Loading change requests...</span>
            </div>
          ) : profileRequests.length === 0 ? (
            <div className="bg-white rounded-2xl shadow-sm border border-gray-200 p-12 text-center text-gray-500">
              <ShieldCheck className="w-12 h-12 text-gray-300 mx-auto mb-3" />
              <div className="text-sm font-semibold text-gray-800">No Profile Change Requests</div>
              <p className="text-xs text-gray-400 mt-1">There are no requests matching the current filter.</p>
            </div>
          ) : (
            <div className="space-y-4">
              {profileRequests.map((req) => {
                const isRequesterSelf = user?.id === req.userId || user?.username === req.username;
                const isPending = req.status === 'PENDING';

                return (
                  <div key={req.id} className="bg-white rounded-2xl shadow-sm border border-gray-200 overflow-hidden">
                    {/* Request Header */}
                    <div className="p-4 bg-gray-50/70 border-b border-gray-100 flex flex-col md:flex-row md:items-center justify-between gap-3">
                      <div className="flex items-center gap-3">
                        <div className="w-9 h-9 rounded-full bg-blue-100 text-blue-700 font-bold flex items-center justify-center text-sm">
                          {req.employeeName.charAt(0).toUpperCase()}
                        </div>
                        <div>
                          <div className="flex items-center gap-2">
                            <span className="text-sm font-bold text-gray-900">{req.employeeName}</span>
                            <span className="text-xs text-gray-500">(@{req.username})</span>
                            {req.employeeId && (
                              <span className="font-mono text-[10px] bg-blue-50 text-blue-700 px-1.5 py-0.5 rounded font-semibold border border-blue-200">
                                {req.employeeId}
                              </span>
                            )}
                          </div>
                          <div className="text-xs text-gray-400 flex items-center gap-2 mt-0.5">
                            <span>Request #{req.id}</span>
                            <span>•</span>
                            <span>Submitted: {new Date(req.createdAt).toLocaleString()}</span>
                          </div>
                        </div>
                      </div>

                      <div className="flex items-center gap-2">
                        {req.status === 'PENDING' && (
                          <span className="px-2.5 py-1 rounded-full bg-amber-100 text-amber-800 text-xs font-semibold flex items-center gap-1">
                            <Clock className="w-3.5 h-3.5" />
                            Pending Review
                          </span>
                        )}
                        {req.status === 'APPROVED' && (
                          <span className="px-2.5 py-1 rounded-full bg-emerald-100 text-emerald-800 text-xs font-semibold flex items-center gap-1">
                            <CheckCircle2 className="w-3.5 h-3.5" />
                            Approved
                          </span>
                        )}
                        {req.status === 'REJECTED' && (
                          <span className="px-2.5 py-1 rounded-full bg-red-100 text-red-800 text-xs font-semibold flex items-center gap-1">
                            <XCircle className="w-3.5 h-3.5" />
                            Rejected
                          </span>
                        )}
                        {req.status === 'STALE' && (
                          <span className="px-2.5 py-1 rounded-full bg-gray-100 text-gray-800 text-xs font-semibold flex items-center gap-1">
                            <AlertTriangle className="w-3.5 h-3.5 text-amber-600" />
                            Stale (Profile Modified Elsewhere)
                          </span>
                        )}
                      </div>
                    </div>

                    {/* Diff Table */}
                    <div className="p-5 space-y-4">
                      <div className="border border-gray-200 rounded-xl overflow-hidden">
                        <table className="w-full text-left text-xs">
                          <thead className="bg-gray-50 border-b border-gray-200 text-gray-500 uppercase tracking-wider font-semibold">
                            <tr>
                              <th className="py-2.5 px-4 w-1/4">Field</th>
                              <th className="py-2.5 px-4 w-3/8 text-gray-600">Current Official Value</th>
                              <th className="py-2.5 px-4 w-3/8 text-blue-700">Proposed New Value</th>
                            </tr>
                          </thead>
                          <tbody className="divide-y divide-gray-100 text-gray-700">
                            {req.proposedFullName && req.proposedFullName !== req.existingFullName && (
                              <tr className="hover:bg-blue-50/20">
                                <td className="py-2.5 px-4 font-semibold text-gray-900">Full Name</td>
                                <td className="py-2.5 px-4 text-gray-500">{req.existingFullName || '—'}</td>
                                <td className="py-2.5 px-4 font-bold text-blue-700 flex items-center gap-1.5">
                                  <ArrowRight className="w-3.5 h-3.5 text-blue-500 shrink-0" />
                                  {req.proposedFullName}
                                </td>
                              </tr>
                            )}
                            {req.proposedJobTitle && req.proposedJobTitle !== req.existingJobTitle && (
                              <tr className="hover:bg-blue-50/20">
                                <td className="py-2.5 px-4 font-semibold text-gray-900">Job Title</td>
                                <td className="py-2.5 px-4 text-gray-500">{req.existingJobTitle || '—'}</td>
                                <td className="py-2.5 px-4 font-bold text-blue-700 flex items-center gap-1.5">
                                  <ArrowRight className="w-3.5 h-3.5 text-blue-500 shrink-0" />
                                  {req.proposedJobTitle}
                                </td>
                              </tr>
                            )}
                            {req.proposedDepartment && req.proposedDepartment !== req.existingDepartment && (
                              <tr className="hover:bg-blue-50/20">
                                <td className="py-2.5 px-4 font-semibold text-gray-900">Department</td>
                                <td className="py-2.5 px-4 text-gray-500">{req.existingDepartment || '—'}</td>
                                <td className="py-2.5 px-4 font-bold text-blue-700 flex items-center gap-1.5">
                                  <ArrowRight className="w-3.5 h-3.5 text-blue-500 shrink-0" />
                                  {req.proposedDepartment}
                                </td>
                              </tr>
                            )}
                            {req.proposedPhone && req.proposedPhone !== req.existingPhone && (
                              <tr className="hover:bg-blue-50/20">
                                <td className="py-2.5 px-4 font-semibold text-gray-900">Phone Number</td>
                                <td className="py-2.5 px-4 text-gray-500">{req.existingPhone || '—'}</td>
                                <td className="py-2.5 px-4 font-bold text-blue-700 flex items-center gap-1.5">
                                  <ArrowRight className="w-3.5 h-3.5 text-blue-500 shrink-0" />
                                  {req.proposedPhone}
                                </td>
                              </tr>
                            )}
                            {req.proposedWorkEmail && req.proposedWorkEmail !== req.existingWorkEmail && (
                              <tr className="hover:bg-blue-50/20">
                                <td className="py-2.5 px-4 font-semibold text-gray-900">Work Email</td>
                                <td className="py-2.5 px-4 text-gray-500">{req.existingWorkEmail || '—'}</td>
                                <td className="py-2.5 px-4">
                                  <div className="flex items-center gap-2">
                                    <span className="font-bold text-blue-700 flex items-center gap-1.5">
                                      <ArrowRight className="w-3.5 h-3.5 text-blue-500 shrink-0" />
                                      {req.proposedWorkEmail}
                                    </span>
                                    {req.emailVerified ? (
                                      <span className="bg-emerald-100 text-emerald-800 text-[10px] font-bold px-2 py-0.5 rounded-full flex items-center gap-1">
                                        <CheckCircle className="w-3 h-3 text-emerald-600" />
                                        Token Verified
                                      </span>
                                    ) : (
                                      <span className="bg-amber-100 text-amber-800 text-[10px] font-bold px-2 py-0.5 rounded-full flex items-center gap-1">
                                        <AlertTriangle className="w-3 h-3 text-amber-600" />
                                        Verification Pending
                                      </span>
                                    )}
                                  </div>
                                </td>
                              </tr>
                            )}
                            {req.proposedAvatarUrl && req.proposedAvatarUrl !== req.existingAvatarUrl && (
                              <tr className="hover:bg-blue-50/20">
                                <td className="py-2.5 px-4 font-semibold text-gray-900">Avatar URL</td>
                                <td className="py-2.5 px-4 text-gray-500 truncate max-w-xs">{req.existingAvatarUrl || '—'}</td>
                                <td className="py-2.5 px-4 font-bold text-blue-700 flex items-center gap-1.5 truncate max-w-xs">
                                  <ArrowRight className="w-3.5 h-3.5 text-blue-500 shrink-0" />
                                  {req.proposedAvatarUrl}
                                </td>
                              </tr>
                            )}
                          </tbody>
                        </table>
                      </div>

                      {/* Submitter note if any */}
                      {req.submissionComment && (
                        <div className="bg-slate-50 p-3 rounded-lg border border-slate-200 text-xs">
                          <span className="font-bold text-gray-700 block mb-0.5">Employee Submission Note:</span>
                          <span className="text-gray-600 italic">"{req.submissionComment}"</span>
                        </div>
                      )}

                      {/* Decision Review Console for PENDING */}
                      {isPending && (
                        <div className="pt-3 border-t border-gray-100 space-y-3">
                          {isRequesterSelf ? (
                            <div className="p-3 bg-amber-50 border border-amber-200 rounded-lg text-xs text-amber-800 flex items-center gap-2">
                              <AlertCircle className="w-4 h-4 text-amber-600 shrink-0" />
                              <span>
                                <strong>Self-approval prohibited:</strong> This request affects your own official profile. Another authorized organization administrator or owner must review and approve it.
                              </span>
                            </div>
                          ) : (
                            <div>
                              <label className="block text-xs font-semibold text-gray-700 mb-1">
                                Review Decision Comment / Rejection Reason (Optional):
                              </label>
                              <input
                                type="text"
                                placeholder="Add an audit note or reason for decision..."
                                value={reviewComments[req.id] || ''}
                                onChange={(e) => setReviewComments({ ...reviewComments, [req.id]: e.target.value })}
                                className="w-full px-3 py-1.5 border border-gray-300 rounded-lg text-xs text-gray-900 focus:ring-1 focus:ring-blue-500"
                              />
                            </div>
                          )}

                          <div className="flex justify-end gap-2">
                            <button
                              disabled={processingRequestId === req.id}
                              onClick={() => handleRejectProfileRequest(req.id)}
                              className="px-3.5 py-1.5 border border-red-200 bg-red-50 hover:bg-red-100 text-red-700 font-semibold rounded-lg text-xs flex items-center gap-1.5 transition"
                            >
                              <XCircle className="w-3.5 h-3.5" />
                              <span>Reject Request</span>
                            </button>

                            <button
                              disabled={isRequesterSelf || processingRequestId === req.id || (req.proposedWorkEmail !== req.existingWorkEmail && !req.emailVerified)}
                              onClick={() => handleApproveProfileRequest(req.id)}
                              title={
                                isRequesterSelf
                                  ? 'Self-approval is forbidden'
                                  : (req.proposedWorkEmail !== req.existingWorkEmail && !req.emailVerified)
                                  ? 'Work email token verification required before approval'
                                  : 'Approve profile updates'
                              }
                              className="px-4 py-1.5 bg-blue-600 hover:bg-blue-700 disabled:bg-gray-300 disabled:cursor-not-allowed text-white font-semibold rounded-lg text-xs flex items-center gap-1.5 shadow-sm transition"
                            >
                              {processingRequestId === req.id ? (
                                <RefreshCw className="w-3.5 h-3.5 animate-spin" />
                              ) : (
                                <CheckCircle2 className="w-3.5 h-3.5" />
                              )}
                              <span>Approve & Apply to Official Profile</span>
                            </button>
                          </div>
                        </div>
                      )}

                      {/* Decision Details for Processed Requests */}
                      {!isPending && req.reviewedByUsername && (
                        <div className="pt-2 border-t border-gray-100 text-xs text-gray-500 flex flex-col sm:flex-row sm:items-center justify-between gap-2">
                          <div>
                            <span className="font-semibold text-gray-700">Reviewed by:</span> {req.reviewedByUsername}{' '}
                            {req.reviewedAt && (
                              <span>on {new Date(req.reviewedAt).toLocaleString()}</span>
                            )}
                          </div>
                          {req.reviewComment && (
                            <div className="italic text-gray-600">
                              Decision Note: "{req.reviewComment}"
                            </div>
                          )}
                        </div>
                      )}
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </div>
      )}

      {/* Invite Member Modal */}
      {showInviteModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/50 backdrop-blur-sm animate-in fade-in">
          <div className="bg-white rounded-2xl max-w-md w-full p-6 shadow-2xl border border-gray-100 space-y-4">
            <div className="flex items-center justify-between pb-3 border-b border-gray-100">
              <div className="flex items-center gap-2">
                <UserPlus className="w-5 h-5 text-blue-600" />
                <h3 className="text-base font-bold text-gray-900">Provision Employee Account</h3>
              </div>
              <button 
                type="button" 
                onClick={() => setShowInviteModal(false)}
                className="text-gray-400 hover:text-gray-600 font-bold"
              >
                ✕
              </button>
            </div>

            {newInviteResult ? (
              <div className="space-y-4 text-xs">
                <div className="p-4 rounded-xl bg-emerald-50 border border-emerald-200 text-emerald-900 space-y-2">
                  <div className="flex items-center gap-2 font-bold text-sm">
                    <CheckCircle className="w-4 h-4 text-emerald-600" />
                    <span>Employee Account Provisioned!</span>
                  </div>
                  <p className="text-emerald-700 leading-relaxed">
                    A secure, single-use invitation token has been generated. The employee can use this link to establish their password.
                  </p>
                  <div className="pt-2 font-mono space-y-1">
                    <div>Assigned Employee ID: <strong className="text-emerald-900">{newInviteResult.employeeId}</strong></div>
                    <div>Work Email: <strong>{newInviteResult.email}</strong></div>
                  </div>
                </div>

                {newInviteResult.activationLink && (
                  <div>
                    <label className="block font-semibold text-gray-700 mb-1">One-Use Activation Link (Local Dev Preview)</label>
                    <div className="flex items-center gap-2">
                      <input
                        type="text"
                        readOnly
                        value={window.location.origin + newInviteResult.activationLink}
                        className="w-full px-3 py-2 bg-gray-50 border border-gray-200 rounded-lg text-xs font-mono text-gray-700 select-all"
                      />
                      <button
                        type="button"
                        onClick={() => copyToClipboard(newInviteResult.activationLink!)}
                        className="px-3 py-2 bg-blue-600 text-white rounded-lg flex items-center gap-1 font-medium hover:bg-blue-700 shrink-0"
                      >
                        {copiedLink ? <Check className="w-3.5 h-3.5" /> : <Copy className="w-3.5 h-3.5" />}
                        <span>{copiedLink ? 'Copied' : 'Copy'}</span>
                      </button>
                    </div>
                  </div>
                )}

                <div className="pt-3">
                  <button
                    type="button"
                    onClick={() => {
                      setShowInviteModal(false);
                      setNewInviteResult(null);
                    }}
                    className="w-full py-2 bg-gray-100 hover:bg-gray-200 text-gray-800 rounded-lg font-semibold transition"
                  >
                    Done
                  </button>
                </div>
              </div>
            ) : (
              <form onSubmit={handleInviteMember} className="space-y-3.5 text-xs">
                <div>
                  <label className="block font-semibold text-gray-700 mb-1">Employee Work Email *</label>
                  <input
                    type="email"
                    required
                    placeholder="e.g. employee@acme.com"
                    value={inviteEmail}
                    onChange={(e) => setInviteEmail(e.target.value)}
                    className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm text-gray-900 focus:ring-1 focus:ring-blue-500"
                  />
                </div>

                <div>
                  <label className="block font-semibold text-gray-700 mb-1">Employee Full Name (optional)</label>
                  <input
                    type="text"
                    placeholder="e.g. Rachel Adams"
                    value={inviteFullName}
                    onChange={(e) => setInviteFullName(e.target.value)}
                    className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm text-gray-900 focus:ring-1 focus:ring-blue-500"
                  />
                </div>

                <div>
                  <label className="block font-semibold text-gray-700 mb-1">Organization Role *</label>
                  <select
                    value={inviteRole}
                    onChange={(e) => setInviteRole(e.target.value)}
                    className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm text-gray-900 focus:ring-1 focus:ring-blue-500"
                  >
                    <option value="WORKER">WORKER (Task Execution & Updates)</option>
                    <option value="PROJECT_MANAGER">PROJECT_MANAGER (Projects, Tasks, Scheduling)</option>
                    <option value="CLIENT">CLIENT (Review Deliverables & Progress)</option>
                    <option value="VENDOR">VENDOR (Procurement & Delivery Fulfillment)</option>
                    <option value="ORGANIZATION_ADMIN">ORGANIZATION_ADMIN (Member Provisioning & Settings)</option>
                  </select>
                </div>

                <div>
                  <label className="block font-semibold text-gray-700 mb-1">
                    Custom Employee ID <span className="font-normal text-gray-400">(optional - auto-generated if left blank)</span>
                  </label>
                  <input
                    type="text"
                    placeholder={`e.g. ${currentOrg.orgCode || 'SYN'}-EMP-0012`}
                    value={inviteCustomEmpId}
                    onChange={(e) => setInviteCustomEmpId(e.target.value.toUpperCase())}
                    className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm font-mono text-gray-900 focus:ring-1 focus:ring-blue-500"
                  />
                </div>

                <div className="pt-3 flex gap-2">
                  <button
                    type="button"
                    onClick={() => setShowInviteModal(false)}
                    className="flex-1 py-2 border border-gray-300 rounded-lg font-medium text-gray-700 hover:bg-gray-50 transition"
                  >
                    Cancel
                  </button>
                  <button
                    type="submit"
                    disabled={inviting}
                    className="flex-1 py-2 bg-blue-600 hover:bg-blue-700 text-white rounded-lg font-semibold flex items-center justify-center gap-1.5 transition shadow-sm"
                  >
                    {inviting ? <RefreshCw className="w-4 h-4 animate-spin" /> : <Send className="w-4 h-4" />}
                    <span>Issue Invitation</span>
                  </button>
                </div>
              </form>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
