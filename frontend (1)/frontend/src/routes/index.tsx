import { createFileRoute, redirect } from '@tanstack/react-router'
import { useAuthStore, selectIsAuthenticated } from '@/features/auth/store/authStore'
import { ROUTES } from '@/lib/constants'

export const Route = createFileRoute('/')({
  beforeLoad: () => {
    const isAuthenticated = selectIsAuthenticated(useAuthStore.getState())
    throw redirect({ to: isAuthenticated ? ROUTES.DASHBOARD : ROUTES.LOGIN })
  },
})
