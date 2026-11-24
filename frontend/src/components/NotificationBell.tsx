import React, { useState, useEffect, useRef } from 'react';
import { api } from '../services/api';
import {
  Bell,
  Check,
  CheckCheck,
  Clock,
  AlertTriangle,
  Users,
  Sparkles,
  Settings,
  X,
  ExternalLink,
} from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { NotificationPreferencesModal } from './NotificationPreferencesModal';

export interface NotificationItem {
  id: number;
  eventType: string;
  title: string;
  message: string;
  relatedProjectId: number | null;
  relatedTaskId: number | null;
  isRead: boolean;
  deliveryChannel: string;
  deliveryStatus: string;
  createdAt: string;
}

export function NotificationBell() {
  const [isOpen, setIsOpen] = useState(false);
  const [unreadCount, setUnreadCount] = useState<number>(0);
  const [notifications, setNotifications] = useState<NotificationItem[]>([]);
  const [filterUnread, setFilterUnread] = useState(false);
  const [loading, setLoading] = useState(false);
  const [prefsOpen, setPrefsOpen] = useState(false);

  const popoverRef = useRef<HTMLDivElement>(null);
  const navigate = useNavigate();

  // Fetch unread count periodically or on mount
  const fetchUnreadCount = async () => {
    try {
      const res = await api.get('/notifications/unread-count');
      setUnreadCount(res.data.unreadCount || 0);
    } catch (err) {
      // Ignore network errors in polling
    }
  };

  const fetchNotifications = async () => {
    setLoading(true);
    try {
      const res = await api.get(`/notifications?unreadOnly=${filterUnread}`);
      setNotifications(res.data || []);
      fetchUnreadCount();
    } catch (err) {
      console.error('Failed to load notifications:', err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchUnreadCount();
    const interval = setInterval(fetchUnreadCount, 30000); // 30s poll
    return () => clearInterval(interval);
  }, []);

  useEffect(() => {
    if (isOpen) {
      fetchNotifications();
    }
  }, [isOpen, filterUnread]);

  // Click outside listener
  useEffect(() => {
    function handleClickOutside(event: MouseEvent) {
      if (popoverRef.current && !popoverRef.current.contains(event.target as Node)) {
        setIsOpen(false);
      }
    }
    if (isOpen) {
      document.addEventListener('mousedown', handleClickOutside);
    }
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, [isOpen]);

  const handleMarkAsRead = async (id: number, e: React.MouseEvent) => {
    e.stopPropagation();
    try {
      await api.patch(`/notifications/${id}/read`);
      setNotifications((prev) =>
        prev.map((n) => (n.id === id ? { ...n, isRead: true } : n))
      );
      setUnreadCount((c) => Math.max(0, c - 1));
    } catch (err) {
      console.error('Failed to mark notification as read:', err);
    }
  };

  const handleMarkAllAsRead = async () => {
    try {
      await api.patch('/notifications/read-all');
      setNotifications((prev) => prev.map((n) => ({ ...n, isRead: true })));
      setUnreadCount(0);
    } catch (err) {
      console.error('Failed to mark all as read:', err);
    }
  };

  const handleNotificationClick = (item: NotificationItem) => {
    if (!item.isRead) {
      api.patch(`/notifications/${item.id}/read`).catch(() => {});
      setUnreadCount((c) => Math.max(0, c - 1));
    }
    setIsOpen(false);

    // Route to appropriate view
    if (item.eventType === 'VENDOR_DELAY') {
      navigate('/schedule?tab=delays');
    } else if (item.eventType === 'RESOURCE_CONFLICT') {
      navigate('/schedule?tab=conflicts');
    } else if (item.eventType === 'RECOMMENDATION_APPLIED') {
      navigate('/schedule?tab=optimization');
    } else if (item.eventType === 'MILESTONE_COMPLETED') {
      navigate('/schedule?tab=analytics');
    } else {
      navigate('/schedule?tab=gantt');
    }
  };

  const getEventIcon = (type: string) => {
    switch (type) {
      case 'RESOURCE_CONFLICT':
        return <Users className="w-4 h-4 text-rose-500" />;
      case 'VENDOR_DELAY':
        return <AlertTriangle className="w-4 h-4 text-amber-500" />;
      case 'TASK_DEADLINE_APPROACHING':
      case 'PROJECT_DEADLINE_APPROACHING':
      case 'TASK_OVERDUE':
        return <Clock className="w-4 h-4 text-blue-500" />;
      case 'RECOMMENDATION_APPLIED':
        return <Sparkles className="w-4 h-4 text-purple-500" />;
      case 'MILESTONE_COMPLETED':
      case 'PROJECT_COMPLETED':
        return <Check className="w-4 h-4 text-emerald-500" />;
      default:
        return <Bell className="w-4 h-4 text-slate-500" />;
    }
  };

  const formatTimestamp = (ts: string) => {
    if (!ts) return '';
    try {
      const d = new Date(ts);
      return d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) + ' ' + d.toLocaleDateString([], { month: 'short', day: 'numeric' });
    } catch {
      return ts;
    }
  };

  return (
    <div className="relative" ref={popoverRef}>
      {/* Bell Trigger */}
      <button
        onClick={() => setIsOpen(!isOpen)}
        className="relative p-2 rounded-xl text-gray-500 hover:text-indigo-600 hover:bg-slate-100 transition"
        title="Notifications"
      >
        <Bell className="w-5 h-5" />
        {unreadCount > 0 && (
          <span className="absolute top-1.5 right-1.5 flex h-4 min-w-4 items-center justify-center rounded-full bg-rose-500 px-1 text-[10px] font-bold text-white shadow-sm animate-pulse">
            {unreadCount > 9 ? '9+' : unreadCount}
          </span>
        )}
      </button>

      {/* Popover Drawer */}
      {isOpen && (
        <div className="absolute right-0 mt-2 w-96 max-w-[calc(100vw-2rem)] rounded-2xl bg-white shadow-2xl border border-slate-200 z-50 overflow-hidden">
          {/* Header */}
          <div className="flex items-center justify-between px-4 py-3 border-b border-slate-100 bg-slate-50/70">
            <div className="flex items-center gap-2">
              <span className="font-bold text-slate-800 text-sm">Notifications</span>
              {unreadCount > 0 && (
                <span className="bg-indigo-100 text-indigo-700 text-xs px-2 py-0.5 rounded-full font-semibold">
                  {unreadCount} unread
                </span>
              )}
            </div>
            <div className="flex items-center gap-1">
              <button
                onClick={() => setPrefsOpen(true)}
                className="p-1.5 text-slate-400 hover:text-slate-700 hover:bg-slate-200/60 rounded-lg transition"
                title="Notification Settings"
              >
                <Settings className="w-4 h-4" />
              </button>
              {unreadCount > 0 && (
                <button
                  onClick={handleMarkAllAsRead}
                  className="flex items-center gap-1 text-xs text-indigo-600 hover:text-indigo-800 font-semibold px-2 py-1 hover:bg-indigo-50 rounded-lg transition"
                  title="Mark all as read"
                >
                  <CheckCheck className="w-3.5 h-3.5" />
                  Mark all read
                </button>
              )}
            </div>
          </div>

          {/* Filter Bar */}
          <div className="flex border-b border-slate-100 px-4 py-2 bg-white text-xs gap-2">
            <button
              onClick={() => setFilterUnread(false)}
              className={`px-3 py-1 rounded-lg font-medium transition ${
                !filterUnread
                  ? 'bg-indigo-50 text-indigo-700 font-semibold'
                  : 'text-slate-500 hover:bg-slate-50'
              }`}
            >
              All
            </button>
            <button
              onClick={() => setFilterUnread(true)}
              className={`px-3 py-1 rounded-lg font-medium transition ${
                filterUnread
                  ? 'bg-indigo-50 text-indigo-700 font-semibold'
                  : 'text-slate-500 hover:bg-slate-50'
              }`}
            >
              Unread only
            </button>
          </div>

          {/* Notification List */}
          <div className="max-h-96 overflow-y-auto divide-y divide-slate-100">
            {loading ? (
              <div className="p-8 text-center text-slate-400 text-xs">Loading notifications...</div>
            ) : notifications.length === 0 ? (
              <div className="p-8 text-center text-slate-400">
                <Bell className="w-8 h-8 mx-auto text-slate-300 mb-2 stroke-1" />
                <p className="text-sm font-medium text-slate-600">No notifications</p>
                <p className="text-xs text-slate-400 mt-1">You're all caught up with deadlines and tasks.</p>
              </div>
            ) : (
              notifications.map((n) => (
                <div
                  key={n.id}
                  onClick={() => handleNotificationClick(n)}
                  className={`p-3.5 flex items-start gap-3 hover:bg-slate-50 transition cursor-pointer ${
                    !n.isRead ? 'bg-indigo-50/30' : ''
                  }`}
                >
                  <div className="mt-0.5 p-1.5 rounded-lg bg-slate-100 shrink-0">
                    {getEventIcon(n.eventType)}
                  </div>
                  <div className="flex-1 min-w-0">
                    <div className="flex items-baseline justify-between gap-2">
                      <p className={`text-xs font-semibold truncate ${!n.isRead ? 'text-slate-900' : 'text-slate-700'}`}>
                        {n.title}
                      </p>
                      <span className="text-[10px] text-slate-400 shrink-0">
                        {formatTimestamp(n.createdAt)}
                      </span>
                    </div>
                    <p className="text-xs text-slate-500 mt-0.5 line-clamp-2 leading-relaxed">
                      {n.message}
                    </p>
                  </div>
                  {!n.isRead && (
                    <button
                      onClick={(e) => handleMarkAsRead(n.id, e)}
                      className="text-slate-400 hover:text-indigo-600 p-1 hover:bg-indigo-50 rounded-md transition shrink-0"
                      title="Mark as read"
                    >
                      <Check className="w-3.5 h-3.5" />
                    </button>
                  )}
                </div>
              ))
            )}
          </div>

          {/* Footer */}
          <div className="px-4 py-2 border-t border-slate-100 bg-slate-50/50 text-center">
            <span className="text-[11px] text-slate-400">
              Database-backed event notifications & deduplicated reminders
            </span>
          </div>
        </div>
      )}

      {/* Preferences Modal */}
      <NotificationPreferencesModal
        isOpen={prefsOpen}
        onClose={() => setPrefsOpen(false)}
      />
    </div>
  );
}
