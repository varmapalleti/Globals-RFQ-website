import * as React from 'react'
import { createRootRoute, Outlet, useNavigate } from '@tanstack/react-router'
import { useSessionRestore } from '@/features/auth/hooks/useSessionRestore'
import { Loader2 } from 'lucide-react'

const TanStackRouterDevtools = import.meta.env.PROD
  ? () => null
  : React.lazy(() =>
      import('@tanstack/react-router-devtools').then((res) => ({
        default: res.TanStackRouterDevtools,
      })),
    )

function RootComponent() {
  const sessionStatus = useSessionRestore()
  const navigate = useNavigate()

  // Check for session_expired reason in URL (set by http.ts interceptor)
  React.useEffect(() => {
    const params = new URLSearchParams(window.location.search)
    if (params.get('reason') === 'session_expired') {
      // Already at login, toast will appear via navigate in http.ts
      // Clean the URL param
      const clean = window.location.pathname
      window.history.replaceState({}, '', clean)
    }
  }, [navigate])

  if (sessionStatus === 'loading') {
    return (
      <div className="flex min-h-dvh items-center justify-center">
        <Loader2 className="h-8 w-8 animate-spin text-[--color-primary]" aria-label="Loading…" />
      </div>
    )
  }

  return (
    <>
      <Outlet />
      {import.meta.env.DEV && (
        <React.Suspense>
          <TanStackRouterDevtools />
        </React.Suspense>
      )}
    </>
  )
}

export const Route = createRootRoute({
  component: RootComponent,
})
