import { create } from 'zustand'
import type { UserSummary, RoleName } from '@/types/api'

interface AuthSession {
  accessToken: string | null
  /** Epoch ms when the access token expires */
  expiresAt: number | null
  user: UserSummary | null
}

interface AuthActions {
  setSession: (accessToken: string, expiresInSeconds: number, user: UserSummary) => void
  clearSession: () => void
  updateUser: (user: UserSummary) => void
}

type AuthStore = AuthSession & AuthActions

const initialState: AuthSession = {
  accessToken: null,
  expiresAt: null,
  user: null,
}

export const useAuthStore = create<AuthStore>()((set) => ({
  ...initialState,

  setSession: (accessToken, expiresInSeconds, user) =>
    set({
      accessToken,
      expiresAt: Date.now() + expiresInSeconds * 1000,
      user,
    }),

  clearSession: () => set(initialState),

  updateUser: (user) => set({ user }),
}))

/** Convenience selectors — stable references avoid unnecessary re-renders. */
export const selectIsAuthenticated = (s: AuthStore): boolean => s.accessToken !== null
export const selectUser = (s: AuthStore): UserSummary | null => s.user
export const selectAccessToken = (s: AuthStore): string | null => s.accessToken
export const selectHasRole =
  (role: RoleName) =>
  (s: AuthStore): boolean =>
    s.user?.roles.includes(role) ?? false
export const selectHasAnyRole =
  (roles: RoleName[]) =>
  (s: AuthStore): boolean =>
    roles.some((r) => s.user?.roles.includes(r) ?? false)
