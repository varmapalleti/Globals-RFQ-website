import * as React from 'react'
import * as authApi from '@/features/auth/api/authApi'
import { useAuthStore } from '@/features/auth/store/authStore'
import { normalizeError } from '@/lib/errors'

type Status = 'loading' | 'ready' | 'unauthenticated'

/**
 * On app mount, attempt ONE refresh to restore the session from the HttpOnly cookie.
 * Handles React StrictMode double-mount safely via a ref guard.
 *
 * Returns:
 *   - 'loading'        — refresh in flight, show spinner
 *   - 'ready'          — session restored (or confirmed absent)
 *   - 'unauthenticated'— refresh rejected (no valid cookie)
 */
export function useSessionRestore(): Status {
  const [status, setStatus] = React.useState<Status>('loading')
  const called = React.useRef(false)
  const setSession = useAuthStore((s) => s.setSession)
  const clearSession = useAuthStore((s) => s.clearSession)

  React.useEffect(() => {
    // StrictMode safety: only run once per mount lifecycle
    if (called.current) return
    called.current = true

    authApi
      .refresh()
      .then((data) => {
        const { accessToken, expiresInSeconds, user } = data.data
        setSession(accessToken, expiresInSeconds, user)
        setStatus('ready')
      })
      .catch((err) => {
        const apiErr = normalizeError(err)
        // A 401 means no valid refresh cookie — not an error, just unauthenticated
        if (apiErr.httpStatus === 401 || apiErr.httpStatus === 0) {
          clearSession()
          setStatus('unauthenticated')
        } else {
          // Network error or unexpected — still mark ready so the app doesn't hang
          clearSession()
          setStatus('unauthenticated')
        }
      })
  }, [setSession, clearSession])

  return status
}
