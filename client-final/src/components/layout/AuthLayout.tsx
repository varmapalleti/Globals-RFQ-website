import * as React from 'react'
import { ShieldCheck } from 'lucide-react'
import { Logo } from '@/components/common/Logo'
import { brand } from '@/config/brand'
import { cn } from '@/lib/utils'
import { BackgroundDecoration } from '@/components/layout/BackgroundDecoration'

interface AuthLayoutProps {
  children: React.ReactNode
  /** Tailwind max-width class for the card */
  maxWidth?: string
}

/**
 * The white card shared by every guest page (login, forgot-password, verify-email).
 *
 * Layout rules (same as the GSS reference design):
 *  - the page is exactly one viewport tall (h-dvh) and never scrolls
 *  - the card is centred; its padding, logo and spacing scale with the viewport
 *    height (clamp + vh) so the content fits without scrolling
 *  - safety net: on an extremely short window the card scrolls internally
 *    instead of pushing the page down
 */
export function AuthLayout({ children, maxWidth = 'max-w-[420px]' }: AuthLayoutProps) {
  return (
    <div className="bg-brand-surface relative flex h-dvh w-full flex-col overflow-hidden">
      <BackgroundDecoration />

      <main className="relative z-10 flex min-h-0 flex-1 items-center justify-center px-4 py-[clamp(10px,1.6vh,22px)]">
        <div
          className={cn(
            'shadow-auth flex max-h-full w-full flex-col overflow-y-auto rounded-2xl border border-white/80 bg-white p-[clamp(18px,2.6vh,28px)]',
            maxWidth,
          )}
        >
          {/* the logo grows with the window height, capped so the card always fits */}
          <Logo className="mx-auto mb-[clamp(8px,1.2vh,14px)] h-[clamp(56px,10vh,110px)]" />

          {children}

          <div className="border-brand-border/70 mt-[clamp(12px,1.8vh,18px)] border-t pt-[clamp(10px,1.4vh,14px)]">
            <SecureFooter />
          </div>
        </div>
      </main>
    </div>
  )
}

interface AuthHeadingProps {
  title: string
  subtitle?: string
}

/** Centred navy title + muted subtitle shown at the top of every auth card. */
export function AuthHeading({ title, subtitle }: AuthHeadingProps) {
  return (
    <div className="mb-[clamp(12px,1.8vh,18px)] text-center">
      <h1 className="text-brand-primary text-2xl font-bold tracking-tight">{title}</h1>
      {subtitle && <p className="text-brand-muted mt-1.5 text-sm text-balance">{subtitle}</p>}
    </div>
  )
}

function SecureFooter() {
  return (
    <div className="space-y-1.5 text-center">
      <p className="text-brand-muted flex items-center justify-center gap-2.5 text-sm">
        <ShieldCheck className="text-brand-primary h-5 w-5" aria-hidden="true" />
        <span className="bg-brand-border h-4 w-px" aria-hidden="true" />
        {brand.tagline}
      </p>
      <p className="text-brand-muted/80 text-xs">
        &copy; {new Date().getFullYear()} {brand.name}. All rights reserved.
      </p>
    </div>
  )
}
