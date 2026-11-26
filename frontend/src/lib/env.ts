import { z } from 'zod'

const envSchema = z.object({
  VITE_API_BASE_URL: z.string().min(1, 'VITE_API_BASE_URL must not be empty'),
  VITE_FRONTEND_URL: z.string().url('VITE_FRONTEND_URL must be a valid URL'),
})

function normalizeApiBaseUrl(value: string): string {
  const trimmed = value.trim()
  // API functions already use /api/... paths. These relative values mean
  // same-origin requests through nginx's /api proxy, so use only the origin
  // as Axios' baseURL (avoids producing /api/api/... URLs).
  if (trimmed === '/' || trimmed === '/api' || trimmed === '/api/') {
    return window.location.origin
  }

  let parsed: URL
  try {
    parsed = new URL(trimmed)
  } catch {
    throw new Error('[env] VITE_API_BASE_URL must be an http(s) URL or /, /api, or /api/')
  }
  if (!['http:', 'https:'].includes(parsed.protocol) || parsed.pathname !== '/' || parsed.search || parsed.hash) {
    throw new Error('[env] VITE_API_BASE_URL must be an origin URL or a supported relative proxy path')
  }
  return parsed.origin
}

function parseEnv() {
  const envSource: Record<string, unknown> = import.meta.env
  const result = envSchema.safeParse({
    VITE_API_BASE_URL: envSource['VITE_API_BASE_URL'],
    VITE_FRONTEND_URL: envSource['VITE_FRONTEND_URL'],
  })

  if (!result.success) {
    const issues = result.error.issues
      .map((i) => `  • ${i.path.join('.')}: ${i.message}`)
      .join('\n')
    throw new Error(`[env] Invalid environment variables:\n${issues}`)
  }

  return {
    ...result.data,
    VITE_API_BASE_URL: normalizeApiBaseUrl(result.data.VITE_API_BASE_URL),
  }
}

export const env = parseEnv()

/**
 * Demo data is available only in Vite's development server. Even when a demo
 * mode file is passed to a production build, this evaluates to false.
 */
export const isDemoMode = import.meta.env.DEV && import.meta.env['VITE_DEMO_MODE'] === 'true'
