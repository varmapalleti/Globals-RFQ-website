import { RouterProvider, createRouter } from '@tanstack/react-router'
import { routeTree } from '../routeTree.gen'
import { queryClient } from '@/lib/query-client'

const router = createRouter({
  routeTree,
  context: {},
  defaultPreload: 'intent',
  defaultPreloadStaleTime: 0,
})

// Type-safe router module augmentation
declare module '@tanstack/react-router' {
  interface Register {
    router: typeof router
  }
}

export function AppRouter() {
  return <RouterProvider router={router} />
}

export { queryClient }
