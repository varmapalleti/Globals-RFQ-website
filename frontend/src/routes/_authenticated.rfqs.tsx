import { createFileRoute, Outlet } from '@tanstack/react-router'

export const Route = createFileRoute('/_authenticated/rfqs')({
  component: () => <Outlet />,
})
