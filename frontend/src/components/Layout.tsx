import React, { useState, useEffect, useRef } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { 
  LayoutDashboard, FolderKanban, Users, Briefcase, Truck, Calendar, 
  BarChart3, LogOut, Sparkles, Bot, TrendingUp, Building2, ChevronDown, Plus, Check, DollarSign, FileCheck, Share2, Zap,
  User as UserIcon, Settings as SettingsIcon, Shield as ShieldIcon, Brain
} from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import { useOrganization } from '../context/OrganizationContext';
import { NotificationBell } from './NotificationBell';
import { Modal, Button, Input, Alert } from './ui';
import { AiChatbotWidget } from './AiChatbotWidget';

interface NavItem {
  icon: React.ComponentType<{ className?: string }>;
  label: string;
  path: string;
  roles?: string[];
}

interface NavGroup {
  title: string;
  items: NavItem[];
}

const navGroups: NavGroup[] = [
  {
    title: 'WORKSPACE',
    items: [
      { icon: LayoutDashboard, label: 'Dashboard', path: '/' },
      { icon: FolderKanban, label: 'Projects', path: '/projects' },
      { icon: Calendar, label: 'Tasks', path: '/tasks' },
      { icon: BarChart3, label: 'Schedule', path: '/schedule' },
      { icon: Sparkles, label: 'Optimization', path: '/schedule?tab=optimization' },
    ],
  },
  {
    title: 'PEOPLE & RESOURCES',
    items: [
      { icon: Users, label: 'Clients', path: '/clients' },
      { icon: Briefcase, label: 'Workers', path: '/workers' },
      { icon: Truck, label: 'Vendors', path: '/vendors' },
      { icon: Building2, label: 'Organization', path: '/organization' },
    ],
  },
  {
    title: 'INSIGHTS & OPERATIONS',
    items: [
      { icon: TrendingUp, label: 'Analytics', path: '/analytics' },
      { icon: Brain, label: 'ML Delay Risk', path: '/ml-risk' },
      { icon: DollarSign, label: 'Cost & Budget', path: '/budget' },
      { icon: FileCheck, label: 'Deliverables & Approvals', path: '/deliverables' },
      { icon: Share2, label: 'Integrations & APIs', path: '/integrations' },
      { icon: Bot, label: 'AI Assistant', path: '/ai-assistant' },
    ],
  },
];

const routeTitles: Record<string, string> = {
  '/': 'Executive Dashboard',
  '/analytics': 'Portfolio & Resource Analytics',
  '/ml-risk': 'ML Delay Risk & Historical Outcomes',
  '/delay-risk': 'ML Delay Risk & Historical Outcomes',
  '/ai-assistant': 'AI Scheduling Assistant',
  '/assistant': 'AI Scheduling Assistant',
  '/budget': 'Cost & Budget Tracking',
  '/cost-management': 'Cost & Budget Tracking',
  '/deliverables': 'Deliverables & Stage-Gate Approvals',
  '/integrations': 'Integrations & External Sync',
  '/projects': 'Projects',
  '/clients': 'Clients',
  '/workers': 'Workforce & Skills Roster',
  '/vendors': 'Vendor Directory & POs',
  '/tasks': 'Task Planning & Execution',
  '/schedule': 'Schedule',
  '/organization': 'Workspace & Organization Settings',
};

