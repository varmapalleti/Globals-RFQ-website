import * as React from 'react'
import * as DropdownMenu from '@radix-ui/react-dropdown-menu'
import {
  Bell,
  FileText,
  Flag,
  History,
  LayoutDashboard,
  LogOut,
  ShieldCheck,
  ShoppingCart,
  Users,
  BarChart3,
} from 'lucide-react'
import { cn } from '@/lib/utils'
import { useAuth } from '@/features/auth/hooks/useAuth'
import { useLogout } from '@/features/auth/hooks/useAuthMutations'

const NAV_ITEMS = [
  { label: 'Dashboard', icon: LayoutDashboard },
  { label: 'RFQs', icon: FileText },
  { label: 'Red Flags', icon: Flag },
  { label: 'Purchases', icon: ShoppingCart },
  { label: 'Users', icon: Users },
  { label: 'Roles', icon: ShieldCheck },
  { label: 'Audit Logs', icon: History },
  { label: 'Reports', icon: BarChart3 },
] as const

const ACTIVE_ITEM = 'Dashboard'

function initialOf(name?: string | null) {
  return (name?.trim().charAt(0) || 'U').toUpperCase()
}

/** Top-right profile avatar + dropdown (name, email, roles, sign out). */
function ProfileMenu() {
  const { user } = useAuth()
  const logout = useLogout()

  return (
    <DropdownMenu.Root>
      <DropdownMenu.Trigger asChild>
        <button
          type="button"
          aria-label="Open profile menu"
          className="bg-brand-primary flex h-9 w-9 items-center justify-center rounded-full text-sm font-semibold text-white transition hover:opacity-90"
        >
          {initialOf(user?.fullName)}
        </button>
      </DropdownMenu.Trigger>

      <DropdownMenu.Portal>
        <DropdownMenu.Content
          align="end"
          sideOffset={8}
          className="shadow-dropdown border-brand-border z-50 w-64 rounded-xl border bg-white p-1.5"
        >
          <div className="flex items-center gap-3 px-3 py-3">
            <span className="bg-brand-primary flex h-10 w-10 shrink-0 items-center justify-center rounded-full font-semibold text-white">
              {initialOf(user?.fullName)}
            </span>
            <div className="min-w-0">
              <p className="text-brand-ink truncate text-sm font-semibold">
                {user?.fullName ?? 'User'}
              </p>
              <p className="text-brand-muted truncate text-xs">{user?.officialEmail}</p>
            </div>
          </div>
          {user?.roles.length ? (
            <div className="flex flex-wrap gap-1 px-3 pb-3">
              {user.roles.map((role) => (
                <span
                  key={role}
                  className="bg-brand-primary-soft text-brand-primary rounded-full px-2 py-0.5 text-[11px] font-semibold"
                >
                  {role.replace(/_/g, ' ')}
                </span>
              ))}
            </div>
          ) : null}
          <DropdownMenu.Separator className="bg-brand-border my-1 h-px" />
          <DropdownMenu.Item
            onSelect={() => logout.mutate()}
            className="text-brand-ink data-[highlighted]:bg-brand-primary-soft flex cursor-pointer items-center gap-2 rounded-lg px-3 py-2 text-sm outline-none"
          >
            <LogOut className="h-4 w-4" aria-hidden="true" />
            Sign out
          </DropdownMenu.Item>
        </DropdownMenu.Content>
      </DropdownMenu.Portal>
    </DropdownMenu.Root>
  )
}

export function HomeLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className="flex min-h-dvh bg-[#f7f7f8]">
      {/* Sidebar */}
      <aside className="border-brand-border hidden w-[182px] shrink-0 flex-col border-r bg-white md:flex">
        <div className="flex h-[60px] items-center justify-center">
          <span className="text-brand-primary text-sm font-bold">RFQ System</span>
        </div>
        <nav aria-label="Main" className="flex flex-col py-1">
          {NAV_ITEMS.map(({ label, icon: Icon }) => {
            const active = label === ACTIVE_ITEM
            return (
              <button
                key={label}
                type="button"
                aria-current={active ? 'page' : undefined}
                className={cn(
                  'flex items-center gap-4 px-3 py-2.5 text-left text-xs font-semibold transition-colors',
                  active ? 'bg-[#e8f0fb] text-slate-900' : 'text-slate-700 hover:bg-slate-50',
                )}
              >
                <Icon
                  className={cn(
                    'h-[18px] w-[18px]',
                    active ? 'text-brand-primary' : 'text-slate-800',
                  )}
                  aria-hidden="true"
                />
                {label}
              </button>
            )
          })}
        </nav>
      </aside>

      {/* Right side */}
      <div className="flex min-w-0 flex-1 flex-col">
        <header className="border-brand-border flex h-[60px] shrink-0 items-center justify-end gap-5 border-b bg-white px-6 shadow-sm">
          <button
            type="button"
            aria-label="Notifications, 1 unread"
            className="relative text-slate-600 hover:text-slate-900"
          >
            <Bell className="h-5 w-5" aria-hidden="true" />
            <span className="absolute -top-2 -right-2 flex h-4 w-4 items-center justify-center rounded-full bg-red-600 text-[10px] font-bold text-white">
              1
            </span>
          </button>
          <ProfileMenu />
        </header>

        <main id="main-content" className="flex-1 px-6 py-6 sm:px-8">
          {children}
        </main>
      </div>
    </div>
  )
}
