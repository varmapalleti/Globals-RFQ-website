import { createFileRoute, useNavigate } from '@tanstack/react-router'
import { zodResolver } from '@hookform/resolvers/zod'
import { Loader2, MailCheck } from 'lucide-react'
import * as React from 'react'
import { useForm } from 'react-hook-form'
import { z } from 'zod'
import { toast } from 'sonner'
import { AuthLayout } from '@/components/layout/AuthLayout'
import { Button } from '@/components/ui/button'
import { FormField } from '@/components/common/FormField'
import { PasswordField } from '@/features/auth/components/PasswordField'
import { PasswordChecklist } from '@/features/auth/components/PasswordChecklist'
import { acceptInvite } from '@/features/auth/api/authApi'
import { passwordSchema } from '@/features/auth/schemas/authSchemas'
import { normalizeError, unexpectedErrorMessage } from '@/lib/errors'
import { ROUTES } from '@/lib/constants'

const searchSchema = z.object({ token: z.string().optional() })
const inviteSchema = z
  .object({
    newPassword: passwordSchema,
    confirmPassword: z.string().min(1, 'Please confirm your password.'),
  })
  .refine((values) => values.newPassword === values.confirmPassword, {
    message: 'Passwords do not match.',
    path: ['confirmPassword'],
  })
type InviteValues = z.infer<typeof inviteSchema>

export const Route = createFileRoute('/_guest/accept-invite')({
  validateSearch: searchSchema,
  component: AcceptInvitePage,
})

function AcceptInvitePage() {
  const { token: routeToken } = Route.useSearch()
  // Capture the original URL value once before removing it from the address bar.
  // This fallback also handles router/search parsing differences without losing
  // the token on form rerenders after the URL has been cleaned.
  const [token] = React.useState<string | undefined>(() => {
    const urlToken = new URLSearchParams(window.location.search).get('token')
    return routeToken ?? urlToken ?? undefined
  })
  const navigate = useNavigate()
  const [busy, setBusy] = React.useState(false)
  const {
    register,
    handleSubmit,
    watch,
    formState: { errors },
  } = useForm<InviteValues>({
    resolver: zodResolver(inviteSchema),
    defaultValues: { newPassword: '', confirmPassword: '' },
  })
  const password = watch('newPassword')

  React.useEffect(() => {
    if (!token) return
    const url = new URL(window.location.href)
    url.searchParams.delete('token')
    window.history.replaceState({}, '', url.toString())
  }, [token])

  async function submit(values: InviteValues) {
    if (!token || token.length > 200) {
      toast.error('This invitation link is missing or invalid. Ask an administrator to resend it.')
      return
    }
    setBusy(true)
    try {
      await acceptInvite({ token, ...values })
      toast.success('Invitation accepted. Sign in with your new password.')
      await navigate({ to: ROUTES.LOGIN, replace: true })
    } catch (error) {
      toast.error(unexpectedErrorMessage(normalizeError(error)))
    } finally {
      setBusy(false)
    }
  }

  return (
    <AuthLayout>
      <div className="mb-6 text-center">
        <span className="mx-auto flex size-12 items-center justify-center rounded-full bg-brand-50">
          <MailCheck className="size-6 text-brand-900" />
        </span>
        <h1 className="mt-3 text-2xl font-bold tracking-tight text-brand-900">
          Accept your invitation
        </h1>
        <p className="mt-2 text-sm text-muted-foreground">
          Create a password to activate your Global RFQ account.
        </p>
      </div>
      {!token ? (
        <div className="space-y-4 rounded-lg border border-amber-200 bg-amber-50 p-4 text-sm text-amber-950">
          <p>
            This page did not receive an invitation token. Open the original link from the invitation
            email; if this invitation expired or was accepted already, ask an administrator to
            resend it.
          </p>
          <Button
            className="w-full"
            variant="outline"
            onClick={() => void navigate({ to: ROUTES.LOGIN })}
          >
            Return to sign in
          </Button>
        </div>
      ) : (
        <form onSubmit={handleSubmit(submit)} noValidate className="space-y-5">
          <FormField
            id="invite-password"
            label="New password"
            error={errors.newPassword?.message}
            required
          >
            <PasswordField
              {...register('newPassword')}
              autoComplete="new-password"
              disabled={busy}
            />
          </FormField>
          <PasswordChecklist value={password} />
          <FormField
            id="invite-confirm"
            label="Confirm password"
            error={errors.confirmPassword?.message}
            required
          >
            <PasswordField
              {...register('confirmPassword')}
              autoComplete="new-password"
              disabled={busy}
            />
          </FormField>
          <Button type="submit" className="w-full" size="lg" disabled={busy}>
            {busy ? (
              <>
                <Loader2 className="size-4 animate-spin" />
                Activating account…
              </>
            ) : (
              'Accept invitation'
            )}
          </Button>
        </form>
      )}
    </AuthLayout>
  )
}
