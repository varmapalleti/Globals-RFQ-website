import { Check, X } from 'lucide-react'
import { cn } from '@/lib/utils'

const RULES = [
  { key: 'length', label: 'At least 8 characters', test: (v: string) => v.length >= 8 },
  { key: 'maxLength', label: 'At most 64 characters', test: (v: string) => v.length <= 64 },
  { key: 'upper', label: 'One uppercase letter', test: (v: string) => /[A-Z]/.test(v) },
  { key: 'lower', label: 'One lowercase letter', test: (v: string) => /[a-z]/.test(v) },
  { key: 'digit', label: 'One number', test: (v: string) => /[0-9]/.test(v) },
  { key: 'special', label: 'One special character', test: (v: string) => /[^A-Za-z0-9\s]/.test(v) },
  { key: 'noSpace', label: 'No spaces', test: (v: string) => !/\s/.test(v) },
] as const

interface PasswordChecklistProps {
  value: string
  className?: string
}

/** Live password rule checklist — mirrors backend PasswordPolicy.java exactly. */
export function PasswordChecklist({ value, className }: PasswordChecklistProps) {
  if (!value) return null

  return (
    <ul
      aria-label="Password requirements"
      className={cn(
        'mt-2 space-y-1 rounded-md border border-[--color-border] bg-[--color-muted] p-3',
        className,
      )}
    >
      {RULES.map((rule) => {
        const passed = rule.test(value)
        return (
          <li
            key={rule.key}
            className={cn(
              'flex items-center gap-2 text-xs',
              passed ? 'text-green-700' : 'text-[--color-muted-foreground]',
            )}
          >
            {passed ? (
              <Check className="h-3.5 w-3.5 shrink-0 text-green-600" aria-hidden="true" />
            ) : (
              <X className="h-3.5 w-3.5 shrink-0 text-slate-400" aria-hidden="true" />
            )}
            <span>
              {passed ? <span className="sr-only">✓ </span> : <span className="sr-only">✗ </span>}
              {rule.label}
            </span>
          </li>
        )
      })}
    </ul>
  )
}
