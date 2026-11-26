import { describe, it, expect } from 'vitest'
import { normalizeError } from '@/lib/errors'
import type { AxiosError, InternalAxiosRequestConfig } from 'axios'

function makeAxiosError(status: number, body: object): AxiosError {
  const err = new Error('Request failed') as AxiosError
  err.isAxiosError = true
  err.response = {
    status,
    data: body,
    headers: { 'x-correlation-id': 'header-correlation-id' },
    config: {} as InternalAxiosRequestConfig,
    statusText: String(status),
  }
  return err
}

function makeNetworkError(): AxiosError {
  const err = new Error('Network Error') as AxiosError
  err.isAxiosError = true
  err.response = undefined
  return err
}

describe('normalizeError', () => {
  it('extracts backend error envelope correctly', () => {
    const err = makeAxiosError(401, {
      success: false,
      status: 401,
      errorCode: 'INVALID_CREDENTIALS',
      message: 'Invalid email or password',
      correlationId: 'body-correlation-id',
    })
    const result = normalizeError(err)
    expect(result.errorCode).toBe('INVALID_CREDENTIALS')
    expect(result.message).toBe('Invalid email or password')
    expect(result.httpStatus).toBe(401)
    expect(result.fieldErrors).toEqual({})
    // Header takes priority over body for correlationId
    expect(result.correlationId).toBe('header-correlation-id')
  })

  it('groups multiple fieldErrors by field name', () => {
    const err = makeAxiosError(400, {
      success: false,
      status: 400,
      errorCode: 'VALIDATION_FAILED',
      message: 'Validation failed',
      fieldErrors: [
        { field: 'newPassword', message: 'Password must be at least 8 characters long' },
        { field: 'newPassword', message: 'Password must contain at least one uppercase letter' },
        { field: 'confirmPasswordMatching', message: 'Passwords do not match' },
      ],
    })
    const result = normalizeError(err)
    expect(result.fieldErrors['newPassword']).toHaveLength(2)
    expect(result.fieldErrors['confirmPasswordMatching']).toHaveLength(1)
    expect(result.fieldErrors['newPassword']?.[0]).toBe(
      'Password must be at least 8 characters long',
    )
  })

  it('handles network error (no response)', () => {
    const err = makeNetworkError()
    const result = normalizeError(err)
    expect(result.errorCode).toBe('NETWORK_ERROR')
    expect(result.httpStatus).toBe(0)
    expect(result.correlationId).toBeNull()
  })

  it('handles non-axios unknown error', () => {
    const result = normalizeError(new Error('Something random'))
    expect(result.errorCode).toBe('UNKNOWN_ERROR')
    expect(result.message).toBe('An unexpected error occurred.')
  })

  it('handles null/undefined gracefully', () => {
    expect(() => normalizeError(null)).not.toThrow()
    expect(() => normalizeError(undefined)).not.toThrow()
  })
})
