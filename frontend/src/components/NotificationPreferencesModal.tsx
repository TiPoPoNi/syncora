import React, { useState, useEffect } from 'react';
import { api } from '../services/api';
import { X, Bell, Mail, Clock, Users, AlertTriangle, Sparkles, Check } from 'lucide-react';

interface NotificationPreferences {
  inAppEnabled: boolean;
  emailEnabled: boolean;
  deadlineRemindersEnabled: boolean;
  taskAssignmentsEnabled: boolean;
  delayAlertsEnabled: boolean;
  recommendationAlertsEnabled: boolean;
}

interface Props {
  isOpen: boolean;
  onClose: () => void;
}

export function NotificationPreferencesModal({ isOpen, onClose }: Props) {
  const [prefs, setPrefs] = useState<NotificationPreferences>({
    inAppEnabled: true,
    emailEnabled: false,
    deadlineRemindersEnabled: true,
    taskAssignmentsEnabled: true,
    delayAlertsEnabled: true,
    recommendationAlertsEnabled: true,
  });
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [savedSuccess, setSavedSuccess] = useState(false);

  useEffect(() => {
    if (isOpen) {
      setLoading(true);
      setSavedSuccess(false);
      api.get('/users/me/notification-preferences')
        .then((res) => {
          if (res.data) setPrefs(res.data);
        })
        .catch((err) => console.error('Failed to load preferences:', err))
        .finally(() => setLoading(false));
    }
  }, [isOpen]);

  if (!isOpen) return null;

  const handleToggle = (key: keyof NotificationPreferences) => {
    setPrefs((prev) => ({ ...prev, [key]: !prev[key] }));
  };

  const handleSave = async () => {
    setSaving(true);
    try {
      const res = await api.put('/users/me/notification-preferences', prefs);
      setPrefs(res.data);
      setSavedSuccess(true);
      setTimeout(() => {
        setSavedSuccess(false);
        onClose();
      }, 1000);
    } catch (err: any) {
      alert('Failed to save preferences: ' + (err.response?.data?.message || err.message));
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-sm p-4">
      <div className="bg-white rounded-2xl border border-slate-200 shadow-2xl max-w-md w-full overflow-hidden">
        {/* Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-slate-100 bg-slate-50/50">
          <div className="flex items-center gap-2 text-slate-800 font-bold text-base">
            <Bell className="w-5 h-5 text-indigo-600" />
            Notification Preferences
          </div>
          <button
            onClick={onClose}
            className="text-slate-400 hover:text-slate-600 p-1 rounded-lg hover:bg-slate-100 transition"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Content */}
        <div className="p-6 space-y-5">
          {loading ? (
            <div className="py-8 text-center text-slate-400 text-sm">Loading user preferences...</div>
          ) : (
            <>
              {/* Delivery Channels */}
              <div>
                <span className="text-xs font-bold text-slate-400 uppercase tracking-wider block mb-3">
                  Delivery Channels
                </span>
                <div className="space-y-3">
                  <label className="flex items-center justify-between p-3 rounded-xl border border-slate-200 hover:bg-slate-50 cursor-pointer transition">
                    <div className="flex items-center gap-3">
                      <Bell className="w-4 h-4 text-indigo-500" />
                      <div>
                        <div className="text-sm font-semibold text-slate-800">In-App Notifications</div>
                        <div className="text-xs text-slate-400">Receive alerts in the notification center</div>
                      </div>
                    </div>
                    <input
                      type="checkbox"
                      checked={prefs.inAppEnabled}
                      onChange={() => handleToggle('inAppEnabled')}
                      className="w-4 h-4 text-indigo-600 rounded border-slate-300 focus:ring-indigo-500"
                    />
                  </label>

                  <label className="flex items-center justify-between p-3 rounded-xl border border-slate-200 hover:bg-slate-50 cursor-pointer transition">
                    <div className="flex items-center gap-3">
                      <Mail className="w-4 h-4 text-blue-500" />
                      <div>
                        <div className="text-sm font-semibold text-slate-800">Email Notifications</div>
                        <div className="text-xs text-slate-400">Mock delivery enabled in demo mode</div>
                      </div>
                    </div>
                    <input
                      type="checkbox"
                      checked={prefs.emailEnabled}
                      onChange={() => handleToggle('emailEnabled')}
                      className="w-4 h-4 text-indigo-600 rounded border-slate-300 focus:ring-indigo-500"
                    />
                  </label>
                </div>
              </div>

              {/* Event Subscriptions */}
              <div>
                <span className="text-xs font-bold text-slate-400 uppercase tracking-wider block mb-3">
                  Subscribed Event Categories
                </span>
                <div className="space-y-3">
                  <label className="flex items-center justify-between p-2.5 rounded-lg hover:bg-slate-50 cursor-pointer transition">
                    <div className="flex items-center gap-2.5">
                      <Clock className="w-4 h-4 text-amber-500" />
                      <span className="text-sm text-slate-700">Deadline Reminders & Overdue Alerts</span>
                    </div>
                    <input
                      type="checkbox"
                      checked={prefs.deadlineRemindersEnabled}
                      onChange={() => handleToggle('deadlineRemindersEnabled')}
                      className="w-4 h-4 text-indigo-600 rounded border-slate-300 focus:ring-indigo-500"
                    />
                  </label>

                  <label className="flex items-center justify-between p-2.5 rounded-lg hover:bg-slate-50 cursor-pointer transition">
                    <div className="flex items-center gap-2.5">
                      <Users className="w-4 h-4 text-emerald-500" />
                      <span className="text-sm text-slate-700">Task Assignments & Reassignments</span>
                    </div>
                    <input
                      type="checkbox"
                      checked={prefs.taskAssignmentsEnabled}
                      onChange={() => handleToggle('taskAssignmentsEnabled')}
                      className="w-4 h-4 text-indigo-600 rounded border-slate-300 focus:ring-indigo-500"
                    />
                  </label>

                  <label className="flex items-center justify-between p-2.5 rounded-lg hover:bg-slate-50 cursor-pointer transition">
                    <div className="flex items-center gap-2.5">
                      <AlertTriangle className="w-4 h-4 text-rose-500" />
                      <span className="text-sm text-slate-700">Vendor Delays & Overlap Conflicts</span>
                    </div>
                    <input
                      type="checkbox"
                      checked={prefs.delayAlertsEnabled}
                      onChange={() => handleToggle('delayAlertsEnabled')}
                      className="w-4 h-4 text-indigo-600 rounded border-slate-300 focus:ring-indigo-500"
                    />
                  </label>

                  <label className="flex items-center justify-between p-2.5 rounded-lg hover:bg-slate-50 cursor-pointer transition">
                    <div className="flex items-center gap-2.5">
                      <Sparkles className="w-4 h-4 text-purple-500" />
                      <span className="text-sm text-slate-700">Applied Schedule Recommendations</span>
                    </div>
                    <input
                      type="checkbox"
                      checked={prefs.recommendationAlertsEnabled}
                      onChange={() => handleToggle('recommendationAlertsEnabled')}
                      className="w-4 h-4 text-indigo-600 rounded border-slate-300 focus:ring-indigo-500"
                    />
                  </label>
                </div>
              </div>
            </>
          )}
        </div>

        {/* Footer */}
        <div className="flex items-center justify-end gap-3 px-6 py-4 border-t border-slate-100 bg-slate-50/50">
          <button
            onClick={onClose}
            className="px-4 py-2 rounded-xl text-sm font-medium text-slate-600 hover:bg-slate-200 transition"
          >
            Cancel
          </button>
          <button
            onClick={handleSave}
            disabled={saving || loading}
            className="flex items-center gap-2 px-5 py-2 bg-indigo-600 hover:bg-indigo-700 text-white font-medium text-sm rounded-xl shadow-sm transition disabled:opacity-50"
          >
            {savedSuccess ? (
              <>
                <Check className="w-4 h-4" />
                Saved!
              </>
            ) : saving ? (
              'Saving...'
            ) : (
              'Save Preferences'
            )}
          </button>
        </div>
      </div>
    </div>
  );
}
