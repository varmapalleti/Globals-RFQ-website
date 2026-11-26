/** Application-wide constants — never use magic strings in feature code. */

export const ROUTES = {
  LOGIN: '/login',
  FORGOT_PASSWORD: '/forgot-password',
  VERIFY_EMAIL: '/verify-email',
  DASHBOARD: '/dashboard',
  CHANGE_PASSWORD: '/change-password',
} as const

export const QUERY_KEYS = {
  ME: ['auth', 'me'] as const,
} as const

export const AUTH_COOKIE = 'refresh_token'

export const HTTP_STATUS = {
  UNAUTHORIZED: 401,
  FORBIDDEN: 403,
  TOO_MANY_REQUESTS: 429,
} as const

/** How many ms before access token expiry to proactively refresh. */
export const REFRESH_BEFORE_EXPIRY_MS = 60_000
