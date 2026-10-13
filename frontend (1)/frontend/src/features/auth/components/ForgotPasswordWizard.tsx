import * as React from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { Loader2, ArrowLeft, RefreshCw, Mail, Briefcase } from 'lucide-react'
import {
  forgotPasswordSchema,
  resetPasswordSchema,
  type ForgotPasswordFormValues,
  type ResetPasswordFormValues,
} from '@/features/auth/schemas/authSchemas'
import {
  useForgotPassword,
  useVerifyOtp,
  useResetPassword,
} from '@/features/auth/hooks/useAuthMutations'
import { normalizeError } from '@/lib/errors'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { FormField } from '@/components/common/FormField'
import { OtpInput } from '@/features/auth/components/OtpInput'
import { PasswordField } from '@/features/auth/components/PasswordField'
import { PasswordChecklist } from '@/features/auth/components/PasswordChecklist'
import type { EmailType } from '@/types/api'
import { toast } from 'sonner'
import { cn } from '@/lib/utils'
import { AuthHeading } from '@/components/layout/AuthLayout'
import { useNavigate } from '@tanstack/react-router'
import { ROUTES } from '@/lib/constants'

type WizardStep = 'request' | 'otp' | 'reset' | 'success'

interface StepState {
  emailType: EmailType
  email: string
}

// OTP resend cooldown in seconds — matches UX spec
const RESEND_COOLDOWN_S = 60
// Max OTP attempts — matches backend AppSecurityProperties.otpMaxAttempts = 3
const MAX_OTP_ATTEMPTS = 3

// ── Step 1: Request OTP ──────────────────────────────────────────────────────
function RequestStep({ onSuccess }: { onSuccess: (state: StepState) => void }) {
  const forgotPassword = useForgotPassword()
  const {
    register,
    handleSubmit,
    watch,
    formState: { errors, isSubmitting },
  } = useForm<ForgotPasswordFormValues>({
    resolver: zodResolver(forgotPasswordSchema),
    defaultValues: { emailType: 'OFFICIAL', email: '' },
  })

  async function onSubmit(values: ForgotPasswordFormValues) {
    await forgotPassword.mutateAsync({ emailType: values.emailType, email: values.email })
    // Backend always returns 200 — never reveal if account exists
    onSuccess({ emailType: values.emailType, email: values.email })
  }

  const emailType = watch('emailType')
  const isPending = isSubmitting || forgotPassword.isPending

  return (
    <form onSubmit={handleSubmit(onSubmit)} noValidate className="space-y-5">
      <fieldset>
        <legend className="text-brand-ink mb-2 text-sm font-semibold">
          Which email should we send the code to?
        </legend>
        <div className="grid grid-cols-2 gap-3">
          {(['OFFICIAL', 'PERSONAL'] as const).map((type) => {
            const checked = emailType === type
            const Icon = type === 'OFFICIAL' ? Briefcase : Mail
            return (
              <label
                key={type}
                className={cn(
                  'focus-within:outline-brand-primary flex cursor-pointer items-center gap-2 rounded-xl border p-3 text-sm font-semibold whitespace-nowrap transition focus-within:outline-2 focus-within:outline-offset-2',
                  checked
                    ? 'border-brand-primary bg-brand-primary-soft text-brand-primary ring-brand-primary/30 ring-1'
                    : 'border-brand-border text-brand-ink hover:border-brand-primary/50 hover:bg-brand-primary-soft/40 bg-white',
                  isPending && 'cursor-not-allowed opacity-60',
                )}
              >
                <input
                  {...register('emailType')}
                  type="radio"
                  value={type}
                  className="sr-only"
                  disabled={isPending}
                />
                <span
                  aria-hidden="true"
                  className={cn(
                    'flex h-5 w-5 shrink-0 items-center justify-center rounded-full border-2 transition',
                    checked ? 'border-brand-primary' : 'border-brand-border',
                  )}
                >
                  {checked && <span className="bg-brand-primary h-2.5 w-2.5 rounded-full" />}
                </span>
                <Icon className="hidden h-4 w-4 shrink-0 min-[420px]:block" aria-hidden="true" />
                {type === 'OFFICIAL' ? 'Official email' : 'Personal email'}
              </label>
            )
          })}
        </div>
        {errors.emailType && (
          <p role="alert" className="text-brand-accent mt-1 text-xs">
            {errors.emailType.message}
          </p>
        )}
      </fieldset>

      <FormField
        id="fp-email"
        label={`${emailType === 'OFFICIAL' ? 'Official' : 'Personal'} email address`}
        error={errors.email?.message}
        required
      >
        <Input
          leftIcon={Mail}
          {...register('email')}
          id="fp-email"
          type="email"
          autoComplete="email"
          placeholder={emailType === 'OFFICIAL' ? 'you@company.com' : 'you@gmail.com'}
          hasError={!!errors.email}
          disabled={isPending}
        />
      </FormField>

      <Button type="submit" className="w-full" size="lg" disabled={isPending}>
        {isPending ? (
          <>
            <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" /> Sending code…
          </>
        ) : (
          'Send Verification Code'
        )}
      </Button>
    </form>
  )
}

