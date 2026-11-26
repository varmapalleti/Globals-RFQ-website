import axios, { type AxiosError, type InternalAxiosRequestConfig } from 'axios'
import { env } from '@/lib/env'
import { useAuthStore } from '@/features/auth/store/authStore'
import { ROUTES } from '@/lib/constants'
import { queryClient } from '@/lib/query-client'
import type { ApiSuccessResponse, AuthResponse } from '@/types/api'

/** Paths that must NEVER trigger the refresh interceptor. */
const AUTH_PATHS = new Set([
  '/api/auth/login',
  '/api/auth/refresh',
  '/api/auth/logout',
  '/api/auth/forgot-password',
  '/api/auth/verify-reset-otp',
  '/api/auth/reset-password',
  '/api/auth/verify-email',
  '/api/auth/resend-verification',
])

// Single-flight promise for concurrent 401 → refresh handling
let refreshPromise: Promise<string | null> | null = null

export const http = axios.create({
  baseURL: env.VITE_API_BASE_URL,
  withCredentials: true, // sends refresh_token HttpOnly cookie on every request
  headers: {
    'Content-Type': 'application/json',
  },
})

// ── Request interceptor: attach access token from memory ──────────────────
http.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const token = useAuthStore.getState().accessToken
  if (token) {
    config.headers.set('Authorization', `Bearer ${token}`)
  }
  return config
})

// ── Response interceptor: handle 401 with single-flight refresh ──────────
http.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const original = error.config as InternalAxiosRequestConfig & { _retry?: boolean }

    // Only intercept 401 on non-auth, non-retried requests
    if (
      error.response?.status !== 401 ||
      original._retry ||
      !original.url ||
      AUTH_PATHS.has(original.url.replace(env.VITE_API_BASE_URL, '').split('?')[0] ?? '')
    ) {
      return Promise.reject(error)
    }

    original._retry = true

    try {
      // Single-flight: if another request is already refreshing, queue behind it
      if (!refreshPromise) {
        refreshPromise = performRefresh()
      }

      const newToken = await refreshPromise

      if (newToken) {
        original.headers.set('Authorization', `Bearer ${newToken}`)
        return http(original)
      }
    } catch {
      // Refresh failed — handled inside performRefresh
    } finally {
      refreshPromise = null
    }

    return Promise.reject(error)
  },
)

/**
 * Call /api/auth/refresh and update the in-memory store.
 * Returns the new access token, or null if refresh failed.
 * On failure: clears session, clears query cache, redirects to login.
 */
async function performRefresh(): Promise<string | null> {
  try {
    const res = await http.post<ApiSuccessResponse<AuthResponse>>('/api/auth/refresh')
    const { accessToken, expiresInSeconds, user } = res.data.data

    useAuthStore.getState().setSession(accessToken, expiresInSeconds, user)

    return accessToken
  } catch {
    // Clear session and redirect
    useAuthStore.getState().clearSession()
    queryClient.clear()

    // Use replace so the user can't navigate back to the protected page
    window.location.replace(`${ROUTES.LOGIN}?reason=session_expired`)

    return null
  }
}
