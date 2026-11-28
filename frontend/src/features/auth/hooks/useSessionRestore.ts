import * as React from 'react'
import * as authApi from '@/features/auth/api/authApi'
import { useAuthStore } from '@/features/auth/store/authStore'
import { normalizeError } from '@/lib/errors'
import { isDemoMode } from '@/lib/env'
import { restoreDemoSession } from '@/features/auth/demoSession'

type Status = 'loading' | 'ready' | 'unauthenticated' | 'unavailable'

/**
 * On app mount, attempt ONE refresh to restore the session from the HttpOnly cookie.
 * Handles React StrictMode double-mount safely via a ref guard.
 *
 * Returns:
 *   - 'loading'        — refresh in flight, show spinner
 *   - 'ready'          — session restored (or confirmed absent)
 *   - 'unauthenticated'— refresh rejected (no valid cookie)
 */
export function useSessionRestore(): {
  status: Status
  errorMessage: string
  retry: () => void
} {
  const [status, setStatus] = React.useState<Status>('loading')
  const [attempt, setAttempt] = React.useState(0)
  const [errorMessage, setErrorMessage] = React.useState('')
  const setSession = useAuthStore((s) => s.setSession)
  const clearSession = useAuthStore((s) => s.clearSession)

  const retry = React.useCallback(() => {
    setErrorMessage('')
    setStatus('loading')
    setAttempt((value) => value + 1)
  }, [])

  React.useEffect(() => {
    let active = true
    let retryTimer: number | undefined

    if (isDemoMode) {
      if (!restoreDemoSession()) clearSession()
      setStatus('ready')
      return () => {
        active = false
      }
    }

    authApi
      .refresh()
      .then((data) => {
        if (!active) return
        const { accessToken, expiresInSeconds, user } = data.data
        setSession(accessToken, expiresInSeconds, user)
        setStatus('ready')
      })
      .catch((err) => {
        if (!active) return
        const apiErr = normalizeError(err)
        // Only a rejected refresh cookie means the session is actually over.
        if (apiErr.httpStatus === 401) {
          clearSession()
          setStatus('unauthenticated')
        } else if (
          attempt < 2 &&
          (apiErr.httpStatus === 503 ||
            apiErr.status === 503 ||
            apiErr.errorCode === 'SERVICE_UNAVAILABLE' ||
            apiErr.errorCode === 'NETWORK_ERROR')
        ) {
          // Gateway/Eureka cold start discovery delay: automatically retry after 1.5s
          retryTimer = window.setTimeout(() => {
            if (active) {
              setAttempt((prev) => prev + 1)
            }
          }, 1500)
        } else {
          // Preserve the session state and let the user retry after a transient
          // network, gateway, or origin configuration failure.
          setErrorMessage(apiErr.message)
          setStatus('unavailable')
        }
      })
    return () => {
      active = false
      if (retryTimer !== undefined) {
        window.clearTimeout(retryTimer)
      }
    }
  }, [attempt, setSession, clearSession])

  return { status, errorMessage, retry }
}
