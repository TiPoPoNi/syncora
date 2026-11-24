import React, { useState, useEffect } from 'react';
import { api } from '../services/api';
import { useOrganization } from '../context/OrganizationContext';
import {
  Truck,
  Upload,
  CheckCircle2,
  AlertTriangle,
  FileSpreadsheet,
  Plus,
  RefreshCw,
  Mail,
  Phone,
  Briefcase,
  Search,
  Edit2,
  Trash2,
  ExternalLink,
} from 'lucide-react';
import {
  Button,
  Badge,
  Card,
  Modal,
  Input,
  ConfirmDialog,
  LoadingSpinner,
  EmptyState,
  Alert,
} from '../components/ui';

interface Vendor {
  id: number;
  name: string;
  company: string;
  email: string;
  phone: string;
  services: string;
  status: string;
}

interface VendorRowDto {
  rowNumber: number;
  name: string;
  company: string;
  email: string;
  phone: string;
  services: string;
  duplicateInDatabase: boolean;
}

interface RowErrorDto {
  rowNumber: number;
  field: string;
  errorMessage: string;
  rawContent: string;
}

interface PreviewResult {
  totalRows: number;
  validRowsCount: number;
  errorRowsCount: number;
  validRecords: VendorRowDto[];
  errors: RowErrorDto[];
}

