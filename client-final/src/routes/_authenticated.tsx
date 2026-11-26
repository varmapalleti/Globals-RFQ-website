import { createFileRoute, redirect, Outlet } from '@tanstack/react-router'
import { useAuthStore, selectIsAuthenticated } from '@/features/auth/store/authStore'
import { ROUTES } from '@/lib/constants'

export const Route = createFileRoute('/_authenticated')({
  beforeLoad: () => {
    // If not authenticated, redirect to login
    const isAuthenticated = selectIsAuthenticated(useAuthStore.getState())
    if (!isAuthenticated) {
      throw redirect({ to: ROUTES.LOGIN })
    }
  },
  component: Outlet,
})