// ── Step 2: Verify OTP ───────────────────────────────────────────────────────
function OtpStep({
  stepState,
  onSuccess,
  onBack,
}: {
  stepState: StepState
  onSuccess: (resetToken: string, expiresInSeconds: number) => void
  onBack: () => void
}) {
  const verifyOtp = useVerifyOtp()
  const forgotPassword = useForgotPassword()

  const [otp, setOtp] = React.useState('')
  const [otpError, setOtpError] = React.useState<string | null>(null)
  const [attempts, setAttempts] = React.useState(0)
  const [cooldown, setCooldown] = React.useState(RESEND_COOLDOWN_S)
  const [isResending, setIsResending] = React.useState(false)

  // Cooldown timer
  React.useEffect(() => {
    if (cooldown <= 0) return
    const id = setTimeout(() => setCooldown((c) => c - 1), 1000)
    return () => clearTimeout(id)
  }, [cooldown])

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    if (otp.length !== 6) {
      setOtpError('Enter the 6-digit code from your email')
      return
    }
    setOtpError(null)

    try {
      const res = await verifyOtp.mutateAsync({
        emailType: stepState.emailType,
        email: stepState.email,
        otp,
      })
      onSuccess(res.data.resetToken, res.data.expiresInSeconds)
    } catch (err) {
      const apiErr = normalizeError(err)
      const newAttempts = attempts + 1
      setAttempts(newAttempts)

      if (apiErr.errorCode === 'OTP_INVALID' || apiErr.errorCode === 'OTP_EXPIRED') {
        const remaining = MAX_OTP_ATTEMPTS - newAttempts
        setOtpError(
          remaining > 0
            ? `${apiErr.message} (${remaining} attempt${remaining === 1 ? '' : 's'} remaining)`
            : `${apiErr.message} — maximum attempts reached. Request a new code.`,
        )
      } else {
        setOtpError(apiErr.message)
      }
      setOtp('')
    }
  }

  async function handleResend() {
    setIsResending(true)
    setOtpError(null)
    setAttempts(0)
    try {
      await forgotPassword.mutateAsync({ emailType: stepState.emailType, email: stepState.email })
      setCooldown(RESEND_COOLDOWN_S)
      setOtp('')
      toast.success('A new code has been sent. Check your inbox.')
    } catch (err) {
      const apiErr = normalizeError(err)
      toast.error(apiErr.message)
    } finally {
      setIsResending(false)
    }
  }

  const isPending = verifyOtp.isPending

  return (
    <form onSubmit={handleSubmit} noValidate className="space-y-5">
      <p className="text-brand-muted text-sm">
        We sent a 6-digit code to{' '}
        <span className="text-brand-ink font-semibold">{stepState.email}</span>. The code expires in
        10 minutes.
      </p>

      <div className="space-y-1.5">
        <label htmlFor="otp-0" className="text-brand-ink block text-sm font-semibold">
          Verification code
        </label>
        <OtpInput
          id="otp-0"
          value={otp}
          onChange={setOtp}
          hasError={!!otpError}
          disabled={isPending}
        />
        {otpError && (
          <p role="alert" className="text-brand-accent text-xs">
            {otpError}
          </p>
        )}
        <p className="text-brand-muted text-xs">Didn't receive it? Check your spam folder first.</p>
      </div>

      <Button type="submit" className="w-full" size="lg" disabled={isPending || otp.length !== 6}>
        {isPending ? (
          <>
            <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" /> Verifying…
          </>
        ) : (
          'Verify Code'
        )}
      </Button>

      <div className="flex items-center justify-between">
        <button
          type="button"
          onClick={onBack}
          className="text-brand-muted hover:text-brand-primary focus-visible:outline-brand-primary flex items-center gap-1 rounded text-sm focus-visible:outline-2 focus-visible:outline-offset-2"
        >
          <ArrowLeft className="h-3.5 w-3.5" aria-hidden="true" />
          Change email
        </button>

        <button
          type="button"
          onClick={handleResend}
          disabled={cooldown > 0 || isResending}
          className="text-brand-primary focus-visible:outline-brand-primary flex items-center gap-1 rounded text-sm font-semibold hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 disabled:cursor-not-allowed disabled:opacity-50"
        >
          <RefreshCw
            className={`h-3.5 w-3.5 ${isResending ? 'animate-spin' : ''}`}
            aria-hidden="true"
          />
          {cooldown > 0 ? `Resend in ${cooldown}s` : 'Resend code'}
        </button>
      </div>
    </form>
  )
}

