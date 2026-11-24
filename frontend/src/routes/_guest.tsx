import { createFileRoute, Navigate, Outlet } from '@tanstack/react-router'
import { useAuthStore } from '@/features/auth/store/authStore'
import { ROUTES } from '@/lib/constants'

export const Route = createFileRoute('/_guest')({
  component: GuestOnlyGate,
})

function GuestOnlyGate() {
  const accessToken = useAuthStore((session) => session.accessToken)
  return accessToken ? <Navigate to={ROUTES.DASHBOARD} replace /> : <Outlet />
}
