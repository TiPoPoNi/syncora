import React from 'react';
import { AlertCircle, CheckCircle2, AlertTriangle, Info, X } from 'lucide-react';

export type AlertType = 'info' | 'success' | 'warning' | 'error';

export interface AlertProps {
  type?: AlertType;
  title?: string;
  children: React.ReactNode;
  onClose?: () => void;
  className?: string;
}

export function Alert({
  type = 'info',
  title,
  children,
  onClose,
  className = '',
}: AlertProps) {
  const styles: Record<
    AlertType,
    { bg: string; border: string; text: string; icon: React.ReactNode }
  > = {
    info: {
      bg: 'bg-blue-50/70',
      border: 'border-blue-200',
      text: 'text-blue-800',
      icon: <Info className="w-5 h-5 text-blue-600 shrink-0 mt-0.5" />,
    },
    success: {
      bg: 'bg-emerald-50/70',
      border: 'border-emerald-200',
      text: 'text-emerald-800',
      icon: <CheckCircle2 className="w-5 h-5 text-emerald-600 shrink-0 mt-0.5" />,
    },
    warning: {
      bg: 'bg-amber-50/70',
      border: 'border-amber-200',
      text: 'text-amber-800',
      icon: <AlertTriangle className="w-5 h-5 text-amber-600 shrink-0 mt-0.5" />,
    },
    error: {
      bg: 'bg-rose-50/70',
      border: 'border-rose-200',
      text: 'text-rose-800',
      icon: <AlertCircle className="w-5 h-5 text-rose-600 shrink-0 mt-0.5" />,
    },
  };

  const selected = styles[type];

  return (
    <div
      className={`p-4 rounded-xl border flex items-start justify-between gap-3 ${selected.bg} ${selected.border} ${className}`}
      role="alert"
    >
      <div className="flex items-start gap-3">
        {selected.icon}
        <div className={`space-y-0.5 text-xs ${selected.text}`}>
          {title && <h5 className="font-bold tracking-tight text-sm">{title}</h5>}
          <div className="leading-relaxed">{children}</div>
        </div>
      </div>
      {onClose && (
        <button
          onClick={onClose}
          className="p-1 -mr-1 text-gray-400 hover:text-gray-600 rounded-lg hover:bg-black/5 transition"
          aria-label="Close alert"
        >
          <X className="w-4 h-4" />
        </button>
      )}
    </div>
  );
}
