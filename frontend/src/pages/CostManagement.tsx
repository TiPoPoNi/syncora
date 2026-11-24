import React, { useState, useEffect } from 'react';
import { 
  DollarSign, Receipt, ShoppingCart, Truck, AlertTriangle, CheckCircle2, 
  TrendingUp, TrendingDown, Plus, RefreshCw, Clock, FileText, ChevronRight, Layers,
  ExternalLink, Calendar, User, Check
} from 'lucide-react';
import { api } from '../services/api';
import { useOrganization } from '../context/OrganizationContext';

interface CostSummary {
  projectId: number;
  projectName: string;
  currency: string;
  approvedBudget: number;
  totalCommittedCost: number;
  totalActualCost: number;
  remainingBudget: number;
  remainingCommittedBudget: number;
  costVariance: number;
  costVariancePercent: number;
  isOverBudget: boolean;
  categoryBreakdown: Record<string, number>;
}

interface ProjectBudget {
  id?: number;
  projectId: number;
  initialBudget: number;
  approvedBudget: number;
  currency: string;
  status: string;
  notes?: string;
  createdAt?: string;
  updatedAt?: string;
}

interface BudgetRevision {
  id: number;
  revisionNumber: number;
  previousAmount: number;
  revisedAmount: number;
  reason: string;
  approvedBy: string;
  approvedAt: string;
}

interface QuotationItem {
  id?: number;
  description: string;
  quantity: number;
  unitPrice: number;
  subtotal: number;
}

interface VendorQuotation {
  id: number;
  quoteReference: string;
  vendorId: number;
  vendorName: string;
  issueDate: string;
  validUntil: string;
  currency: string;
  taxAmount: number;
  totalAmount: number;
  status: string;
  notes?: string;
  items: QuotationItem[];
}

interface PurchaseOrderLine {
  id: number;
  description: string;
  quantity: number;
  unitPrice: number;
  lineTotal: number;
  deliveredQuantity: number;
}

interface PurchaseOrder {
  id: number;
  poReference: string;
  vendorId: number;
  vendorName: string;
  quotationId?: number;
  quotationReference?: string;
  orderDate: string;
  expectedDeliveryDate?: string;
  currency: string;
  totalCommittedAmount: number;
  approvalStatus: string;
  deliveryStatus: string;
  notes?: string;
  lines: PurchaseOrderLine[];
}

interface PurchaseOrderDelivery {
  id: number;
  deliveryReference: string;
  poLineId?: number;
  lineDescription?: string;
  actualDeliveryDate: string;
  quantityDelivered: number;
  receivedBy: string;
  status: string;
  notes?: string;
}

interface ProjectExpense {
  id: number;
  category: string;
  amount: number;
  currency: string;
  expenseDate: string;
  description: string;
  taskId?: number;
  taskTitle?: string;
  vendorId?: number;
  vendorName?: string;
  purchaseOrderId?: number;
  poReference?: string;
  recordedBy: string;
  recordedAt: string;
}

