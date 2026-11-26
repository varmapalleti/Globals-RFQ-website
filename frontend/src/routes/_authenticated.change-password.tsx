import { createFileRoute, useNavigate } from '@tanstack/react-router'
import { zodResolver } from '@hookform/resolvers/zod'
import { KeyRound, Loader2 } from 'lucide-react'
import * as React from 'react'
import { useForm } from 'react-hook-form'
import { z } from 'zod'
import { toast } from 'sonner'
import { AppShell } from '@/components/layout/AppShell'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { FormField } from '@/components/common/FormField'
import { PasswordField } from '@/features/auth/components/PasswordField'
import { PasswordChecklist } from '@/features/auth/components/PasswordChecklist'
import { changePassword } from '@/features/auth/api/authApi'
import { passwordSchema } from '@/features/auth/schemas/authSchemas'
import { useAuthStore } from '@/features/auth/store/authStore'
import { queryClient } from '@/lib/query-client'
import { normalizeError, unexpectedErrorMessage } from '@/lib/errors'
import { ROUTES } from '@/lib/constants'

const passwordChangeSchema = z
  .object({
    currentPassword: z.string().min(1, 'Enter your current password.').max(128),
    newPassword: passwordSchema,
    confirmPassword: z.string().min(1, 'Please confirm your password.'),
  })
  .refine((values) => values.newPassword !== values.currentPassword, {
    message: 'Choose a password different from your current password.',
    path: ['newPassword'],
  })
  .refine((values) => values.newPassword === values.confirmPassword, {
    message: 'Passwords do not match.',
    path: ['confirmPassword'],
  })
type PasswordChangeValues = z.infer<typeof passwordChangeSchema>

export const Route = createFileRoute('/_authenticated/change-password')({
  component: ChangePasswordPage,
})

function ChangePasswordPage() {
  const navigate = useNavigate()
  const clearSession = useAuthStore((state) => state.clearSession)
  const [busy, setBusy] = React.useState(false)
  const {
    register,
    handleSubmit,
    watch,
    formState: { errors },
  } = useForm<PasswordChangeValues>({
    resolver: zodResolver(passwordChangeSchema),
    defaultValues: { currentPassword: '', newPassword: '', confirmPassword: '' },
  })
  const password = watch('newPassword')

  async function submit(values: PasswordChangeValues) {
    setBusy(true)
    try {
      await changePassword(values)
      clearSession()
      queryClient.clear()
      toast.success('Password changed. Sign in again with your new password.')
      await navigate({ to: ROUTES.LOGIN, replace: true })
    } catch (error) {
      toast.error(unexpectedErrorMessage(normalizeError(error)))
    } finally {
      setBusy(false)
    }
  }

  return (
    <AppShell>
      <div className="mx-auto max-w-xl">
        <Card className="border-slate-200 p-6 shadow-none sm:p-8">
          <div className="mb-6 text-center">
            <KeyRound className="mx-auto size-9 text-blue-700" />
            <h1 className="mt-3 text-2xl font-bold tracking-tight">Set a new password</h1>
            <p className="mt-2 text-sm text-slate-500">
              Your account must use a new password before you can continue.
            </p>
          </div>
          <form onSubmit={handleSubmit(submit)} noValidate className="space-y-5">
            <FormField
              id="current-password"
              label="Current password"
              error={errors.currentPassword?.message}
              required
            >
              <PasswordField
                {...register('currentPassword')}
                autoComplete="current-password"
                disabled={busy}
              />
            </FormField>
            <FormField
              id="new-password"
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
              id="confirm-new-password"
              label="Confirm new password"
              error={errors.confirmPassword?.message}
              required
            >
              <PasswordField
                {...register('confirmPassword')}
                autoComplete="new-password"
                disabled={busy}
              />
            </FormField>
            <Button type="submit" size="lg" className="w-full" disabled={busy}>
              {busy ? (
                <>
                  <Loader2 className="size-4 animate-spin" />
                  Updating…
                </>
              ) : (
                'Change password'
              )}
            </Button>
          </form>
        </Card>
      </div>
    </AppShell>
  )
}
