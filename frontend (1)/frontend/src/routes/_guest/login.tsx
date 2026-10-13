import { createFileRoute } from '@tanstack/react-router'
import { AuthLayout, AuthHeading } from '@/components/layout/AuthLayout'
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
      <AuthHeading
        title="Welcome back"
        subtitle="Sign in to your Global Services Solutions RFQ account"
      />
      <LoginForm />
    </AuthLayout>
  )
}
