import * as React from 'react'
import { cn } from '@/lib/utils'

export interface InputProps extends React.InputHTMLAttributes<HTMLInputElement> {
  /** When true, applies error ring and sets aria-invalid */
  hasError?: boolean
  /** Optional decorative icon shown inside the field on the left */
  icon?: React.ReactNode
}

const Input = React.forwardRef<HTMLInputElement, InputProps>(
  ({ className, type, hasError, icon, ...props }, ref) => {
    const field = (
      <input
        type={type}
        aria-invalid={hasError ? true : undefined}
        className={cn(
          'flex h-11 w-full rounded-lg border border-input bg-white px-3 py-2 text-sm text-foreground transition-colors',
          'file:border-0 file:bg-transparent file:text-sm file:font-medium',
          'placeholder:text-slate-400',
          'hover:border-brand-200',
          'focus-visible:border-brand-900 focus-visible:ring-2 focus-visible:ring-brand-900/20 focus-visible:outline-none',
          'disabled:cursor-not-allowed disabled:bg-muted disabled:opacity-60',
          icon && 'pl-10',
          hasError &&
            'border-destructive focus-visible:border-destructive focus-visible:ring-destructive/20',
          className,
        )}
        ref={ref}
        {...props}
      />
    )

    if (!icon) return field

    return (
      <div className="relative w-full">
        <span
          className="pointer-events-none absolute inset-y-0 left-3 flex items-center text-slate-500 [&_svg]:size-[18px]"
          aria-hidden="true"
        >
          {icon}
        </span>
        {field}
      </div>
    )
  },
)
Input.displayName = 'Input'

export { Input }
