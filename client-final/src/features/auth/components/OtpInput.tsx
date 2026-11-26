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
  // `value` is a plain string of the digits typed so far ("123"). Box i shows value[i] (or empty).
  const digits = Array.from({ length }, (_, i) => value[i] ?? '')

  function focusBox(index: number) {
    inputRefs.current[Math.max(0, Math.min(index, length - 1))]?.focus()
  }

  function handleChange(index: number, raw: string) {
    const digit = raw.replace(/\D/g, '').slice(-1)
    if (!digit) return // ignore letters / symbols

    const chars = value.split('')
    if (index < chars.length) {
      chars[index] = digit // overwrite an existing digit
    } else if (chars.length < length) {
      chars.push(digit) // append to the next free slot
    }
    onChange(chars.join('').slice(0, length))
    focusBox(Math.min(index, chars.length - 1) + 1)
  }

  function handleKeyDown(index: number, e: React.KeyboardEvent<HTMLInputElement>) {
    if (e.key === 'Backspace') {
      e.preventDefault()
      if (index < value.length) {
        // Box has a digit: remove it
        onChange(value.slice(0, index) + value.slice(index + 1))
      } else if (value.length > 0) {
        // Empty box: remove the previous digit and step back
        onChange(value.slice(0, -1))
        focusBox(value.length - 1)
      }
    } else if (e.key === 'ArrowLeft') {
      e.preventDefault()
      focusBox(index - 1)
    } else if (e.key === 'ArrowRight') {
      e.preventDefault()
      focusBox(index + 1)
    }
  }

  function handlePaste(e: React.ClipboardEvent) {
    e.preventDefault()
    const pasted = e.clipboardData.getData('text').replace(/\D/g, '').slice(0, length)
    onChange(pasted)
    focusBox(pasted.length)
  }

  return (
    <div role="group" aria-label="One-time code" className="flex justify-center gap-2 sm:gap-2.5">
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
            'text-brand-ink h-12 w-10 rounded-lg border text-center text-xl font-semibold transition outline-none sm:w-11',
            'disabled:cursor-not-allowed disabled:bg-slate-50 disabled:opacity-60',
            hasError
              ? 'border-brand-accent focus:ring-brand-accent/15 bg-white focus:ring-2'
              : cn(
                  'focus:border-brand-primary focus:ring-brand-primary/15 focus:ring-2',
                  digits[i]
                    ? 'border-brand-primary bg-brand-primary-soft'
                    : 'border-brand-border bg-white',
                ),
          )}
        />
      ))}
    </div>
  )
}
