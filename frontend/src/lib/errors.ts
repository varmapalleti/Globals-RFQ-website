import type { AxiosError } from 'axios'

/** Shape of the backend's error envelope (ApiErrorResponse). */
export interface BackendErrorBody {
  success?: false
  status?: number
  errorCode?: string
  /** Gateway circuit-breaker fallbacks use `code`. */
  code?: string
  message?: string
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
  /** Grouped by field name — multiple messages per field are possible. */
  fieldErrors: Record<string, string[]>
  correlationId: string | null
  /** Raw HTTP status */
  httpStatus: number
}

/**
 * Normalize service, gateway-fallback, and transport errors to one shape.
 * RFQ and Identity use `errorCode`; gateway circuit-breaker responses use `code`.
 */
export function normalizeError(err: unknown): ApiError {
  if (
    typeof err === 'object' &&
    err !== null &&
    'isAxiosError' in err &&
    (err as AxiosError).isAxiosError
  ) {
    const axiosError = err as AxiosError<BackendErrorBody>
    const response = axiosError.response
    if (!response) {
      return {
        message: 'Network error — please check your connection and try again.',
        errorCode: 'NETWORK_ERROR',
        status: 0,
        fieldErrors: {},
        correlationId: null,
        httpStatus: 0,
      }
    }

    const body =
      typeof response.data === 'object' && response.data !== null ? response.data : undefined
    const fieldErrors: Record<string, string[]> = {}
    for (const fieldError of body?.fieldErrors ?? []) {
      const messages = fieldErrors[fieldError.field] ?? []
      messages.push(fieldError.message)
      fieldErrors[fieldError.field] = messages
    }

    // Prefer the response header because security filters and gateway errors
    // can bypass the service's normal JSON exception envelope.
    const headers = response.headers as Record<string, unknown> | undefined
    const headerCorrelationId = headers?.['x-correlation-id']
    return {
      message:
        typeof body?.message === 'string'
          ? body.message
          : `Request failed with HTTP ${response.status}.`,
      errorCode:
        typeof body?.errorCode === 'string'
          ? body.errorCode
          : typeof body?.code === 'string'
            ? body.code
            : 'HTTP_ERROR',
      status: typeof body?.status === 'number' ? body.status : response.status,
      fieldErrors,
      correlationId:
        typeof headerCorrelationId === 'string'
          ? headerCorrelationId
          : (body?.correlationId ?? null),
      httpStatus: response.status,
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

/** Return only the user-facing message. Correlation IDs stay in diagnostics/logs. */
export function unexpectedErrorMessage(err: ApiError): string {
  return err.message
}
