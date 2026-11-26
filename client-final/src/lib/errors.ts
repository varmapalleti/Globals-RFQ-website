import type { AxiosError } from 'axios'

/** Shape of the backend's error envelope (ApiErrorResponse). */
export interface BackendErrorBody {
  success: false
  status: number
  errorCode: string
  message: string
  path?: string
  fieldErrors?: Array<{ field: string; message: string }>
  timestamp?: string
  correlationId?: string
}

/** Normalised error used throughout the frontend. */
export interface ApiError {
  message: string
  errorCode: string
  status: number
  /** Grouped by field name — multiple messages per field are possible (password rules). */
  fieldErrors: Record<string, string[]>
  correlationId: string | null
  /** Raw HTTP status */
  httpStatus: number
}

/** Guard: is this an Axios error with a backend error body? */
function isBackendError(err: unknown): err is AxiosError<BackendErrorBody> {
  return (
    typeof err === 'object' &&
    err !== null &&
    'isAxiosError' in err &&
    (err as AxiosError).isAxiosError === true &&
    (err as AxiosError<BackendErrorBody>).response?.data?.success === false
  )
}

/**
 * Normalise any error into an `ApiError`.
 * - Backend API errors: extract envelope fields.
 * - Network errors (no response): mark as NETWORK_ERROR.
 * - Anything else: mark as UNKNOWN_ERROR.
 */
export function normalizeError(err: unknown): ApiError {
  if (isBackendError(err)) {
    const res = err.response!
    const body = res.data

    // Collect fieldErrors — multiple messages per field (e.g. password violations)
    const fieldErrors: Record<string, string[]> = {}
    for (const fe of body.fieldErrors ?? []) {
      const existing = fieldErrors[fe.field]
      if (existing) {
        existing.push(fe.message)
      } else {
        fieldErrors[fe.field] = [fe.message]
      }
    }

    // Read correlation ID from header first (may differ from body when errors
    // are written by security filters that bypass GlobalExceptionHandler)
    const headers = err.response?.headers as Record<string, unknown> | undefined
    const rawHeader = headers?.['x-correlation-id']
    const correlationId = typeof rawHeader === 'string' ? rawHeader : (body.correlationId ?? null)

    return {
      message: body.message ?? 'An error occurred',
      errorCode: body.errorCode ?? 'UNKNOWN',
      status: body.status ?? res.status,
      fieldErrors,
      correlationId,
      httpStatus: res.status,
    }
  }

  if (
    typeof err === 'object' &&
    err !== null &&
    'isAxiosError' in err &&
    (err as AxiosError).isAxiosError &&
    !(err as AxiosError).response
  ) {
    return {
      message: 'Network error — please check your connection and try again.',
      errorCode: 'NETWORK_ERROR',
      status: 0,
      fieldErrors: {},
      correlationId: null,
      httpStatus: 0,
    }
  }

  return {
    message: 'An unexpected error occurred.',
    errorCode: 'UNKNOWN_ERROR',
    status: 500,
    fieldErrors: {},
    correlationId: null,
    httpStatus: 500,
  }
}

/** Return a user-facing message for an unexpected error that includes its correlation ID. */
export function unexpectedErrorMessage(err: ApiError): string {
  if (err.correlationId) {
    return `${err.message} (Ref: ${err.correlationId})`
  }
  return err.message
}
