import type * as React from 'react'
import { useAuth } from '@/features/auth/hooks/useAuth'
import type { RoleName } from '@/types/api'

interface CanProps {
  roles: RoleName[]
  children: React.ReactNode
  fallback?: React.ReactNode
}

/**
 * Conditionally renders children if the authenticated user has at least one of the given roles.
 *
 * IMPORTANT: This is UI-only convenience — the backend enforces access control.
 * Never rely on this for security decisions.
 */
export function Can({ roles, children, fallback = null }: CanProps) {
  const { hasAnyRole } = useAuth()

  if (!hasAnyRole(roles)) return fallback as React.ReactElement | null
  return children as React.ReactElement
}
