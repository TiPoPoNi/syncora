import React, { Suspense, lazy } from 'react';
import { HashRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider, useAuth } from './context/AuthContext';
import { OrganizationProvider } from './context/OrganizationContext';
import { ThemeProvider } from './context/ThemeContext';
import { Layout } from './components/Layout';

const Login = lazy(() => import('./pages/Login').then(m => ({ default: m.Login })));
const Dashboard = lazy(() => import('./pages/Dashboard').then(m => ({ default: m.Dashboard })));
const Schedule = lazy(() => import('./pages/Schedule').then(m => ({ default: m.Schedule })));
const Vendors = lazy(() => import('./pages/Vendors').then(m => ({ default: m.Vendors })));
const PortfolioAnalytics = lazy(() => import('./pages/PortfolioAnalytics').then(m => ({ default: m.PortfolioAnalytics })));
const OrganizationSettings = lazy(() => import('./pages/OrganizationSettings').then(m => ({ default: m.OrganizationSettings })));
const Settings = lazy(() => import('./pages/Settings').then(m => ({ default: m.Settings })));
const Projects = lazy(() => import('./pages/Projects').then(m => ({ default: m.Projects })));
const Clients = lazy(() => import('./pages/Clients').then(m => ({ default: m.Clients })));
const Workers = lazy(() => import('./pages/Workers').then(m => ({ default: m.Workers })));
const Tasks = lazy(() => import('./pages/Tasks').then(m => ({ default: m.Tasks })));
const CostManagement = lazy(() => import('./pages/CostManagement').then(m => ({ default: m.CostManagement })));
const DeliverablesManagement = lazy(() => import('./pages/DeliverablesManagement').then(m => ({ default: m.DeliverablesManagement })));
const Integrations = lazy(() => import('./pages/Integrations').then(m => ({ default: m.Integrations })));
const ActivateAccount = lazy(() => import('./pages/ActivateAccount').then(m => ({ default: m.ActivateAccount })));
const ResetPassword = lazy(() => import('./pages/ResetPassword').then(m => ({ default: m.ResetPassword })));
const AiAssistant = lazy(() => import('./pages/AiAssistant').then(m => ({ default: m.AiAssistant })));
const HistoricalMlRisk = lazy(() => import('./pages/HistoricalMlRisk').then(m => ({ default: m.HistoricalMlRisk })));

function RouteLoading() {
  return (
    <div className="flex items-center justify-center min-h-[60vh]">
      <div className="w-8 h-8 border-4 border-indigo-600 border-t-transparent rounded-full animate-spin"></div>
    </div>
  );
}

function ProtectedRoute({ children }: { children: React.ReactNode }) {
  const { user } = useAuth();
  if (!user) {
    return <Navigate to="/login" replace />;
  }
  return <Layout><Suspense fallback={<RouteLoading />}>{children}</Suspense></Layout>;
}

function AppRoutes() {
  const { user } = useAuth();
  return (
    <Suspense fallback={<RouteLoading />}>
      <Routes>
        <Route path="/login" element={user ? <Navigate to="/" /> : <Login />} />
        <Route path="/activate" element={<ActivateAccount />} />
        <Route path="/reset-password" element={<ResetPassword />} />
        <Route path="/" element={<ProtectedRoute><Dashboard /></ProtectedRoute>} />
        <Route path="/analytics" element={<ProtectedRoute><PortfolioAnalytics /></ProtectedRoute>} />
        <Route path="/budget" element={<ProtectedRoute><CostManagement /></ProtectedRoute>} />
        <Route path="/cost-management" element={<ProtectedRoute><CostManagement /></ProtectedRoute>} />
        <Route path="/deliverables" element={<ProtectedRoute><DeliverablesManagement /></ProtectedRoute>} />
        <Route path="/integrations" element={<ProtectedRoute><Integrations /></ProtectedRoute>} />
        <Route path="/projects" element={<ProtectedRoute><Projects /></ProtectedRoute>} />
        <Route path="/clients" element={<ProtectedRoute><Clients /></ProtectedRoute>} />
        <Route path="/workers" element={<ProtectedRoute><Workers /></ProtectedRoute>} />
        <Route path="/vendors" element={<ProtectedRoute><Vendors /></ProtectedRoute>} />
        <Route path="/tasks" element={<ProtectedRoute><Tasks /></ProtectedRoute>} />
        <Route path="/schedule" element={<ProtectedRoute><Schedule /></ProtectedRoute>} />
        <Route path="/optimization" element={<Navigate to="/schedule?tab=optimization" replace />} />
        <Route path="/assistant" element={<ProtectedRoute><AiAssistant /></ProtectedRoute>} />
        <Route path="/ai-assistant" element={<ProtectedRoute><AiAssistant /></ProtectedRoute>} />
        <Route path="/ml-risk" element={<ProtectedRoute><HistoricalMlRisk /></ProtectedRoute>} />
        <Route path="/delay-risk" element={<ProtectedRoute><HistoricalMlRisk /></ProtectedRoute>} />
        <Route path="/organization" element={<ProtectedRoute><OrganizationSettings /></ProtectedRoute>} />
        <Route path="/settings" element={<ProtectedRoute><Settings /></ProtectedRoute>} />
      </Routes>
    </Suspense>
  );
}

export default function App() {
  return (
    <AuthProvider>
      <OrganizationProvider>
        <ThemeProvider>
          <HashRouter>
            <AppRoutes />
          </HashRouter>
        </ThemeProvider>
      </OrganizationProvider>
    </AuthProvider>
  );
}

