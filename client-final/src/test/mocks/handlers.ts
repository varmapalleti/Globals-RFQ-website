import { http, HttpResponse } from 'msw'

const BASE = 'http://localhost:8081'

const makeSuccess = <T>(message: string, data?: T) => ({
  success: true,
  message,
  data,
  timestamp: new Date().toISOString(),
  correlationId: 'test-correlation-id',
})

const makeError = (
  status: number,
  errorCode: string,
  message: string,
  fieldErrors?: Array<{ field: string; message: string }>,
) => ({
  success: false,
  status,
  errorCode,
  message,
  path: '/api/auth/login',
  fieldErrors: fieldErrors ?? null,
  timestamp: new Date().toISOString(),
  correlationId: 'test-correlation-id',
})

const mockUser = {
  id: 1,
  fullName: 'Test User',
  officialEmail: 'test@company.com',
  officialEmailVerified: true,
  personalEmailVerified: false,
  status: 'ACTIVE',
  mustChangePassword: false,
  roles: ['ADMIN'],
}

const mockAuthResponse = {
  accessToken: 'mock-access-token',
  tokenType: 'Bearer',
  expiresInSeconds: 900,
  user: mockUser,
}

export const handlers = [
  // Login — success
  http.post(`${BASE}/api/auth/login`, async ({ request }) => {
    const body = (await request.json()) as { officialEmail: string; password: string }

    if (body.officialEmail === 'unverified@company.com') {
      return HttpResponse.json(
        makeError(
          403,
          'ACCOUNT_NOT_VERIFIED',
          'Please verify your official email before signing in',
        ),
        { status: 403 },
      )
    }

    if (body.password !== 'ValidPass1!' || body.officialEmail !== 'test@company.com') {
      return HttpResponse.json(makeError(401, 'INVALID_CREDENTIALS', 'Invalid email or password'), {
        status: 401,
      })
    }

    return HttpResponse.json(makeSuccess('Login successful', mockAuthResponse), { status: 200 })
  }),

  // Refresh
  http.post(`${BASE}/api/auth/refresh`, () => {
    return HttpResponse.json(makeSuccess('Token refreshed', mockAuthResponse), { status: 200 })
  }),

  // Logout
  http.post(`${BASE}/api/auth/logout`, () => {
    return HttpResponse.json({ success: true, message: 'Logged out' }, { status: 200 })
  }),

  // Me
  http.get(`${BASE}/api/auth/me`, () => {
    return HttpResponse.json(makeSuccess('Current user', mockUser), { status: 200 })
  }),

  // Forgot password
  http.post(`${BASE}/api/auth/forgot-password`, () => {
    return HttpResponse.json(
      {
        success: true,
        message: 'If an account exists for this email, a verification code has been sent',
        timestamp: new Date().toISOString(),
        correlationId: 'test-id',
      },
      { status: 200 },
    )
  }),

  // Verify OTP — success
  http.post(`${BASE}/api/auth/verify-reset-otp`, async ({ request }) => {
    const body = (await request.json()) as { otp: string }

    if (body.otp === '000000') {
      return HttpResponse.json(makeError(400, 'OTP_INVALID', 'Invalid or expired code'), {
        status: 400,
      })
    }

    return HttpResponse.json(
      makeSuccess('Code verified', { resetToken: 'mock-reset-token', expiresInSeconds: 600 }),
      { status: 200 },
    )
  }),

  // Reset password
  http.post(`${BASE}/api/auth/reset-password`, async ({ request }) => {
    const body = (await request.json()) as {
      resetToken: string
      newPassword: string
      confirmPassword: string
    }

    if (body.resetToken !== 'mock-reset-token') {
      return HttpResponse.json(
        makeError(
          401,
          'TOKEN_INVALID',
          'Your reset session is invalid or has expired. Please start again',
        ),
        { status: 401 },
      )
    }

    // Simulate password validation failure
    if (body.newPassword === 'weak') {
      return HttpResponse.json(
        makeError(400, 'VALIDATION_FAILED', 'Validation failed', [
          { field: 'newPassword', message: 'Password must be at least 8 characters long' },
          { field: 'newPassword', message: 'Password must contain at least one uppercase letter' },
        ]),
        { status: 400 },
      )
    }

    return HttpResponse.json(
      {
        success: true,
        message: 'Password changed successfully. Please sign in with your new password',
        timestamp: new Date().toISOString(),
        correlationId: 'test-id',
      },
      { status: 200 },
    )
  }),

  // Verify email
  http.post(`${BASE}/api/auth/verify-email`, async ({ request }) => {
    const body = (await request.json()) as { token: string }

    if (body.token === 'invalid-token') {
      return HttpResponse.json(
        makeError(401, 'TOKEN_INVALID', 'This verification link is invalid or has expired'),
        { status: 401 },
      )
    }

    return HttpResponse.json(
      {
        success: true,
        message: 'Email verified successfully',
        timestamp: new Date().toISOString(),
        correlationId: 'test-id',
      },
      { status: 200 },
    )
  }),

  // Resend verification
  http.post(`${BASE}/api/auth/resend-verification`, () => {
    return HttpResponse.json(
      {
        success: true,
        message: 'If the email needs verification, a new link has been sent',
        timestamp: new Date().toISOString(),
        correlationId: 'test-id',
      },
      { status: 200 },
    )
  }),
]