export function CostManagement() {
  const { currentOrg } = useOrganization();
  const [projects, setProjects] = useState<any[]>([]);
  const [selectedProjectId, setSelectedProjectId] = useState<number | null>(null);
  const [vendors, setVendors] = useState<any[]>([]);
  const [loading, setLoading] = useState(false);
  const [activeTab, setActiveTab] = useState<'overview' | 'budget' | 'quotations' | 'orders' | 'expenses'>('overview');

  // Data states
  const [summary, setSummary] = useState<CostSummary | null>(null);
  const [budget, setBudget] = useState<ProjectBudget | null>(null);
  const [revisions, setRevisions] = useState<BudgetRevision[]>([]);
  const [quotations, setQuotations] = useState<VendorQuotation[]>([]);
  const [purchaseOrders, setPurchaseOrders] = useState<PurchaseOrder[]>([]);
  const [expenses, setExpenses] = useState<ProjectExpense[]>([]);

  // Modals
  const [showSetBudgetModal, setShowSetBudgetModal] = useState(false);
  const [showReviseBudgetModal, setShowReviseBudgetModal] = useState(false);
  const [showAddQuotationModal, setShowAddQuotationModal] = useState(false);
  const [showCreatePoModal, setShowCreatePoModal] = useState(false);
  const [showRecordDeliveryModal, setShowRecordDeliveryModal] = useState(false);
  const [showRecordExpenseModal, setShowRecordExpenseModal] = useState(false);
  const [selectedPoForDelivery, setSelectedPoForDelivery] = useState<PurchaseOrder | null>(null);
  const [activePoDeliveries, setActivePoDeliveries] = useState<PurchaseOrderDelivery[]>([]);

  // Form states
  const [initialBudgetInput, setInitialBudgetInput] = useState('');
  const [budgetCurrency, setBudgetCurrency] = useState('INR');
  const [budgetNotes, setBudgetNotes] = useState('');

  const [revisedAmountInput, setRevisedAmountInput] = useState('');
  const [revisionReasonInput, setRevisionReasonInput] = useState('');

  // Quotation form
  const [qVendorId, setQVendorId] = useState('');
  const [qReference, setQReference] = useState('');
  const [qIssueDate, setQIssueDate] = useState(new Date().toISOString().split('T')[0]);
  const [qValidUntil, setQValidUntil] = useState('');
  const [qTax, setQTax] = useState('0');
  const [qNotes, setQNotes] = useState('');
  const [qItems, setQItems] = useState<{ description: string; quantity: string; unitPrice: string }[]>([
    { description: '', quantity: '1', unitPrice: '0' }
  ]);

  // PO form
  const [poVendorId, setPoVendorId] = useState('');
  const [poReference, setPoReference] = useState('');
  const [poLinkedQuotationId, setPoLinkedQuotationId] = useState('');
  const [poExpectedDate, setPoExpectedDate] = useState('');
  const [poNotes, setPoNotes] = useState('');
  const [poLines, setPoLines] = useState<{ description: string; quantity: string; unitPrice: string }[]>([
    { description: '', quantity: '1', unitPrice: '0' }
  ]);

  // Delivery form
  const [delLineId, setDelLineId] = useState<string>('');
  const [delQuantity, setDelQuantity] = useState('');
  const [delReference, setDelReference] = useState('');
  const [delNotes, setDelNotes] = useState('');

  // Expense form
  const [expCategory, setExpCategory] = useState('MATERIALS');
  const [expAmount, setExpAmount] = useState('');
  const [expDescription, setExpDescription] = useState('');
  const [expDate, setExpDate] = useState(new Date().toISOString().split('T')[0]);
  const [expVendorId, setExpVendorId] = useState('');
  const [expPoId, setExpPoId] = useState('');

  // Load projects & vendors on workspace change
  useEffect(() => {
    loadProjectsAndVendors();
  }, [currentOrg]);

  // Reload project data when selected project changes
  useEffect(() => {
    if (selectedProjectId) {
      loadProjectCostData(selectedProjectId);
    }
  }, [selectedProjectId]);

  const loadProjectsAndVendors = async () => {
    try {
      const [projRes, vendRes] = await Promise.all([
        api.get('/projects'),
        api.get('/vendors')
      ]);
      setProjects(projRes.data);
      setVendors(vendRes.data);
      if (projRes.data.length > 0 && !selectedProjectId) {
        setSelectedProjectId(projRes.data[0].id);
      }
    } catch (err) {
      console.error('Failed to load initial data', err);
    }
  };

  const loadProjectCostData = async (projId: number) => {
    setLoading(true);
    try {
      const [sumRes, budRes, revRes, quotRes, poRes, expRes] = await Promise.all([
        api.get(`/projects/${projId}/cost-summary`).catch(() => null),
        api.get(`/projects/${projId}/budget`).catch(() => null),
        api.get(`/projects/${projId}/budget/revisions`).catch(() => null),
        api.get(`/projects/${projId}/quotations`).catch(() => null),
        api.get(`/projects/${projId}/purchase-orders`).catch(() => null),
        api.get(`/projects/${projId}/expenses`).catch(() => null),
      ]);

      if (sumRes) setSummary(sumRes.data);
      if (budRes) setBudget(budRes.data);
      if (revRes) setRevisions(revRes.data);
      if (quotRes) setQuotations(quotRes.data);
      if (poRes) setPurchaseOrders(poRes.data);
      if (expRes) setExpenses(expRes.data);
    } catch (err) {
      console.error('Error fetching cost data', err);
    } finally {
      setLoading(false);
    }
  };

  const handleSetBudget = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedProjectId) return;
    try {
      await api.post(`/projects/${selectedProjectId}/budget`, {
        initialBudget: parseFloat(initialBudgetInput),
        currency: budgetCurrency,
        notes: budgetNotes
      });
      setShowSetBudgetModal(false);
      loadProjectCostData(selectedProjectId);
    } catch (err: any) {
      alert(err.response?.data?.message || 'Failed to set budget');
    }
  };

  const handleApproveBudget = async () => {
    if (!selectedProjectId) return;
    try {
      await api.post(`/projects/${selectedProjectId}/budget/approve`);
      loadProjectCostData(selectedProjectId);
    } catch (err: any) {
      alert(err.response?.data?.message || 'Failed to approve budget');
    }
  };

  const handleReviseBudget = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedProjectId) return;
    try {
      await api.post(`/projects/${selectedProjectId}/budget/revisions`, {
        revisedAmount: parseFloat(revisedAmountInput),
        reason: revisionReasonInput
      });
      setShowReviseBudgetModal(false);
      setRevisedAmountInput('');
      setRevisionReasonInput('');
      loadProjectCostData(selectedProjectId);
    } catch (err: any) {
      alert(err.response?.data?.message || 'Failed to revise budget');
    }
  };

  const handleCreateQuotation = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedProjectId || !qVendorId) return;
    try {
      const items = qItems.map(item => ({
        description: item.description,
        quantity: parseFloat(item.quantity) || 1,
        unitPrice: parseFloat(item.unitPrice) || 0,
      }));

      await api.post(`/projects/${selectedProjectId}/quotations`, {
        vendorId: parseInt(qVendorId),
        quoteReference: qReference,
        issueDate: qIssueDate,
        validUntil: qValidUntil || null,
        currency: budgetCurrency,
        taxAmount: parseFloat(qTax) || 0,
        notes: qNotes,
        items
      });
      setShowAddQuotationModal(false);
      setQReference('');
      setQNotes('');
      loadProjectCostData(selectedProjectId);
    } catch (err: any) {
      alert(err.response?.data?.message || 'Failed to create quotation');
    }
  };

  const handleUpdateQuotationStatus = async (quoteId: number, status: string) => {
    try {
      await api.put(`/quotations/${quoteId}/status`, { status });
      if (selectedProjectId) loadProjectCostData(selectedProjectId);
    } catch (err: any) {
      alert(err.response?.data?.message || 'Failed to update quotation');
    }
  };

  const handleCreatePO = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedProjectId || !poVendorId) return;
    try {
      const lines = poLines.map(line => ({
        description: line.description,
        quantity: parseFloat(line.quantity) || 1,
        unitPrice: parseFloat(line.unitPrice) || 0,
      }));

      await api.post(`/projects/${selectedProjectId}/purchase-orders`, {
        vendorId: parseInt(poVendorId),
        quotationId: poLinkedQuotationId ? parseInt(poLinkedQuotationId) : null,
        poReference,
        expectedDeliveryDate: poExpectedDate || null,
        currency: budgetCurrency,
        notes: poNotes,
        lines
      });
      setShowCreatePoModal(false);
      setPoReference('');
      setPoNotes('');
      loadProjectCostData(selectedProjectId);
    } catch (err: any) {
      alert(err.response?.data?.message || 'Failed to create purchase order');
    }
  };

  const handleUpdatePoStatus = async (poId: number, status: string) => {
    try {
      await api.put(`/purchase-orders/${poId}/status`, { status });
      if (selectedProjectId) loadProjectCostData(selectedProjectId);
    } catch (err: any) {
      alert(err.response?.data?.message || 'Failed to update order status');
    }
  };

  const openDeliveriesModal = async (po: PurchaseOrder) => {
    setSelectedPoForDelivery(po);
    if (po.lines.length > 0) {
      setDelLineId(po.lines[0].id.toString());
    }
    try {
      const res = await api.get(`/purchase-orders/${po.id}/deliveries`);
      setActivePoDeliveries(res.data);
    } catch (err) {
      console.error(err);
    }
    setShowRecordDeliveryModal(true);
  };

  const handleRecordDelivery = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedPoForDelivery) return;
    try {
      await api.post(`/purchase-orders/${selectedPoForDelivery.id}/deliveries`, {
        poLineId: delLineId ? parseInt(delLineId) : null,
        quantityDelivered: parseFloat(delQuantity),
        deliveryReference: delReference || undefined,
        notes: delNotes
      });
      // Refresh deliveries
      const res = await api.get(`/purchase-orders/${selectedPoForDelivery.id}/deliveries`);
      setActivePoDeliveries(res.data);
      setDelQuantity('');
      setDelReference('');
      setDelNotes('');
      if (selectedProjectId) loadProjectCostData(selectedProjectId);
    } catch (err: any) {
      alert(err.response?.data?.message || 'Failed to record delivery');
    }
  };

  const handleRecordExpense = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedProjectId) return;
    try {
      await api.post(`/projects/${selectedProjectId}/expenses`, {
        category: expCategory,
        amount: parseFloat(expAmount),
        currency: budgetCurrency,
        description: expDescription,
        expenseDate: expDate,
        vendorId: expVendorId ? parseInt(expVendorId) : null,
        purchaseOrderId: expPoId ? parseInt(expPoId) : null
      });
      setShowRecordExpenseModal(false);
      setExpAmount('');
      setExpDescription('');
      setExpVendorId('');
      setExpPoId('');
      loadProjectCostData(selectedProjectId);
    } catch (err: any) {
      alert(err.response?.data?.message || 'Failed to record expense');
    }
  };

  const formatCurrency = (val?: number, cur?: string) => {
    if (val === undefined || val === null) return '0.00';
    return `${cur || summary?.currency || 'INR'} ${Number(val).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
  };

  return (
    <div className="p-8 max-w-7xl mx-auto space-y-6">
      {/* Header & Project Selector */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 bg-white p-6 rounded-xl border border-gray-200 shadow-sm">
        <div>
          <div className="flex items-center gap-2 text-sm text-gray-500 font-medium">
            <span>Procurement & Finance</span>
            <ChevronRight className="w-4 h-4" />
            <span className="text-blue-600">Phase 13</span>
          </div>
          <h1 className="text-2xl font-bold text-gray-900 mt-1">Project Budget, Procurement & Cost Management</h1>
          <p className="text-sm text-gray-600 mt-0.5">Track budgets, revisions, vendor quotations, purchase orders, deliveries, and cost variance.</p>
        </div>

        <div className="flex items-center gap-3">
          <label className="text-sm font-semibold text-gray-700 whitespace-nowrap">Active Project:</label>
          <select
            value={selectedProjectId || ''}
            onChange={(e) => setSelectedProjectId(Number(e.target.value))}
            className="bg-gray-50 border border-gray-300 text-gray-900 text-sm rounded-lg focus:ring-blue-500 focus:border-blue-500 block p-2.5 font-medium min-w-[220px]"
          >
            {projects.map((p) => (
              <option key={p.id} value={p.id}>{p.name}</option>
            ))}
          </select>

          <button
            onClick={() => selectedProjectId && loadProjectCostData(selectedProjectId)}
            className="p-2.5 text-gray-500 hover:text-blue-600 hover:bg-blue-50 rounded-lg border border-gray-300 transition"
            title="Refresh Cost Data"
          >
            <RefreshCw className={`w-4 h-4 ${loading ? 'animate-spin' : ''}`} />
          </button>
        </div>
      </div>

      {/* KPI Cards: Budget, Committed, Actual, Remaining, Variance */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-5 gap-4">
        {/* Approved Budget */}
        <div className="bg-white p-5 rounded-xl border border-gray-200 shadow-sm relative overflow-hidden">
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold text-gray-500 uppercase tracking-wider">Approved Budget</span>
            <div className="p-2 bg-blue-50 text-blue-600 rounded-lg"><DollarSign className="w-4 h-4" /></div>
          </div>
          <div className="text-2xl font-bold text-gray-900 mt-2">
            {formatCurrency(summary?.approvedBudget)}
          </div>
          <div className="flex items-center gap-1.5 mt-2 text-xs">
            <span className={`px-2 py-0.5 rounded font-semibold ${
              budget?.status === 'APPROVED' ? 'bg-green-100 text-green-700' :
              budget?.status === 'PENDING_APPROVAL' ? 'bg-amber-100 text-amber-700' :
              'bg-gray-100 text-gray-700'
            }`}>
              {budget?.status || 'NO BUDGET'}
            </span>
            <span className="text-gray-400">|</span>
            <span className="text-gray-500">{revisions.length} revision{revisions.length === 1 ? '' : 's'}</span>
          </div>
        </div>

        {/* Committed Cost */}
        <div className="bg-white p-5 rounded-xl border border-gray-200 shadow-sm">
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold text-gray-500 uppercase tracking-wider">Committed Cost (POs)</span>
            <div className="p-2 bg-purple-50 text-purple-600 rounded-lg"><ShoppingCart className="w-4 h-4" /></div>
          </div>
          <div className="text-2xl font-bold text-gray-900 mt-2">
            {formatCurrency(summary?.totalCommittedCost)}
          </div>
          <p className="text-xs text-gray-500 mt-2">
            Active approved purchase orders
          </p>
        </div>

        {/* Actual Expenses */}
        <div className="bg-white p-5 rounded-xl border border-gray-200 shadow-sm">
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold text-gray-500 uppercase tracking-wider">Actual Expenses</span>
            <div className="p-2 bg-indigo-50 text-indigo-600 rounded-lg"><Receipt className="w-4 h-4" /></div>
          </div>
          <div className="text-2xl font-bold text-gray-900 mt-2">
            {formatCurrency(summary?.totalActualCost)}
          </div>
          <p className="text-xs text-gray-500 mt-2">
            {expenses.length} recorded expense{expenses.length === 1 ? '' : 's'}
          </p>
        </div>

        {/* Remaining Budget */}
        <div className="bg-white p-5 rounded-xl border border-gray-200 shadow-sm">
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold text-gray-500 uppercase tracking-wider">Remaining Budget</span>
            <div className={`p-2 rounded-lg ${(summary?.remainingBudget || 0) < 0 ? 'bg-red-50 text-red-600' : 'bg-emerald-50 text-emerald-600'}`}>
              <CheckCircle2 className="w-4 h-4" />
            </div>
          </div>
          <div className={`text-2xl font-bold mt-2 ${(summary?.remainingBudget || 0) < 0 ? 'text-red-600' : 'text-emerald-700'}`}>
            {formatCurrency(summary?.remainingBudget)}
          </div>
          <p className="text-xs text-gray-500 mt-2">
            Budget minus actual expenses
          </p>
        </div>

        {/* Cost Variance */}
        <div className={`p-5 rounded-xl border shadow-sm ${
          summary?.isOverBudget ? 'bg-red-50/50 border-red-200' : 'bg-white border-gray-200'
        }`}>
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold text-gray-500 uppercase tracking-wider">Cost Variance</span>
            <div className={`p-2 rounded-lg ${summary?.isOverBudget ? 'bg-red-100 text-red-700' : 'bg-blue-50 text-blue-600'}`}>
              {summary?.isOverBudget ? <AlertTriangle className="w-4 h-4" /> : <TrendingDown className="w-4 h-4" />}
            </div>
          </div>
          <div className={`text-2xl font-bold mt-2 ${summary?.isOverBudget ? 'text-red-700' : 'text-gray-900'}`}>
            {formatCurrency(summary?.costVariance)}
          </div>
          <div className="flex items-center gap-1 mt-2 text-xs">
            <span className={`font-semibold ${summary?.isOverBudget ? 'text-red-700' : 'text-emerald-600'}`}>
              {summary?.costVariancePercent !== undefined ? `${summary.costVariancePercent > 0 ? '+' : ''}${summary.costVariancePercent}%` : '0%'}
            </span>
            <span className="text-gray-500">
              {summary?.isOverBudget ? 'over budget' : 'within budget'}
            </span>
          </div>
        </div>
      </div>

      {/* Navigation Tabs */}
      <div className="flex items-center justify-between border-b border-gray-200">
        <div className="flex space-x-8">
          {[
            { id: 'overview', label: 'Variance Overview', icon: Layers },
            { id: 'budget', label: 'Budget & Revisions', icon: DollarSign },
            { id: 'quotations', label: 'Vendor Quotations', icon: FileText },
            { id: 'orders', label: 'Purchase Orders & Deliveries', icon: ShoppingCart },
            { id: 'expenses', label: 'Recorded Expenses', icon: Receipt },
          ].map((tab) => (
            <button
              key={tab.id}
              onClick={() => setActiveTab(tab.id as any)}
              className={`flex items-center gap-2 py-4 px-1 border-b-2 font-medium text-sm transition ${
                activeTab === tab.id
                  ? 'border-blue-600 text-blue-600'
                  : 'border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300'
              }`}
            >
              <tab.icon className="w-4 h-4" />
              {tab.label}
            </button>
          ))}
        </div>

        <div className="flex items-center gap-2">
          {activeTab === 'budget' && (
            <>
              {(!budget || budget.status === 'DRAFT') && (
                <button
                  onClick={() => setShowSetBudgetModal(true)}
                  className="px-3 py-1.5 bg-blue-600 text-white rounded-lg text-sm font-medium hover:bg-blue-700 transition flex items-center gap-1.5"
                >
                  <Plus className="w-4 h-4" /> Set Initial Budget
                </button>
              )}
              {budget && budget.status === 'PENDING_APPROVAL' && (
                <button
                  onClick={handleApproveBudget}
                  className="px-3 py-1.5 bg-green-600 text-white rounded-lg text-sm font-medium hover:bg-green-700 transition flex items-center gap-1.5"
                >
                  <Check className="w-4 h-4" /> Approve Budget
                </button>
              )}
              {budget && budget.status === 'APPROVED' && (
                <button
                  onClick={() => setShowReviseBudgetModal(true)}
                  className="px-3 py-1.5 bg-indigo-600 text-white rounded-lg text-sm font-medium hover:bg-indigo-700 transition flex items-center gap-1.5"
                >
                  <Plus className="w-4 h-4" /> Request Revision
                </button>
              )}
            </>
          )}

          {activeTab === 'quotations' && (
            <button
              onClick={() => setShowAddQuotationModal(true)}
              className="px-3 py-1.5 bg-blue-600 text-white rounded-lg text-sm font-medium hover:bg-blue-700 transition flex items-center gap-1.5"
            >
              <Plus className="w-4 h-4" /> Add Quotation
            </button>
          )}

          {activeTab === 'orders' && (
            <button
              onClick={() => setShowCreatePoModal(true)}
              className="px-3 py-1.5 bg-blue-600 text-white rounded-lg text-sm font-medium hover:bg-blue-700 transition flex items-center gap-1.5"
            >
              <Plus className="w-4 h-4" /> Create Purchase Order
            </button>
          )}

          {activeTab === 'expenses' && (
            <button
              onClick={() => setShowRecordExpenseModal(true)}
              className="px-3 py-1.5 bg-blue-600 text-white rounded-lg text-sm font-medium hover:bg-blue-700 transition flex items-center gap-1.5"
            >
              <Plus className="w-4 h-4" /> Record Expense
            </button>
          )}
        </div>
      </div>

      {/* TAB CONTENT */}

      {/* 1. OVERVIEW & CATEGORY BREAKDOWN */}
      {activeTab === 'overview' && (
        <div className="space-y-6">
          <div className="bg-white p-6 rounded-xl border border-gray-200 shadow-sm">
            <h3 className="text-base font-bold text-gray-900 mb-4">Cost Distribution by Category</h3>
            <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-6 gap-4">
              {[
                { key: 'LABOR', label: 'Labor', color: 'bg-blue-500' },
                { key: 'MATERIALS', label: 'Materials', color: 'bg-emerald-500' },
                { key: 'VENDOR_SERVICES', label: 'Vendor Services', color: 'bg-purple-500' },
                { key: 'EQUIPMENT', label: 'Equipment', color: 'bg-amber-500' },
                { key: 'TRANSPORTATION', label: 'Transportation', color: 'bg-indigo-500' },
                { key: 'OTHER', label: 'Other Expenses', color: 'bg-gray-500' },
              ].map((cat) => {
                const amount = summary?.categoryBreakdown?.[cat.key] || 0;
                const totalActual = summary?.totalActualCost || 1;
                const pct = totalActual > 0 ? ((amount / totalActual) * 100).toFixed(1) : '0';
                return (
                  <div key={cat.key} className="p-4 bg-gray-50 rounded-lg border border-gray-100 flex flex-col justify-between">
                    <div>
                      <div className="flex items-center gap-2 mb-2">
                        <span className={`w-2.5 h-2.5 rounded-full ${cat.color}`} />
                        <span className="text-xs font-semibold text-gray-600">{cat.label}</span>
                      </div>
                      <div className="text-lg font-bold text-gray-900">
                        {formatCurrency(amount)}
                      </div>
                    </div>
                    <div className="mt-3">
                      <div className="flex justify-between text-xs text-gray-500 mb-1">
                        <span>Share</span>
                        <span>{pct}%</span>
                      </div>
                      <div className="w-full bg-gray-200 rounded-full h-1.5">
                        <div
                          className={`h-1.5 rounded-full ${cat.color}`}
                          style={{ width: `${Math.min(100, parseFloat(pct))}%` }}
                        />
                      </div>
                    </div>
                  </div>
                );
              })}
            </div>
          </div>

          {/* Recent Expenses & Active Orders */}
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
            <div className="bg-white p-6 rounded-xl border border-gray-200 shadow-sm">
              <div className="flex items-center justify-between mb-4">
                <h3 className="text-base font-bold text-gray-900">Recent Project Expenses</h3>
                <button
                  onClick={() => setActiveTab('expenses')}
                  className="text-xs font-semibold text-blue-600 hover:text-blue-800"
                >
                  View All
                </button>
              </div>
              <div className="overflow-x-auto">
                <table className="w-full text-left text-sm text-gray-600">
                  <thead className="bg-gray-50 text-xs uppercase font-semibold text-gray-500">
                    <tr>
                      <th className="py-2.5 px-3">Date</th>
                      <th className="py-2.5 px-3">Category</th>
                      <th className="py-2.5 px-3">Description</th>
                      <th className="py-2.5 px-3 text-right">Amount</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-gray-100">
                    {expenses.slice(0, 5).map((e) => (
                      <tr key={e.id} className="hover:bg-gray-50">
                        <td className="py-2.5 px-3 text-xs">{e.expenseDate}</td>
                        <td className="py-2.5 px-3">
                          <span className="text-xs px-2 py-0.5 rounded font-medium bg-gray-100 text-gray-700">
                            {e.category}
                          </span>
                        </td>
                        <td className="py-2.5 px-3 text-xs text-gray-900 font-medium truncate max-w-[180px]">
                          {e.description}
                        </td>
                        <td className="py-2.5 px-3 text-xs font-bold text-gray-900 text-right">
                          {formatCurrency(e.amount, e.currency)}
                        </td>
                      </tr>
                    ))}
                    {expenses.length === 0 && (
                      <tr>
                        <td colSpan={4} className="text-center py-6 text-gray-400 text-xs">
                          No expenses recorded yet.
                        </td>
                      </tr>
                    )}
                  </tbody>
                </table>
              </div>
            </div>

            <div className="bg-white p-6 rounded-xl border border-gray-200 shadow-sm">
              <div className="flex items-center justify-between mb-4">
                <h3 className="text-base font-bold text-gray-900">Active Procurement Orders</h3>
                <button
                  onClick={() => setActiveTab('orders')}
                  className="text-xs font-semibold text-blue-600 hover:text-blue-800"
                >
                  View All
                </button>
              </div>
              <div className="overflow-x-auto">
                <table className="w-full text-left text-sm text-gray-600">
                  <thead className="bg-gray-50 text-xs uppercase font-semibold text-gray-500">
                    <tr>
                      <th className="py-2.5 px-3">PO Ref</th>
                      <th className="py-2.5 px-3">Vendor</th>
                      <th className="py-2.5 px-3">Delivery</th>
                      <th className="py-2.5 px-3 text-right">Committed</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-gray-100">
                    {purchaseOrders.slice(0, 5).map((po) => (
                      <tr key={po.id} className="hover:bg-gray-50">
                        <td className="py-2.5 px-3 text-xs font-semibold text-blue-600">{po.poReference}</td>
                        <td className="py-2.5 px-3 text-xs font-medium text-gray-900">{po.vendorName}</td>
                        <td className="py-2.5 px-3">
                          <span className={`text-[10px] px-2 py-0.5 rounded font-bold ${
                            po.deliveryStatus === 'DELIVERED' ? 'bg-green-100 text-green-700' :
                            po.deliveryStatus === 'PARTIALLY_DELIVERED' ? 'bg-amber-100 text-amber-700' :
                            'bg-gray-100 text-gray-700'
                          }`}>
                            {po.deliveryStatus}
                          </span>
                        </td>
                        <td className="py-2.5 px-3 text-xs font-bold text-gray-900 text-right">
                          {formatCurrency(po.totalCommittedAmount, po.currency)}
                        </td>
                      </tr>
                    ))}
                    {purchaseOrders.length === 0 && (
                      <tr>
                        <td colSpan={4} className="text-center py-6 text-gray-400 text-xs">
                          No purchase orders created yet.
                        </td>
                      </tr>
                    )}
                  </tbody>
                </table>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* 2. BUDGET & REVISIONS */}
      {activeTab === 'budget' && (
        <div className="space-y-6">
          <div className="bg-white p-6 rounded-xl border border-gray-200 shadow-sm">
            <h3 className="text-base font-bold text-gray-900 mb-4">Budget Details</h3>
            <div className="grid grid-cols-1 md:grid-cols-4 gap-4 p-4 bg-gray-50 rounded-lg">
              <div>
                <span className="text-xs text-gray-500">Initial Established Budget:</span>
                <p className="text-base font-bold text-gray-900 mt-0.5">{formatCurrency(budget?.initialBudget)}</p>
              </div>
              <div>
                <span className="text-xs text-gray-500">Current Approved Budget:</span>
                <p className="text-base font-bold text-green-700 mt-0.5">{formatCurrency(budget?.approvedBudget)}</p>
              </div>
              <div>
                <span className="text-xs text-gray-500">Budget Currency:</span>
                <p className="text-base font-bold text-gray-900 mt-0.5">{budget?.currency || 'INR'}</p>
              </div>
              <div>
                <span className="text-xs text-gray-500">Approval Status:</span>
                <p className="mt-0.5">
                  <span className={`text-xs px-2.5 py-1 rounded font-bold ${
                    budget?.status === 'APPROVED' ? 'bg-green-100 text-green-700' :
                    budget?.status === 'PENDING_APPROVAL' ? 'bg-amber-100 text-amber-700' :
                    'bg-gray-100 text-gray-700'
                  }`}>
                    {budget?.status || 'NOT SET'}
                  </span>
                </p>
              </div>
            </div>
          </div>

          <div className="bg-white p-6 rounded-xl border border-gray-200 shadow-sm">
            <h3 className="text-base font-bold text-gray-900 mb-4">Budget Revision History</h3>
            <div className="overflow-x-auto">
              <table className="w-full text-left text-sm text-gray-600">
                <thead className="bg-gray-50 text-xs uppercase font-semibold text-gray-500">
                  <tr>
                    <th className="py-3 px-4">Rev #</th>
                    <th className="py-3 px-4">Previous Approved</th>
                    <th className="py-3 px-4">Revised Approved</th>
                    <th className="py-3 px-4">Net Change</th>
                    <th className="py-3 px-4">Reason for Revision</th>
                    <th className="py-3 px-4">Approved By</th>
                    <th className="py-3 px-4">Approved At</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-100">
                  {revisions.map((rev) => {
                    const diff = (rev.revisedAmount || 0) - (rev.previousAmount || 0);
                    return (
                      <tr key={rev.id} className="hover:bg-gray-50">
                        <td className="py-3 px-4 font-bold text-blue-600">v{rev.revisionNumber}</td>
                        <td className="py-3 px-4">{formatCurrency(rev.previousAmount)}</td>
                        <td className="py-3 px-4 font-bold text-gray-900">{formatCurrency(rev.revisedAmount)}</td>
                        <td className={`py-3 px-4 font-semibold ${diff >= 0 ? 'text-green-600' : 'text-red-600'}`}>
                          {diff >= 0 ? `+${formatCurrency(diff)}` : formatCurrency(diff)}
                        </td>
                        <td className="py-3 px-4 text-xs max-w-[280px]">{rev.reason}</td>
                        <td className="py-3 px-4 text-xs font-medium text-gray-700">{rev.approvedBy}</td>
                        <td className="py-3 px-4 text-xs text-gray-500">
                          {rev.approvedAt ? new Date(rev.approvedAt).toLocaleString() : '-'}
                        </td>
                      </tr>
                    );
                  })}
                  {revisions.length === 0 && (
                    <tr>
                      <td colSpan={7} className="text-center py-8 text-gray-400 text-sm">
                        No budget revisions recorded. The initial baseline amount is unchanged.
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      )}

      {/* 3. VENDOR QUOTATIONS */}
      {activeTab === 'quotations' && (
        <div className="bg-white p-6 rounded-xl border border-gray-200 shadow-sm">
          <div className="flex items-center justify-between mb-4">
            <h3 className="text-base font-bold text-gray-900">Recorded Vendor Quotations</h3>
          </div>

          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm text-gray-600">
              <thead className="bg-gray-50 text-xs uppercase font-semibold text-gray-500">
                <tr>
                  <th className="py-3 px-4">Quote Ref</th>
                  <th className="py-3 px-4">Vendor</th>
                  <th className="py-3 px-4">Issue Date</th>
                  <th className="py-3 px-4">Valid Until</th>
                  <th className="py-3 px-4">Line Items</th>
                  <th className="py-3 px-4">Tax</th>
                  <th className="py-3 px-4">Total Amount</th>
                  <th className="py-3 px-4">Status</th>
                  <th className="py-3 px-4 text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {quotations.map((q) => (
                  <tr key={q.id} className="hover:bg-gray-50">
                    <td className="py-3 px-4 font-semibold text-blue-600">{q.quoteReference}</td>
                    <td className="py-3 px-4 font-medium text-gray-900">{q.vendorName}</td>
                    <td className="py-3 px-4 text-xs">{q.issueDate}</td>
                    <td className="py-3 px-4 text-xs text-gray-500">{q.validUntil || '-'}</td>
                    <td className="py-3 px-4 text-xs">{q.items?.length || 0} items</td>
                    <td className="py-3 px-4 text-xs">{formatCurrency(q.taxAmount, q.currency)}</td>
                    <td className="py-3 px-4 font-bold text-gray-900">{formatCurrency(q.totalAmount, q.currency)}</td>
                    <td className="py-3 px-4">
                      <span className={`text-[10px] px-2.5 py-0.5 rounded font-bold ${
                        q.status === 'ACCEPTED' ? 'bg-green-100 text-green-700' :
                        q.status === 'REJECTED' ? 'bg-red-100 text-red-700' :
                        q.status === 'UNDER_REVIEW' ? 'bg-purple-100 text-purple-700' :
                        'bg-amber-100 text-amber-700'
                      }`}>
                        {q.status}
                      </span>
                    </td>
                    <td className="py-3 px-4 text-right">
                      <div className="flex items-center justify-end gap-1.5">
                        {q.status !== 'ACCEPTED' && (
                          <button
                            onClick={() => handleUpdateQuotationStatus(q.id, 'ACCEPTED')}
                            className="px-2 py-1 text-xs bg-green-50 text-green-700 hover:bg-green-100 rounded font-semibold transition"
                          >
                            Accept
                          </button>
                        )}
                        {q.status !== 'REJECTED' && (
                          <button
                            onClick={() => handleUpdateQuotationStatus(q.id, 'REJECTED')}
                            className="px-2 py-1 text-xs bg-red-50 text-red-700 hover:bg-red-100 rounded font-semibold transition"
                          >
                            Reject
                          </button>
                        )}
                      </div>
                    </td>
                  </tr>
                ))}
                {quotations.length === 0 && (
                  <tr>
                    <td colSpan={9} className="text-center py-8 text-gray-400 text-sm">
                      No quotations recorded for this project.
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* 4. PURCHASE ORDERS & DELIVERIES */}
      {activeTab === 'orders' && (
        <div className="bg-white p-6 rounded-xl border border-gray-200 shadow-sm">
          <div className="flex items-center justify-between mb-4">
            <h3 className="text-base font-bold text-gray-900">Procurement Purchase Orders</h3>
          </div>

          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm text-gray-600">
              <thead className="bg-gray-50 text-xs uppercase font-semibold text-gray-500">
                <tr>
                  <th className="py-3 px-4">PO Ref</th>
                  <th className="py-3 px-4">Vendor</th>
                  <th className="py-3 px-4">Linked Quote</th>
                  <th className="py-3 px-4">Order Date</th>
                  <th className="py-3 px-4">Expected Delivery</th>
                  <th className="py-3 px-4">Total Committed</th>
                  <th className="py-3 px-4">Approval</th>
                  <th className="py-3 px-4">Delivery Status</th>
                  <th className="py-3 px-4 text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {purchaseOrders.map((po) => (
                  <tr key={po.id} className="hover:bg-gray-50">
                    <td className="py-3 px-4 font-semibold text-blue-600">{po.poReference}</td>
                    <td className="py-3 px-4 font-medium text-gray-900">{po.vendorName}</td>
                    <td className="py-3 px-4 text-xs text-gray-500">{po.quotationReference || '-'}</td>
                    <td className="py-3 px-4 text-xs">{po.orderDate}</td>
                    <td className="py-3 px-4 text-xs text-gray-500">{po.expectedDeliveryDate || '-'}</td>
                    <td className="py-3 px-4 font-bold text-gray-900">{formatCurrency(po.totalCommittedAmount, po.currency)}</td>
                    <td className="py-3 px-4">
                      <span className={`text-[10px] px-2.5 py-0.5 rounded font-bold ${
                        po.approvalStatus === 'APPROVED' ? 'bg-green-100 text-green-700' :
                        po.approvalStatus === 'CANCELLED' ? 'bg-red-100 text-red-700' :
                        'bg-amber-100 text-amber-700'
                      }`}>
                        {po.approvalStatus}
                      </span>
                    </td>
                    <td className="py-3 px-4">
                      <span className={`text-[10px] px-2.5 py-0.5 rounded font-bold ${
                        po.deliveryStatus === 'DELIVERED' ? 'bg-green-100 text-green-700' :
                        po.deliveryStatus === 'PARTIALLY_DELIVERED' ? 'bg-amber-100 text-amber-700' :
                        'bg-gray-100 text-gray-700'
                      }`}>
                        {po.deliveryStatus}
                      </span>
                    </td>
                    <td className="py-3 px-4 text-right">
                      <div className="flex items-center justify-end gap-1.5">
                        {po.approvalStatus === 'DRAFT' && (
                          <button
                            onClick={() => handleUpdatePoStatus(po.id, 'APPROVED')}
                            className="px-2 py-1 text-xs bg-green-50 text-green-700 hover:bg-green-100 rounded font-semibold transition"
                          >
                            Approve
                          </button>
                        )}
                        <button
                          onClick={() => openDeliveriesModal(po)}
                          className="px-2 py-1 text-xs bg-blue-50 text-blue-700 hover:bg-blue-100 rounded font-semibold transition flex items-center gap-1"
                        >
                          <Truck className="w-3 h-3" /> Track Deliveries
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
                {purchaseOrders.length === 0 && (
                  <tr>
                    <td colSpan={9} className="text-center py-8 text-gray-400 text-sm">
                      No purchase orders registered for this project.
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* 5. RECORDED EXPENSES */}
      {activeTab === 'expenses' && (
        <div className="bg-white p-6 rounded-xl border border-gray-200 shadow-sm">
          <div className="flex items-center justify-between mb-4">
            <h3 className="text-base font-bold text-gray-900">Recorded Project Expenses</h3>
          </div>

          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm text-gray-600">
              <thead className="bg-gray-50 text-xs uppercase font-semibold text-gray-500">
                <tr>
                  <th className="py-3 px-4">Date</th>
                  <th className="py-3 px-4">Category</th>
                  <th className="py-3 px-4">Description</th>
                  <th className="py-3 px-4">Vendor / PO</th>
                  <th className="py-3 px-4">Recorded By</th>
                  <th className="py-3 px-4 text-right">Amount</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {expenses.map((exp) => (
                  <tr key={exp.id} className="hover:bg-gray-50">
                    <td className="py-3 px-4 text-xs">{exp.expenseDate}</td>
                    <td className="py-3 px-4">
                      <span className="text-xs px-2.5 py-0.5 rounded font-medium bg-gray-100 text-gray-700">
                        {exp.category}
                      </span>
                    </td>
                    <td className="py-3 px-4 font-medium text-gray-900">{exp.description}</td>
                    <td className="py-3 px-4 text-xs text-gray-500">
                      {exp.vendorName ? exp.vendorName : '-'} {exp.poReference ? `(${exp.poReference})` : ''}
                    </td>
                    <td className="py-3 px-4 text-xs text-gray-600">{exp.recordedBy}</td>
                    <td className="py-3 px-4 font-bold text-gray-900 text-right">
                      {formatCurrency(exp.amount, exp.currency)}
                    </td>
                  </tr>
                ))}
                {expenses.length === 0 && (
                  <tr>
                    <td colSpan={6} className="text-center py-8 text-gray-400 text-sm">
                      No expenses recorded yet.
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* MODAL: SET BUDGET */}
      {showSetBudgetModal && (
        <div className="fixed inset-0 bg-black/50 backdrop-blur-sm z-50 flex items-center justify-center p-4">
          <div className="bg-white rounded-xl max-w-md w-full p-6 shadow-xl space-y-4">
            <h3 className="text-lg font-bold text-gray-900">Set Initial Project Budget</h3>
            <form onSubmit={handleSetBudget} className="space-y-4">
              <div>
                <label className="block text-xs font-semibold text-gray-700 mb-1">Budget Amount</label>
                <input
                  type="number"
                  step="0.01"
                  required
                  placeholder="e.g. 500000"
                  value={initialBudgetInput}
                  onChange={(e) => setInitialBudgetInput(e.target.value)}
                  className="w-full bg-gray-50 border border-gray-300 rounded-lg p-2.5 text-sm"
                />
              </div>
              <div>
                <label className="block text-xs font-semibold text-gray-700 mb-1">Currency</label>
                <input
                  type="text"
                  required
                  value={budgetCurrency}
                  onChange={(e) => setBudgetCurrency(e.target.value.toUpperCase())}
                  className="w-full bg-gray-50 border border-gray-300 rounded-lg p-2.5 text-sm uppercase"
                />
              </div>
              <div>
                <label className="block text-xs font-semibold text-gray-700 mb-1">Notes / Scope Basis</label>
                <textarea
                  rows={3}
                  value={budgetNotes}
                  onChange={(e) => setBudgetNotes(e.target.value)}
                  placeholder="Initial estimated scope and contingency allocation..."
                  className="w-full bg-gray-50 border border-gray-300 rounded-lg p-2.5 text-sm"
                />
              </div>
              <div className="flex justify-end gap-3 pt-2">
                <button
                  type="button"
                  onClick={() => setShowSetBudgetModal(false)}
                  className="px-4 py-2 text-sm text-gray-600 hover:bg-gray-100 rounded-lg font-medium"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="px-4 py-2 text-sm bg-blue-600 text-white rounded-lg font-semibold hover:bg-blue-700"
                >
                  Save Budget
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* MODAL: REVISE BUDGET */}
      {showReviseBudgetModal && (
        <div className="fixed inset-0 bg-black/50 backdrop-blur-sm z-50 flex items-center justify-center p-4">
          <div className="bg-white rounded-xl max-w-md w-full p-6 shadow-xl space-y-4">
            <h3 className="text-lg font-bold text-gray-900">Request Budget Revision</h3>
            <p className="text-xs text-gray-500">
              Approved revisions update the current budget while preserving historical approved values in the audit trail.
            </p>
            <form onSubmit={handleReviseBudget} className="space-y-4">
              <div>
                <label className="block text-xs font-semibold text-gray-700 mb-1">New Approved Amount</label>
                <input
                  type="number"
                  step="0.01"
                  required
                  placeholder="e.g. 650000"
                  value={revisedAmountInput}
                  onChange={(e) => setRevisedAmountInput(e.target.value)}
                  className="w-full bg-gray-50 border border-gray-300 rounded-lg p-2.5 text-sm"
                />
              </div>
              <div>
                <label className="block text-xs font-semibold text-gray-700 mb-1">Reason for Revision</label>
                <textarea
                  rows={3}
                  required
                  value={revisionReasonInput}
                  onChange={(e) => setRevisionReasonInput(e.target.value)}
                  placeholder="e.g. Scope expansion, unexpected site foundation requirement..."
                  className="w-full bg-gray-50 border border-gray-300 rounded-lg p-2.5 text-sm"
                />
              </div>
              <div className="flex justify-end gap-3 pt-2">
                <button
                  type="button"
                  onClick={() => setShowReviseBudgetModal(false)}
                  className="px-4 py-2 text-sm text-gray-600 hover:bg-gray-100 rounded-lg font-medium"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="px-4 py-2 text-sm bg-indigo-600 text-white rounded-lg font-semibold hover:bg-indigo-700"
                >
                  Approve Revision
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* MODAL: ADD QUOTATION */}
      {showAddQuotationModal && (
        <div className="fixed inset-0 bg-black/50 backdrop-blur-sm z-50 flex items-center justify-center p-4">
          <div className="bg-white rounded-xl max-w-2xl w-full p-6 shadow-xl space-y-4 max-h-[90vh] overflow-y-auto">
            <h3 className="text-lg font-bold text-gray-900">Record Vendor Quotation</h3>
            <form onSubmit={handleCreateQuotation} className="space-y-4">
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-xs font-semibold text-gray-700 mb-1">Vendor</label>
                  <select
                    required
                    value={qVendorId}
                    onChange={(e) => setQVendorId(e.target.value)}
                    className="w-full bg-gray-50 border border-gray-300 rounded-lg p-2.5 text-sm"
                  >
                    <option value="">Select Vendor</option>
                    {vendors.map((v) => (
                      <option key={v.id} value={v.id}>{v.name} ({v.company || 'Vendor'})</option>
                    ))}
                  </select>
                </div>
                <div>
                  <label className="block text-xs font-semibold text-gray-700 mb-1">Quote Reference</label>
                  <input
                    type="text"
                    required
                    placeholder="e.g. QT-2026-001"
                    value={qReference}
                    onChange={(e) => setQReference(e.target.value)}
                    className="w-full bg-gray-50 border border-gray-300 rounded-lg p-2.5 text-sm"
                  >
                  </input>
                </div>
                <div>
                  <label className="block text-xs font-semibold text-gray-700 mb-1">Issue Date</label>
                  <input
                    type="date"
                    required
                    value={qIssueDate}
                    onChange={(e) => setQIssueDate(e.target.value)}
                    className="w-full bg-gray-50 border border-gray-300 rounded-lg p-2.5 text-sm"
                  />
                </div>
                <div>
                  <label className="block text-xs font-semibold text-gray-700 mb-1">Valid Until</label>
                  <input
                    type="date"
                    value={qValidUntil}
                    onChange={(e) => setQValidUntil(e.target.value)}
                    className="w-full bg-gray-50 border border-gray-300 rounded-lg p-2.5 text-sm"
                  />
                </div>
              </div>

              {/* Line Items */}
              <div>
                <div className="flex items-center justify-between mb-2">
                  <label className="text-xs font-semibold text-gray-700 uppercase">Quotation Items</label>
                  <button
                    type="button"
                    onClick={() => setQItems([...qItems, { description: '', quantity: '1', unitPrice: '0' }])}
                    className="text-xs font-semibold text-blue-600 hover:text-blue-800 flex items-center gap-1"
                  >
                    <Plus className="w-3.5 h-3.5" /> Add Item
                  </button>
                </div>
                <div className="space-y-2">
                  {qItems.map((item, idx) => (
                    <div key={idx} className="flex items-center gap-2">
                      <input
                        type="text"
                        placeholder="Item description"
                        value={item.description}
                        onChange={(e) => {
                          const copy = [...qItems];
                          copy[idx].description = e.target.value;
                          setQItems(copy);
                        }}
                        required
                        className="flex-1 bg-gray-50 border border-gray-300 rounded-lg p-2 text-xs"
                      />
                      <input
                        type="number"
                        placeholder="Qty"
                        value={item.quantity}
                        onChange={(e) => {
                          const copy = [...qItems];
                          copy[idx].quantity = e.target.value;
                          setQItems(copy);
                        }}
                        className="w-20 bg-gray-50 border border-gray-300 rounded-lg p-2 text-xs"
                      />
                      <input
                        type="number"
                        placeholder="Unit Price"
                        value={item.unitPrice}
                        onChange={(e) => {
                          const copy = [...qItems];
                          copy[idx].unitPrice = e.target.value;
                          setQItems(copy);
                        }}
                        className="w-24 bg-gray-50 border border-gray-300 rounded-lg p-2 text-xs"
                      />
                      {qItems.length > 1 && (
                        <button
                          type="button"
                          onClick={() => setQItems(qItems.filter((_, i) => i !== idx))}
                          className="text-red-500 hover:text-red-700 text-xs px-2"
                        >
                          ✕
                        </button>
                      )}
                    </div>
                  ))}
                </div>
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-xs font-semibold text-gray-700 mb-1">Taxes / Charges ({budgetCurrency})</label>
                  <input
                    type="number"
                    step="0.01"
                    value={qTax}
                    onChange={(e) => setQTax(e.target.value)}
                    className="w-full bg-gray-50 border border-gray-300 rounded-lg p-2.5 text-sm"
                  />
                </div>
                <div>
                  <label className="block text-xs font-semibold text-gray-700 mb-1">Notes</label>
                  <input
                    type="text"
                    value={qNotes}
                    onChange={(e) => setQNotes(e.target.value)}
                    placeholder="Delivery terms, warranty..."
                    className="w-full bg-gray-50 border border-gray-300 rounded-lg p-2.5 text-sm"
                  />
                </div>
              </div>

              <div className="flex justify-end gap-3 pt-2">
                <button
                  type="button"
                  onClick={() => setShowAddQuotationModal(false)}
                  className="px-4 py-2 text-sm text-gray-600 hover:bg-gray-100 rounded-lg font-medium"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="px-4 py-2 text-sm bg-blue-600 text-white rounded-lg font-semibold hover:bg-blue-700"
                >
                  Save Quotation
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* MODAL: CREATE PURCHASE ORDER */}
      {showCreatePoModal && (
        <div className="fixed inset-0 bg-black/50 backdrop-blur-sm z-50 flex items-center justify-center p-4">
          <div className="bg-white rounded-xl max-w-2xl w-full p-6 shadow-xl space-y-4 max-h-[90vh] overflow-y-auto">
            <h3 className="text-lg font-bold text-gray-900">Create Purchase Order</h3>
            <form onSubmit={handleCreatePO} className="space-y-4">
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-xs font-semibold text-gray-700 mb-1">Vendor</label>
                  <select
                    required
                    value={poVendorId}
                    onChange={(e) => setPoVendorId(e.target.value)}
                    className="w-full bg-gray-50 border border-gray-300 rounded-lg p-2.5 text-sm"
                  >
                    <option value="">Select Vendor</option>
                    {vendors.map((v) => (
                      <option key={v.id} value={v.id}>{v.name} ({v.company || 'Vendor'})</option>
                    ))}
                  </select>
                </div>
                <div>
                  <label className="block text-xs font-semibold text-gray-700 mb-1">PO Reference</label>
                  <input
                    type="text"
                    required
                    placeholder="e.g. PO-ALPHA-2026-01"
                    value={poReference}
                    onChange={(e) => setPoReference(e.target.value)}
                    className="w-full bg-gray-50 border border-gray-300 rounded-lg p-2.5 text-sm"
                  />
                </div>
                <div>
                  <label className="block text-xs font-semibold text-gray-700 mb-1">Linked Quotation (Optional)</label>
                  <select
                    value={poLinkedQuotationId}
                    onChange={(e) => setPoLinkedQuotationId(e.target.value)}
                    className="w-full bg-gray-50 border border-gray-300 rounded-lg p-2.5 text-sm"
                  >
                    <option value="">None</option>
                    {quotations.map((q) => (
                      <option key={q.id} value={q.id}>{q.quoteReference} ({q.vendorName}) - {formatCurrency(q.totalAmount, q.currency)}</option>
                    ))}
                  </select>
                </div>
                <div>
                  <label className="block text-xs font-semibold text-gray-700 mb-1">Expected Delivery Date</label>
                  <input
                    type="date"
                    value={poExpectedDate}
                    onChange={(e) => setPoExpectedDate(e.target.value)}
                    className="w-full bg-gray-50 border border-gray-300 rounded-lg p-2.5 text-sm"
                  />
                </div>
              </div>

              {/* Order Lines */}
              <div>
                <div className="flex items-center justify-between mb-2">
                  <label className="text-xs font-semibold text-gray-700 uppercase">Purchase Order Lines</label>
                  <button
                    type="button"
                    onClick={() => setPoLines([...poLines, { description: '', quantity: '1', unitPrice: '0' }])}
                    className="text-xs font-semibold text-blue-600 hover:text-blue-800 flex items-center gap-1"
                  >
                    <Plus className="w-3.5 h-3.5" /> Add Line
                  </button>
                </div>
                <div className="space-y-2">
                  {poLines.map((line, idx) => (
                    <div key={idx} className="flex items-center gap-2">
                      <input
                        type="text"
                        placeholder="Item or service description"
                        value={line.description}
                        onChange={(e) => {
                          const copy = [...poLines];
                          copy[idx].description = e.target.value;
                          setPoLines(copy);
                        }}
                        required
                        className="flex-1 bg-gray-50 border border-gray-300 rounded-lg p-2 text-xs"
                      />
                      <input
                        type="number"
                        placeholder="Qty"
                        value={line.quantity}
                        onChange={(e) => {
                          const copy = [...poLines];
                          copy[idx].quantity = e.target.value;
                          setPoLines(copy);
                        }}
                        className="w-20 bg-gray-50 border border-gray-300 rounded-lg p-2 text-xs"
                      />
                      <input
                        type="number"
                        placeholder="Unit Price"
                        value={line.unitPrice}
                        onChange={(e) => {
                          const copy = [...poLines];
                          copy[idx].unitPrice = e.target.value;
                          setPoLines(copy);
                        }}
                        className="w-24 bg-gray-50 border border-gray-300 rounded-lg p-2 text-xs"
                      />
                      {poLines.length > 1 && (
                        <button
                          type="button"
                          onClick={() => setPoLines(poLines.filter((_, i) => i !== idx))}
                          className="text-red-500 hover:text-red-700 text-xs px-2"
                        >
                          ✕
                        </button>
                      )}
                    </div>
                  ))}
                </div>
              </div>

              <div>
                <label className="block text-xs font-semibold text-gray-700 mb-1">Notes</label>
                <input
                  type="text"
                  value={poNotes}
                  onChange={(e) => setPoNotes(e.target.value)}
                  placeholder="Terms, payment milestone instructions..."
                  className="w-full bg-gray-50 border border-gray-300 rounded-lg p-2.5 text-sm"
                />
              </div>

              <div className="flex justify-end gap-3 pt-2">
                <button
                  type="button"
                  onClick={() => setShowCreatePoModal(false)}
                  className="px-4 py-2 text-sm text-gray-600 hover:bg-gray-100 rounded-lg font-medium"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="px-4 py-2 text-sm bg-blue-600 text-white rounded-lg font-semibold hover:bg-blue-700"
                >
                  Submit Order
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* MODAL: DELIVERY TRACKING */}
      {showRecordDeliveryModal && selectedPoForDelivery && (
        <div className="fixed inset-0 bg-black/50 backdrop-blur-sm z-50 flex items-center justify-center p-4">
          <div className="bg-white rounded-xl max-w-2xl w-full p-6 shadow-xl space-y-4 max-h-[90vh] overflow-y-auto">
            <div className="flex items-center justify-between">
              <div>
                <h3 className="text-lg font-bold text-gray-900">
                  Deliveries: {selectedPoForDelivery.poReference}
                </h3>
                <p className="text-xs text-gray-500">
                  Vendor: {selectedPoForDelivery.vendorName} | Current Status: {selectedPoForDelivery.deliveryStatus}
                </p>
              </div>
              <button
                onClick={() => setShowRecordDeliveryModal(false)}
                className="text-gray-400 hover:text-gray-600 font-bold"
              >
                ✕
              </button>
            </div>

            {/* Delivery History */}
            <div>
              <h4 className="text-xs font-bold text-gray-700 uppercase mb-2">Recorded Delivery History</h4>
              <div className="border border-gray-200 rounded-lg overflow-hidden">
                <table className="w-full text-left text-xs">
                  <thead className="bg-gray-50 text-gray-500 uppercase">
                    <tr>
                      <th className="py-2 px-3">Date</th>
                      <th className="py-2 px-3">Ref</th>
                      <th className="py-2 px-3">Item</th>
                      <th className="py-2 px-3">Qty Delivered</th>
                      <th className="py-2 px-3">Received By</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-gray-100">
                    {activePoDeliveries.map((del) => (
                      <tr key={del.id}>
                        <td className="py-2 px-3">{del.actualDeliveryDate}</td>
                        <td className="py-2 px-3 font-semibold text-blue-600">{del.deliveryReference}</td>
                        <td className="py-2 px-3 text-gray-700">{del.lineDescription || 'General'}</td>
                        <td className="py-2 px-3 font-bold text-gray-900">{del.quantityDelivered}</td>
                        <td className="py-2 px-3 text-gray-500">{del.receivedBy}</td>
                      </tr>
                    ))}
                    {activePoDeliveries.length === 0 && (
                      <tr>
                        <td colSpan={5} className="py-4 text-center text-gray-400">
                          No deliveries recorded yet for this purchase order.
                        </td>
                      </tr>
                    )}
                  </tbody>
                </table>
              </div>
            </div>

            {/* Form to record new delivery */}
            <form onSubmit={handleRecordDelivery} className="p-4 bg-gray-50 rounded-lg border border-gray-200 space-y-3">
              <h4 className="text-xs font-bold text-gray-800 uppercase">Record New Delivery Against Line</h4>
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-[11px] font-semibold text-gray-700 mb-1">Target Order Line</label>
                  <select
                    value={delLineId}
                    onChange={(e) => setDelLineId(e.target.value)}
                    className="w-full bg-white border border-gray-300 rounded p-2 text-xs"
                  >
                    {selectedPoForDelivery.lines.map((l) => (
                      <option key={l.id} value={l.id}>
                        {l.description} (Ordered: {l.quantity}, Delivered: {l.deliveredQuantity || 0})
                      </option>
                    ))}
                  </select>
                </div>
                <div>
                  <label className="block text-[11px] font-semibold text-gray-700 mb-1">Quantity Delivered</label>
                  <input
                    type="number"
                    step="0.01"
                    required
                    placeholder="e.g. 50"
                    value={delQuantity}
                    onChange={(e) => setDelQuantity(e.target.value)}
                    className="w-full bg-white border border-gray-300 rounded p-2 text-xs"
                  />
                </div>
                <div>
                  <label className="block text-[11px] font-semibold text-gray-700 mb-1">Delivery Challan / Ref</label>
                  <input
                    type="text"
                    placeholder="e.g. CHAL-1049"
                    value={delReference}
                    onChange={(e) => setDelReference(e.target.value)}
                    className="w-full bg-white border border-gray-300 rounded p-2 text-xs"
                  />
                </div>
                <div>
                  <label className="block text-[11px] font-semibold text-gray-700 mb-1">Notes / Inspection</label>
                  <input
                    type="text"
                    placeholder="Condition verified upon receipt..."
                    value={delNotes}
                    onChange={(e) => setDelNotes(e.target.value)}
                    className="w-full bg-white border border-gray-300 rounded p-2 text-xs"
                  />
                </div>
              </div>
              <div className="flex justify-end pt-1">
                <button
                  type="submit"
                  className="px-3 py-1.5 bg-blue-600 text-white rounded text-xs font-semibold hover:bg-blue-700"
                >
                  Log Delivery
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* MODAL: RECORD EXPENSE */}
      {showRecordExpenseModal && (
        <div className="fixed inset-0 bg-black/50 backdrop-blur-sm z-50 flex items-center justify-center p-4">
          <div className="bg-white rounded-xl max-w-md w-full p-6 shadow-xl space-y-4">
            <h3 className="text-lg font-bold text-gray-900">Record Actual Expense</h3>
            <form onSubmit={handleRecordExpense} className="space-y-4">
              <div>
                <label className="block text-xs font-semibold text-gray-700 mb-1">Cost Category</label>
                <select
                  value={expCategory}
                  onChange={(e) => setExpCategory(e.target.value)}
                  className="w-full bg-gray-50 border border-gray-300 rounded-lg p-2.5 text-sm"
                >
                  <option value="MATERIALS">Materials</option>
                  <option value="LABOR">Labor</option>
                  <option value="VENDOR_SERVICES">Vendor Services</option>
                  <option value="EQUIPMENT">Equipment</option>
                  <option value="TRANSPORTATION">Transportation</option>
                  <option value="OTHER">Other Expenses</option>
                </select>
              </div>

              <div>
                <label className="block text-xs font-semibold text-gray-700 mb-1">Amount ({budgetCurrency})</label>
                <input
                  type="number"
                  step="0.01"
                  required
                  placeholder="e.g. 15000"
                  value={expAmount}
                  onChange={(e) => setExpAmount(e.target.value)}
                  className="w-full bg-gray-50 border border-gray-300 rounded-lg p-2.5 text-sm"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-gray-700 mb-1">Expense Date</label>
                <input
                  type="date"
                  required
                  value={expDate}
                  onChange={(e) => setExpDate(e.target.value)}
                  className="w-full bg-gray-50 border border-gray-300 rounded-lg p-2.5 text-sm"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-gray-700 mb-1">Description</label>
                <textarea
                  rows={2}
                  required
                  placeholder="Detailed description of cost incurred..."
                  value={expDescription}
                  onChange={(e) => setExpDescription(e.target.value)}
                  className="w-full bg-gray-50 border border-gray-300 rounded-lg p-2.5 text-sm"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-gray-700 mb-1">Linked Vendor (Optional)</label>
                <select
                  value={expVendorId}
                  onChange={(e) => setExpVendorId(e.target.value)}
                  className="w-full bg-gray-50 border border-gray-300 rounded-lg p-2.5 text-sm"
                >
                  <option value="">None</option>
                  {vendors.map((v) => (
                    <option key={v.id} value={v.id}>{v.name}</option>
                  ))}
                </select>
              </div>

              <div className="flex justify-end gap-3 pt-2">
                <button
                  type="button"
                  onClick={() => setShowRecordExpenseModal(false)}
                  className="px-4 py-2 text-sm text-gray-600 hover:bg-gray-100 rounded-lg font-medium"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="px-4 py-2 text-sm bg-blue-600 text-white rounded-lg font-semibold hover:bg-blue-700"
                >
                  Record Expense
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
export default CostManagement;
