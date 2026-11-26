import { QueryClient } from '@tanstack/react-query'
import { normalizeError } from '@/lib/errors'

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 5 * 60 * 1000, // 5 min
      gcTime: 10 * 60 * 1000, // 10 min
      refetchOnWindowFocus: false,
      retry: (failureCount, error) => {
        const apiError = normalizeError(error)
        // Never retry client errors (4xx)
        if (apiError.httpStatus >= 400 && apiError.httpStatus < 500) return false
        return failureCount < 2
      },
    },
    mutations: {
      // Mutations do not retry by default
      retry: false,
    },
  },
})
