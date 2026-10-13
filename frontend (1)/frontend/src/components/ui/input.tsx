import * as React from 'react'
import type { LucideIcon } from 'lucide-react'
import { cn } from '@/lib/utils'

export interface InputProps extends React.InputHTMLAttributes<HTMLInputElement> {
  /** When true, applies error ring and sets aria-invalid */
  hasError?: boolean
  /** Optional icon shown inside the field, on the left */
  leftIcon?: LucideIcon
}

const Input = React.forwardRef<HTMLInputElement, InputProps>(
  ({ className, type, hasError, leftIcon: LeftIcon, ...props }, ref) => {
    const field = (
      <input
        type={type}
        aria-invalid={hasError ? true : undefined}
        className={cn(
          'border-brand-border text-brand-ink flex h-11 w-full rounded-lg border bg-white py-2.5 pr-4 text-[15px] transition outline-none',
          LeftIcon ? 'pl-11' : 'pl-4',
          'file:border-0 file:bg-transparent file:text-sm file:font-medium',
          'placeholder:text-brand-muted/70',
          'focus:border-brand-primary focus:ring-brand-primary/15 focus:ring-2',
          'disabled:cursor-not-allowed disabled:bg-slate-50 disabled:opacity-70',
          hasError && 'border-brand-accent focus:border-brand-accent focus:ring-brand-accent/15',
          className,
        )}
        ref={ref}
        {...props}
      />
    )

    if (!LeftIcon) return field

    return (
      <div className="relative">
        <span className="text-brand-muted pointer-events-none absolute top-1/2 left-3.5 -translate-y-1/2">
          <LeftIcon className="h-5 w-5" aria-hidden="true" />
        </span>
        {field}
      </div>
    )
  },
)
Input.displayName = 'Input'

export { Input }
