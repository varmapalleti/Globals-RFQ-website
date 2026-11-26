import {
  useAuthStore,
  selectUser,
  selectIsAuthenticated,
  selectHasRole,
  selectHasAnyRole,
} from '@/features/auth/store/authStore'
import type { RoleName } from '@/types/api'

/**
 * Central auth hook — use this everywhere in the UI instead of reaching into the store directly.
 * Provides stable selector references to avoid unnecessary re-renders.
 */
export function useAuth() {
  const user = useAuthStore(selectUser)
  const isAuthenticated = useAuthStore(selectIsAuthenticated)

  return {
    user,
    isAuthenticated,
    hasRole: (role: RoleName) => user?.roles.includes(role) ?? false,
    hasAnyRole: (roles: RoleName[]) => roles.some((r) => user?.roles.includes(r) ?? false),
    mustChangePassword: user?.mustChangePassword ?? false,
  }
}

export { selectHasRole, selectHasAnyRole }
