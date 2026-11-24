import React, { useState, useEffect, useMemo } from 'react';
import { api } from '../services/api';
import { useOrganization } from '../context/OrganizationContext';
import {
  Briefcase,
  Plus,
  Search,
  Mail,
  Award,
  CheckCircle2,
  XCircle,
  Edit2,
  Trash2,
  Tag,
  Clock,
  Sparkles,
} from 'lucide-react';
import {
  Button,
  Card,
  Badge,
  Modal,
  ConfirmDialog,
  Input,
  Select,
  EmptyState,
  LoadingSpinner,
  Alert,
} from '../components/ui';

interface Worker {
  id: number;
  userId?: number;
  name: string;
  email?: string;
  skills?: string;
  experience?: number;
  status: 'AVAILABLE' | 'UNAVAILABLE';
}

export function Workers() {
  const { currentOrg } = useOrganization();
  const [workers, setWorkers] = useState<Worker[]>([]);
  const [loading, setLoading] = useState(true);
  const [searchQuery, setSearchQuery] = useState('');
  const [statusFilter, setStatusFilter] = useState<'ALL' | 'AVAILABLE' | 'UNAVAILABLE'>('ALL');
  const [alertMessage, setAlertMessage] = useState<{ type: 'success' | 'error'; text: string } | null>(null);

  // Modal states
  const [isCreateModalOpen, setIsCreateModalOpen] = useState(false);
  const [isEditModalOpen, setIsEditModalOpen] = useState(false);
  const [isDeleteModalOpen, setIsDeleteModalOpen] = useState(false);
  const [selectedWorker, setSelectedWorker] = useState<Worker | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  // Form state
  const [formData, setFormData] = useState({
    name: '',
    email: '',
    skills: '',
    experience: 3,
    status: 'AVAILABLE' as 'AVAILABLE' | 'UNAVAILABLE',
  });

  const loadWorkers = async () => {
    try {
      setLoading(true);
      const res = await api.get('/workers');
      setWorkers(res.data || []);
    } catch (err: any) {
      console.error('Failed to load workers:', err);
      setAlertMessage({ type: 'error', text: 'Failed to load team members.' });
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadWorkers();
  }, [currentOrg?.id]);

  const handleOpenCreate = () => {
    setFormData({
      name: '',
      email: '',
      skills: '',
      experience: 3,
      status: 'AVAILABLE',
    });
    setIsCreateModalOpen(true);
  };

  const handleOpenEdit = (worker: Worker) => {
    setSelectedWorker(worker);
    setFormData({
      name: worker.name,
      email: worker.email || '',
      skills: worker.skills || '',
      experience: worker.experience || 1,
      status: worker.status || 'AVAILABLE',
    });
    setIsEditModalOpen(true);
  };

  const handleOpenDelete = (worker: Worker) => {
    setSelectedWorker(worker);
    setIsDeleteModalOpen(true);
  };

  const handleToggleAvailability = async (worker: Worker) => {
    try {
      const nextStatus = worker.status === 'AVAILABLE' ? 'UNAVAILABLE' : 'AVAILABLE';
      await api.put(`/workers/${worker.id}`, {
        ...worker,
        status: nextStatus,
      });
      setAlertMessage({
        type: 'success',
        text: `Availability for ${worker.name} toggled to ${nextStatus}.`,
      });
      loadWorkers();
    } catch (err: any) {
      setAlertMessage({
        type: 'error',
        text: err.response?.data?.message || 'Failed to toggle availability.',
      });
    }
  };

  const handleCreateSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!formData.name.trim()) return;

    try {
      setIsSubmitting(true);
      await api.post('/workers', {
        name: formData.name.trim(),
        email: formData.email.trim() || undefined,
        skills: formData.skills.trim() || undefined,
        experience: Number(formData.experience) || 0,
        status: formData.status,
      });
      setAlertMessage({ type: 'success', text: `Team member "${formData.name}" added successfully.` });
      setIsCreateModalOpen(false);
      loadWorkers();
    } catch (err: any) {
      setAlertMessage({ type: 'error', text: err.response?.data?.message || 'Failed to create worker profile.' });
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleEditSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedWorker || !formData.name.trim()) return;

    try {
      setIsSubmitting(true);
      await api.put(`/workers/${selectedWorker.id}`, {
        ...selectedWorker,
        name: formData.name.trim(),
        email: formData.email.trim() || undefined,
        skills: formData.skills.trim() || undefined,
        experience: Number(formData.experience) || 0,
        status: formData.status,
      });
      setAlertMessage({ type: 'success', text: `Profile for "${formData.name}" updated successfully.` });
      setIsEditModalOpen(false);
      loadWorkers();
    } catch (err: any) {
      setAlertMessage({ type: 'error', text: err.response?.data?.message || 'Failed to update profile.' });
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleDeleteConfirm = async () => {
    if (!selectedWorker) return;

    try {
      setIsSubmitting(true);
      await api.delete(`/workers/${selectedWorker.id}`);
      setAlertMessage({ type: 'success', text: `Worker profile "${selectedWorker.name}" deleted.` });
      setIsDeleteModalOpen(false);
      loadWorkers();
    } catch (err: any) {
      setAlertMessage({ type: 'error', text: err.response?.data?.message || 'Failed to delete worker profile.' });
    } finally {
      setIsSubmitting(false);
    }
  };

  const filteredWorkers = useMemo(() => {
    return workers.filter((w) => {
      const q = searchQuery.toLowerCase();
      const matchesSearch =
        w.name.toLowerCase().includes(q) ||
        (w.email && w.email.toLowerCase().includes(q)) ||
        (w.skills && w.skills.toLowerCase().includes(q));
      const matchesStatus = statusFilter === 'ALL' || w.status === statusFilter;
      return matchesSearch && matchesStatus;
    });
  }, [workers, searchQuery, statusFilter]);

  const uniqueSkillsCount = useMemo(() => {
    const set = new Set<string>();
    workers.forEach((w) => {
      if (w.skills) {
        w.skills.split(',').forEach((s) => set.add(s.trim().toLowerCase()));
      }
    });
    return set.size;
  }, [workers]);

  return (
    <div className="p-8 space-y-6 max-w-7xl mx-auto">
      {/* Top Banner */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-black text-gray-900 tracking-tight flex items-center gap-2.5">
            <Briefcase className="w-7 h-7 text-blue-600" />
            Workers & Team Roster
          </h1>
          <p className="text-sm text-gray-500 mt-1">
            Manage engineering talent, technical skill tags, capacity commitments, and availability.
          </p>
        </div>
        <Button variant="primary" size="md" leftIcon={<Plus className="w-4 h-4" />} onClick={handleOpenCreate}>
          New Team Member
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

      {/* KPI Stats Grid */}
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
        <Card className="p-5">
          <span className="text-xs font-bold text-gray-400 uppercase tracking-wider">Total Team</span>
          <div className="text-2xl font-black text-gray-900 mt-1">{workers.length}</div>
          <span className="text-xs text-gray-500 mt-0.5 block">Staffed resources</span>
        </Card>
        <Card className="p-5">
          <span className="text-xs font-bold text-emerald-600 uppercase tracking-wider">Available</span>
          <div className="text-2xl font-black text-emerald-600 mt-1">
            {workers.filter((w) => w.status === 'AVAILABLE').length}
          </div>
          <span className="text-xs text-gray-500 mt-0.5 block">Ready for task assignment</span>
        </Card>
        <Card className="p-5">
          <span className="text-xs font-bold text-amber-600 uppercase tracking-wider">Unavailable / Leave</span>
          <div className="text-2xl font-black text-amber-600 mt-1">
            {workers.filter((w) => w.status === 'UNAVAILABLE').length}
          </div>
          <span className="text-xs text-gray-500 mt-0.5 block">Excluded from optimization</span>
        </Card>
        <Card className="p-5">
          <span className="text-xs font-bold text-purple-600 uppercase tracking-wider">Skill Domains</span>
          <div className="text-2xl font-black text-purple-600 mt-1">{uniqueSkillsCount}</div>
          <span className="text-xs text-gray-500 mt-0.5 block">Unique technical proficiencies</span>
        </Card>
      </div>

      {/* Filter and Search Bar */}
      <Card className="p-4">
        <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
          <div className="relative flex-1 max-w-md">
            <Input
              placeholder="Search by worker name, email, or skill tag..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              leftIcon={<Search className="w-4 h-4" />}
            />
          </div>

          <div className="flex items-center gap-1.5">
            {(['ALL', 'AVAILABLE', 'UNAVAILABLE'] as const).map((st) => (
              <button
                key={st}
                onClick={() => setStatusFilter(st)}
                className={`px-3 py-1.5 rounded-xl text-xs font-bold transition-all ${
                  statusFilter === st
                    ? 'bg-blue-600 text-white shadow-2xs'
                    : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
                }`}
              >
                {st}
              </button>
            ))}
          </div>
        </div>
      </Card>

      {/* Workers Table */}
      {loading ? (
        <LoadingSpinner label="Loading team roster..." size="lg" />
      ) : filteredWorkers.length === 0 ? (
        <EmptyState
          icon={<Briefcase className="w-10 h-10 text-gray-400" />}
          title={searchQuery || statusFilter !== 'ALL' ? 'No matching workers found' : 'No team members registered yet'}
          description={
            searchQuery || statusFilter !== 'ALL'
              ? 'Try modifying your search query or reset the availability filter.'
              : 'Add your first developer, engineer, or specialist to enable task allocation and scheduling.'
          }
          actionLabel={searchQuery || statusFilter !== 'ALL' ? undefined : 'Add First Worker'}
          onAction={handleOpenCreate}
        />
      ) : (
        <Card className="overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm text-gray-600">
              <thead className="bg-gray-50/80 text-xs font-bold text-gray-500 uppercase border-b border-gray-200">
                <tr>
                  <th className="px-6 py-4">Worker Profile</th>
                  <th className="px-6 py-4">Skills & Proficiencies</th>
                  <th className="px-6 py-4">Experience</th>
                  <th className="px-6 py-4">Availability</th>
                  <th className="px-6 py-4 text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {filteredWorkers.map((worker) => (
                  <tr key={worker.id} className="hover:bg-gray-50/80 transition-colors">
                    <td className="px-6 py-4">
                      <div className="space-y-0.5">
                        <div className="font-bold text-gray-900">{worker.name}</div>
                        {worker.email && (
                          <div className="text-xs text-gray-500 flex items-center gap-1.5">
                            <Mail className="w-3 h-3 text-gray-400" />
                            <span>{worker.email}</span>
                          </div>
                        )}
                      </div>
                    </td>

                    <td className="px-6 py-4">
                      <div className="flex flex-wrap gap-1.5 max-w-sm">
                        {worker.skills ? (
                          worker.skills.split(',').map((skill, idx) => (
                            <span
                              key={idx}
                              className="px-2 py-0.5 bg-blue-50 text-blue-700 border border-blue-100 rounded-md text-[11px] font-semibold"
                            >
                              {skill.trim()}
                            </span>
                          ))
                        ) : (
                          <span className="text-xs italic text-gray-400">No skills tagged</span>
                        )}
                      </div>
                    </td>

                    <td className="px-6 py-4 text-xs font-semibold text-gray-700">
                      <div className="flex items-center gap-1.5">
                        <Award className="w-3.5 h-3.5 text-gray-400" />
                        <span>{worker.experience || 0} years exp</span>
                      </div>
                    </td>

                    <td className="px-6 py-4">
                      <button
                        onClick={() => handleToggleAvailability(worker)}
                        title="Click to toggle availability"
                        className="group flex items-center gap-1.5 focus:outline-none"
                      >
                        <Badge
                          variant={worker.status === 'AVAILABLE' ? 'success' : 'amber' as any}
                          size="sm"
                          dot
                          className="group-hover:opacity-80 transition cursor-pointer"
                        >
                          {worker.status || 'AVAILABLE'}
                        </Badge>
                      </button>
                    </td>

                    <td className="px-6 py-4 text-right">
                      <div className="flex items-center justify-end gap-1.5">
                        <button
                          onClick={() => handleOpenEdit(worker)}
                          title="Edit Worker Profile"
                          className="p-1.5 text-gray-500 hover:text-blue-600 hover:bg-gray-100 rounded-lg transition"
                        >
                          <Edit2 className="w-4 h-4" />
                        </button>
                        <button
                          onClick={() => handleOpenDelete(worker)}
                          title="Delete Worker"
                          className="p-1.5 text-gray-500 hover:text-rose-600 hover:bg-rose-50 rounded-lg transition"
                        >
                          <Trash2 className="w-4 h-4" />
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </Card>
      )}

      {/* Create Worker Modal */}
      <Modal
        isOpen={isCreateModalOpen}
        onClose={() => setIsCreateModalOpen(false)}
        title="Add Team Member"
        description="Register an internal developer, engineer, or specialist for schedule allocation."
        maxWidth="md"
      >
        <form onSubmit={handleCreateSubmit} className="space-y-4">
          <Input
            label="Full Name"
            placeholder="e.g. Alex Rivera"
            value={formData.name}
            onChange={(e) => setFormData({ ...formData, name: e.target.value })}
            required
          />

          <Input
            type="email"
            label="Email Address"
            placeholder="alex.rivera@acmeglobal.com"
            value={formData.email}
            onChange={(e) => setFormData({ ...formData, email: e.target.value })}
          />

          <Input
            label="Skills & Technologies"
            placeholder="e.g. React, TypeScript, Java, Spring Boot, Docker"
            helperText="Comma-separated skills used by the optimizer for suitability scoring."
            value={formData.skills}
            onChange={(e) => setFormData({ ...formData, skills: e.target.value })}
          />

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <Input
              type="number"
              min="0"
              max="50"
              label="Years of Experience"
              value={formData.experience}
              onChange={(e) => setFormData({ ...formData, experience: Number(e.target.value) })}
            />

            <Select
              label="Availability Status"
              value={formData.status}
              onChange={(e) => setFormData({ ...formData, status: e.target.value as any })}
            >
              <option value="AVAILABLE">Available</option>
              <option value="UNAVAILABLE">Unavailable (Leave / Off)</option>
            </Select>
          </div>

          <div className="flex items-center justify-end gap-3 pt-4 border-t border-gray-100">
            <Button variant="outline" size="md" onClick={() => setIsCreateModalOpen(false)}>
              Cancel
            </Button>
            <Button type="submit" variant="primary" size="md" isLoading={isSubmitting}>
              Add Worker
            </Button>
          </div>
        </form>
      </Modal>

      {/* Edit Worker Modal */}
      <Modal
        isOpen={isEditModalOpen}
        onClose={() => setIsEditModalOpen(false)}
        title="Edit Team Member Profile"
        description="Update contact, skills metadata, and availability."
        maxWidth="md"
      >
        <form onSubmit={handleEditSubmit} className="space-y-4">
          <Input
            label="Full Name"
            value={formData.name}
            onChange={(e) => setFormData({ ...formData, name: e.target.value })}
            required
          />

          <Input
            type="email"
            label="Email Address"
            value={formData.email}
            onChange={(e) => setFormData({ ...formData, email: e.target.value })}
          />

          <Input
            label="Skills & Technologies"
            helperText="Comma-separated skill proficiencies."
            value={formData.skills}
            onChange={(e) => setFormData({ ...formData, skills: e.target.value })}
          />

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <Input
              type="number"
              min="0"
              max="50"
              label="Years of Experience"
              value={formData.experience}
              onChange={(e) => setFormData({ ...formData, experience: Number(e.target.value) })}
            />

            <Select
              label="Availability Status"
              value={formData.status}
              onChange={(e) => setFormData({ ...formData, status: e.target.value as any })}
            >
              <option value="AVAILABLE">Available</option>
              <option value="UNAVAILABLE">Unavailable (Leave / Off)</option>
            </Select>
          </div>

          <div className="flex items-center justify-end gap-3 pt-4 border-t border-gray-100">
            <Button variant="outline" size="md" onClick={() => setIsEditModalOpen(false)}>
              Cancel
            </Button>
            <Button type="submit" variant="primary" size="md" isLoading={isSubmitting}>
              Save Profile
            </Button>
          </div>
        </form>
      </Modal>

      {/* Delete Confirmation */}
      <ConfirmDialog
        isOpen={isDeleteModalOpen}
        onClose={() => setIsDeleteModalOpen(false)}
        onConfirm={handleDeleteConfirm}
        title="Delete Worker Profile?"
        description={`Are you sure you want to remove "${selectedWorker?.name}"? Any active task assignments for this worker will be unassigned.`}
        confirmLabel="Delete Worker"
        variant="danger"
        isLoading={isSubmitting}
      />
    </div>
  );
}
