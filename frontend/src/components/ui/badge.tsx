import * as React from 'react'
import { cn } from '@/lib/utils'

interface BadgeProps extends React.HTMLAttributes<HTMLSpanElement> {
  variant?: 'default' | 'secondary' | 'destructive' | 'outline' | 'success'
}

function Badge({ className, variant = 'default', ...props }: BadgeProps) {
  return (
    <span
      className={cn(
        'inline-flex items-center rounded-full border px-2.5 py-0.5 text-xs font-semibold transition-colors focus:ring-2 focus:ring-ring focus:ring-offset-2 focus:outline-none',
        {
          default: 'border-transparent bg-primary text-primary-foreground',
          secondary: 'border-brand-100 bg-brand-50 text-brand-900',
          destructive: 'border-red-brand-100 bg-red-brand-50 text-red-brand-700',
          outline: 'border-brand-200 bg-white text-brand-900',
          success: 'border-green-200 bg-green-50 text-green-800',
        }[variant],
        className,
      )}
      {...props}
    />
  )
}

export { Badge }
