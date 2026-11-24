import type { RoleName, UserSummary } from '@/types/api'
import { useAuthStore } from './store/authStore'
import { demoRoleOrder, demoUsers } from '@/features/rfq/types'

const DEMO_ROLE_KEY = 'global-rfq.demo-role'

export function demoUserForRole(role: RoleName): UserSummary {
  const user = demoUsers.find((candidate) => candidate.roles[0] === role) ?? demoUsers[0]!
  return {
    ...user,
    officialEmailVerified: true,
    personalEmailVerified: true,
    status: 'ACTIVE',
    mustChangePassword: false,
  }
}

export function startDemoSession(role: RoleName): void {
  if (!import.meta.env.DEV || import.meta.env['VITE_DEMO_MODE'] !== 'true') {
    throw new Error('Demo access is disabled outside an explicitly enabled development server.')
  }
  sessionStorage.setItem(DEMO_ROLE_KEY, role)
  useAuthStore.getState().setSession(`demo:${role}`, 8 * 60 * 60, demoUserForRole(role))
}

export function restoreDemoSession(): boolean {
  const role = sessionStorage.getItem(DEMO_ROLE_KEY) as RoleName | null
  if (!role || !demoRoleOrder.includes(role)) return false
  useAuthStore.getState().setSession(`demo:${role}`, 8 * 60 * 60, demoUserForRole(role))
  return true
}

export function changeDemoRole(role: RoleName): void {
  startDemoSession(role)
}

export function clearDemoSession(): void {
  sessionStorage.removeItem(DEMO_ROLE_KEY)
}

export function isDemoSession(): boolean {
  return useAuthStore.getState().accessToken?.startsWith('demo:') ?? false
}
