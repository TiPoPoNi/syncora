import React, { useState, useEffect } from 'react';
import { useSearchParams, useNavigate, Link } from 'react-router-dom';
import { api } from '../services/api';
import { Zap, Eye, EyeOff, ArrowRight, ShieldCheck, RefreshCw } from 'lucide-react';
import { Button, Alert } from '../components/ui';

export function ResetPassword() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();

  const urlToken = searchParams.get('token') || '';
  const [token, setToken] = useState(urlToken);
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);
  
  const [verifying, setVerifying] = useState(!!urlToken);
  const [tokenValid, setTokenValid] = useState<boolean | null>(urlToken ? null : true);
  const [tokenAccountInfo, setTokenAccountInfo] = useState<{ username?: string; email?: string } | null>(null);
  
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);

  // Auto-verify token from URL on mount
  useEffect(() => {
    if (!urlToken) return;

    let isMounted = true;
    setVerifying(true);
    setError(null);

    api.get(`/auth/password-reset/verify?token=${encodeURIComponent(urlToken.trim())}`)
      .then((res) => {
        if (!isMounted) return;
        setTokenValid(true);
        setTokenAccountInfo({
          username: res.data.username,
          email: res.data.email,
        });
      })
      .catch((err) => {
        if (!isMounted) return;
        setTokenValid(false);
        setError(err.response?.data?.message || 'This password reset link is invalid, expired, or has already been used. Please request a new link.');
      })
      .finally(() => {
        if (isMounted) setVerifying(false);
      });

    return () => {
      isMounted = false;
    };
  }, [urlToken]);

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

  const strength = getPasswordStrength(newPassword);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    const effectiveToken = token.trim();
    if (!effectiveToken) {
      setError('Password reset token is required.');
      return;
    }
    if (newPassword.length < 12) {
      setError('Password must be at least 12 characters in length.');
      return;
    }
    if (newPassword.length > 128) {
      setError('Password cannot exceed 128 characters.');
      return;
    }
    if (newPassword !== confirmPassword) {
      setError('Passwords do not match. Please re-enter.');
      return;
    }

    try {
      setSubmitting(true);
      setError(null);
      await api.post('/auth/password-reset/confirm', {
        token: effectiveToken,
        newPassword,
        confirmPassword,
      });

      setSuccess('Your password has been reset successfully! Redirecting to sign in...');
      setTimeout(() => {
        navigate('/login');
      }, 2500);
    } catch (err: any) {
      setError(err.response?.data?.message || 'Failed to reset password. The link or token may be expired or invalid.');
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
          <h2 className="text-2xl font-bold tracking-tight text-white">Reset Your Password</h2>
          <p className="text-xs text-slate-400 mt-1">
            Choose a strong, secure passphrase for your SYNCORA account.
          </p>
        </div>

        {verifying && (
          <div className="flex flex-col items-center justify-center py-8 space-y-3">
            <RefreshCw className="w-8 h-8 text-blue-500 animate-spin" />
            <p className="text-sm text-slate-300">Verifying your secure password reset link...</p>
          </div>
        )}

        {!verifying && tokenValid === false && (
          <div className="space-y-4">
            <Alert type="error" title="Invalid or Expired Link">
              {error || 'This password reset link is invalid, expired, or has already been used. For your security, reset links are single-use and expire in 1 hour.'}
            </Alert>
            <div className="pt-2 flex flex-col gap-2">
              <Link
                to="/login"
                className="w-full inline-flex items-center justify-center px-4 py-2.5 rounded-lg bg-blue-600 hover:bg-blue-500 text-white font-medium text-sm transition shadow-lg shadow-blue-500/20"
              >
                Request a New Reset Link
              </Link>
              <Link
                to="/login"
                className="w-full inline-flex items-center justify-center px-4 py-2 rounded-lg text-slate-400 hover:text-slate-200 text-xs transition"
              >
                Back to Sign In
              </Link>
            </div>
          </div>
        )}

        {!verifying && tokenValid !== false && (
          <>
            {tokenAccountInfo && (
              <div className="p-3 bg-blue-950/40 border border-blue-900/60 rounded-lg flex items-center gap-2.5 text-xs text-blue-300">
                <ShieldCheck className="w-4 h-4 text-blue-400 shrink-0" />
                <span>
                  Resetting credentials for <strong className="text-white">{tokenAccountInfo.username || tokenAccountInfo.email}</strong>
                </span>
              </div>
            )}

            {error && (
              <Alert type="error" title="Reset Error">
                {error}
              </Alert>
            )}

            {success ? (
              <div className="space-y-4">
                <Alert type="success" title="Password Updated">
                  {success}
                </Alert>
                <Link
                  to="/login"
                  className="w-full inline-flex items-center justify-center px-4 py-2.5 rounded-lg bg-blue-600 hover:bg-blue-500 text-white font-medium text-sm transition shadow-lg shadow-blue-500/20"
                >
                  Return to Sign In Now
                </Link>
              </div>
            ) : (
              <form onSubmit={handleSubmit} className="space-y-4">
                {/* Token input only if not supplied via URL */}
                {!urlToken && (
                  <div>
                    <label className="block text-xs font-medium text-slate-300 mb-1">Reset Token *</label>
                    <input
                      type="text"
                      required
                      placeholder="Paste your reset token"
                      value={token}
                      onChange={(e) => setToken(e.target.value)}
                      className="w-full px-3 py-2 bg-slate-950 border border-slate-700 rounded-lg text-sm text-white font-mono focus:outline-none focus:ring-2 focus:ring-blue-500"
                    />
                  </div>
                )}

                <div>
                  <div className="flex items-center justify-between mb-1">
                    <label className="block text-xs font-medium text-slate-300">New Password *</label>
                    <span className="text-[11px] text-slate-400">Min. 12 characters</span>
                  </div>
                  <div className="relative">
                    <input
                      type={showPassword ? 'text' : 'password'}
                      required
                      placeholder="Enter at least 12 characters"
                      value={newPassword}
                      onChange={(e) => setNewPassword(e.target.value)}
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
                  {newPassword.length > 0 && (
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
                  <label className="block text-xs font-medium text-slate-300 mb-1">Confirm New Password *</label>
                  <div className="relative">
                    <input
                      type={showConfirmPassword ? 'text' : 'password'}
                      required
                      placeholder="Re-enter your new password"
                      value={confirmPassword}
                      onChange={(e) => setConfirmPassword(e.target.value)}
                      className={`w-full pl-3 pr-10 py-2 bg-slate-950 border rounded-lg text-sm text-white focus:outline-none focus:ring-2 ${
                        confirmPassword && confirmPassword !== newPassword
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
                  {confirmPassword && confirmPassword !== newPassword && (
                    <p className="text-[11px] text-rose-400 mt-1">Passwords do not match.</p>
                  )}
                </div>

                <div className="p-3 bg-slate-950/60 border border-slate-800/80 rounded-lg text-[11px] text-slate-400 space-y-1">
                  <p className="font-semibold text-slate-300">Password Policy Requirements:</p>
                  <ul className="list-disc list-inside space-y-0.5 text-slate-400">
                    <li>Must be between 12 and 128 characters in length</li>
                    <li>Avoid easily guessable words and common passwords</li>
                    <li>Cannot contain your username</li>
                  </ul>
                </div>

                <Button
                  type="submit"
                  variant="primary"
                  size="lg"
                  className="w-full justify-center mt-2"
                  isLoading={submitting}
                  disabled={newPassword.length < 12 || newPassword !== confirmPassword}
                  rightIcon={<ArrowRight className="w-4 h-4" />}
                >
                  Update Password
                </Button>
              </form>
            )}

            <div className="pt-4 border-t border-slate-800 text-center">
              <Link to="/login" className="text-xs text-blue-400 hover:text-blue-300 transition">
                Back to Sign In
              </Link>
            </div>
          </>
        )}
      </div>
    </div>
  );
}
