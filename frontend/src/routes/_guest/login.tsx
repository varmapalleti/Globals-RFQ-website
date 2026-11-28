import { createFileRoute } from '@tanstack/react-router'
import { AuthLayout } from '@/components/layout/AuthLayout'
import { LoginForm } from '@/features/auth/components/LoginForm'
import { toast } from 'sonner'
import * as React from 'react'
import { useNavigate } from '@tanstack/react-router'
import {
  ClipboardCheck,
  Crown,
  Eye,
  Megaphone,
  PackageSearch,
  Shield,
  ArrowRight,
  Sparkles,
} from 'lucide-react'
import { Button } from '@/components/ui/button'
import { isDemoMode } from '@/lib/env'
import { startDemoSession } from '@/features/auth/demoSession'
import { demoRoleOrder, roleTitles } from '@/features/rfq/types'
import type { RoleName } from '@/types/api'

export const Route = createFileRoute('/_guest/login')({
  component: LoginPage,
})

function LoginPage() {
  const navigate = useNavigate()
  // Show session-expired toast if redirected here with that reason
  React.useEffect(() => {
    const params = new URLSearchParams(window.location.search)
    if (params.get('reason') === 'session_expired') {
      toast.warning('Your session expired. Please sign in again.')
    }
  }, [])

  return (
    <AuthLayout>
      <div className="mb-6 space-y-2 text-center">
        <h1 className="text-2xl font-bold tracking-tight text-brand-900">Welcome back</h1>
        <p className="text-sm text-muted-foreground">Sign in to your Global RFQ account</p>
      </div>
      {isDemoMode ? (
        <DemoWorkspacePicker
          onSelect={(role) => {
            startDemoSession(role)
            void navigate({ to: '/dashboard' })
          }}
        />
      ) : (
        <LoginForm />
      )}
    </AuthLayout>
  )
}

const roleIcons: Record<RoleName, typeof Shield> = {
  ADMIN: Shield,
  SUPER_ADMIN: Crown,
  QUOTATION_MANAGER: PackageSearch,
  REVIEWER: ClipboardCheck,
  MARKETING_EXECUTIVE: Megaphone,
  VIEWER: Eye,
}

function DemoWorkspacePicker({ onSelect }: { onSelect: (role: RoleName) => void }) {
  return (
    <section aria-labelledby="demo-heading" className="space-y-4">
      <div className="flex items-start gap-3 rounded-xl border border-brand-100 bg-brand-50 p-4 text-sm text-brand-900">
        <Sparkles className="mt-0.5 size-5 shrink-0 text-red-brand-600" aria-hidden="true" />
        <div>
          <p className="font-semibold">Client presentation mode</p>
          <p className="mt-1 text-brand-900/80">
            Choose a role to preview its RFQ workspace. All sample records are local demo data.
          </p>
        </div>
      </div>
      <div className="grid gap-2 sm:grid-cols-2" aria-labelledby="demo-heading">
        <h2 id="demo-heading" className="sr-only">
          Choose a demo role
        </h2>
        {demoRoleOrder.map((role) => {
          const Icon = roleIcons[role]
          return (
            <Button
              key={role}
              type="button"
              variant="outline"
              className="h-auto justify-between p-3 text-left"
              onClick={() => onSelect(role)}
            >
              <span className="flex items-center gap-3">
                <span className="flex size-9 items-center justify-center rounded-lg bg-brand-50 text-brand-900">
                  <Icon className="size-4" aria-hidden="true" />
                </span>
                <span>
                  <span className="block text-sm font-semibold">{roleTitles[role]}</span>
                  <span className="mt-0.5 block text-xs font-normal text-slate-500">
                    Open role dashboard
                  </span>
                </span>
              </span>
              <ArrowRight className="size-4 shrink-0 text-slate-400" aria-hidden="true" />
            </Button>
          )
        })}
      </div>
      <p className="text-xs leading-5 text-slate-500">
        Demo access is enabled only by the local Vite demo mode. It is disabled in production
        builds.
      </p>
      <details className="rounded-lg border border-brand-100 p-3 text-sm">
        <summary className="cursor-pointer font-semibold text-brand-900">
          Use a live account instead
        </summary>
        <div className="mt-4">
          <LoginForm />
        </div>
      </details>
    </section>
  )
}
