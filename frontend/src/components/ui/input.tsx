import * as React from 'react'
import { cn } from '@/lib/utils'

export interface InputProps extends React.InputHTMLAttributes<HTMLInputElement> {
  /** When true, applies error ring and sets aria-invalid */
  hasError?: boolean
}

const Input = React.forwardRef<HTMLInputElement, InputProps>(
  ({ className, type, hasError, ...props }, ref) => {
    return (
      <input
        type={type}
        aria-invalid={hasError ? true : undefined}
        className={cn(
          'flex h-10 w-full rounded-md border border-[--color-input] bg-[--color-background] px-3 py-2 text-sm shadow-sm transition-colors',
          'file:border-0 file:bg-transparent file:text-sm file:font-medium',
          'placeholder:text-[--color-muted-foreground]',
          'focus-visible:ring-2 focus-visible:ring-[--color-ring] focus-visible:outline-none',
          'disabled:cursor-not-allowed disabled:opacity-50',
          hasError && 'border-[--color-destructive] focus-visible:ring-[--color-destructive]',
          className,
        )}
        ref={ref}
        {...props}
      />
    )
  },
)
Input.displayName = 'Input'

export { Input }
