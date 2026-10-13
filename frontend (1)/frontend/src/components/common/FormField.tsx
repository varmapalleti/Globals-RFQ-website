import * as React from 'react'
import { AlertCircle } from 'lucide-react'
import { cn } from '@/lib/utils'

interface FormFieldProps {
  id: string
  label: string
  error?: string
  description?: string
  required?: boolean
  children: React.ReactNode
  className?: string
}

interface InjectedChildProps {
  id?: string
  'aria-describedby'?: string
  'aria-invalid'?: boolean
  'aria-required'?: boolean
}

/**
 * Accessible form field wrapper.
 * Automatically injects id, aria-describedby, aria-invalid, and aria-required into its child form control.
 */
export function FormField({
  id,
  label,
  error,
  description,
  required,
  children,
  className,
}: FormFieldProps) {
  const errorId = `${id}-error`
  const descriptionId = description ? `${id}-description` : undefined
  const describedBy =
    [descriptionId, error ? errorId : undefined].filter(Boolean).join(' ') || undefined

  return (
    <div className={cn('space-y-1.5', className)}>
      <label
        htmlFor={id}
        className={cn(
          'block text-sm leading-none font-semibold',
          error ? 'text-brand-accent' : 'text-brand-ink',
        )}
      >
        {label}
        {required && (
          <span className="text-brand-accent ml-0.5" aria-hidden="true">
            *
          </span>
        )}
      </label>

      {description && (
        <p id={descriptionId} className="text-brand-muted text-xs">
          {description}
        </p>
      )}

      {React.isValidElement<InjectedChildProps>(children)
        ? React.cloneElement(children, {
            id: children.props.id ?? id,
            'aria-describedby': children.props['aria-describedby'] ?? describedBy,
            'aria-invalid': children.props['aria-invalid'] ?? (error ? true : undefined),
            'aria-required': children.props['aria-required'] ?? (required ? true : undefined),
          })
        : children}

      {error && (
        <p
          id={errorId}
          role="alert"
          className="text-brand-accent flex items-center gap-1.5 text-sm"
        >
          <AlertCircle className="h-3.5 w-3.5 shrink-0" aria-hidden="true" />
          {error}
        </p>
      )}
    </div>
  )
}
