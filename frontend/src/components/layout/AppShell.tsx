import * as React from 'react'
import { Logo } from '@/components/common/Logo'
import { Button } from '@/components/ui/button'
import { useAuth } from '@/features/auth/hooks/useAuth'
import { useLogout } from '@/features/auth/hooks/useAuthMutations'
import { LogOut, User } from 'lucide-react'

interface AppShellProps {
  children: React.ReactNode
}

export function AppShell({ children }: AppShellProps) {
  const { user } = useAuth()
  const logout = useLogout()

  return (
    <div className="flex min-h-dvh flex-col">
      {/* Topbar */}
      <header className="sticky top-0 z-40 border-b border-[--color-border] bg-white/95 backdrop-blur supports-[backdrop-filter]:bg-white/80">
        <div className="mx-auto flex h-16 max-w-7xl items-center justify-between px-4 sm:px-6 lg:px-8">
          <Logo className="h-8" />

          <div className="flex items-center gap-3">
            {user && (
              <div className="hidden items-center gap-2 sm:flex">
                <div className="flex h-8 w-8 items-center justify-center rounded-full bg-[--color-secondary]">
                  <User className="h-4 w-4 text-[--color-muted-foreground]" aria-hidden="true" />
                </div>
                <span className="text-sm font-medium">{user.fullName}</span>
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
      <footer className="border-t border-[--color-border] bg-[--color-muted] py-4">
        <div className="mx-auto max-w-7xl px-4 text-center text-xs text-[--color-muted-foreground] sm:px-6">
          &copy; {new Date().getFullYear()} Global RFQ. All rights reserved.
        </div>
      </footer>
    </div>
  )
}
