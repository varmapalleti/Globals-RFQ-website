import * as React from 'react'
import { cn } from '@/lib/utils'

interface BadgeProps extends React.HTMLAttributes<HTMLSpanElement> {
  variant?: 'default' | 'secondary' | 'destructive' | 'outline' | 'success'
}

function Badge({ className, variant = 'default', ...props }: BadgeProps) {
  return (
    <span
      className={cn(
        'focus:ring-brand-primary inline-flex items-center rounded-full border px-2.5 py-0.5 text-xs font-semibold transition-colors focus:ring-2 focus:ring-offset-2 focus:outline-none',
        {
          default: 'bg-brand-primary border-transparent text-white',
          secondary: 'bg-brand-primary-soft text-brand-primary border-transparent',
          destructive: 'bg-brand-accent border-transparent text-white',
          outline: 'border-brand-border text-brand-ink',
          success: 'border-transparent bg-emerald-50 text-emerald-700',
        }[variant],
        className,
      )}
      {...props}
    />
  )
}

export { Badge }
