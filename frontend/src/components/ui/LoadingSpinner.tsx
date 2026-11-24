import React from 'react';
import { Loader2 } from 'lucide-react';

export interface LoadingSpinnerProps {
  label?: string;
  message?: string;
  size?: 'sm' | 'md' | 'lg';
  className?: string;
}

export function LoadingSpinner({
  label,
  message,
  size = 'md',
  className = '',
}: LoadingSpinnerProps) {
  const displayText = label || message || 'Loading data...';

  const sizeStyles = {
    sm: 'w-4 h-4',
    md: 'w-8 h-8',
    lg: 'w-12 h-12',
  };

  return (
    <div className={`flex flex-col items-center justify-center p-12 space-y-3 ${className}`}>
      <Loader2 className={`${sizeStyles[size]} text-blue-600 animate-spin`} />
      {displayText && <p className="text-xs font-semibold text-gray-500 tracking-tight">{displayText}</p>}
    </div>
  );
}
