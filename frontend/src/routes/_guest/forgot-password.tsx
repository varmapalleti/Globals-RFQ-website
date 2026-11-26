import { createFileRoute } from '@tanstack/react-router'
import { AuthLayout } from '@/components/layout/AuthLayout'
import { ForgotPasswordWizard } from '@/features/auth/components/ForgotPasswordWizard'

export const Route = createFileRoute('/_guest/forgot-password')({
  component: ForgotPasswordPage,
})

function ForgotPasswordPage() {
  return (
    <AuthLayout>
      <ForgotPasswordWizard />
    </AuthLayout>
  )
}
