import { createFileRoute, Navigate, Outlet, useRouterState } from '@tanstack/react-router'
import { useAuthStore } from '@/features/auth/store/authStore'
import { ROUTES } from '@/lib/constants'

export const Route = createFileRoute('/_authenticated')({
  component: AuthenticatedGate,
})

/**
 * Route guards run before React effects. Keeping this check in beforeLoad sent
 * every hard refresh to /login before the HttpOnly refresh cookie was restored.
 * The root route waits for session restoration, so this component can safely
 * decide whether to render protected content afterwards.
 */
function AuthenticatedGate() {
  const accessToken = useAuthStore((session) => session.accessToken)
  const user = useAuthStore((session) => session.user)
  const pathname = useRouterState({ select: (state) => state.location.pathname })

  if (!accessToken) return <Navigate to={ROUTES.LOGIN} replace />
  if (user?.mustChangePassword && pathname !== ROUTES.CHANGE_PASSWORD) {
    return <Navigate to={ROUTES.CHANGE_PASSWORD} replace />
  }
  if (!user?.mustChangePassword && pathname === ROUTES.CHANGE_PASSWORD) {
    return <Navigate to={ROUTES.DASHBOARD} replace />
  }
  return <Outlet />
}
