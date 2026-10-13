import { z } from 'zod'

const envSchema = z.object({
  VITE_API_BASE_URL: z.string().url('VITE_API_BASE_URL must be a valid URL'),
  VITE_FRONTEND_URL: z.string().url('VITE_FRONTEND_URL must be a valid URL'),
})

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

  return result.data
}

export const env = parseEnv()
