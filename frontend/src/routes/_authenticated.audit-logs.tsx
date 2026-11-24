import { createFileRoute } from '@tanstack/react-router'
import { useQuery } from '@tanstack/react-query'
import { ChevronLeft, ChevronRight, ClipboardList, FilterX, Search } from 'lucide-react'
import * as React from 'react'
import { AppShell } from '@/components/layout/AppShell'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { useAuth } from '@/features/auth/hooks/useAuth'
import { getIdentityAudit } from '@/features/auth/api/userAdminApi'

export const Route = createFileRoute('/_authenticated/audit-logs')({ component: AuditLogsPage })

const eventTypes = [
  'LOGIN_SUCCESS',
  'LOGIN_FAILED',
  'ACCOUNT_LOCKED',
  'LOGOUT',
  'TOKEN_REFRESHED',
  'TOKEN_REUSE_DETECTED',
  'USER_CREATED',
  'USER_ROLES_CHANGED',
  'USER_STATUS_CHANGED',
  'EMAIL_VERIFICATION_SENT',
  'EMAIL_VERIFIED',
  'PASSWORD_RESET_OTP_SENT',
  'PASSWORD_RESET_OTP_VERIFIED',
  'PASSWORD_RESET_OTP_FAILED',
  'PASSWORD_RESET_RATE_LIMITED',
  'PASSWORD_RESET_SUCCESS',
  'PERSONAL_EMAIL_CHANGED',
  'INVITE_ACCEPTED',
  'PASSWORD_CHANGE_SUCCESS',
  'USER_PROFILE_UPDATED',
  'USER_INVITE_SENT',
  'USER_UNLOCKED',
  'AUDIT_LOGS_VIEWED',
]

interface AuditFilters {
  from: string
  to: string
  eventType: string
  userId: string
}

const emptyFilters: AuditFilters = { from: '', to: '', eventType: '', userId: '' }

function dateStart(value: string) {
  return value ? new Date(`${value}T00:00:00`).toISOString() : undefined
}

function dateEnd(value: string) {
  return value ? new Date(`${value}T23:59:59.999`).toISOString() : undefined
}

