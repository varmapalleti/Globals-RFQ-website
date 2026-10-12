import { createFileRoute } from '@tanstack/react-router'
import { AppShell } from '@/components/layout/AppShell'
import { useAuth } from '@/features/auth/hooks/useAuth'
import { Badge } from '@/components/ui/badge'
import { Can } from '@/components/common/Can'
import { AlertCircle, User, Shield, Mail, CheckCircle } from 'lucide-react'
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card'

export const Route = createFileRoute('/_authenticated/dashboard')({
  component: DashboardPage,
})

function DashboardPage() {
  const { user, mustChangePassword } = useAuth()

  return (
    <AppShell>
      {/* Must-change-password notice */}
      {mustChangePassword && (
        <div
          role="alert"
          className="mb-6 flex items-start gap-3 rounded-lg border border-amber-200 bg-amber-50 p-4 text-sm text-amber-800"
        >
          <AlertCircle className="mt-0.5 h-4 w-4 shrink-0 text-amber-500" aria-hidden="true" />
          <div>
            <p className="font-semibold">Password change required</p>
            <p className="mt-0.5">
              Your account requires a password change. The change-password API is not yet available
              — please contact an administrator or wait for the next backend release.
            </p>
          </div>
        </div>
      )}

      <div className="space-y-6">
        <div>
          <h1 className="text-2xl font-bold tracking-tight">Dashboard</h1>
          <p className="text-[--color-muted-foreground]">
            Welcome back, {user?.fullName ?? 'User'}!
          </p>
        </div>

        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {/* Account Info */}
          <Card>
            <CardHeader className="pb-3">
              <div className="flex items-center gap-2">
                <User className="h-4 w-4 text-[--color-primary]" aria-hidden="true" />
                <CardTitle className="text-base">Account</CardTitle>
              </div>
            </CardHeader>
            <CardContent className="space-y-2 text-sm">
              <div>
                <span className="text-[--color-muted-foreground]">Name</span>
                <p className="font-medium">{user?.fullName}</p>
              </div>
              <div>
                <span className="text-[--color-muted-foreground]">Official email</span>
                <p className="font-medium">{user?.officialEmail}</p>
              </div>
              <div>
                <span className="text-[--color-muted-foreground]">Status</span>
                <p>
                  <Badge variant={user?.status === 'ACTIVE' ? 'success' : 'secondary'}>
                    {user?.status}
                  </Badge>
                </p>
              </div>
            </CardContent>
          </Card>

          {/* Email Verification */}
          <Card>
            <CardHeader className="pb-3">
              <div className="flex items-center gap-2">
                <Mail className="h-4 w-4 text-[--color-primary]" aria-hidden="true" />
                <CardTitle className="text-base">Email Status</CardTitle>
              </div>
            </CardHeader>
            <CardContent className="space-y-2 text-sm">
              <div className="flex items-center justify-between">
                <span className="text-[--color-muted-foreground]">Official</span>
                {user?.officialEmailVerified ? (
                  <span className="flex items-center gap-1 font-medium text-green-600">
                    <CheckCircle className="h-3.5 w-3.5" aria-hidden="true" />
                    Verified
                  </span>
                ) : (
                  <Badge variant="outline">Unverified</Badge>
                )}
              </div>
              <div className="flex items-center justify-between">
                <span className="text-[--color-muted-foreground]">Personal</span>
                {user?.personalEmailVerified ? (
                  <span className="flex items-center gap-1 font-medium text-green-600">
                    <CheckCircle className="h-3.5 w-3.5" aria-hidden="true" />
                    Verified
                  </span>
                ) : (
                  <Badge variant="outline">Unverified</Badge>
                )}
              </div>
            </CardContent>
          </Card>

          {/* Roles */}
          <Card>
            <CardHeader className="pb-3">
              <div className="flex items-center gap-2">
                <Shield className="h-4 w-4 text-[--color-primary]" aria-hidden="true" />
                <CardTitle className="text-base">Roles</CardTitle>
              </div>
              <CardDescription>Your assigned permissions</CardDescription>
            </CardHeader>
            <CardContent>
              {user?.roles.length ? (
                <div className="flex flex-wrap gap-1.5">
                  {user.roles.map((role) => (
                    <Badge key={role} variant="secondary" className="text-xs">
                      {role.replace(/_/g, ' ')}
                    </Badge>
                  ))}
                </div>
              ) : (
                <p className="text-sm text-[--color-muted-foreground]">No roles assigned</p>
              )}
            </CardContent>
          </Card>
        </div>

        {/* Admin-only section demo */}
        <Can roles={['SUPER_ADMIN', 'ADMIN']}>
          <Card className="border-blue-100 bg-blue-50">
            <CardHeader>
              <CardTitle className="text-base text-blue-800">Admin Area</CardTitle>
              <CardDescription className="text-blue-600">
                Visible to SUPER_ADMIN and ADMIN roles only. User management and system
                configuration APIs are not yet implemented.
              </CardDescription>
            </CardHeader>
          </Card>
        </Can>
      </div>
    </AppShell>
  )
}
