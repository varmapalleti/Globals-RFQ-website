import * as React from 'react'
import { ShieldCheck } from 'lucide-react'
import { Logo } from '@/components/common/Logo'

interface AuthLayoutProps {
  children: React.ReactNode
}

/**
 * Centered auth card layout — used for login, forgot-password, verify-email.
 * The globe / arcs background comes from globals.css (body::before).
 * Responsive from 360px up, no layout shift.
 */
export function AuthLayout({ children }: AuthLayoutProps) {
  return (
    <div className="flex min-h-dvh flex-col items-center justify-center px-4 py-12">
      <div className="w-full max-w-md">
        {/* Card */}
        <div className="rounded-2xl border border-white/70 bg-white p-8 shadow-[var(--shadow-auth)]">
          {/* Brand header */}
          <div className="mb-5 flex justify-center">
            <Logo className="h-20" />
          </div>

          {children}

          <div className="mt-6 flex items-center justify-center gap-2 border-t border-slate-200 pt-4 text-xs text-[--color-muted-foreground]">
            <ShieldCheck className="size-4 text-brand-900" aria-hidden="true" />
            <span className="h-4 w-px bg-slate-200" aria-hidden="true" />
            <span>Secure access to your workspace</span>
          </div>
        </div>

        {/* Footer */}
        <p className="mt-6 text-center text-xs text-[--color-muted-foreground]">
          &copy; {new Date().getFullYear()} Global Services Solution. All rights reserved.
        </p>
      </div>
    </div>
  )
}