function AuditLogsPage() {
  const { hasAnyRole } = useAuth()
  const [draft, setDraft] = React.useState<AuditFilters>(emptyFilters)
  const [filters, setFilters] = React.useState<AuditFilters>(emptyFilters)
  const [page, setPage] = React.useState(0)
  const [pageSize, setPageSize] = React.useState(10)
  const isSuperAdmin = hasAnyRole(['SUPER_ADMIN'])
  const parsedUserId = filters.userId ? Number(filters.userId) : undefined
  const invalidUserId =
    filters.userId !== '' && (!Number.isInteger(parsedUserId) || parsedUserId! <= 0)
  const invalidDateRange = Boolean(filters.from && filters.to && filters.from > filters.to)

  const query = useQuery({
    queryKey: ['identity', 'audit', filters, page, pageSize],
    queryFn: () =>
      getIdentityAudit({
        page,
        size: pageSize,
        userId: parsedUserId,
        from: dateStart(filters.from),
        to: dateEnd(filters.to),
        eventType: filters.eventType || undefined,
      }),
    enabled: isSuperAdmin && !invalidDateRange && !invalidUserId,
    retry: false,
  })

  function applyFilters(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setFilters(draft)
    setPage(0)
  }

  function clearFilters() {
    setDraft(emptyFilters)
    setFilters(emptyFilters)
    setPage(0)
  }

  const result = query.data
  const rows = result?.content ?? []
  const totalElements = result?.totalElements ?? 0
  const totalPages = result?.totalPages ?? 0

  return (
    <AppShell>
      {!isSuperAdmin ? (
        <Card className="mx-auto max-w-xl p-8 text-center shadow-none">
          <ClipboardList className="mx-auto size-8 text-red-600" aria-hidden="true" />
          <h1 className="mt-3 text-xl font-bold">Super Admin access required</h1>
          <p className="mt-2 text-sm text-slate-500">
            Identity audit records contain sensitive security activity and are restricted to Super
            Admins.
          </p>
        </Card>
      ) : (
        <>
          <div className="mb-6">
            <p className="text-xs font-semibold tracking-[0.15em] text-blue-700 uppercase">
              Identity and access
            </p>
            <h1 className="mt-1 text-2xl font-bold tracking-tight">Audit Logs</h1>
            <p className="mt-1.5 text-sm text-slate-500">
              Read-only security events recorded by Identity Service.
            </p>
          </div>

          <Card className="mb-5 border-slate-200 p-4 shadow-none">
            <form
              onSubmit={applyFilters}
              className="grid gap-3 sm:grid-cols-2 xl:grid-cols-[1fr_1.2fr_1fr_1fr_auto_auto] xl:items-end"
            >
              <Field label="From date">
                <Input
                  type="date"
                  value={draft.from}
                  onChange={(event) =>
                    setDraft((current) => ({ ...current, from: event.target.value }))
                  }
                />
              </Field>
              <Field label="To date">
                <Input
                  type="date"
                  value={draft.to}
                  onChange={(event) =>
                    setDraft((current) => ({ ...current, to: event.target.value }))
                  }
                />
              </Field>
              <Field label="Event">
                <select
                  value={draft.eventType}
                  onChange={(event) =>
                    setDraft((current) => ({ ...current, eventType: event.target.value }))
                  }
                  className="h-10 w-full rounded-md border border-slate-200 bg-white px-3 text-sm outline-none focus-visible:ring-2 focus-visible:ring-blue-500"
                >
                  <option value="">All events</option>
                  {eventTypes.map((eventType) => (
                    <option key={eventType} value={eventType}>
                      {eventType.replaceAll('_', ' ')}
                    </option>
                  ))}
                </select>
              </Field>
              <Field label="Actor or target user ID">
                <Input
                  inputMode="numeric"
                  value={draft.userId}
                  onChange={(event) =>
                    setDraft((current) => ({ ...current, userId: event.target.value }))
                  }
                  placeholder="Any user"
                />
              </Field>
              <Button type="submit" className="w-full xl:w-auto">
                <Search className="size-4" /> Apply
              </Button>
              <Button
                type="button"
                variant="outline"
                onClick={clearFilters}
                className="w-full xl:w-auto"
              >
                <FilterX className="size-4" /> Clear
              </Button>
            </form>
            {invalidDateRange && (
              <p className="mt-2 text-sm text-red-700" role="alert">
                The start date must be on or before the end date.
              </p>
            )}
            {invalidUserId && (
              <p className="mt-2 text-sm text-red-700" role="alert">
                Enter a positive whole number for the user ID.
              </p>
            )}
          </Card>

          <Card className="overflow-hidden border-slate-200 shadow-none">
            {query.isLoading ? (
              <div className="p-10 text-center text-sm text-slate-500" role="status">
                Loading audit records…
              </div>
            ) : query.error ? (
              <div className="p-6 text-sm text-red-800" role="alert">
                <p className="font-semibold">Could not load audit logs.</p>
                <p className="mt-1">
                  Check Identity Service availability and your Super Admin session.
                </p>
                <Button
                  className="mt-3"
                  size="sm"
                  variant="outline"
                  onClick={() => void query.refetch()}
                >
                  Retry
                </Button>
              </div>
            ) : rows.length ? (
              <div className="overflow-x-auto">
                <table className="w-full min-w-[900px] border-collapse text-left">
                  <thead>
                    <tr className="border-b border-slate-200 text-xs text-slate-600">
                      <th scope="col" className="px-5 py-4 font-medium">
                        Timestamp
                      </th>
                      <th scope="col" className="px-5 py-4 font-medium">
                        User
                      </th>
                      <th scope="col" className="px-5 py-4 font-medium">
                        Action
                      </th>
                      <th scope="col" className="px-5 py-4 font-medium">
                        Outcome
                      </th>
                      <th scope="col" className="px-5 py-4 font-medium">
                        IP address
                      </th>
                      <th scope="col" className="px-5 py-4 font-medium">
                        Details
                      </th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100">
                    {rows.map((event) => (
                      <tr key={event.id} className="align-top hover:bg-slate-50">
                        <td className="px-5 py-4 text-sm whitespace-nowrap text-slate-600">
                          {new Intl.DateTimeFormat('en-IN', {
                            dateStyle: 'medium',
                            timeStyle: 'medium',
                          }).format(new Date(event.createdAt))}
                        </td>
                        <td className="px-5 py-4 text-sm">
                          <p>Actor: {event.actorUserId ?? 'System'}</p>
                          <p className="mt-1 text-xs text-slate-500">
                            Target: {event.targetUserId ?? '—'}
                          </p>
                        </td>
                        <td className="px-5 py-4 text-sm font-semibold text-blue-800">
                          {event.eventType.replaceAll('_', ' ')}
                          <p className="mt-1 text-xs font-normal text-slate-500">{event.action}</p>
                        </td>
                        <td className="px-5 py-4">
                          <span
                            className={`rounded-full px-2.5 py-1 text-xs font-semibold ${event.outcome === 'SUCCESS' ? 'bg-emerald-50 text-emerald-800' : 'bg-red-50 text-red-800'}`}
                          >
                            {event.outcome}
                          </span>
                        </td>
                        <td className="px-5 py-4 text-sm text-slate-600">
                          {event.ipAddress ?? '—'}
                        </td>
                        <td className="px-5 py-4 text-xs text-slate-600">
                          <details>
                            <summary className="cursor-pointer font-medium text-blue-800">
                              View
                            </summary>
                            <dl className="mt-2 max-w-xs space-y-1 break-all">
                              <div>
                                <dt className="inline font-semibold">Correlation: </dt>
                                <dd className="inline">{event.correlationId ?? '—'}</dd>
                              </div>
                              <div>
                                <dt className="inline font-semibold">User agent: </dt>
                                <dd className="inline">{event.userAgent ?? '—'}</dd>
                              </div>
                              {event.metadata && (
                                <div>
                                  <dt className="font-semibold">Metadata</dt>
                                  <dd>
                                    <pre className="mt-1 whitespace-pre-wrap">
                                      {JSON.stringify(event.metadata, null, 2)}
                                    </pre>
                                  </dd>
                                </div>
                              )}
                            </dl>
                          </details>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            ) : (
              <div className="px-6 py-16 text-center">
                <ClipboardList className="mx-auto size-8 text-slate-300" aria-hidden="true" />
                <p className="mt-3 font-semibold">No audit records found</p>
                <p className="mt-1 text-sm text-slate-500">Try clearing one or more filters.</p>
              </div>
            )}
            {!query.isLoading && !query.error && (
              <div className="flex flex-wrap items-center justify-between gap-3 border-t border-slate-100 px-5 py-3 text-sm text-slate-600">
                <label className="flex items-center gap-2">
                  Rows per page
                  <select
                    value={pageSize}
                    onChange={(event) => {
                      setPageSize(Number(event.target.value))
                      setPage(0)
                    }}
                    className="h-9 rounded-md border border-slate-200 bg-white px-2 text-sm"
                  >
                    {[10, 20, 50, 100].map((size) => (
                      <option key={size} value={size}>
                        {size}
                      </option>
                    ))}
                  </select>
                </label>
                <div className="flex items-center gap-3">
                  <span>
                    {totalElements === 0 ? 0 : page * pageSize + 1}–
                    {Math.min((page + 1) * pageSize, totalElements)} of {totalElements}
                  </span>
                  <Button
                    size="icon"
                    variant="ghost"
                    aria-label="Previous page"
                    disabled={page === 0}
                    onClick={() => setPage((current) => Math.max(0, current - 1))}
                  >
                    <ChevronLeft />
                  </Button>
                  <span>
                    Page {totalPages === 0 ? 0 : page + 1} of {totalPages}
                  </span>
                  <Button
                    size="icon"
                    variant="ghost"
                    aria-label="Next page"
                    disabled={page + 1 >= totalPages}
                    onClick={() => setPage((current) => Math.min(totalPages - 1, current + 1))}
                  >
                    <ChevronRight />
                  </Button>
                </div>
              </div>
            )}
          </Card>
        </>
      )}
    </AppShell>
  )
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <label className="block space-y-1.5 text-xs font-medium text-slate-700">
      <span>{label}</span>
      {children}
    </label>
  )
}
