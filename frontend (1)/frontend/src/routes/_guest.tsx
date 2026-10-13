import { createFileRoute, redirect, Outlet } from '@tanstack/react-router'
import { useAuthStore, selectIsAuthenticated } from '@/features/auth/store/authStore'
import { ROUTES } from '@/lib/constants'

export const Route = createFileRoute('/_guest')({
  beforeLoad: () => {
    // If already authenticated, redirect away from guest pages
    const isAuthenticated = selectIsAuthenticated(useAuthStore.getState())
    if (isAuthenticated) {
      throw redirect({ to: ROUTES.DASHBOARD })
    }
  },
  component: Outlet,
})
