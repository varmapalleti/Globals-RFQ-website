import { createFileRoute } from '@tanstack/react-router'
import { AuthLayout } from '@/components/layout/AuthLayout'
import { LoginForm } from '@/features/auth/components/LoginForm'
import { toast } from 'sonner'
import * as React from 'react'

export const Route = createFileRoute('/_guest/login')({
  component: LoginPage,
})

function LoginPage() {
  // Show session-expired toast if redirected here with that reason
  React.useEffect(() => {
    const params = new URLSearchParams(window.location.search)
    if (params.get('reason') === 'session_expired') {
      toast.warning('Your session expired. Please sign in again.')
    }
  }, [])

  return (
    <AuthLayout>
      <div className="mb-6 space-y-2">
        <h1 className="text-2xl font-bold tracking-tight text-[--color-foreground]">
          Welcome back
        </h1>
        <p className="text-sm text-[--color-muted-foreground]">
          Sign in to your Global RFQ account
        </p>
      </div>
      <LoginForm />
    </AuthLayout>
  )
}