export function Layout({ children }: { children: React.ReactNode }) {
  const { t } = useTranslation();
  const { user, logout } = useAuth();
  const { organizations, currentOrg, switchOrganization, createOrganization } = useOrganization();
  const location = useLocation();

  const [orgDropdownOpen, setOrgDropdownOpen] = useState(false);
  const [profileDropdownOpen, setProfileDropdownOpen] = useState(false);
  const profileMenuRef = useRef<HTMLDivElement>(null);

  const [showCreateModal, setShowCreateModal] = useState(false);
  const [newOrgName, setNewOrgName] = useState('');
  const [newOrgSlug, setNewOrgSlug] = useState('');
  const [creatingOrg, setCreatingOrg] = useState(false);
  const [createError, setCreateError] = useState<string | null>(null);

  // Close profile dropdown on outside click or Escape
  useEffect(() => {
    const handleClickOutside = (e: MouseEvent) => {
      if (profileMenuRef.current && !profileMenuRef.current.contains(e.target as Node)) {
        setProfileDropdownOpen(false);
      }
    };
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        setProfileDropdownOpen(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    document.addEventListener('keydown', handleKeyDown);
    return () => {
      document.removeEventListener('mousedown', handleClickOutside);
      document.removeEventListener('keydown', handleKeyDown);
    };
  }, []);

  useEffect(() => {
    const pageBrowserTitles: Record<string, string> = {
      '/': 'Dashboard',
      '/projects': 'Projects',
      '/schedule': 'Schedule',
      '/ai-assistant': 'AI Assistant',
      '/assistant': 'AI Assistant',
      '/ml-risk': 'ML Delay Risk',
      '/delay-risk': 'ML Delay Risk',
      '/clients': 'Clients',
      '/workers': 'Workers',
      '/vendors': 'Vendors',
      '/tasks': 'Tasks',
      '/analytics': 'Analytics',
      '/budget': 'Cost & Budget',
      '/cost-management': 'Cost & Budget',
      '/deliverables': 'Deliverables',
      '/integrations': 'Integrations',
      '/organization': 'Organization',
    };
    const title = pageBrowserTitles[location.pathname] || 'Dashboard';
    document.title = `SYNCORA | ${title}`;
  }, [location.pathname]);

  const handleCreateOrg = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!newOrgName.trim() || !newOrgSlug.trim()) return;
    try {
      setCreatingOrg(true);
      setCreateError(null);
      await createOrganization(newOrgName.trim(), newOrgSlug.trim());
      setShowCreateModal(false);
      setNewOrgName('');
      setNewOrgSlug('');
      setOrgDropdownOpen(false);
    } catch (err: any) {
      setCreateError(err.response?.data?.message || 'Failed to create workspace');
    } finally {
      setCreatingOrg(false);
    }
  };

  const getPageTitle = () => {
    if (routeTitles[location.pathname]) {
      return routeTitles[location.pathname];
    }
    const clean = location.pathname.replace(/^\//, '').replace(/-/g, ' ');
    return clean ? clean.charAt(0).toUpperCase() + clean.slice(1) : 'Dashboard';
  };

  return (
    <div className="flex h-screen bg-gray-50">
      <aside className="w-64 bg-white dark:bg-slate-900 border-r border-gray-200 dark:border-slate-800 flex flex-col flex-shrink-0">
        <Link 
          to="/" 
          id="syncora-logo-link"
          className="h-16 flex items-center px-6 border-b border-gray-200 dark:border-slate-800 gap-3 hover:bg-gray-50/80 dark:hover:bg-slate-800/50 transition-colors group focus:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 cursor-pointer"
          title="Return to Dashboard"
        >
          <div className="w-8 h-8 rounded-lg bg-blue-600 group-hover:bg-blue-700 transition-colors flex items-center justify-center text-white shadow-sm shadow-blue-500/20">
            <Zap className="w-4 h-4 fill-white" />
          </div>
          <div>
            <span className="text-xl font-black text-gray-900 dark:text-white tracking-tight group-hover:text-blue-600 dark:group-hover:text-blue-400 transition-colors">SYNCORA</span>
            <span className="block text-[8px] font-bold text-blue-600 dark:text-blue-400 tracking-wider uppercase -mt-0.5">Plan • Coordinate • Deliver</span>
          </div>
        </Link>
        <nav className="flex-1 overflow-y-auto py-3 px-3 space-y-4">
          {navGroups.map((group) => (
            <div key={group.title}>
              <div className="text-[10px] font-black text-gray-400 tracking-wider uppercase px-3 py-1">
                {group.title}
              </div>
              <ul className="space-y-0.5 mt-1">
                {group.items.map((item) => {
                  const currentFull = location.pathname + (location.search || '');
                  const isActive = item.path.includes('?')
                    ? currentFull === item.path
                    : location.pathname === item.path && !location.search;
                  return (
                    <li key={item.path}>
                      <Link
                        to={item.path}
                        className={`flex items-center gap-3 px-3 py-2 rounded-lg text-xs font-medium transition border-l-2 focus:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 ${
                          isActive 
                            ? 'bg-blue-50 text-blue-700 font-bold border-blue-600 shadow-2xs' 
                            : 'text-gray-600 hover:bg-gray-100 hover:text-gray-900 border-transparent'
                        }`}
                      >
                        <item.icon className={`w-4 h-4 flex-shrink-0 ${isActive ? 'text-blue-600' : 'text-gray-400'}`} />
                        <span className="truncate">{item.label}</span>
                      </Link>
                    </li>
                  );
                })}
              </ul>
            </div>
          ))}
        </nav>
      </aside>
      
      <div className="flex-1 flex flex-col min-w-0 overflow-hidden">
        <header className="h-16 bg-white border-b border-gray-200 flex items-center justify-between px-8">
          <div className="flex items-center gap-6">
            <h1 className="text-xl font-bold text-gray-900 tracking-tight">
              {getPageTitle()}
            </h1>

            {/* Organization Switcher Dropdown */}
            <div className="relative">
              <button
                onClick={() => setOrgDropdownOpen(!orgDropdownOpen)}
                className="flex items-center gap-2 px-3 py-1.5 bg-gray-50 border border-gray-300 rounded-lg hover:bg-gray-100 transition text-sm"
              >
                <Building2 className="w-4 h-4 text-blue-600" />
                <span className="font-semibold text-gray-800 max-w-[160px] truncate">
                  {currentOrg ? currentOrg.name : 'Select Workspace'}
                </span>
                {currentOrg?.status === 'SUSPENDED' && (
                  <span className="text-[10px] bg-red-100 text-red-700 px-1.5 py-0.2 rounded font-bold">SUSPENDED</span>
                )}
                <ChevronDown className="w-4 h-4 text-gray-400" />
              </button>

              {orgDropdownOpen && (
                <div className="absolute left-0 mt-2 w-72 bg-white rounded-xl shadow-xl border border-gray-200 py-2 z-50 animate-in fade-in zoom-in-95">
                  <div className="px-4 py-2 border-b border-gray-100 flex items-center justify-between">
                    <span className="text-xs font-semibold text-gray-500 uppercase tracking-wider">Workspaces</span>
                    <button
                      onClick={() => { setShowCreateModal(true); setOrgDropdownOpen(false); }}
                      className="text-xs text-blue-600 hover:text-blue-700 font-medium flex items-center gap-1"
                    >
                      <Plus className="w-3.5 h-3.5" /> New
                    </button>
                  </div>

                  <div className="max-h-60 overflow-y-auto py-1">
                    {organizations.length === 0 ? (
                      <div className="px-4 py-6 text-center text-xs text-gray-500">
                        <p className="font-semibold text-gray-700">No workspaces available</p>
                        <p className="mt-1 text-gray-400">Create a new workspace to get started.</p>
                      </div>
                    ) : (
                      organizations.map((org) => {
                        const isSelected = currentOrg?.id === org.id;
                        return (
                          <button
                            key={org.id}
                            onClick={() => { switchOrganization(org); setOrgDropdownOpen(false); }}
                            className={`w-full px-4 py-2 text-left flex items-center justify-between hover:bg-gray-50 text-sm transition ${
                              isSelected ? 'bg-blue-50 text-blue-700 font-semibold' : 'text-gray-700'
                            }`}
                          >
                            <div className="truncate">
                              <div className="font-medium truncate flex items-center gap-1.5">
                                <span>{org.name}</span>
                                {org.orgCode && (
                                  <span className="text-[10px] font-mono font-bold text-gray-400 bg-gray-100 px-1 py-0.2 rounded">
                                    {org.orgCode}
                                  </span>
                                )}
                              </div>
                              <div className="text-xs text-gray-400 font-mono">{org.slug}</div>
                            </div>
                            {isSelected && <Check className="w-4 h-4 text-blue-600 flex-shrink-0" />}
                          </button>
                        );
                      })
                    )}
                  </div>

                  <div className="border-t border-gray-100 mt-1 pt-1 px-3">
                    <Link
                      to="/organization"
                      onClick={() => setOrgDropdownOpen(false)}
                      className="block text-center py-1.5 text-xs text-gray-600 hover:text-blue-600 font-medium"
                    >
                      Workspace Settings & Members
                    </Link>
                  </div>
                </div>
              )}
            </div>
          </div>

          <div className="flex items-center gap-6">
            <NotificationBell />
            {/* Top-Right Clickable Profile Dropdown Area */}
            <div className="relative pl-6 border-l border-gray-200 dark:border-slate-800" ref={profileMenuRef}>
              <button
                type="button"
                onClick={() => setProfileDropdownOpen(!profileDropdownOpen)}
                className="flex items-center gap-3 p-1 rounded-lg hover:bg-gray-100 dark:hover:bg-slate-800 transition focus:outline-none focus:ring-2 focus:ring-blue-500"
                aria-expanded={profileDropdownOpen}
                aria-haspopup="true"
                id="top-profile-menu-button"
              >
                <div className="w-8 h-8 rounded-full bg-blue-600 text-white flex items-center justify-center font-bold text-sm shadow-sm">
                  {user?.username.charAt(0).toUpperCase()}
                </div>
                <div className="flex flex-col text-left">
                  <span className="text-sm font-semibold text-gray-800 dark:text-slate-200 leading-tight">
                    {user?.username}
                  </span>
                  <span className="text-[11px] text-gray-500 dark:text-slate-400">
                    {currentOrg?.currentUserRole || currentOrg?.userRole || user?.role}
                  </span>
                </div>
                <ChevronDown className="w-4 h-4 text-gray-400 ml-1" />
              </button>

              {profileDropdownOpen && (
                <div className="absolute right-0 mt-2 w-64 bg-white dark:bg-slate-900 rounded-xl shadow-xl border border-gray-200 dark:border-slate-800 py-2 z-50 animate-in fade-in zoom-in-95">
                  {/* User info banner */}
                  <div className="px-4 py-2.5 border-b border-gray-100 dark:border-slate-800">
                    <div className="text-sm font-bold text-gray-900 dark:text-white truncate">
                      {user?.username}
                    </div>
                    <div className="text-xs text-gray-500 dark:text-slate-400 truncate mt-0.5">
                      {user?.email || (user?.username + '@syncora.internal')}
                    </div>
                    <div className="mt-1.5 flex items-center gap-1.5">
                      <span className="text-[10px] font-semibold uppercase tracking-wider bg-blue-100 dark:bg-blue-950/60 text-blue-700 dark:text-blue-300 px-2 py-0.5 rounded-full">
                        {currentOrg?.currentUserRole || currentOrg?.userRole || user?.role}
                      </span>
                    </div>
                  </div>

                  {/* Navigation Links */}
                  <div className="py-1 text-xs">
                    <Link
                      to="/settings?tab=profile"
                      onClick={() => setProfileDropdownOpen(false)}
                      className="flex items-center gap-2.5 px-4 py-2 text-gray-700 dark:text-slate-300 hover:bg-gray-50 dark:hover:bg-slate-800 transition"
                    >
                      <UserIcon className="w-4 h-4 text-gray-400" />
                      <span>{t('profileMenu.myProfile')}</span>
                    </Link>

                    <Link
                      to="/settings?tab=appearance"
                      onClick={() => setProfileDropdownOpen(false)}
                      className="flex items-center gap-2.5 px-4 py-2 text-gray-700 dark:text-slate-300 hover:bg-gray-50 dark:hover:bg-slate-800 transition"
                    >
                      <SettingsIcon className="w-4 h-4 text-gray-400" />
                      <span>{t('profileMenu.preferencesSettings')}</span>
                    </Link>

                    <Link
                      to="/settings?tab=security"
                      onClick={() => setProfileDropdownOpen(false)}
                      className="flex items-center gap-2.5 px-4 py-2 text-gray-700 dark:text-slate-300 hover:bg-gray-50 dark:hover:bg-slate-800 transition"
                    >
                      <ShieldIcon className="w-4 h-4 text-gray-400" />
                      <span>{t('profileMenu.accountSecurity')}</span>
                    </Link>

                    {/* Organization Settings only for authorized roles */}
                    {(user?.role === 'ADMIN' || 
                      currentOrg?.currentUserRole === 'ORGANIZATION_OWNER' || 
                      currentOrg?.currentUserRole === 'ORGANIZATION_ADMIN' ||
                      currentOrg?.userRole === 'ORGANIZATION_OWNER' || 
                      currentOrg?.userRole === 'ORGANIZATION_ADMIN') && (
                      <Link
                        to="/organization"
                        onClick={() => setProfileDropdownOpen(false)}
                        className="flex items-center gap-2.5 px-4 py-2 text-gray-700 dark:text-slate-300 hover:bg-gray-50 dark:hover:bg-slate-800 transition border-t border-gray-100 dark:border-slate-800/60 my-1"
                      >
                        <Building2 className="w-4 h-4 text-gray-400" />
                        <span>{t('profileMenu.organizationSettings')}</span>
                      </Link>
                    )}
                  </div>

                  {/* Sign Out */}
                  <div className="border-t border-gray-100 dark:border-slate-800 pt-1">
                    <button
                      onClick={() => {
                        setProfileDropdownOpen(false);
                        logout();
                      }}
                      className="w-full flex items-center gap-2.5 px-4 py-2 text-xs font-medium text-red-600 dark:text-red-400 hover:bg-red-50 dark:hover:bg-red-950/20 transition text-left"
                    >
                      <LogOut className="w-4 h-4" />
                      <span>{t('profileMenu.signOut')}</span>
                    </button>
                  </div>
                </div>
              )}
            </div>
          </div>
        </header>
        <main className="flex-1 overflow-y-auto p-8">
          {children}
        </main>
      </div>

      {/* Floating AI Assistant Chatbot (Bottom-Right) */}
      <AiChatbotWidget />

      {/* Create Organization Modal */}
      <Modal
        isOpen={showCreateModal}
        onClose={() => setShowCreateModal(false)}
        title="Create New Organization Workspace"
        description="Create an isolated organization. You will automatically become its Organization Administrator."
        maxWidth="md"
      >
        {createError && (
          <div className="mb-4">
            <Alert type="error" title="Creation Failed">
              {createError}
            </Alert>
          </div>
        )}

        <form onSubmit={handleCreateOrg} className="space-y-4">
          <Input
            label="Organization Name"
            type="text"
            required
            placeholder="e.g. Wayne Enterprises"
            value={newOrgName}
            onChange={(e) => {
              setNewOrgName(e.target.value);
              if (!newOrgSlug) {
                setNewOrgSlug(e.target.value.toLowerCase().replace(/[^a-z0-9]/g, '-'));
              }
            }}
          />

          <Input
            label="Workspace Slug (Unique identifier)"
            type="text"
            required
            placeholder="e.g. wayne-enterprises"
            value={newOrgSlug}
            onChange={(e) => setNewOrgSlug(e.target.value)}
            helperText="Lowercase alphanumeric letters and hyphens only."
          />

          <div className="flex justify-end gap-3 pt-3 border-t border-gray-100">
            <Button
              type="button"
              variant="outline"
              onClick={() => setShowCreateModal(false)}
            >
              Cancel
            </Button>
            <Button
              type="submit"
              variant="primary"
              isLoading={creatingOrg}
            >
              Create Workspace
            </Button>
          </div>
        </form>
      </Modal>
    </div>
  );
}
