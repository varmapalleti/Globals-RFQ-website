import * as React from 'react'
import { createRootRoute, Outlet, useNavigate } from '@tanstack/react-router'
import { useSessionRestore } from '@/features/auth/hooks/useSessionRestore'
import { Loader2, RefreshCw, ShieldAlert } from 'lucide-react'

const TanStackRouterDevtools = import.meta.env.PROD
  ? () => null
  : React.lazy(() =>
      import('@tanstack/react-router-devtools').then((res) => ({
        default: res.TanStackRouterDevtools,
      })),
    )

function RootComponent() {
  const { status: sessionStatus, errorMessage, retry } = useSessionRestore()
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

  if (sessionStatus === 'unavailable') {
    return (
      <main className="flex min-h-dvh items-center justify-center bg-slate-50 px-4 py-10">
        <section
          className="w-full max-w-lg rounded-xl border border-slate-200 bg-white p-8 shadow-sm"
          role="alert"
        >
          <ShieldAlert className="size-9 text-amber-600" aria-hidden="true" />
          <h1 className="mt-4 text-xl font-semibold text-slate-900">
            Could not restore your session
          </h1>
          <p className="mt-2 text-sm leading-6 text-slate-600">
            The identity service could not be reached or rejected the request temporarily. Your
            browser session has been kept; try again when the services are available.
          </p>
          <p className="mt-3 rounded-md bg-slate-50 p-3 text-xs text-slate-600">{errorMessage}</p>
          <button
            type="button"
            onClick={retry}
            className="mt-5 inline-flex h-10 items-center gap-2 rounded-md bg-blue-700 px-4 text-sm font-semibold text-white hover:bg-blue-800 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-700"
          >
            <RefreshCw className="size-4" aria-hidden="true" /> Retry
          </button>
        </section>
      </main>
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