// ── Step 3: Reset Password ───────────────────────────────────────────────────
function ResetStep({ resetToken, onSuccess }: { resetToken: string; onSuccess: () => void }) {
  const resetPassword = useResetPassword()

  const {
    register,
    handleSubmit,
    setError,
    watch,
    formState: { errors, isSubmitting },
  } = useForm<ResetPasswordFormValues>({
    resolver: zodResolver(resetPasswordSchema),
  })

  const newPasswordValue = watch('newPassword', '')

  async function onSubmit(values: ResetPasswordFormValues) {
    try {
      await resetPassword.mutateAsync({
        resetToken,
        newPassword: values.newPassword,
        confirmPassword: values.confirmPassword,
      })
      onSuccess()
    } catch (err) {
      const apiErr = normalizeError(err)

      if (apiErr.errorCode === 'TOKEN_INVALID') {
        // Reset token expired — restart wizard at step 1
        toast.error('Your reset session has expired. Please start over.', { duration: 6000 })
        return
      }

      // Map field errors (multiple violations for newPassword possible)
      for (const [field, messages] of Object.entries(apiErr.fieldErrors)) {
        const mappedField = field === 'confirmPasswordMatching' ? 'confirmPassword' : field
        if (mappedField === 'newPassword' || mappedField === 'confirmPassword') {
          setError(mappedField as keyof ResetPasswordFormValues, {
            message: messages.join('. '),
          })
        }
      }

      if (Object.keys(apiErr.fieldErrors).length === 0) {
        toast.error(apiErr.message)
      }
    }
  }

  const isPending = isSubmitting || resetPassword.isPending

  return (
    <form onSubmit={handleSubmit(onSubmit)} noValidate className="space-y-5">
      <FormField
        id="new-password"
        label="New password"
        error={errors.newPassword?.message}
        required
      >
        <PasswordField
          {...register('newPassword')}
          id="new-password"
          autoComplete="new-password"
          placeholder="••••••••"
          hasError={!!errors.newPassword}
          disabled={isPending}
        />
      </FormField>

      <PasswordChecklist value={newPasswordValue} />

      <FormField
        id="confirm-password"
        label="Confirm new password"
        error={errors.confirmPassword?.message}
        required
      >
        <PasswordField
          {...register('confirmPassword')}
          id="confirm-password"
          autoComplete="new-password"
          placeholder="••••••••"
          hasError={!!errors.confirmPassword}
          disabled={isPending}
        />
      </FormField>

      <Button type="submit" className="w-full" size="lg" disabled={isPending}>
        {isPending ? (
          <>
            <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" /> Setting password…
          </>
        ) : (
          'Set New Password'
        )}
      </Button>
    </form>
  )
}

// ── Success Step ─────────────────────────────────────────────────────────────
function SuccessStep({ onGoToLogin }: { onGoToLogin: () => void }) {
  return (
    <div className="space-y-5 text-center">
      <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-full bg-emerald-50">
        <svg
          className="h-6 w-6 text-emerald-600"
          fill="none"
          viewBox="0 0 24 24"
          stroke="currentColor"
          aria-hidden="true"
        >
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
        </svg>
      </div>
      <div>
        <h3 className="text-brand-primary text-base font-bold">Password changed!</h3>
        <p className="text-brand-muted mt-1 text-sm">
          Your password has been updated successfully. You can now sign in with your new password.
        </p>
      </div>
      <Button onClick={onGoToLogin} className="w-full" size="lg">
        Sign In
      </Button>
    </div>
  )
}

// ── Main Wizard ──────────────────────────────────────────────────────────────
const STEP_TITLES: Record<WizardStep, string> = {
  request: 'Forgot your password?',
  otp: 'Enter verification code',
  reset: 'Set a new password',
  success: 'Password changed',
}

const STEP_DESCRIPTIONS: Record<WizardStep, string> = {
  request: "Enter your email and we'll send you a verification code.",
  otp: 'Check your inbox for a 6-digit code.',
  reset: 'Choose a strong password that meets the requirements below.',
  success: '',
}

export function ForgotPasswordWizard() {
  const navigate = useNavigate()
  const [step, setStep] = React.useState<WizardStep>('request')
  const [stepState, setStepState] = React.useState<StepState | null>(null)
  // resetToken kept in component memory — never in URL or storage
  const [resetToken, setResetToken] = React.useState<string | null>(null)

  return (
    <div>
      <AuthHeading title={STEP_TITLES[step]} subtitle={STEP_DESCRIPTIONS[step] || undefined} />

      {step === 'request' && (
        <RequestStep
          onSuccess={(state) => {
            setStepState(state)
            setStep('otp')
          }}
        />
      )}

      {step === 'otp' && stepState && (
        <OtpStep
          stepState={stepState}
          onSuccess={(token, _expires) => {
            setResetToken(token)
            setStep('reset')
          }}
          onBack={() => setStep('request')}
        />
      )}

      {step === 'reset' && resetToken && (
        <ResetStep resetToken={resetToken} onSuccess={() => setStep('success')} />
      )}

      {step === 'success' && (
        <SuccessStep onGoToLogin={() => void navigate({ to: ROUTES.LOGIN })} />
      )}

      {step !== 'success' && (
        <div className="mt-4 text-center">
          <button
            type="button"
            onClick={() => void navigate({ to: ROUTES.LOGIN })}
            className="text-brand-muted hover:text-brand-primary focus-visible:outline-brand-primary rounded text-sm hover:underline focus-visible:outline-2 focus-visible:outline-offset-2"
          >
            Back to Sign In
          </button>
        </div>
      )}
    </div>
  )
}
