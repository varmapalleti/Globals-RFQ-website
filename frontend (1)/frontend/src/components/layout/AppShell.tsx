import * as React from 'react'
import { LogOut, User } from 'lucide-react'
import { Logo } from '@/components/common/Logo'
import { Button } from '@/components/ui/button'
import { brand } from '@/config/brand'
import { useAuth } from '@/features/auth/hooks/useAuth'
import { useLogout } from '@/features/auth/hooks/useAuthMutations'

interface AppShellProps {
  children: React.ReactNode
}

export function AppShell({ children }: AppShellProps) {
  const { user } = useAuth()
  const logout = useLogout()

  return (
    <div className="flex min-h-dvh flex-col bg-linear-to-br from-[#eef4fc] via-[#f8fbfe] to-[#e7eefa]">
      {/* Topbar */}
      <header className="border-brand-border sticky top-0 z-40 border-b bg-white/95 backdrop-blur supports-[backdrop-filter]:bg-white/85">
        {/* brand strip: navy → red, echoing the logo */}
        <div
          className="from-brand-primary via-brand-primary to-brand-accent h-1 bg-linear-to-r"
          aria-hidden="true"
        />
        <div className="mx-auto flex h-[76px] max-w-7xl items-center justify-between px-4 sm:px-6 lg:px-8">
          <Logo className="h-14" />

          <div className="flex items-center gap-3">
            {user && (
              <div className="hidden items-center gap-2 sm:flex">
                <div className="bg-brand-primary-soft flex h-8 w-8 items-center justify-center rounded-full">
                  <User className="text-brand-primary h-4 w-4" aria-hidden="true" />
                </div>
                <span className="text-brand-ink text-sm font-semibold">{user.fullName}</span>
              </div>
            )}
            <Button
              variant="outline"
              size="sm"
              onClick={() => logout.mutate()}
              disabled={logout.isPending}
              aria-label="Sign out"
            >
              <LogOut className="h-4 w-4" aria-hidden="true" />
              <span className="hidden sm:inline">Sign out</span>
            </Button>
          </div>
        </div>
      </header>

      {/* Main */}
      <main className="flex-1" id="main-content">
        <div className="mx-auto max-w-7xl px-4 py-8 sm:px-6 lg:px-8">{children}</div>
      </main>

      {/* Footer */}
      <footer className="border-brand-accent bg-brand-primary border-t-4 py-4">
        <div className="mx-auto max-w-7xl px-4 text-center text-xs text-white/85 sm:px-6">
          &copy; {new Date().getFullYear()} {brand.name}. All rights reserved.
        </div>
      </footer>
    </div>
  )
}
