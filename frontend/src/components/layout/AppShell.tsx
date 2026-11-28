import * as React from 'react'
import { Link, useRouterState } from '@tanstack/react-router'
import {
  BarChart3,
  ChevronDown,
  ClipboardList,
  FileText,
  Flag,
  LayoutDashboard,
  LogOut,
  Menu,
  Settings2,
  ShoppingCart,
  Users,
  X,
} from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Logo } from '@/components/common/Logo'
import { NotificationBell } from './NotificationBell'
import { useAuth } from '@/features/auth/hooks/useAuth'
import { useLogout } from '@/features/auth/hooks/useAuthMutations'
import { changeDemoRole, isDemoSession } from '@/features/auth/demoSession'
import { demoRoleOrder, roleTitles } from '@/features/rfq/types'
import { isDemoMode } from '@/lib/env'
import type { RoleName } from '@/types/api'

interface AppShellProps {
  children: React.ReactNode
}
interface NavItem {
  label: string
  to:
    | '/dashboard'
    | '/rfqs'
    | '/red-flags'
    | '/purchases'
    | '/reports'
    | '/users'
    | '/rfq-settings'
    | '/audit-logs'
  icon: typeof LayoutDashboard
  roles: RoleName[]
}

const navItems: NavItem[] = [
  {
    label: 'Dashboard',
    to: '/dashboard',
    icon: LayoutDashboard,
    roles: [
      'SUPER_ADMIN',
      'ADMIN',
      'QUOTATION_MANAGER',
      'REVIEWER',
      'MARKETING_EXECUTIVE',
      'VIEWER',
    ],
  },
  {
    label: 'RFQs',
    to: '/rfqs',
    icon: FileText,
    roles: [
      'SUPER_ADMIN',
      'ADMIN',
      'QUOTATION_MANAGER',
      'REVIEWER',
      'MARKETING_EXECUTIVE',
      'VIEWER',
    ],
  },
  {
    label: 'Red Flags',
    to: '/red-flags',
    icon: Flag,
    roles: [
      'SUPER_ADMIN',
      'ADMIN',
      'QUOTATION_MANAGER',
      'REVIEWER',
      'MARKETING_EXECUTIVE',
    ],
  },
  {
    label: 'Purchases',
    to: '/purchases',
    icon: ShoppingCart,
    roles: ['SUPER_ADMIN', 'ADMIN', 'MARKETING_EXECUTIVE'],
  },
  { label: 'Users', to: '/users', icon: Users, roles: ['SUPER_ADMIN', 'ADMIN'] },
  { label: 'RFQ Rules', to: '/rfq-settings', icon: Settings2, roles: ['SUPER_ADMIN', 'ADMIN'] },
  { label: 'Audit Logs', to: '/audit-logs', icon: ClipboardList, roles: ['SUPER_ADMIN'] },
  {
    label: 'Reports',
    to: '/reports',
    icon: BarChart3,
    roles: ['SUPER_ADMIN', 'ADMIN'],
  },
]

