import React, { useState, useEffect } from 'react';
import { useSearchParams, useNavigate, Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { api } from '../services/api';
import { Zap, Eye, EyeOff, ArrowRight, Building2 } from 'lucide-react';
import { Button, Input, Alert } from '../components/ui';

export function ActivateAccount() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const { login } = useAuth();

  const token = searchParams.get('token') || '';

  const [verifying, setVerifying] = useState(true);
  const [invitationData, setInvitationData] = useState<{
    valid: boolean;
    organizationName: string;
    organizationSlug: string;
    email: string;
    username: string;
    role: string;
    employeeId: string;
  } | null>(null);

  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [fullName, setFullName] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    document.title = 'SYNCORA | Account Activation';
    if (!token) {
      setVerifying(false);
      setError('No activation token provided. Please use the complete link from your invitation email.');
      return;
    }

    const verifyToken = async () => {
      try {
        setVerifying(true);
        setError(null);
        const res = await api.get(`/auth/activate/verify?token=${encodeURIComponent(token)}`);
        setInvitationData(res.data);
      } catch (err: any) {
        setError(err.response?.data?.message || 'Invalid or expired invitation token. Please request a new invitation from your administrator.');
      } finally {
        setVerifying(false);
      }
    };

    verifyToken();
  }, [token]);

  // Password strength calculation
  const getPasswordStrength = (pwd: string) => {
    if (!pwd) return { score: 0, label: 'None', color: 'bg-slate-700' };
    let score = 0;
    if (pwd.length >= 12) score += 2;
    else if (pwd.length >= 8) score += 1;
    if (/[A-Z]/.test(pwd)) score += 1;
    if (/[0-9]/.test(pwd)) score += 1;
    if (/[^A-Za-z0-9]/.test(pwd)) score += 1;

    if (score <= 1) return { score: 1, label: 'Weak', color: 'bg-rose-500' };
    if (score <= 3) return { score: 2, label: 'Fair', color: 'bg-amber-500' };
    if (score <= 4) return { score: 3, label: 'Good', color: 'bg-blue-500' };
    return { score: 4, label: 'Strong', color: 'bg-emerald-500' };
  };

  const strength = getPasswordStrength(password);

  const handleActivate = async (e: React.FormEvent) => {
    e.preventDefault();
    if (password.length < 12) {
      setError('Password must be at least 12 characters in length.');
      return;
    }
    if (password.length > 128) {
      setError('Password cannot exceed 128 characters.');
      return;
    }
    if (password !== confirmPassword) {
      setError('Passwords do not match. Please re-enter.');
      return;
    }

    try {
      setSubmitting(true);
      setError(null);
      const res = await api.post('/auth/activate', {
        token,
        password,
        confirmPassword,
        fullName: fullName.trim() || undefined,
      });

      // Automatically authenticate and redirect to workspace
      login(res.data);
      navigate('/');
    } catch (err: any) {
      setError(err.response?.data?.message || 'Account activation failed. Please try again.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center p-6 bg-slate-950 text-slate-100 font-sans">
      <div className="max-w-md w-full bg-slate-900 border border-slate-800 rounded-2xl p-8 shadow-2xl space-y-6">
        {/* Brand Header */}
        <div className="flex items-center gap-3">
          <div className="w-10 h-10 rounded-xl bg-blue-600 flex items-center justify-center text-white shadow-lg shadow-blue-500/30">
            <Zap className="w-5 h-5 fill-white" />
          </div>
          <div>
            <span className="text-xl font-black tracking-tight text-white block">SYNCORA</span>
            <span className="block text-[11px] font-bold text-blue-400 tracking-wide uppercase">Plan. Coordinate. Deliver.</span>
          </div>
        </div>

        <div>
          <h2 className="text-2xl font-bold tracking-tight text-white">Activate Your Employee Account</h2>
          <p className="text-xs text-slate-400 mt-1">Set your permanent password to complete your workspace onboarding.</p>
        </div>

        {verifying && (
          <div className="py-8 flex flex-col items-center justify-center gap-3 text-sm text-slate-400">
            <div className="w-8 h-8 border-2 border-blue-500 border-t-transparent rounded-full animate-spin" />
            <span>Verifying invitation credentials...</span>
          </div>
        )}

        {error && (
          <Alert type="error" title="Activation Error">
            {error}
          </Alert>
        )}

        {!verifying && invitationData && (
          <div className="space-y-5">
            {/* Invitation Details Summary Card */}
            <div className="bg-slate-800/80 border border-slate-700/80 rounded-xl p-4 text-xs space-y-2">
              <div className="flex items-center justify-between">
                <span className="text-slate-400">Organization:</span>
                <span className="font-semibold text-white flex items-center gap-1.5">
                  <Building2 className="w-3.5 h-3.5 text-blue-400" />
                  {invitationData.organizationName}
                </span>
              </div>
              <div className="flex items-center justify-between">
                <span className="text-slate-400">Work Email:</span>
                <span className="font-mono text-slate-200">{invitationData.email}</span>
              </div>
              <div className="flex items-center justify-between">
                <span className="text-slate-400">Assigned Employee ID:</span>
                <span className="font-mono font-bold text-emerald-400">{invitationData.employeeId}</span>
              </div>
              <div className="flex items-center justify-between">
                <span className="text-slate-400">Assigned Role:</span>
                <span className="px-2 py-0.5 rounded-full bg-blue-500/20 text-blue-300 font-medium">
                  {invitationData.role.replace('_', ' ')}
                </span>
              </div>
            </div>

            <form onSubmit={handleActivate} className="space-y-4">
              <div>
                <label className="block text-xs font-medium text-slate-300 mb-1">Your Full Name (optional)</label>
                <input
                  type="text"
                  placeholder="e.g. Jane Doe"
                  value={fullName}
                  onChange={(e) => setFullName(e.target.value)}
                  className="w-full px-3 py-2 bg-slate-950 border border-slate-700 rounded-lg text-sm text-white focus:outline-none focus:ring-2 focus:ring-blue-500"
                />
              </div>

              <div>
                <div className="flex items-center justify-between mb-1">
                  <label className="block text-xs font-medium text-slate-300">Set Password *</label>
                  <span className="text-[11px] text-slate-400">Min. 12 characters</span>
                </div>
                <div className="relative">
                  <input
                    type={showPassword ? 'text' : 'password'}
                    required
                    placeholder="Enter at least 12 characters"
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    className="w-full pl-3 pr-10 py-2 bg-slate-950 border border-slate-700 rounded-lg text-sm text-white focus:outline-none focus:ring-2 focus:ring-blue-500"
                  />
                  <button
                    type="button"
                    onClick={() => setShowPassword(!showPassword)}
                    className="absolute right-2.5 top-2.5 text-slate-400 hover:text-slate-200"
                  >
                    {showPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                  </button>
                </div>

                {/* Password Strength Meter */}
                {password.length > 0 && (
                  <div className="mt-2 space-y-1">
                    <div className="flex items-center justify-between text-[11px]">
                      <span className="text-slate-400">Strength:</span>
                      <span className="font-semibold text-slate-200">{strength.label}</span>
                    </div>
                    <div className="w-full h-1.5 bg-slate-800 rounded-full overflow-hidden flex gap-0.5">
                      <div className={`h-full flex-1 transition-all ${strength.score >= 1 ? strength.color : 'bg-slate-800'}`} />
                      <div className={`h-full flex-1 transition-all ${strength.score >= 2 ? strength.color : 'bg-slate-800'}`} />
                      <div className={`h-full flex-1 transition-all ${strength.score >= 3 ? strength.color : 'bg-slate-800'}`} />
                      <div className={`h-full flex-1 transition-all ${strength.score >= 4 ? strength.color : 'bg-slate-800'}`} />
                    </div>
                  </div>
                )}
              </div>

              <div>
                <label className="block text-xs font-medium text-slate-300 mb-1">Confirm Password *</label>
                <div className="relative">
                  <input
                    type={showConfirmPassword ? 'text' : 'password'}
                    required
                    placeholder="Re-enter password"
                    value={confirmPassword}
                    onChange={(e) => setConfirmPassword(e.target.value)}
                    className={`w-full pl-3 pr-10 py-2 bg-slate-950 border rounded-lg text-sm text-white focus:outline-none focus:ring-2 ${
                      confirmPassword && confirmPassword !== password
                        ? 'border-rose-500 focus:ring-rose-500'
                        : 'border-slate-700 focus:ring-blue-500'
                    }`}
                  />
                  <button
                    type="button"
                    onClick={() => setShowConfirmPassword(!showConfirmPassword)}
                    className="absolute right-2.5 top-2.5 text-slate-400 hover:text-slate-200"
                  >
                    {showConfirmPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                  </button>
                </div>
                {confirmPassword && confirmPassword !== password && (
                  <p className="text-[11px] text-rose-400 mt-1">Passwords do not match.</p>
                )}
              </div>

              <div className="p-3 bg-slate-950/60 border border-slate-800/80 rounded-lg text-[11px] text-slate-400 space-y-1">
                <p className="font-semibold text-slate-300">Password Policy Requirements:</p>
                <ul className="list-disc list-inside space-y-0.5 text-slate-400">
                  <li>Must be between 12 and 128 characters in length</li>
                  <li>Avoid common passwords or words easily guessed</li>
                  <li>Cannot contain your username</li>
                </ul>
              </div>

              <Button
                type="submit"
                variant="primary"
                size="lg"
                className="w-full justify-center mt-2"
                isLoading={submitting}
                disabled={password.length < 12 || password !== confirmPassword}
                rightIcon={<ArrowRight className="w-4 h-4" />}
              >
                Activate Account & Sign In
              </Button>
            </form>
          </div>
        )}

        <div className="pt-4 border-t border-slate-800 text-center">
          <Link to="/login" className="text-xs text-blue-400 hover:text-blue-300 transition">
            Back to Sign In
          </Link>
        </div>
      </div>
    </div>
  );
}
