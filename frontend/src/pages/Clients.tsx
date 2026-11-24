import React, { useState, useEffect, useMemo } from 'react';
import { api } from '../services/api';
import { useOrganization } from '../context/OrganizationContext';
import { useNavigate } from 'react-router-dom';
import {
  Users,
  Plus,
  Search,
  Building2,
  Mail,
  Phone,
  MapPin,
  Edit2,
  Trash2,
  CheckCircle2,
  Briefcase,
  Eye,
  Calendar,
  AlertTriangle,
  Archive,
  RefreshCw,
  X,
  FolderKanban,
  FileCheck,
  ExternalLink,
} from 'lucide-react';
import {
  Button,
  Card,
  Badge,
  Modal,
  Input,
  Select,
  EmptyState,
  LoadingSpinner,
  Alert,
} from '../components/ui';

interface Client {
  id: number;
  name: string;
  company?: string;
  email?: string;
  phone?: string;
  address?: string;
  status?: string;
  projectCount?: number;
  activeProjectCount?: number;
  createdAt?: string;
  updatedAt?: string;
}

interface ClientProjectSummary {
  id: number;
  name: string;
  description?: string;
  status?: string;
  priority?: string;
  progress?: number;
  startDate?: string;
  deadline?: string;
  expectedCompletionDate?: string;
  managerId?: number;
  managerName?: string;
}

interface ClientDeliverableSummary {
  id: number;
  projectId: number;
  projectName: string;
  name: string;
  description?: string;
  deliverableType?: string;
  status?: string;
  requiredSubmissionDate?: string;
  actualSubmissionDate?: string;
  currentVersionNumber?: number;
}

interface ClientDetail {
  id: number;
  name: string;
  company?: string;
  email?: string;
  phone?: string;
  address?: string;
  status?: string;
  createdAt?: string;
  updatedAt?: string;
  totalProjects: number;
  activeProjects: number;
  projects: ClientProjectSummary[];
  deliverables: ClientDeliverableSummary[];
}

