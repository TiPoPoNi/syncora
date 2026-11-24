import React from 'react';
import { AlertTriangle, Trash2, Info } from 'lucide-react';
import { Modal } from './Modal';
import { Button } from './Button';

export interface ConfirmDialogProps {
  isOpen: boolean;
  onClose?: () => void;
  onCancel?: () => void;
  onConfirm: () => void;
  title: string;
  description?: string;
  message?: string;
  confirmLabel?: string;
  cancelLabel?: string;
  variant?: 'danger' | 'warning' | 'primary';
  isLoading?: boolean;
}

export function ConfirmDialog({
  isOpen,
  onClose,
  onCancel,
  onConfirm,
  title,
  description,
  message,
  confirmLabel = 'Confirm',
  cancelLabel = 'Cancel',
  variant = 'danger',
  isLoading = false,
}: ConfirmDialogProps) {
  const handleClose = () => {
    if (onClose) onClose();
    else if (onCancel) onCancel();
  };

  const displayText = description || message || '';

  const icons = {
    danger: <Trash2 className="w-6 h-6 text-rose-600" />,
    warning: <AlertTriangle className="w-6 h-6 text-amber-600" />,
    primary: <Info className="w-6 h-6 text-blue-600" />,
  };

  const iconBg = {
    danger: 'bg-rose-50 border-rose-100',
    warning: 'bg-amber-50 border-amber-100',
    primary: 'bg-blue-50 border-blue-100',
  };

  const buttonVariants: Record<'danger' | 'warning' | 'primary', 'danger' | 'primary'> = {
    danger: 'danger',
    warning: 'danger',
    primary: 'primary',
  };

  return (
    <Modal isOpen={isOpen} onClose={handleClose} title="" maxWidth="md" showCloseButton={false}>
      <div className="space-y-4 text-center">
        <div
          className={`w-12 h-12 mx-auto rounded-2xl border flex items-center justify-center ${iconBg[variant]}`}
        >
          {icons[variant]}
        </div>

        <div className="space-y-1.5">
          <h3 className="text-base font-bold text-gray-900">{title}</h3>
          {displayText && (
            <p className="text-xs text-gray-500 leading-relaxed max-w-sm mx-auto">{displayText}</p>
          )}
        </div>

        <div className="flex items-center justify-center gap-3 pt-3">
          <Button variant="outline" size="md" onClick={handleClose} disabled={isLoading}>
            {cancelLabel}
          </Button>
          <Button
            variant={buttonVariants[variant]}
            size="md"
            onClick={onConfirm}
            isLoading={isLoading}
          >
            {confirmLabel}
          </Button>
        </div>
      </div>
    </Modal>
  );
}
