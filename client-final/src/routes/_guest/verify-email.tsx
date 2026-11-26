import * as React from 'react'
import { createFileRoute, useNavigate } from '@tanstack/react-router'
import { z } from 'zod'
import { AuthLayout } from '@/components/layout/AuthLayout'
import { useVerifyEmail, useResendVerification } from '@/features/auth/hooks/useAuthMutations'
import { normalizeError } from '@/lib/errors'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Loader2, CheckCircle, XCircle, Mail } from 'lucide-react'
import { toast } from 'sonner'
import { ROUTES } from '@/lib/constants'

const searchSchema = z.object({
  token: z.string().optional(),
})

export const Route = createFileRoute('/_guest/verify-email')({
  validateSearch: searchSchema,
  component: VerifyEmailPage,
})

function VerifyEmailPage() {
  const { token } = Route.useSearch()
  const navigate = useNavigate()
  const verifyEmail = useVerifyEmail()

  const [status, setStatus] = React.useState<'idle' | 'loading' | 'success' | 'error'>('idle')
  const [errorMessage, setErrorMessage] = React.useState('')
  const called = React.useRef(false)

  React.useEffect(() => {
    // Remove token from URL immediately (before the API call) to prevent sharing
    if (token) {
      const url = new URL(window.location.href)
      url.searchParams.delete('token')
      window.history.replaceState({}, '', url.toString())
    }

    if (!token) {
      setStatus('error')
      setErrorMessage(
        'No verification token found in the link. Please use the link from your email.',
      )
      return
    }

    if (called.current) return
    called.current = true

    setStatus('loading')
    verifyEmail
      .mutateAsync(token)
      .then(() => {
        setStatus('success')
      })
      .catch((err) => {
        const apiErr = normalizeError(err)
        setStatus('error')
        setErrorMessage(apiErr.message)
      })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []) // Run once on mount — token is captured via closure

  return (
    <AuthLayout>
      <div className="space-y-6 text-center">
        {status === 'loading' && (
          <>
            <Loader2
              className="text-brand-primary mx-auto h-12 w-12 animate-spin"
              aria-hidden="true"
            />
            <div>
              <h1 className="text-brand-primary text-2xl font-bold">Verifying your email…</h1>
              <p className="text-brand-muted mt-1 text-sm">Please wait a moment.</p>
            </div>
          </>
        )}

        {status === 'success' && (
          <>
            <CheckCircle className="mx-auto h-12 w-12 text-emerald-500" aria-hidden="true" />
            <div>
              <h1 className="text-brand-primary text-2xl font-bold">Email verified!</h1>
              <p className="text-brand-muted mt-1 text-sm">
                Your email has been verified successfully. You can now sign in.
              </p>
            </div>
            <Button
              className="w-full"
              size="lg"
              onClick={() => void navigate({ to: ROUTES.LOGIN })}
            >
              Sign In
            </Button>
          </>
        )}

        {status === 'error' && (
          <>
            <XCircle className="text-brand-accent mx-auto h-12 w-12" aria-hidden="true" />
            <div>
              <h1 className="text-brand-primary text-2xl font-bold">Verification failed</h1>
              <p className="text-brand-muted mt-1 text-sm">{errorMessage}</p>
            </div>

            <div className="border-brand-border bg-brand-surface space-y-3 rounded-xl border p-4 text-left text-sm">
              <p className="text-brand-ink font-medium">Request a new verification link</p>
              <p className="text-brand-muted">
                Enter the email address you registered with to receive a new link.
              </p>
              <ResendForm
                onSuccess={() => toast.success('Check your inbox for a new verification link.')}
              />
            </div>

            <button
              type="button"
              onClick={() => void navigate({ to: ROUTES.LOGIN })}
              className="text-brand-muted hover:text-brand-primary focus-visible:outline-brand-primary rounded text-sm font-semibold hover:underline focus-visible:outline-2 focus-visible:outline-offset-2"
            >
              Back to Sign In
            </button>
          </>
        )}
      </div>
    </AuthLayout>
  )
}

function ResendForm({ onSuccess }: { onSuccess: () => void }) {
  const resend = useResendVerification()
  const [email, setEmail] = React.useState('')
  const [sent, setSent] = React.useState(false)

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    if (!email) return
    try {
      await resend.mutateAsync({ emailType: 'OFFICIAL', email })
      setSent(true)
      onSuccess()
    } catch (err) {
      const apiErr = normalizeError(err)
      toast.error(apiErr.message)
    }
  }

  if (sent) {
    return (
      <div className="flex items-center gap-2 text-sm text-emerald-700">
        <Mail className="h-4 w-4" aria-hidden="true" />
        If that email needs verification, a new link has been sent.
      </div>
    )
  }

  return (
    <form onSubmit={handleSubmit} className="flex gap-2">
      <Input
        type="email"
        autoComplete="email"
        value={email}
        onChange={(e) => setEmail(e.target.value)}
        placeholder="your@email.com"
        required
        aria-label="Email address for resend"
        className="h-9 flex-1"
      />
      <Button type="submit" size="sm" disabled={resend.isPending || !email}>
        {resend.isPending ? (
          <Loader2 className="h-3.5 w-3.5 animate-spin" aria-hidden="true" />
        ) : (
          'Send'
        )}
      </Button>
    </form>
  )
}
