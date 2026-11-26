import * as React from 'react'
import { Eye, EyeOff, Lock } from 'lucide-react'
import { Input, type InputProps } from '@/components/ui/input'
import { cn } from '@/lib/utils'

interface PasswordFieldProps extends Omit<InputProps, 'type'> {
  showToggle?: boolean
}

/**
 * Password input with optional show/hide toggle.
 * Accessible: toggle button is labelled, input type switches between password/text.
 */
export const PasswordField = React.forwardRef<HTMLInputElement, PasswordFieldProps>(
  ({ showToggle = true, className, ...rest }, ref) => {
    const [visible, setVisible] = React.useState(false)

    return (
      <div className={cn('relative', className)}>
        <Input
          leftIcon={Lock}
          {...rest}
          ref={ref}
          type={visible ? 'text' : 'password'}
          className="pr-11"
        />
        {showToggle && (
          <button
            type="button"
            aria-label={visible ? 'Hide password' : 'Show password'}
            aria-pressed={visible}
            onClick={() => setVisible((v) => !v)}
            className="text-brand-muted hover:text-brand-primary focus-visible:outline-brand-primary absolute top-1/2 right-3 -translate-y-1/2 rounded p-1 transition focus-visible:outline-2 focus-visible:outline-offset-2"
            tabIndex={0}
          >
            {visible ? (
              <EyeOff className="h-5 w-5" aria-hidden="true" />
            ) : (
              <Eye className="h-5 w-5" aria-hidden="true" />
            )}
          </button>
        )}
      </div>
    )
  },
)
PasswordField.displayName = 'PasswordField'
