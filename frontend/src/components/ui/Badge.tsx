import React from 'react';

export type BadgeVariant = 'success' | 'warning' | 'danger' | 'info' | 'purple' | 'neutral';

export interface BadgeProps extends React.HTMLAttributes<HTMLSpanElement> {
  variant?: BadgeVariant;
  size?: 'sm' | 'md';
  dot?: boolean;
}

export function Badge({
  children,
  variant = 'neutral',
  size = 'md',
  dot = false,
  className = '',
  ...props
}: BadgeProps) {
  const variantStyles: Record<BadgeVariant, { bg: string; dotColor: string }> = {
    success: {
      bg: 'bg-emerald-50 text-emerald-700 border-emerald-200/80',
      dotColor: 'bg-emerald-500',
    },
    warning: {
      bg: 'bg-amber-50 text-amber-700 border-amber-200/80',
      dotColor: 'bg-amber-500',
    },
    danger: {
      bg: 'bg-rose-50 text-rose-700 border-rose-200/80',
      dotColor: 'bg-rose-500',
    },
    info: {
      bg: 'bg-blue-50 text-blue-700 border-blue-200/80',
      dotColor: 'bg-blue-500',
    },
    purple: {
      bg: 'bg-purple-50 text-purple-700 border-purple-200/80',
      dotColor: 'bg-purple-500',
    },
    neutral: {
      bg: 'bg-gray-100 text-gray-700 border-gray-200',
      dotColor: 'bg-gray-400',
    },
  };

  const sizeStyles = {
    sm: 'text-[10px] px-2 py-0.5 tracking-wider font-bold',
    md: 'text-xs px-2.5 py-1 font-semibold',
  };

  const selected = variantStyles[variant] || variantStyles.neutral;

  return (
    <span
      className={`inline-flex items-center gap-1.5 rounded-full border uppercase ${selected.bg} ${sizeStyles[size]} ${className}`}
      {...props}
    >
      {dot && <span className={`w-1.5 h-1.5 rounded-full shrink-0 ${selected.dotColor}`} />}
      {children}
    </span>
  );
}
