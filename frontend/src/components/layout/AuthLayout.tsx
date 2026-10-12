import * as React from 'react'
import { Logo } from '@/components/common/Logo'

interface AuthLayoutProps {
  children: React.ReactNode
}

/**
 * Centered auth card layout — used for login, forgot-password, verify-email.
 * Responsive from 360px up, no layout shift.
 */
export function AuthLayout({ children }: AuthLayoutProps) {
  return (
    <div className="flex min-h-dvh flex-col items-center justify-center bg-gradient-to-br from-blue-50 to-slate-100 px-4 py-12">
      <div className="w-full max-w-md">
        {/* Brand header */}
        <div className="mb-8 flex justify-center">
          <Logo className="h-9" />
        </div>

        {/* Card */}
        <div className="rounded-2xl border border-[--color-border] bg-white p-8 shadow-[var(--shadow-auth)]">
          {children}
        </div>

        {/* Footer */}
        <p className="mt-6 text-center text-xs text-[--color-muted-foreground]">
          &copy; {new Date().getFullYear()} Global RFQ. All rights reserved.
        </p>
      </div>
    </div>
  )
}