export function Vendors() {
  const { currentOrg } = useOrganization();
  const [vendors, setVendors] = useState<Vendor[]>([]);
  const [loading, setLoading] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');

  // Manual Create/Edit Modal State
  const [modalOpen, setModalOpen] = useState(false);
  const [editingVendor, setEditingVendor] = useState<Vendor | null>(null);
  const [formName, setFormName] = useState('');
  const [formCompany, setFormCompany] = useState('');
  const [formEmail, setFormEmail] = useState('');
  const [formPhone, setFormPhone] = useState('');
  const [formServices, setFormServices] = useState('');
  const [saving, setSaving] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);

  // Delete State
  const [deleteVendorTarget, setDeleteVendorTarget] = useState<Vendor | null>(null);
  const [deleting, setDeleting] = useState(false);

  // CSV Import Modal State
  const [importModalOpen, setImportModalOpen] = useState(false);
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [previewData, setPreviewData] = useState<PreviewResult | null>(null);
  const [previewing, setPreviewing] = useState(false);
  const [importing, setImporting] = useState(false);
  const [importSuccessMsg, setImportSuccessMsg] = useState<string | null>(null);
  const [importError, setImportError] = useState<string | null>(null);

  const fetchVendors = async () => {
    setLoading(true);
    try {
      const res = await api.get('/vendors');
      setVendors(res.data || []);
    } catch (err) {
      console.error('Failed to load vendors:', err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchVendors();
  }, [currentOrg?.id]);

  const openCreateModal = () => {
    setEditingVendor(null);
    setFormName('');
    setFormCompany('');
    setFormEmail('');
    setFormPhone('');
    setFormServices('');
    setFormError(null);
    setModalOpen(true);
  };

  const openEditModal = (vendor: Vendor) => {
    setEditingVendor(vendor);
    setFormName(vendor.name);
    setFormCompany(vendor.company || '');
    setFormEmail(vendor.email || '');
    setFormPhone(vendor.phone || '');
    setFormServices(vendor.services || '');
    setFormError(null);
    setModalOpen(true);
  };

  const handleSaveVendor = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!formName.trim()) {
      setFormError('Vendor contact name is required.');
      return;
    }
    setSaving(true);
    setFormError(null);

    const payload = {
      name: formName.trim(),
      company: formCompany.trim(),
      email: formEmail.trim(),
      phone: formPhone.trim(),
      services: formServices.trim(),
      status: 'ACTIVE',
    };

    try {
      if (editingVendor) {
        await api.put(`/vendors/${editingVendor.id}`, payload);
      } else {
        await api.post('/vendors', payload);
      }
      setModalOpen(false);
      fetchVendors();
    } catch (err: any) {
      setFormError(err.response?.data?.message || 'Failed to save vendor');
    } finally {
      setSaving(false);
    }
  };

  const handleDeleteVendor = async () => {
    if (!deleteVendorTarget) return;
    setDeleting(true);
    try {
      await api.delete(`/vendors/${deleteVendorTarget.id}`);
      setDeleteVendorTarget(null);
      fetchVendors();
    } catch (err: any) {
      alert('Failed to delete vendor: ' + (err.response?.data?.message || err.message));
    } finally {
      setDeleting(false);
    }
  };

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files && e.target.files[0]) {
      setSelectedFile(e.target.files[0]);
      setPreviewData(null);
      setImportSuccessMsg(null);
      setImportError(null);
    }
  };

  const handlePreviewUpload = async () => {
    if (!selectedFile) return;
    setPreviewing(true);
    setImportSuccessMsg(null);
    setImportError(null);
    const formData = new FormData();
    formData.append('file', selectedFile);

    try {
      const res = await api.post('/vendors/import/preview', formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
      });
      setPreviewData(res.data);
    } catch (err: any) {
      setImportError(err.response?.data?.message || 'CSV Validation failed. Please check column format.');
    } finally {
      setPreviewing(false);
    }
  };

  const handleConfirmImport = async () => {
    if (!previewData || previewData.validRecords.length === 0) return;
    setImporting(true);
    setImportError(null);
    try {
      const res = await api.post('/vendors/import/confirm', previewData.validRecords);
      setImportSuccessMsg(res.data.message || `Imported ${res.data.importedCount} vendor(s).`);
      setPreviewData(null);
      setSelectedFile(null);
      await fetchVendors();
      setTimeout(() => {
        setImportModalOpen(false);
        setImportSuccessMsg(null);
      }, 1500);
    } catch (err: any) {
      setImportError(err.response?.data?.message || 'Error confirming import');
    } finally {
      setImporting(false);
    }
  };

  const filteredVendors = vendors.filter((v) => {
    const q = searchQuery.toLowerCase();
    return (
      v.name.toLowerCase().includes(q) ||
      (v.company && v.company.toLowerCase().includes(q)) ||
      (v.services && v.services.toLowerCase().includes(q)) ||
      (v.email && v.email.toLowerCase().includes(q))
    );
  });

  return (
    <div className="space-y-6 animate-fadeIn pb-12">
      {/* Top Header Card */}
      <div className="bg-white p-6 rounded-2xl shadow-xs border border-gray-200 flex flex-wrap items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl bg-blue-50 border border-blue-200 flex items-center justify-center text-blue-600">
              <Truck className="w-5 h-5" />
            </div>
            <div>
              <h2 className="text-xl font-bold text-gray-900 tracking-tight">
                Vendor & Supplier Directory
              </h2>
              <p className="text-xs text-gray-500 mt-0.5">
                External service providers, contractors, and material lead-time tracking for project scheduling.
              </p>
            </div>
          </div>
        </div>

        <div className="flex items-center gap-2.5">
          <Button
            variant="outline"
            leftIcon={<Upload className="w-4 h-4" />}
            onClick={() => {
              setImportModalOpen(true);
              setSelectedFile(null);
              setPreviewData(null);
              setImportSuccessMsg(null);
              setImportError(null);
            }}
          >
            Import CSV
          </Button>
          <Button
            variant="primary"
            leftIcon={<Plus className="w-4 h-4" />}
            onClick={openCreateModal}
          >
            Add Vendor
          </Button>
          <button
            onClick={fetchVendors}
            className="p-2.5 rounded-lg border border-gray-200 hover:bg-gray-50 text-gray-600 transition"
            title="Refresh Vendors"
          >
            <RefreshCw className={`w-4 h-4 ${loading ? 'animate-spin' : ''}`} />
          </button>
        </div>
      </div>

      {/* KPI Stats */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <div className="bg-white p-4 rounded-xl border border-gray-200 shadow-2xs">
          <p className="text-xs font-semibold text-gray-500 uppercase tracking-wider">Total Suppliers</p>
          <p className="text-2xl font-bold text-gray-900 mt-1">{vendors.length}</p>
        </div>
        <div className="bg-white p-4 rounded-xl border border-gray-200 shadow-2xs">
          <p className="text-xs font-semibold text-gray-500 uppercase tracking-wider">Active Status</p>
          <p className="text-2xl font-bold text-emerald-600 mt-1">
            {vendors.filter((v) => !v.status || v.status === 'ACTIVE').length}
          </p>
        </div>
        <div className="bg-white p-4 rounded-xl border border-gray-200 shadow-2xs">
          <p className="text-xs font-semibold text-gray-500 uppercase tracking-wider">Services Cataloged</p>
          <p className="text-2xl font-bold text-blue-600 mt-1">
            {vendors.filter((v) => v.services && v.services.trim().length > 0).length}
          </p>
        </div>
      </div>

      {/* Search & Filter Toolbar */}
      <div className="bg-white p-4 rounded-xl border border-gray-200 shadow-2xs flex items-center justify-between gap-4">
        <div className="w-full sm:w-80">
          <Input
            placeholder="Search suppliers by name, company, service..."
            leftIcon={<Search className="w-4 h-4" />}
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
          />
        </div>
        <div className="text-xs font-medium text-gray-500">
          Showing {filteredVendors.length} of {vendors.length} vendors
        </div>
      </div>

      {/* Vendor Table */}
      <div className="bg-white rounded-2xl border border-gray-200 shadow-xs overflow-hidden">
        {loading ? (
          <LoadingSpinner label="Loading suppliers directory..." />
        ) : filteredVendors.length === 0 ? (
          <EmptyState
            icon={<Truck className="w-10 h-10 text-gray-400" />}
            title={searchQuery ? 'No suppliers match your search' : 'No vendors registered yet'}
            description={
              searchQuery
                ? 'Try adjusting your search keywords or clear the filter.'
                : 'Create your first supplier or import suppliers in bulk via CSV.'
            }
            actionLabel="Add Vendor"
            onAction={openCreateModal}
          />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm text-gray-600">
              <thead className="bg-gray-50/80 text-xs font-semibold text-gray-500 uppercase tracking-wider border-b border-gray-100">
                <tr>
                  <th className="py-3 px-4">Vendor / Supplier</th>
                  <th className="py-3 px-4">Company</th>
                  <th className="py-3 px-4">Services Provided</th>
                  <th className="py-3 px-4">Contact Details</th>
                  <th className="py-3 px-4 text-center">Status</th>
                  <th className="py-3 px-4 text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100 font-medium">
                {filteredVendors.map((v) => (
                  <tr key={v.id} className="hover:bg-blue-50/20 transition">
                    <td className="py-3.5 px-4">
                      <div className="font-bold text-gray-900">{v.name}</div>
                      <div className="text-xs text-gray-400 font-mono">ID #{v.id}</div>
                    </td>
                    <td className="py-3.5 px-4">
                      {v.company ? (
                        <div className="flex items-center gap-1.5 text-gray-900 font-medium">
                          <Briefcase className="w-3.5 h-3.5 text-gray-400" />
                          {v.company}
                        </div>
                      ) : (
                        <span className="text-xs text-gray-400 italic">Independent</span>
                      )}
                    </td>
                    <td className="py-3.5 px-4">
                      <span className="text-xs text-gray-700 bg-gray-100 px-2 py-0.5 rounded-md inline-block max-w-xs truncate">
                        {v.services || 'General Contracting & Materials'}
                      </span>
                    </td>
                    <td className="py-3.5 px-4 text-xs space-y-1">
                      {v.email && (
                        <div className="flex items-center gap-1.5 text-gray-600">
                          <Mail className="w-3.5 h-3.5 text-gray-400" />
                          <a href={`mailto:${v.email}`} className="hover:text-blue-600">
                            {v.email}
                          </a>
                        </div>
                      )}
                      {v.phone && (
                        <div className="flex items-center gap-1.5 text-gray-600">
                          <Phone className="w-3.5 h-3.5 text-gray-400" />
                          <span>{v.phone}</span>
                        </div>
                      )}
                      {!v.email && !v.phone && (
                        <span className="text-gray-400 italic">No contact specified</span>
                      )}
                    </td>
                    <td className="py-3.5 px-4 text-center">
                      <Badge variant="success" dot size="sm">
                        {v.status || 'ACTIVE'}
                      </Badge>
                    </td>
                    <td className="py-3.5 px-4 text-right">
                      <div className="flex items-center justify-end gap-1">
                        <button
                          onClick={() => openEditModal(v)}
                          className="p-1.5 text-gray-500 hover:text-blue-600 hover:bg-blue-50 rounded-lg transition"
                          title="Edit Vendor"
                        >
                          <Edit2 className="w-4 h-4" />
                        </button>
                        <button
                          onClick={() => setDeleteVendorTarget(v)}
                          className="p-1.5 text-gray-500 hover:text-rose-600 hover:bg-rose-50 rounded-lg transition"
                          title="Delete Vendor"
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
        )}
      </div>

      {/* Create / Edit Vendor Modal */}
      <Modal
        isOpen={modalOpen}
        onClose={() => setModalOpen(false)}
        title={editingVendor ? `Edit Supplier: ${editingVendor.name}` : 'Register New Vendor / Supplier'}
        description="Configure supplier contact details and material/service capabilities."
        maxWidth="md"
      >
        {formError && (
          <div className="mb-4">
            <Alert type="error" title="Validation Error">
              {formError}
            </Alert>
          </div>
        )}

        <form onSubmit={handleSaveVendor} className="space-y-4">
          <Input
            label="Contact / Representative Name"
            required
            placeholder="e.g. Marcus Vance"
            value={formName}
            onChange={(e) => setFormName(e.target.value)}
          />

          <Input
            label="Company Name"
            placeholder="e.g. Apex Structural Steel Corp"
            value={formCompany}
            onChange={(e) => setFormCompany(e.target.value)}
          />

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <Input
              label="Email Address"
              type="email"
              placeholder="e.g. supplier@apexsteel.com"
              value={formEmail}
              onChange={(e) => setFormEmail(e.target.value)}
            />
            <Input
              label="Phone Number"
              type="tel"
              placeholder="e.g. +1 555 382 9921"
              value={formPhone}
              onChange={(e) => setFormPhone(e.target.value)}
            />
          </div>

          <Input
            label="Services / Materials Provided"
            placeholder="e.g. Structural steel beams, prefabricated decking, rigging"
            value={formServices}
            onChange={(e) => setFormServices(e.target.value)}
            helperText="Include delivery capabilities and specialty equipment supplies."
          />

          <div className="flex justify-end gap-3 pt-3 border-t border-gray-100">
            <Button variant="outline" type="button" onClick={() => setModalOpen(false)}>
              Cancel
            </Button>
            <Button variant="primary" type="submit" isLoading={saving}>
              {editingVendor ? 'Save Changes' : 'Register Vendor'}
            </Button>
          </div>
        </form>
      </Modal>

      {/* CSV Import Modal */}
      <Modal
        isOpen={importModalOpen}
        onClose={() => setImportModalOpen(false)}
        title="Validated Vendor CSV Import"
        description="Upload a CSV file containing vendor and supplier records with validation."
        maxWidth="lg"
      >
        {importError && (
          <div className="mb-4">
            <Alert type="error" title="Import Error">
              {importError}
            </Alert>
          </div>
        )}

        {importSuccessMsg ? (
          <div className="p-8 text-center bg-emerald-50 border border-emerald-200 rounded-2xl text-emerald-800 space-y-2">
            <CheckCircle2 className="w-12 h-12 mx-auto text-emerald-600" />
            <div className="text-lg font-bold">Import Completed Successfully</div>
            <p className="text-sm text-emerald-700">{importSuccessMsg}</p>
          </div>
        ) : (
          <div className="space-y-4">
            {/* File Upload Box */}
            <div className="border-2 border-dashed border-gray-300 hover:border-blue-400 rounded-2xl p-6 text-center bg-gray-50/50 transition">
              <input
                type="file"
                id="vendor-csv-input"
                accept=".csv,text/csv"
                onChange={handleFileChange}
                className="hidden"
              />
              <label htmlFor="vendor-csv-input" className="cursor-pointer block">
                <Upload className="w-8 h-8 mx-auto text-blue-500 mb-2 stroke-1" />
                <div className="text-sm font-semibold text-gray-800">
                  {selectedFile ? selectedFile.name : 'Choose CSV file to import'}
                </div>
                <div className="text-xs text-gray-500 mt-1">
                  Expected columns:{' '}
                  <code className="bg-white px-1.5 py-0.5 rounded border border-gray-200">
                    name, company, email, phone, services
                  </code>
                </div>
              </label>
            </div>

            {selectedFile && !previewData && (
              <div className="flex justify-end">
                <Button
                  variant="primary"
                  onClick={handlePreviewUpload}
                  isLoading={previewing}
                  leftIcon={<RefreshCw className="w-4 h-4" />}
                >
                  Validate & Preview Records
                </Button>
              </div>
            )}

            {/* Preview Table & Validation Callouts */}
            {previewData && (
              <div className="space-y-4">
                <div className="flex items-center justify-between p-3.5 rounded-xl bg-gray-100 text-xs font-semibold text-gray-700">
                  <span>Total Rows Evaluated: {previewData.totalRows}</span>
                  <div className="flex items-center gap-3">
                    <span className="text-emerald-700">✓ {previewData.validRowsCount} Valid</span>
                    {previewData.errorRowsCount > 0 && (
                      <span className="text-rose-700">✗ {previewData.errorRowsCount} Errors</span>
                    )}
                  </div>
                </div>

                {previewData.errors.length > 0 && (
                  <div className="p-4 bg-rose-50 border border-rose-200 rounded-xl space-y-2 text-xs">
                    <div className="font-bold text-rose-800 flex items-center gap-1.5">
                      <AlertTriangle className="w-4 h-4 text-rose-600" />
                      Row-Level Validation Errors:
                    </div>
                    <ul className="list-disc list-inside space-y-1 text-rose-700">
                      {previewData.errors.map((err, idx) => (
                        <li key={idx}>
                          <strong>Row {err.rowNumber} [{err.field}]:</strong> {err.errorMessage}
                        </li>
                      ))}
                    </ul>
                  </div>
                )}

                {previewData.validRecords.length > 0 && (
                  <div>
                    <div className="text-xs font-bold text-gray-500 uppercase tracking-wider mb-2">
                      Valid Records Ready to Commit
                    </div>
                    <div className="max-h-48 overflow-y-auto border border-gray-200 rounded-xl overflow-hidden">
                      <table className="w-full text-left text-xs text-gray-600">
                        <thead className="bg-gray-50 font-semibold text-gray-500 border-b border-gray-200">
                          <tr>
                            <th className="py-2 px-3">Name</th>
                            <th className="py-2 px-3">Company</th>
                            <th className="py-2 px-3">Email</th>
                            <th className="py-2 px-3">Status</th>
                          </tr>
                        </thead>
                        <tbody className="divide-y divide-gray-100">
                          {previewData.validRecords.map((r, idx) => (
                            <tr key={idx} className="hover:bg-gray-50">
                              <td className="py-2 px-3 font-semibold text-gray-800">{r.name}</td>
                              <td className="py-2 px-3">{r.company || '—'}</td>
                              <td className="py-2 px-3">{r.email || '—'}</td>
                              <td className="py-2 px-3">
                                {r.duplicateInDatabase ? (
                                  <Badge variant="warning" size="sm">
                                    Existing (Will skip)
                                  </Badge>
                                ) : (
                                  <Badge variant="success" size="sm">
                                    New Record
                                  </Badge>
                                )}
                              </td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  </div>
                )}

                <div className="flex justify-end gap-3 pt-3 border-t border-gray-100">
                  <Button variant="outline" onClick={() => setImportModalOpen(false)}>
                    Cancel
                  </Button>
                  <Button
                    variant="primary"
                    onClick={handleConfirmImport}
                    isLoading={importing}
                    disabled={previewData.validRecords.length === 0}
                  >
                    Commit {previewData.validRowsCount} Valid Records
                  </Button>
                </div>
              </div>
            )}
          </div>
        )}
      </Modal>

      {/* Delete Vendor Confirmation Dialog */}
      <ConfirmDialog
        isOpen={!!deleteVendorTarget}
        title="Delete Supplier Record"
        message={`Are you sure you want to delete supplier "${deleteVendorTarget?.name}"? Any active purchase orders and lead-time configurations may be affected.`}
        confirmLabel="Delete Supplier"
        variant="danger"
        isLoading={deleting}
        onConfirm={handleDeleteVendor}
        onCancel={() => setDeleteVendorTarget(null)}
      />
    </div>
  );
}
