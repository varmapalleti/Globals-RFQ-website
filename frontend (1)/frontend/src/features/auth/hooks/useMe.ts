import { useQuery } from '@tanstack/react-query'
import { getMe } from '@/features/auth/api/authApi'
import { useAuthStore, selectIsAuthenticated } from '@/features/auth/store/authStore'
import { QUERY_KEYS } from '@/lib/constants'

/**
 * Fetches the current user from /api/auth/me.
 * Only runs when we have an access token (authenticated).
 */
export function useMe() {
  const isAuthenticated = useAuthStore(selectIsAuthenticated)

  return useQuery({
    queryKey: QUERY_KEYS.ME,
    queryFn: getMe,
    enabled: isAuthenticated,
    staleTime: 2 * 60 * 1000, // 2 min
    select: (data) => data.data,
  })
}
