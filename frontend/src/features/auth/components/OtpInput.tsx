import * as React from 'react'
import { cn } from '@/lib/utils'

interface OtpInputProps {
  value: string
  onChange: (value: string) => void
  length?: number
  hasError?: boolean
  disabled?: boolean
  id?: string
  'aria-describedby'?: string
}

/**
 * 6-digit OTP input with:
 * - Individual digit boxes for UX
 * - Paste support (pastes across all boxes)
 * - Keyboard: backspace moves to previous, arrow keys supported
 * - Accessible: single composite input for screen readers
 */
export function OtpInput({
  value,
  onChange,
  length = 6,
  hasError,
  disabled,
  id,
  'aria-describedby': ariaDescribedby,
}: OtpInputProps) {
  const inputRefs = React.useRef<Array<HTMLInputElement | null>>([])
  const digits = value.padEnd(length, '').slice(0, length).split('')

  function handleChange(index: number, char: string) {
    const digit = char.replace(/\D/g, '').slice(-1)
    const next = digits.map((d, i) => (i === index ? (digit ?? '') : d)).join('')
    onChange(next.trim())
    if (digit && index < length - 1) {
      inputRefs.current[index + 1]?.focus()
    }
  }

  function handleKeyDown(index: number, e: React.KeyboardEvent<HTMLInputElement>) {
    if (e.key === 'Backspace') {
      if (digits[index]) {
        const next = digits.map((d, i) => (i === index ? '' : d)).join('')
        onChange(next.trim())
      } else if (index > 0) {
        inputRefs.current[index - 1]?.focus()
      }
    } else if (e.key === 'ArrowLeft' && index > 0) {
      inputRefs.current[index - 1]?.focus()
    } else if (e.key === 'ArrowRight' && index < length - 1) {
      inputRefs.current[index + 1]?.focus()
    }
  }

  function handlePaste(e: React.ClipboardEvent) {
    e.preventDefault()
    const pasted = e.clipboardData.getData('text').replace(/\D/g, '').slice(0, length)
    onChange(pasted)
    const focusIndex = Math.min(pasted.length, length - 1)
    inputRefs.current[focusIndex]?.focus()
  }

  return (
    <div role="group" aria-label="One-time code" className="flex gap-2">
      {Array.from({ length }, (_, i) => (
        <input
          key={i}
          ref={(el) => {
            inputRefs.current[i] = el
          }}
          id={i === 0 ? id : undefined}
          aria-label={`Digit ${i + 1} of ${length}`}
          aria-describedby={i === 0 ? ariaDescribedby : undefined}
          aria-invalid={hasError ? true : undefined}
          type="text"
          inputMode="numeric"
          autoComplete={i === 0 ? 'one-time-code' : 'off'}
          maxLength={1}
          value={digits[i] ?? ''}
          disabled={disabled}
          onChange={(e) => handleChange(i, e.target.value)}
          onKeyDown={(e) => handleKeyDown(i, e)}
          onPaste={handlePaste}
          onFocus={(e) => e.target.select()}
          className={cn(
            'h-12 w-10 rounded-md border text-center text-lg font-semibold shadow-sm transition-colors',
            'focus-visible:ring-2 focus-visible:ring-[--color-ring] focus-visible:outline-none',
            'disabled:cursor-not-allowed disabled:opacity-50',
            hasError
              ? 'border-[--color-destructive] focus-visible:ring-[--color-destructive]'
              : 'border-[--color-input]',
            digits[i] ? 'border-[--color-primary] bg-blue-50' : 'bg-[--color-background]',
          )}
        />
      ))}
    </div>
  )
}
