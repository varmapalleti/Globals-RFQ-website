import * as React from 'react'
import { cn } from '@/lib/utils'

interface BadgeProps extends React.HTMLAttributes<HTMLSpanElement> {
  variant?: 'default' | 'secondary' | 'destructive' | 'outline' | 'success'
}

function Badge({ className, variant = 'default', ...props }: BadgeProps) {
  return (
    <span
      className={cn(
        'inline-flex items-center rounded-full border px-2.5 py-0.5 text-xs font-semibold transition-colors focus:ring-2 focus:ring-[--color-ring] focus:ring-offset-2 focus:outline-none',
        {
          default: 'border-transparent bg-[--color-primary] text-[--color-primary-foreground]',
          secondary:
            'border-transparent bg-[--color-secondary] text-[--color-secondary-foreground]',
          destructive:
            'border-transparent bg-[--color-destructive] text-[--color-destructive-foreground]',
          outline: 'text-[--color-foreground]',
          success: 'border-transparent bg-green-100 text-green-800',
        }[variant],
        className,
      )}
      {...props}
    />
  )
}

export { Badge }
