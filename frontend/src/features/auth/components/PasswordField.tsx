import * as React from 'react'
import { Eye, EyeOff } from 'lucide-react'
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
        <Input {...rest} ref={ref} type={visible ? 'text' : 'password'} className="pr-10" />
        {showToggle && (
          <button
            type="button"
            aria-label={visible ? 'Hide password' : 'Show password'}
            aria-pressed={visible}
            onClick={() => setVisible((v) => !v)}
            className="absolute top-1/2 right-3 -translate-y-1/2 rounded text-[--color-muted-foreground] hover:text-[--color-foreground] focus-visible:ring-2 focus-visible:ring-[--color-ring] focus-visible:outline-none"
            tabIndex={0}
          >
            {visible ? (
              <EyeOff className="h-4 w-4" aria-hidden="true" />
            ) : (
              <Eye className="h-4 w-4" aria-hidden="true" />
            )}
          </button>
        )}
      </div>
    )
  },
)
PasswordField.displayName = 'PasswordField'
