import * as React from 'react'
import { QueryClientProvider } from '@tanstack/react-query'
import { Toaster } from 'sonner'
import { queryClient } from '@/lib/query-client'

const ReactQueryDevtools = import.meta.env.DEV
  ? React.lazy(() =>
      import('@tanstack/react-query-devtools').then((res) => ({
        default: res.ReactQueryDevtools,
      })),
    )
  : () => null

interface ProvidersProps {
  children: React.ReactNode
}

export function Providers({ children }: ProvidersProps) {
  return (
    <QueryClientProvider client={queryClient}>
      {children}
      <Toaster
        position="top-right"
        richColors
        closeButton
        duration={5000}
        toastOptions={{
          classNames: {
            toast: 'font-sans',
          },
        }}
      />
      {import.meta.env.DEV && (
        <React.Suspense>
          <ReactQueryDevtools initialIsOpen={false} />
        </React.Suspense>
      )}
    </QueryClientProvider>
  )
}
