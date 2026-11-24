import type { ApiSuccessResponse, AuthResponse } from '@/types/api'

type RefreshResponse = ApiSuccessResponse<AuthResponse>

const REFRESH_LOCK_NAME = 'global-rfq-refresh-token'
let inTabRefresh: Promise<RefreshResponse> | null = null

/**
 * Rotating refresh tokens must not be submitted concurrently by two tabs.
 * Web Locks serializes requests across same-origin tabs; the module promise
 * coalesces requests within one tab (including React Strict Mode mounts).
 */
export function coordinateRefresh(request: () => Promise<RefreshResponse>) {
  if (inTabRefresh) return inTabRefresh

  const run = () => request()
  const operation =
    typeof navigator !== 'undefined' && navigator.locks
      ? navigator.locks.request(REFRESH_LOCK_NAME, run)
      : run()

  inTabRefresh = Promise.resolve(operation).finally(() => {
    inTabRefresh = null
  })
  return inTabRefresh
}