export function AppShell({ children }: AppShellProps) {
  const { user } = useAuth()
  const logout = useLogout()
  const pathname = useRouterState({ select: (state) => state.location.pathname })
  const [mobileNavOpen, setMobileNavOpen] = React.useState(false)
  const role = user?.roles.includes('SUPER_ADMIN')
    ? 'SUPER_ADMIN'
    : user?.roles.includes('ADMIN')
      ? 'ADMIN'
      : (user?.roles[0] ?? 'VIEWER')
  const activeNav = navItems.find(
    (item) => item.to === pathname || (item.to === '/rfqs' && pathname.startsWith('/rfqs/')),
  )
  const initials = (user?.fullName ?? 'Global RFQ')
    .split(' ')
    .map((part) => part[0])
    .slice(0, 2)
    .join('')
    .toUpperCase()

  return (
    <div className="min-h-dvh text-slate-900">
      <div className="flex min-h-dvh">
        <aside
          className={`${mobileNavOpen ? 'fixed inset-y-0 left-0 z-50 block w-72 shadow-xl' : 'hidden'} border-r border-brand-100 bg-white/95 backdrop-blur lg:sticky lg:top-0 lg:block lg:h-dvh lg:w-[240px] lg:shrink-0`}
          aria-label="Primary navigation"
        >
          <div className="flex h-16 items-center justify-between border-b border-brand-100 px-4">
            <Link to="/dashboard" className="flex items-center gap-2.5">
              <Logo variant="icon" className="h-9 shrink-0" />
              <span className="text-lg font-bold tracking-tight text-brand-900">GSS CONNECT</span>
            </Link>
            <Button
              variant="ghost"
              size="icon"
              className="lg:hidden"
              aria-label="Close navigation"
              onClick={() => setMobileNavOpen(false)}
            >
              <X />
            </Button>
          </div>
          <nav className="space-y-0.5 py-3" aria-label="Workspace">
            {navItems
              .filter(
                (item) => user?.roles.some((userRole) => item.roles.includes(userRole)) ?? false,
              )
              .map((item) => {
                const Icon = item.icon
                const isActive = activeNav?.to === item.to
                return (
                  <Link
                    key={item.to}
                    to={item.to}
                    onClick={() => setMobileNavOpen(false)}
                    className={`mx-2 flex items-center gap-4 rounded-lg border-l-[3px] px-[13px] py-3 text-sm transition ${isActive ? 'border-red-brand-600 bg-brand-50 font-semibold text-brand-900' : 'border-transparent text-slate-700 hover:bg-brand-50/70 hover:text-brand-900'}`}
                    aria-current={isActive ? 'page' : undefined}
                  >
                    <Icon
                      className={`size-5 ${isActive ? 'text-brand-900' : 'text-slate-600'}`}
                      aria-hidden="true"
                    />
                    {item.label}
                  </Link>
                )
              })}
          </nav>
          <p className="absolute right-4 bottom-5 left-4 hidden text-center text-[11px] text-slate-400 lg:block">
            Global RFQ Management
          </p>
        </aside>

        {mobileNavOpen && (
          <button
            type="button"
            aria-label="Close navigation overlay"
            className="fixed inset-0 z-40 bg-slate-950/25 lg:hidden"
            onClick={() => setMobileNavOpen(false)}
          />
        )}

        <div className="flex min-h-dvh min-w-0 flex-1 flex-col">
          <header className="sticky top-0 z-30 flex h-16 items-center justify-between border-b border-brand-100 bg-white/90 px-4 shadow-sm backdrop-blur sm:px-6">
            <div className="flex items-center gap-2">
              <Button
                variant="ghost"
                size="icon"
                className="lg:hidden"
                aria-label={mobileNavOpen ? 'Close navigation' : 'Open navigation'}
                onClick={() => setMobileNavOpen((open) => !open)}
              >
                {mobileNavOpen ? <X /> : <Menu />}
              </Button>
              {isDemoMode && isDemoSession() && (
                <label className="flex items-center gap-2 rounded-md border border-brand-100 bg-brand-50 px-3 py-2 text-xs font-medium text-brand-900">
                  Demo role
                  <select
                    aria-label="Switch demo role"
                    value={role}
                    onChange={(event) => changeDemoRole(event.target.value as RoleName)}
                    className="max-w-40 cursor-pointer bg-transparent text-xs font-semibold outline-none"
                  >
                    {demoRoleOrder.map((demoRole) => (
                      <option key={demoRole} value={demoRole}>
                        {roleTitles[demoRole]}
                      </option>
                    ))}
                  </select>
                  <ChevronDown className="size-3" aria-hidden="true" />
                </label>
              )}
            </div>
            <div className="flex items-center gap-3 sm:gap-5">
              <NotificationBell />
              <div className="flex items-center gap-2.5">
                <span className="flex size-9 items-center justify-center rounded-full bg-brand-900 text-sm font-medium text-white ring-2 ring-red-brand-100">
                  {initials}
                </span>
                <div className="hidden max-w-44 leading-tight sm:block">
                  <p className="truncate text-sm font-semibold">
                    {user?.fullName ?? 'Workspace user'}
                  </p>
                  <p className="truncate text-xs text-slate-500">{roleTitles[role]}</p>
                </div>
              </div>
              <Button
                variant="outline"
                size="sm"
                onClick={() => logout.mutate()}
                disabled={logout.isPending}
                aria-label="Sign out"
              >
                <LogOut className="size-4" aria-hidden="true" />
                <span className="hidden sm:inline">Sign out</span>
              </Button>
            </div>
          </header>

          <main className="min-w-0 flex-1 px-4 py-6 sm:px-6 lg:px-7 lg:py-7" id="main-content">
            {isDemoMode && isDemoSession() && (
              <div
                className="mb-5 flex items-start gap-2 rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-xs leading-5 text-amber-900"
                role="status"
              >
                <span className="mt-1 size-2 shrink-0 rounded-full bg-amber-500" />
                <span>
                  <strong>DEMO WORKSPACE</strong> — Sample records and workflow actions are held in
                  this browser for the presentation. No live customer data is connected.
                </span>
              </div>
            )}
            {children}
          </main>
        </div>
      </div>
    </div>
  )
}
