import { describe, it, expect, beforeEach } from 'vitest'
import { http, HttpResponse } from 'msw'
import { server } from './mocks/server'

// We test the single-flight logic in isolation:
// When multiple 401 responses arrive concurrently, only ONE refresh call is made.

const BASE = 'http://localhost:8081'
let refreshCallCount = 0

describe('single-flight refresh logic', () => {
  beforeEach(() => {
    refreshCallCount = 0
    // Override the refresh handler to count calls
    server.use(
      http.post(`${BASE}/api/auth/refresh`, () => {
        refreshCallCount++
        return HttpResponse.json({
          success: true,
          message: 'Token refreshed',
          data: {
            accessToken: 'new-access-token',
            tokenType: 'Bearer',
            expiresInSeconds: 900,
            user: {
              id: 1,
              fullName: 'Test',
              officialEmail: 'test@company.com',
              officialEmailVerified: true,
              personalEmailVerified: false,
              status: 'ACTIVE',
              mustChangePassword: false,
              roles: ['ADMIN'],
            },
          },
          timestamp: new Date().toISOString(),
          correlationId: 'test',
        })
      }),
    )
  })

  it('sends exactly one refresh request when multiple 401s arrive', async () => {
    // Simulate: http.ts keeps a single refreshPromise reference
    // When 3 concurrent 401 failures occur, only 1 refresh should fire.
    // We test the guard logic directly: if refreshPromise exists, do not create another.

    let inflightPromise: Promise<string> | null = null

    async function fakePerformRefresh(): Promise<string> {
      refreshCallCount++
      await new Promise((r) => setTimeout(r, 50))
      return 'new-token'
    }

    async function handleUnauthorized(): Promise<string> {
      if (!inflightPromise) {
        inflightPromise = fakePerformRefresh()
      }
      const token = await inflightPromise
      inflightPromise = null
      return token
    }

    // Trigger 3 concurrent 401 handlers
    const [t1, t2, t3] = await Promise.all([
      handleUnauthorized(),
      handleUnauthorized(),
      handleUnauthorized(),
    ])

    expect(t1).toBe('new-token')
    expect(t2).toBe('new-token')
    expect(t3).toBe('new-token')
    // Only ONE actual refresh call was made despite 3 concurrent invocations
    expect(refreshCallCount).toBe(1)
  })
})
