import { useMutation } from '@tanstack/react-query'
import { useNavigate } from '@tanstack/react-router'
import { toast } from 'sonner'
import * as authApi from '@/features/auth/api/authApi'
import { useAuthStore } from '@/features/auth/store/authStore'
import { queryClient } from '@/lib/query-client'
import { normalizeError, unexpectedErrorMessage } from '@/lib/errors'
import { ROUTES } from '@/lib/constants'
import type { LoginFormValues } from '@/features/auth/schemas/authSchemas'

export function useLogin() {
  const setSession = useAuthStore((s) => s.setSession)
  const navigate = useNavigate()

  return useMutation({
    mutationFn: (values: LoginFormValues) =>
      authApi.login({ officialEmail: values.officialEmail, password: values.password }),

    onSuccess: (data) => {
      const { accessToken, expiresInSeconds, user } = data.data
      setSession(accessToken, expiresInSeconds, user)

      // User must change password before accessing anything else
      if (user.mustChangePassword) {
        toast.info('Please set a new password before continuing.')
        // TODO: navigate to /change-password when backend endpoint exists
        // For now: show informational dashboard with a notice
        void navigate({ to: ROUTES.DASHBOARD })
        return
      }

      void navigate({ to: ROUTES.DASHBOARD })
    },

    onError: (err) => {
      const apiErr = normalizeError(err)
      if (apiErr.errorCode === 'ACCOUNT_NOT_VERIFIED') {
        toast.error(apiErr.message, {
          description: 'Check your inbox for a verification link.',
          duration: 8000,
        })
        return
      }
      // All other login failures are deliberately generic (security)
      toast.error(apiErr.message)
    },
  })
}

export function useLogout() {
  const clearSession = useAuthStore((s) => s.clearSession)
  const navigate = useNavigate()

  return useMutation({
    mutationFn: authApi.logout,
    onSettled: () => {
      // Always clear memory and cache, even if the API call fails
      clearSession()
      queryClient.clear()
      void navigate({ to: ROUTES.LOGIN })
    },
  })
}

export function useForgotPassword() {
  return useMutation({
    mutationFn: authApi.forgotPassword,
    onError: (err) => {
      const apiErr = normalizeError(err)
      toast.error(unexpectedErrorMessage(apiErr))
    },
  })
}

export function useVerifyOtp() {
  return useMutation({
    mutationFn: authApi.verifyResetOtp,
    onError: (err) => {
      const apiErr = normalizeError(err)
      toast.error(apiErr.message)
    },
  })
}

export function useResetPassword() {
  return useMutation({
    mutationFn: authApi.resetPassword,
    onError: (err) => {
      const apiErr = normalizeError(err)
      toast.error(unexpectedErrorMessage(apiErr))
    },
  })
}

export function useVerifyEmail() {
  return useMutation({
    mutationFn: (token: string) => authApi.verifyEmail({ token }),
  })
}

export function useResendVerification() {
  return useMutation({
    mutationFn: authApi.resendVerification,
    onError: (err) => {
      const apiErr = normalizeError(err)
      toast.error(unexpectedErrorMessage(apiErr))
    },
  })
}