export function Clients() {
  const navigate = useNavigate();
  const { currentOrg } = useOrganization();
  const [clients, setClients] = useState<Client[]>([]);
  const [loading, setLoading] = useState(true);
  const [searchQuery, setSearchQuery] = useState('');
  const [statusFilter, setStatusFilter] = useState<'ALL' | 'ACTIVE' | 'INACTIVE'>('ALL');
  const [alertMessage, setAlertMessage] = useState<{ type: 'success' | 'error'; text: string } | null>(null);

  // Modal states
  const [isCreateModalOpen, setIsCreateModalOpen] = useState(false);
  const [isEditModalOpen, setIsEditModalOpen] = useState(false);
  const [isDetailsModalOpen, setIsDetailsModalOpen] = useState(false);
  const [isDeleteModalOpen, setIsDeleteModalOpen] = useState(false);
  const [selectedClient, setSelectedClient] = useState<Client | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  // Client Details state
  const [clientDetails, setClientDetails] = useState<ClientDetail | null>(null);
  const [detailsLoading, setDetailsLoading] = useState(false);
  const [detailsError, setDetailsError] = useState<string | null>(null);

  // Form state
  const [formData, setFormData] = useState({
    name: '',
    company: '',
    email: '',
    phone: '',
    address: '',
    status: 'ACTIVE',
  });
  const [formErrors, setFormErrors] = useState<Record<string, string>>({});

  const loadClients = async () => {
    try {
      setLoading(true);
      const res = await api.get('/clients');
      setClients(res.data || []);
    } catch (err: any) {
      console.error('Failed to load clients:', err);
      setAlertMessage({ type: 'error', text: 'Failed to load clients. Please check your connection and active organization.' });
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadClients();
  }, [currentOrg?.id]);

  const loadClientDetails = async (clientId: number) => {
    try {
      setDetailsLoading(true);
      setDetailsError(null);
      const res = await api.get(`/clients/${clientId}/details`);
      setClientDetails(res.data);
    } catch (err: any) {
      console.error('Failed to load client details:', err);
      setDetailsError(err.response?.data?.message || 'Failed to load client details from backend.');
    } finally {
      setDetailsLoading(false);
    }
  };

  const handleOpenDetails = (client: Client) => {
    setSelectedClient(client);
    setClientDetails(null);
    setIsDetailsModalOpen(true);
    loadClientDetails(client.id);
  };

  const handleOpenCreate = () => {
    setFormData({
      name: '',
      company: '',
      email: '',
      phone: '',
      address: '',
      status: 'ACTIVE',
    });
    setFormErrors({});
    setIsCreateModalOpen(true);
  };

  const handleOpenEdit = (client: Client) => {
    setSelectedClient(client);
    setFormData({
      name: client.name || '',
      company: client.company || '',
      email: client.email || '',
      phone: client.phone || '',
      address: client.address || '',
      status: client.status || 'ACTIVE',
    });
    setFormErrors({});
    setIsEditModalOpen(true);
  };

  const handleOpenDelete = (client: Client) => {
    setSelectedClient(client);
    setIsDeleteModalOpen(true);
  };

  const validateForm = () => {
    const errors: Record<string, string> = {};
    const trimmedName = formData.name.trim();

    if (!trimmedName) {
      errors.name = 'Client contact name is required.';
    } else if (trimmedName.length > 100) {
      errors.name = 'Client contact name cannot exceed 100 characters.';
    }

    if (formData.company && formData.company.trim().length > 100) {
      errors.company = 'Company name cannot exceed 100 characters.';
    }

    if (formData.email && formData.email.trim()) {
      const emailRegex = /^[A-Za-z0-9+_.-]+@([A-Za-z0-9.-]+\.[A-Za-z]{2,})$/;
      if (!emailRegex.test(formData.email.trim())) {
        errors.email = 'Please provide a valid email address (e.g. contact@example.com).';
      } else if (formData.email.trim().length > 100) {
        errors.email = 'Email address cannot exceed 100 characters.';
      }
    }

    if (formData.phone && formData.phone.trim().length > 30) {
      errors.phone = 'Phone number cannot exceed 30 characters.';
    }

    if (formData.address && formData.address.trim().length > 255) {
      errors.address = 'Address cannot exceed 255 characters.';
    }

    setFormErrors(errors);
    return Object.keys(errors).length === 0;
  };

  const handleCreateSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (isSubmitting) return;
    if (!validateForm()) return;

    try {
      setIsSubmitting(true);
      await api.post('/clients', {
        name: formData.name.trim(),
        company: formData.company.trim() || undefined,
        email: formData.email.trim() || undefined,
        phone: formData.phone.trim() || undefined,
        address: formData.address.trim() || undefined,
        status: formData.status,
      });
      setAlertMessage({ type: 'success', text: `Client "${formData.name.trim()}" created successfully.` });
      setIsCreateModalOpen(false);
      await loadClients();
    } catch (err: any) {
      setAlertMessage({ type: 'error', text: err.response?.data?.message || 'Failed to create client.' });
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleEditSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (isSubmitting || !selectedClient) return;
    if (!validateForm()) return;

    try {
      setIsSubmitting(true);
      await api.put(`/clients/${selectedClient.id}`, {
        ...selectedClient,
        name: formData.name.trim(),
        company: formData.company.trim() || undefined,
        email: formData.email.trim() || undefined,
        phone: formData.phone.trim() || undefined,
        address: formData.address.trim() || undefined,
        status: formData.status,
      });
      setAlertMessage({ type: 'success', text: `Client "${formData.name.trim()}" updated successfully.` });
      setIsEditModalOpen(false);
      await loadClients();
      if (isDetailsModalOpen && selectedClient.id) {
        await loadClientDetails(selectedClient.id);
      }
    } catch (err: any) {
      setAlertMessage({ type: 'error', text: err.response?.data?.message || 'Failed to update client.' });
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleArchiveConfirm = async () => {
    if (!selectedClient || isSubmitting) return;

    try {
      setIsSubmitting(true);
      await api.put(`/clients/${selectedClient.id}/archive`);
      setAlertMessage({
        type: 'success',
        text: `Client "${selectedClient.name}" archived successfully (Status set to INACTIVE). Associated project history is preserved.`,
      });
      setIsDeleteModalOpen(false);
      await loadClients();
    } catch (err: any) {
      setAlertMessage({ type: 'error', text: err.response?.data?.message || 'Failed to archive client.' });
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleDeleteConfirm = async () => {
    if (!selectedClient || isSubmitting) return;

    try {
      setIsSubmitting(true);
      await api.delete(`/clients/${selectedClient.id}`);
      setAlertMessage({ type: 'success', text: `Client "${selectedClient.name}" permanently deleted.` });
      setIsDeleteModalOpen(false);
      await loadClients();
    } catch (err: any) {
      setAlertMessage({
        type: 'error',
        text: err.response?.data?.message || 'Failed to delete client. The record was preserved.',
      });
    } finally {
      setIsSubmitting(false);
    }
  };

  // Filter clients by search query and status filter
  const filteredClients = useMemo(() => {
    return clients.filter((c) => {
      const q = searchQuery.toLowerCase().trim();
      const matchesSearch =
        !q ||
        c.name.toLowerCase().includes(q) ||
        (c.company && c.company.toLowerCase().includes(q)) ||
        (c.email && c.email.toLowerCase().includes(q));

      const matchesStatus =
        statusFilter === 'ALL' ||
        (statusFilter === 'ACTIVE' && c.status !== 'INACTIVE') ||
        (statusFilter === 'INACTIVE' && c.status === 'INACTIVE');

      return matchesSearch && matchesStatus;
    });
  }, [clients, searchQuery, statusFilter]);

  // Overall statistics (independent of filtered list)
  const totalClientsCount = clients.length;
  const activeClientsCount = clients.filter((c) => c.status !== 'INACTIVE').length;
  const uniqueCompaniesCount = useMemo(() => {
    const companies = new Set<string>();
    clients.forEach((c) => {
      const companyTrimmed = c.company?.trim();
      if (companyTrimmed) {
        companies.add(companyTrimmed.toLowerCase());
      }
    });
    return companies.size;
  }, [clients]);

  const hasActiveFilters = searchQuery.trim().length > 0 || statusFilter !== 'ALL';

  const handleClearFilters = () => {
    setSearchQuery('');
    setStatusFilter('ALL');
  };

  const getStatusBadge = (status?: string) => {
    const isInactive = status === 'INACTIVE';
    return (
      <Badge variant={isInactive ? 'neutral' : 'success'} size="sm" dot>
        {isInactive ? 'INACTIVE' : 'ACTIVE'}
      </Badge>
    );
  };

  const getProjectStatusBadge = (status?: string) => {
    switch (status) {
      case 'COMPLETED':
        return <Badge variant="success" size="sm">Completed</Badge>;
      case 'IN_PROGRESS':
        return <Badge variant="info" size="sm">In Progress</Badge>;
      case 'BLOCKED':
        return <Badge variant="danger" size="sm">Blocked</Badge>;
      case 'CANCELLED':
        return <Badge variant="neutral" size="sm">Cancelled</Badge>;
      default:
        return <Badge variant="warning" size="sm">Planned</Badge>;
    }
  };

  return (
    <div className="p-8 space-y-6 max-w-7xl mx-auto pb-28">
      {/* Top Banner */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-black text-gray-900 tracking-tight flex items-center gap-2.5">
            <Users className="w-7 h-7 text-blue-600" />
            Clients
          </h1>
          <p className="text-sm text-gray-500 mt-1">
            Manage external partner accounts, commissioning organizations, and client contacts.
          </p>
        </div>
        <Button
          variant="primary"
          size="md"
          leftIcon={<Plus className="w-4 h-4" />}
          onClick={handleOpenCreate}
          aria-label="Add New Client"
        >
          New Client
        </Button>
      </div>

      {alertMessage && (
        <Alert
          type={alertMessage.type}
          onClose={() => setAlertMessage(null)}
          title={alertMessage.type === 'success' ? 'Success' : 'Notice'}
        >
          {alertMessage.text}
        </Alert>
      )}

      {/* KPI Stats Grid - derived directly from backend data */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <Card className="p-5">
          <div className="flex items-center justify-between">
            <span className="text-xs font-bold text-gray-400 uppercase tracking-wider">Total Clients</span>
            <Users className="w-4 h-4 text-gray-400" />
          </div>
          <div className="text-2xl font-black text-gray-900 mt-1.5">{totalClientsCount}</div>
          <span className="text-xs text-gray-500 mt-0.5 block">Registered partner accounts</span>
        </Card>

        <Card className="p-5">
          <div className="flex items-center justify-between">
            <span className="text-xs font-bold text-emerald-600 uppercase tracking-wider">Active Status</span>
            <CheckCircle2 className="w-4 h-4 text-emerald-500" />
          </div>
          <div className="text-2xl font-black text-emerald-600 mt-1.5">{activeClientsCount}</div>
          <span className="text-xs text-gray-500 mt-0.5 block">Eligible for active project assignments</span>
        </Card>

        <Card className="p-5">
          <div className="flex items-center justify-between">
            <span className="text-xs font-bold text-purple-600 uppercase tracking-wider">Companies</span>
            <Building2 className="w-4 h-4 text-purple-500" />
          </div>
          <div className="text-2xl font-black text-purple-600 mt-1.5">{uniqueCompaniesCount}</div>
          <span className="text-xs text-gray-500 mt-0.5 block">Distinct corporate entities (excluding blank)</span>
        </Card>
      </div>

      {/* Search and Filters Bar */}
      <Card className="p-4">
        <div className="flex flex-col sm:flex-row items-stretch sm:items-center justify-between gap-3">
          <div className="flex flex-col sm:flex-row items-stretch sm:items-center gap-3 flex-1">
            <div className="w-full sm:max-w-md">
              <Input
                placeholder="Search by client name, company, or email..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                leftIcon={<Search className="w-4 h-4" />}
                rightIcon={
                  searchQuery ? (
                    <button
                      onClick={() => setSearchQuery('')}
                      className="p-1 hover:text-gray-700 transition"
                      aria-label="Clear search input"
                    >
                      <X className="w-4 h-4" />
                    </button>
                  ) : undefined
                }
                aria-label="Search clients"
              />
            </div>

            <div className="w-full sm:w-48">
              <Select
                value={statusFilter}
                onChange={(e) => setStatusFilter(e.target.value as any)}
                aria-label="Filter by account status"
              >
                <option value="ALL">All Statuses</option>
                <option value="ACTIVE">Active Only</option>
                <option value="INACTIVE">Inactive Only</option>
              </Select>
            </div>

            {hasActiveFilters && (
              <Button
                variant="outline"
                size="sm"
                leftIcon={<X className="w-3.5 h-3.5" />}
                onClick={handleClearFilters}
                className="self-center sm:self-auto text-xs"
                aria-label="Clear search and filters"
              >
                Clear Filters
              </Button>
            )}
          </div>

          <div className="flex items-center justify-between sm:justify-end gap-2 text-xs text-gray-500 pt-2 sm:pt-0 border-t sm:border-t-0 border-gray-100">
            <span>
              Showing <strong className="text-gray-900">{filteredClients.length}</strong> of{' '}
              <strong className="text-gray-900">{clients.length}</strong> clients
            </span>
            <button
              onClick={loadClients}
              className="p-1.5 text-gray-400 hover:text-gray-700 hover:bg-gray-100 rounded-lg transition"
              title="Refresh clients list"
              aria-label="Refresh clients list"
            >
              <RefreshCw className="w-3.5 h-3.5" />
            </button>
          </div>
        </div>
      </Card>

      {/* Clients Table */}
      {loading ? (
        <LoadingSpinner label="Loading clients directory..." size="lg" />
      ) : filteredClients.length === 0 ? (
        <EmptyState
          icon={<Users className="w-10 h-10 text-gray-400" />}
          title={hasActiveFilters ? 'No matching clients found' : 'No clients registered yet'}
          description={
            hasActiveFilters
              ? 'Try adjusting your search criteria or resetting the status filter.'
              : 'Add your first client organization to associate with projects, deadlines, and deliverables.'
          }
          actionLabel={hasActiveFilters ? 'Reset Filters' : 'Add First Client'}
          onAction={hasActiveFilters ? handleClearFilters : handleOpenCreate}
        />
      ) : (
        <Card className="overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm text-gray-600">
              <thead className="bg-gray-50/80 text-xs font-bold text-gray-500 uppercase border-b border-gray-200">
                <tr>
                  <th className="px-6 py-4">Client / Contact</th>
                  <th className="px-6 py-4">Company</th>
                  <th className="px-6 py-4">Contact Info</th>
                  <th className="px-6 py-4">Address</th>
                  <th className="px-6 py-4">Status</th>
                  <th className="px-6 py-4">Projects</th>
                  <th className="px-6 py-4 text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {filteredClients.map((client) => {
                  const linkedProjectsCount = client.projectCount ?? 0;
                  return (
                    <tr key={client.id} className="hover:bg-gray-50/80 transition-colors">
                      <td className="px-6 py-4">
                        <button
                          onClick={() => handleOpenDetails(client)}
                          className="font-bold text-gray-900 hover:text-blue-600 text-left flex items-center gap-1.5 group transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 rounded"
                          title="Click to view client details & associated projects"
                          aria-label={`View details for ${client.name}`}
                        >
                          <span>{client.name}</span>
                          <Eye className="w-3.5 h-3.5 text-gray-400 opacity-0 group-hover:opacity-100 transition-opacity" />
                        </button>
                      </td>

                      <td className="px-6 py-4">
                        <div className="flex items-center gap-1.5 text-gray-700">
                          <Building2 className="w-3.5 h-3.5 text-gray-400 shrink-0" />
                          <span className="truncate max-w-[180px]">{client.company || '—'}</span>
                        </div>
                      </td>

                      <td className="px-6 py-4 text-xs">
                        <div className="space-y-1">
                          {client.email ? (
                            <div className="flex items-center gap-1.5 text-gray-600">
                              <Mail className="w-3.5 h-3.5 text-gray-400 shrink-0" />
                              <a
                                href={`mailto:${client.email}`}
                                className="hover:text-blue-600 hover:underline truncate max-w-[190px]"
                                title={client.email}
                              >
                                {client.email}
                              </a>
                            </div>
                          ) : (
                            <span className="text-gray-400 italic">No email</span>
                          )}
                          {client.phone && (
                            <div className="flex items-center gap-1.5 text-gray-600">
                              <Phone className="w-3.5 h-3.5 text-gray-400 shrink-0" />
                              <a
                                href={`tel:${client.phone}`}
                                className="hover:text-blue-600 hover:underline"
                              >
                                {client.phone}
                              </a>
                            </div>
                          )}
                        </div>
                      </td>

                      <td className="px-6 py-4 text-xs text-gray-500">
                        <div className="flex items-center gap-1.5 max-w-[200px] truncate" title={client.address || ''}>
                          <MapPin className="w-3.5 h-3.5 text-gray-400 shrink-0" />
                          <span className="truncate">{client.address || '—'}</span>
                        </div>
                      </td>

                      <td className="px-6 py-4">
                        {getStatusBadge(client.status)}
                      </td>

                      <td className="px-6 py-4">
                        <button
                          onClick={() => handleOpenDetails(client)}
                          className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-semibold bg-gray-100 text-gray-700 hover:bg-blue-50 hover:text-blue-700 transition"
                          title="View associated projects"
                          aria-label={`${linkedProjectsCount} projects associated`}
                        >
                          <FolderKanban className="w-3 h-3 text-gray-500" />
                          <span>{linkedProjectsCount}</span>
                        </button>
                      </td>

                      <td className="px-6 py-4 text-right">
                        <div className="flex items-center justify-end gap-1">
                          <button
                            onClick={() => handleOpenDetails(client)}
                            title="View Client Details"
                            aria-label={`View details for ${client.name}`}
                            className="p-1.5 text-gray-500 hover:text-blue-600 hover:bg-blue-50 rounded-lg transition"
                          >
                            <Eye className="w-4 h-4" />
                          </button>
                          <button
                            onClick={() => handleOpenEdit(client)}
                            title="Edit Client"
                            aria-label={`Edit ${client.name}`}
                            className="p-1.5 text-gray-500 hover:text-blue-600 hover:bg-gray-100 rounded-lg transition"
                          >
                            <Edit2 className="w-4 h-4" />
                          </button>
                          <button
                            onClick={() => handleOpenDelete(client)}
                            title={linkedProjectsCount > 0 ? "Archive Client (has projects)" : "Delete Client"}
                            aria-label={`Delete or archive ${client.name}`}
                            className="p-1.5 text-gray-500 hover:text-rose-600 hover:bg-rose-50 rounded-lg transition"
                          >
                            {linkedProjectsCount > 0 ? (
                              <Archive className="w-4 h-4 text-amber-600" />
                            ) : (
                              <Trash2 className="w-4 h-4" />
                            )}
                          </button>
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </Card>
      )}

      {/* Client Details Modal */}
      <Modal
        isOpen={isDetailsModalOpen}
        onClose={() => setIsDetailsModalOpen(false)}
        title={selectedClient?.name || 'Client Details'}
        description={selectedClient?.company ? `Organization: ${selectedClient.company}` : 'External Client Profile'}
        maxWidth="2xl"
      >
        <div className="space-y-6">
          {detailsLoading ? (
            <LoadingSpinner label="Loading client details and associated projects..." size="md" />
          ) : detailsError ? (
            <div className="space-y-3">
              <Alert type="error" title="Failed to load details">
                {detailsError}
              </Alert>
              <div className="text-right">
                <Button
                  variant="outline"
                  size="sm"
                  leftIcon={<RefreshCw className="w-3.5 h-3.5" />}
                  onClick={() => selectedClient && loadClientDetails(selectedClient.id)}
                >
                  Retry
                </Button>
              </div>
            </div>
          ) : (
            <>
              {/* Primary Profile Cards */}
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div className="p-4 bg-gray-50 rounded-xl space-y-2 border border-gray-100">
                  <span className="text-xs font-bold text-gray-400 uppercase tracking-wider block">Contact Information</span>
                  <div className="space-y-1.5 text-xs text-gray-700">
                    <div className="flex items-center gap-2">
                      <Mail className="w-3.5 h-3.5 text-gray-400 shrink-0" />
                      {clientDetails?.email ? (
                        <a href={`mailto:${clientDetails.email}`} className="text-blue-600 hover:underline">
                          {clientDetails.email}
                        </a>
                      ) : (
                        <span className="text-gray-400 italic">No email provided</span>
                      )}
                    </div>
                    <div className="flex items-center gap-2">
                      <Phone className="w-3.5 h-3.5 text-gray-400 shrink-0" />
                      {clientDetails?.phone ? (
                        <a href={`tel:${clientDetails.phone}`} className="hover:text-blue-600 hover:underline">
                          {clientDetails.phone}
                        </a>
                      ) : (
                        <span className="text-gray-400 italic">No phone number recorded</span>
                      )}
                    </div>
                    <div className="flex items-start gap-2">
                      <MapPin className="w-3.5 h-3.5 text-gray-400 shrink-0 mt-0.5" />
                      <span>{clientDetails?.address || <span className="text-gray-400 italic">No address recorded</span>}</span>
                    </div>
                  </div>
                </div>

                <div className="p-4 bg-gray-50 rounded-xl space-y-2 border border-gray-100">
                  <span className="text-xs font-bold text-gray-400 uppercase tracking-wider block">Account Overview</span>
                  <div className="space-y-2 text-xs text-gray-700">
                    <div className="flex items-center justify-between">
                      <span className="text-gray-500">Status:</span>
                      {getStatusBadge(clientDetails?.status)}
                    </div>
                    <div className="flex items-center justify-between">
                      <span className="text-gray-500">Total Projects:</span>
                      <span className="font-bold text-gray-900">{clientDetails?.totalProjects ?? 0}</span>
                    </div>
                    <div className="flex items-center justify-between">
                      <span className="text-gray-500">Active Projects:</span>
                      <span className="font-bold text-emerald-600">{clientDetails?.activeProjects ?? 0}</span>
                    </div>
                  </div>
                </div>
              </div>

              {/* Associated Projects Section */}
              <div className="space-y-3">
                <div className="flex items-center justify-between">
                  <h4 className="text-sm font-bold text-gray-900 flex items-center gap-2">
                    <FolderKanban className="w-4 h-4 text-blue-600" />
                    Associated Projects ({clientDetails?.projects?.length || 0})
                  </h4>
                  <Button
                    variant="ghost"
                    size="sm"
                    rightIcon={<ExternalLink className="w-3.5 h-3.5" />}
                    onClick={() => navigate('/projects')}
                    className="text-xs"
                  >
                    All Projects
                  </Button>
                </div>

                {!clientDetails?.projects || clientDetails.projects.length === 0 ? (
                  <div className="p-6 bg-gray-50 rounded-xl border border-gray-100 text-center text-xs text-gray-500">
                    No projects are currently associated with this client in this workspace.
                  </div>
                ) : (
                  <div className="space-y-2 max-h-56 overflow-y-auto pr-1">
                    {clientDetails.projects.map((proj) => (
                      <div
                        key={proj.id}
                        className="p-3 bg-white border border-gray-200 rounded-xl hover:border-blue-300 transition-colors flex flex-col sm:flex-row sm:items-center justify-between gap-2 text-xs"
                      >
                        <div className="space-y-1">
                          <div className="flex items-center gap-2">
                            <span className="font-bold text-gray-900">{proj.name}</span>
                            {getProjectStatusBadge(proj.status)}
                          </div>
                          {proj.description && (
                            <p className="text-gray-500 line-clamp-1">{proj.description}</p>
                          )}
                          <div className="flex items-center gap-3 text-gray-400">
                            {proj.managerName && (
                              <span>Manager: <strong className="text-gray-600">{proj.managerName}</strong></span>
                            )}
                            {proj.deadline && (
                              <span className="flex items-center gap-1">
                                <Calendar className="w-3 h-3" />
                                Deadline: {proj.deadline}
                              </span>
                            )}
                          </div>
                        </div>

                        <div className="sm:text-right shrink-0">
                          <div className="text-xs font-semibold text-gray-700">
                            Progress: {proj.progress ?? 0}%
                          </div>
                          <div className="w-24 bg-gray-100 rounded-full h-1.5 mt-1 overflow-hidden">
                            <div
                              className="bg-blue-600 h-1.5 rounded-full"
                              style={{ width: `${Math.min(100, Math.max(0, proj.progress ?? 0))}%` }}
                            />
                          </div>
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </div>

              {/* Client Deliverables Overview (if any) */}
              {clientDetails?.deliverables && clientDetails.deliverables.length > 0 && (
                <div className="space-y-2 pt-2 border-t border-gray-100">
                  <h4 className="text-sm font-bold text-gray-900 flex items-center gap-2">
                    <FileCheck className="w-4 h-4 text-emerald-600" />
                    Key Deliverables ({clientDetails.deliverables.length})
                  </h4>
                  <div className="space-y-1.5 max-h-40 overflow-y-auto pr-1">
                    {clientDetails.deliverables.map((deliv) => (
                      <div
                        key={deliv.id}
                        className="p-2.5 bg-gray-50 rounded-lg text-xs flex items-center justify-between border border-gray-100"
                      >
                        <div>
                          <div className="font-medium text-gray-900">{deliv.name}</div>
                          <div className="text-gray-400 text-[11px]">{deliv.projectName}</div>
                        </div>
                        <div className="text-right">
                          <Badge variant="neutral" size="sm">{deliv.status || 'PLANNED'}</Badge>
                          {deliv.requiredSubmissionDate && (
                            <div className="text-gray-400 text-[10px] mt-0.5">Due: {deliv.requiredSubmissionDate}</div>
                          )}
                        </div>
                      </div>
                    ))}
                  </div>
                </div>
              )}

              {/* Footer Actions */}
              <div className="flex items-center justify-between pt-4 border-t border-gray-100">
                <Button
                  variant="outline"
                  size="sm"
                  leftIcon={<Edit2 className="w-3.5 h-3.5" />}
                  onClick={() => {
                    if (selectedClient) {
                      setIsDetailsModalOpen(false);
                      handleOpenEdit(selectedClient);
                    }
                  }}
                >
                  Edit Client
                </Button>
                <Button variant="secondary" size="sm" onClick={() => setIsDetailsModalOpen(false)}>
                  Close
                </Button>
              </div>
            </>
          )}
        </div>
      </Modal>

      {/* Create Client Modal */}
      <Modal
        isOpen={isCreateModalOpen}
        onClose={() => !isSubmitting && setIsCreateModalOpen(false)}
        title="Add New Client"
        description="Register a client organization or representative to associate with projects."
        maxWidth="md"
      >
        <form onSubmit={handleCreateSubmit} className="space-y-4">
          <Input
            label="Client Contact Name"
            placeholder="e.g. Sarah Jenkins"
            value={formData.name}
            onChange={(e) => {
              setFormData({ ...formData, name: e.target.value });
              if (formErrors.name) setFormErrors({ ...formErrors, name: '' });
            }}
            error={formErrors.name}
            required
            maxLength={100}
            disabled={isSubmitting}
          />

          <Input
            label="Company / Organization"
            placeholder="e.g. Apex Global Logistics"
            value={formData.company}
            onChange={(e) => {
              setFormData({ ...formData, company: e.target.value });
              if (formErrors.company) setFormErrors({ ...formErrors, company: '' });
            }}
            error={formErrors.company}
            maxLength={100}
            disabled={isSubmitting}
          />

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <Input
              type="email"
              label="Email Address"
              placeholder="sarah@apexlogistics.com"
              value={formData.email}
              onChange={(e) => {
                setFormData({ ...formData, email: e.target.value });
                if (formErrors.email) setFormErrors({ ...formErrors, email: '' });
              }}
              error={formErrors.email}
              maxLength={100}
              disabled={isSubmitting}
            />

            <Input
              type="tel"
              label="Phone Number"
              placeholder="+1 (555) 019-2834"
              value={formData.phone}
              onChange={(e) => {
                setFormData({ ...formData, phone: e.target.value });
                if (formErrors.phone) setFormErrors({ ...formErrors, phone: '' });
              }}
              error={formErrors.phone}
              maxLength={30}
              disabled={isSubmitting}
            />
          </div>

          <Input
            label="Office Address"
            placeholder="100 Enterprise Way, Suite 400"
            value={formData.address}
            onChange={(e) => {
              setFormData({ ...formData, address: e.target.value });
              if (formErrors.address) setFormErrors({ ...formErrors, address: '' });
            }}
            error={formErrors.address}
            maxLength={255}
            disabled={isSubmitting}
          />

          <Select
            label="Account Status"
            value={formData.status}
            onChange={(e) => setFormData({ ...formData, status: e.target.value })}
            disabled={isSubmitting}
          >
            <option value="ACTIVE">Active (Eligible for project assignment)</option>
            <option value="INACTIVE">Inactive (Archived)</option>
          </Select>

          <div className="flex items-center justify-end gap-3 pt-4 border-t border-gray-100">
            <Button
              type="button"
              variant="outline"
              size="md"
              onClick={() => setIsCreateModalOpen(false)}
              disabled={isSubmitting}
            >
              Cancel
            </Button>
            <Button
              type="submit"
              variant="primary"
              size="md"
              isLoading={isSubmitting}
              disabled={isSubmitting}
            >
              Add Client
            </Button>
          </div>
        </form>
      </Modal>

      {/* Edit Client Modal */}
      <Modal
        isOpen={isEditModalOpen}
        onClose={() => !isSubmitting && setIsEditModalOpen(false)}
        title="Edit Client"
        description="Update client account details and contact information."
        maxWidth="md"
      >
        <form onSubmit={handleEditSubmit} className="space-y-4">
          <Input
            label="Client Contact Name"
            value={formData.name}
            onChange={(e) => {
              setFormData({ ...formData, name: e.target.value });
              if (formErrors.name) setFormErrors({ ...formErrors, name: '' });
            }}
            error={formErrors.name}
            required
            maxLength={100}
            disabled={isSubmitting}
          />

          <Input
            label="Company / Organization"
            value={formData.company}
            onChange={(e) => {
              setFormData({ ...formData, company: e.target.value });
              if (formErrors.company) setFormErrors({ ...formErrors, company: '' });
            }}
            error={formErrors.company}
            maxLength={100}
            disabled={isSubmitting}
          />

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <Input
              type="email"
              label="Email Address"
              value={formData.email}
              onChange={(e) => {
                setFormData({ ...formData, email: e.target.value });
                if (formErrors.email) setFormErrors({ ...formErrors, email: '' });
              }}
              error={formErrors.email}
              maxLength={100}
              disabled={isSubmitting}
            />

            <Input
              type="tel"
              label="Phone Number"
              value={formData.phone}
              onChange={(e) => {
                setFormData({ ...formData, phone: e.target.value });
                if (formErrors.phone) setFormErrors({ ...formErrors, phone: '' });
              }}
              error={formErrors.phone}
              maxLength={30}
              disabled={isSubmitting}
            />
          </div>

          <Input
            label="Office Address"
            value={formData.address}
            onChange={(e) => {
              setFormData({ ...formData, address: e.target.value });
              if (formErrors.address) setFormErrors({ ...formErrors, address: '' });
            }}
            error={formErrors.address}
            maxLength={255}
            disabled={isSubmitting}
          />

          <Select
            label="Account Status"
            value={formData.status}
            onChange={(e) => setFormData({ ...formData, status: e.target.value })}
            disabled={isSubmitting}
          >
            <option value="ACTIVE">Active (Eligible for project assignment)</option>
            <option value="INACTIVE">Inactive (Archived)</option>
          </Select>

          <div className="flex items-center justify-end gap-3 pt-4 border-t border-gray-100">
            <Button
              type="button"
              variant="outline"
              size="md"
              onClick={() => setIsEditModalOpen(false)}
              disabled={isSubmitting}
            >
              Cancel
            </Button>
            <Button
              type="submit"
              variant="primary"
              size="md"
              isLoading={isSubmitting}
              disabled={isSubmitting}
            >
              Save Changes
            </Button>
          </div>
        </form>
      </Modal>

      {/* Safe Deletion / Archival Modal */}
      <Modal
        isOpen={isDeleteModalOpen}
        onClose={() => !isSubmitting && setIsDeleteModalOpen(false)}
        title={
          (selectedClient?.projectCount ?? 0) > 0
            ? 'Archive Client Account'
            : 'Delete Client Account'
        }
        maxWidth="md"
      >
        <div className="space-y-4">
          <div className="flex items-center gap-3">
            <div
              className={`w-12 h-12 rounded-2xl border flex items-center justify-center shrink-0 ${
                (selectedClient?.projectCount ?? 0) > 0
                  ? 'bg-amber-50 border-amber-200 text-amber-600'
                  : 'bg-rose-50 border-rose-200 text-rose-600'
              }`}
            >
              {(selectedClient?.projectCount ?? 0) > 0 ? (
                <AlertTriangle className="w-6 h-6" />
              ) : (
                <Trash2 className="w-6 h-6" />
              )}
            </div>
            <div>
              <h3 className="text-base font-bold text-gray-900">
                {(selectedClient?.projectCount ?? 0) > 0
                  ? `Cannot Delete "${selectedClient?.name}"`
                  : `Delete "${selectedClient?.name}"?`}
              </h3>
              <p className="text-xs text-gray-500">
                {(selectedClient?.projectCount ?? 0) > 0
                  ? 'Active or historical project records are linked to this client.'
                  : 'This client has no linked projects and can be safely deleted.'}
              </p>
            </div>
          </div>

          {(selectedClient?.projectCount ?? 0) > 0 ? (
            <div className="p-3.5 bg-amber-50 border border-amber-200 rounded-xl space-y-2 text-xs text-amber-800">
              <p>
                <strong>Safeguard Active:</strong> Client "{selectedClient?.name}" has{' '}
                <strong>{selectedClient?.projectCount} associated project(s)</strong>.
              </p>
              <p>
                To protect scheduling history, audit trails, and deliverable records, destructive deletion is
                blocked. You can safely <strong>Archive</strong> this client instead by setting their status to{' '}
                <strong>INACTIVE</strong>.
              </p>
            </div>
          ) : (
            <p className="text-xs text-gray-600">
              Are you sure you want to permanently delete <strong>"{selectedClient?.name}"</strong>? This action
              cannot be undone.
            </p>
          )}

          <div className="flex items-center justify-end gap-3 pt-3 border-t border-gray-100">
            <Button
              variant="outline"
              size="md"
              onClick={() => setIsDeleteModalOpen(false)}
              disabled={isSubmitting}
            >
              Cancel
            </Button>
            {(selectedClient?.projectCount ?? 0) > 0 ? (
              <Button
                variant="primary"
                size="md"
                leftIcon={<Archive className="w-4 h-4" />}
                onClick={handleArchiveConfirm}
                isLoading={isSubmitting}
                disabled={isSubmitting}
              >
                Archive Client (Set Inactive)
              </Button>
            ) : (
              <Button
                variant="danger"
                size="md"
                leftIcon={<Trash2 className="w-4 h-4" />}
                onClick={handleDeleteConfirm}
                isLoading={isSubmitting}
                disabled={isSubmitting}
              >
                Delete Permanently
              </Button>
            )}
          </div>
        </div>
      </Modal>
    </div>
  );
}
