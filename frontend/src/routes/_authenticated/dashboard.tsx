import { createFileRoute, Link } from '@tanstack/react-router'
import { useQuery } from '@tanstack/react-query'
import {
  ArrowRight,
  Clock3,
  FilePlus2,
  FileStack,
  ListChecks,
  ShieldAlert,
  Users,
} from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { AppShell } from '@/components/layout/AppShell'
import { useAuth } from '@/features/auth/hooks/useAuth'
import { RfqTable } from '@/features/rfq/components/RfqTable'
import { StatusPill } from '@/features/rfq/components/StatusPill'
import { useRfqRecords } from '@/features/rfq/hooks/useRfqRecords'
import { roleTitles, type RfqRecord } from '@/features/rfq/types'
import { getRfqDashboardSummary } from '@/features/rfq/api/rfqApi'
import type { RfqStatus } from '@/features/rfq/types'
import { normalizeError, unexpectedErrorMessage } from '@/lib/errors'
import { isDemoSession } from '@/features/auth/demoSession'

export const Route = createFileRoute('/_authenticated/dashboard')({ component: DashboardPage })

function recordsForRole(rfqs: RfqRecord[], role: string, userId: number | undefined) {
  if (role === 'ADMIN' || role === 'SUPER_ADMIN' || role === 'VIEWER') return rfqs
  const assignmentRole =
    role === 'QUOTATION_MANAGER' ? role : role === 'REVIEWER' ? role : 'MARKETING_EXECUTIVE'
  return rfqs.filter((rfq) =>
    rfq.assignments.some(
      (assignment) => assignment.role === assignmentRole && assignment.userId === userId,
    ),
  )
}

function DashboardPage() {
  const { user, mustChangePassword } = useAuth()
  const role = user?.roles.includes('SUPER_ADMIN')
    ? 'SUPER_ADMIN'
    : user?.roles.includes('ADMIN')
      ? 'ADMIN'
      : (user?.roles[0] ?? 'VIEWER')
  const isAdmin = role === 'ADMIN' || role === 'SUPER_ADMIN'
  const demoSession = isDemoSession()
  const liveQueueStatus: RfqStatus | undefined =
    role === 'REVIEWER'
      ? 'PENDING_REVIEW'
      : role === 'MARKETING_EXECUTIVE'
        ? 'ASSIGNED_TO_MARKETING'
        : undefined
  const {
    rfqs: allRfqs,
    isDemo,
    isLoading,
    error,
    refetch,
    totalElements,
  } = useRfqRecords({
    page: 0,
    size: 6,
    status: demoSession ? undefined : liveQueueStatus,
  })
  const rfqs = isDemo ? recordsForRole(allRfqs, role, user?.id) : allRfqs
  const statsQuery = useQuery({
    queryKey: ['rfqs', 'dashboard-stats', role, user?.id],
    enabled: !isDemo,
    staleTime: 15_000,
    retry: false,
    queryFn: getRfqDashboardSummary,
  })
  const statusCounts = isDemo
    ? Object.fromEntries(
        rfqs
          .map((rfq) => rfq.status)
          .reduce((counts, status) => {
            counts.set(status, (counts.get(status) ?? 0) + 1)
            return counts
          }, new Map<string, number>()),
      )
    : (statsQuery.data?.statusCounts ?? {})
  const statusCount = (status: RfqStatus) => Number(statusCounts[status] ?? 0)
  const rfqTotal = isDemo ? rfqs.length : (statsQuery.data?.totalRfqs ?? totalElements)
  const recentActivity = isDemo
    ? rfqs
        .flatMap((rfq) => rfq.events.slice(0, 1).map((event) => ({ rfq, event })))
        .sort((a, b) => b.event.createdAt.localeCompare(a.event.createdAt))
        .slice(0, 5)
    : []
  const demoActiveQueue =
    role === 'REVIEWER'
      ? rfqs.filter((rfq) => rfq.status === 'PENDING_REVIEW')
      : role === 'QUOTATION_MANAGER'
        ? rfqs.filter((rfq) =>
            [
              'ASSIGNED_TO_QUOTATION_MANAGER',
              'QUOTATION_IN_PROGRESS',
              'REJECTED_BY_REVIEWER',
              'REVISION_IN_PROGRESS',
              'REOPENED_FOR_REVISION',
            ].includes(rfq.status),
          )
        : role === 'MARKETING_EXECUTIVE'
          ? rfqs.filter((rfq) => rfq.status === 'ASSIGNED_TO_MARKETING')
          : rfqs.filter((rfq) =>
              [
                'IMPORTED',
                'APPROVED_BY_REVIEWER',
                'SENT_TO_CUSTOMER',
                'CUSTOMER_APPROVED',
                'ESCALATED',
              ].includes(rfq.status),
            )
  const metrics = isAdmin
    ? [
        {
          label: 'Total RFQs',
          value: rfqTotal,
          caption: 'Across every workflow stage',
          icon: FileStack,
          color: 'blue',
        },
        {
          label: 'Pending review',
          value: statusCount('PENDING_REVIEW'),
          caption: 'Waiting on a reviewer',
          icon: ListChecks,
          color: 'amber',
        },
        {
          label: 'Needs admin action',
          value:
            statusCount('APPROVED_BY_REVIEWER') +
            statusCount('SENT_TO_CUSTOMER') +
            statusCount('CUSTOMER_APPROVED') +
            statusCount('ESCALATED'),
          caption: 'Customer or workflow decision',
          icon: Clock3,
          color: 'violet',
        },
        {
          label: 'Escalated',
          value: statusCount('ESCALATED'),
          caption: 'At rejection policy threshold',
          icon: ShieldAlert,
          color: 'red',
        },
      ]
    : role === 'QUOTATION_MANAGER'
      ? [
          {
            label: 'My assigned RFQs',
            value: rfqTotal,
            caption: 'Requests currently assigned to you',
            icon: FileStack,
            color: 'blue',
          },
          {
            label: 'Needs my work',
            value:
              statusCount('ASSIGNED_TO_QUOTATION_MANAGER') +
              statusCount('QUOTATION_IN_PROGRESS') +
              statusCount('REJECTED_BY_REVIEWER') +
              statusCount('REVISION_IN_PROGRESS') +
              statusCount('REOPENED_FOR_REVISION'),
            caption: 'Draft or revision in progress',
            icon: Clock3,
            color: 'amber',
          },
          {
            label: 'Under review',
            value: statusCount('PENDING_REVIEW'),
            caption: 'Submitted quotation versions',
            icon: ListChecks,
            color: 'violet',
          },
          {
            label: 'Approved',
            value:
              statusCount('APPROVED_BY_REVIEWER') +
              statusCount('SENT_TO_CUSTOMER') +
              statusCount('CUSTOMER_APPROVED') +
              statusCount('ASSIGNED_TO_MARKETING'),
            caption: 'Passed internal review',
            icon: ShieldAlert,
            color: 'green',
          },
        ]
      : role === 'REVIEWER'
        ? [
            {
              label: 'Review queue',
              value: statusCount('PENDING_REVIEW'),
              caption: 'Quotations awaiting your decision',
              icon: ListChecks,
              color: 'amber',
            },
            {
              label: 'Assigned to me',
              value: rfqTotal,
              caption: 'RFQs in your review scope',
              icon: FileStack,
              color: 'blue',
            },
            {
              label: 'Approved',
              value: statusCount('APPROVED_BY_REVIEWER'),
              caption: 'Passed internal review',
              icon: ShieldAlert,
              color: 'green',
            },
            {
              label: 'Escalated',
              value: statusCount('ESCALATED'),
              caption: 'Admin decision required',
              icon: Clock3,
              color: 'red',
            },
          ]
        : role === 'MARKETING_EXECUTIVE'
          ? [
              {
                label: 'Assigned to me',
                value: rfqTotal,
                caption: 'Customer-approved RFQs',
                icon: Users,
                color: 'blue',
              },
              {
                label: 'Ready to progress',
                value: statusCount('ASSIGNED_TO_MARKETING'),
                caption: 'Assigned marketing work',
                icon: ListChecks,
                color: 'teal',
              },
              {
                label: 'Customer approved',
                value: statusCount('CUSTOMER_APPROVED'),
                caption: 'Approved by the customer',
                icon: ShieldAlert,
                color: 'green',
              },
            ]
          : [
              {
                label: 'Visible RFQs',
                value: rfqTotal,
                caption: 'Read-only portfolio view',
                icon: FileStack,
                color: 'blue',
              },
              {
                label: 'Under review',
                value: statusCount('PENDING_REVIEW'),
                caption: 'Internal review stage',
                icon: ListChecks,
                color: 'amber',
              },
              {
                label: 'Awaiting customer',
                value: statusCount('SENT_TO_CUSTOMER'),
                caption: 'Response not yet recorded',
                icon: Clock3,
                color: 'violet',
              },
            ]

  const subtitle = isAdmin
    ? 'Monitor ownership, customer responses, and workflow exceptions across the RFQ lifecycle.'
    : role === 'QUOTATION_MANAGER'
      ? 'Prepare accurate quotations, manage revisions, and track your submissions.'
      : role === 'REVIEWER'
        ? 'Review submitted quotation versions and record clear, traceable decisions.'
        : role === 'MARKETING_EXECUTIVE'
          ? 'View customer-approved work assigned to your marketing queue.'
          : 'Review RFQ progress and status across the portfolio.'

  return (
    <AppShell>
      {mustChangePassword && (
        <div
          className="mb-5 rounded-lg border border-amber-200 bg-amber-50 p-4 text-sm text-amber-900"
          role="alert"
        >
          <strong>Password change required.</strong> Contact an administrator to update your account
          before using this workspace.
        </div>
      )}
      <div className="mb-6 flex flex-wrap items-end justify-between gap-4">
        <div>
          <p className="text-xs font-semibold tracking-[0.15em] text-red-brand-600 uppercase">
            {roleTitles[role]}
          </p>
          <h1 className="mt-1 text-2xl font-bold tracking-tight text-brand-900 sm:text-[28px]">
            Good day, {user?.fullName?.split(' ')[0] ?? 'there'}
          </h1>
          <p className="mt-1.5 max-w-2xl text-sm text-slate-600">{subtitle}</p>
        </div>
        <div className="flex gap-2">
          {isAdmin && (
            <Button asChild size="sm">
              <Link to="/rfqs/import">
                <FilePlus2 className="size-4" aria-hidden="true" /> Import RFQ
              </Link>
            </Button>
          )}
          <Button asChild variant="outline" size="sm">
            <Link to="/rfqs">
              Open RFQ workspace
              <ArrowRight className="size-4" aria-hidden="true" />
            </Link>
          </Button>
        </div>
      </div>

      <section aria-label="RFQ metrics" className="mb-6 grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
        {metrics.map((metric) => {
          const Icon = metric.icon
          const colorMap: Record<string, string> = {
            blue: 'bg-brand-50 text-brand-900',
            amber: 'bg-amber-50 text-amber-700',
            violet: 'bg-violet-50 text-violet-700',
            red: 'bg-red-brand-50 text-red-brand-600',
            green: 'bg-green-50 text-green-700',
            teal: 'bg-teal-50 text-teal-700',
          }
          return (
            <Card key={metric.label} className="p-4 sm:p-5">
              <div className="flex items-start justify-between">
                <div>
                  <p className="text-sm font-medium text-slate-600">{metric.label}</p>
                  <p className="mt-2 text-3xl font-bold tracking-tight text-brand-900">
                    {!isDemo && statsQuery.isLoading
                      ? '…'
                      : !isDemo && statsQuery.error
                        ? '—'
                        : metric.value}
                  </p>
                </div>
                <span
                  className={`flex size-10 items-center justify-center rounded-xl ${colorMap[metric.color]}`}
                >
                  <Icon className="size-5" aria-hidden="true" />
                </span>
              </div>
              <p className="mt-3 text-xs text-slate-500">{metric.caption}</p>
            </Card>
          )
        })}
      </section>
      {statsQuery.error && !isDemo && (
        <div
          className="mb-5 flex flex-wrap items-center justify-between gap-3 rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-900"
          role="alert"
        >
          <span>
            Dashboard counts could not be loaded:{' '}
            {unexpectedErrorMessage(normalizeError(statsQuery.error))}
          </span>
          <Button size="sm" variant="outline" onClick={() => void statsQuery.refetch()}>
            Retry counts
          </Button>
        </div>
      )}

      <div className="grid gap-5 xl:grid-cols-[minmax(0,1fr)_320px]">
        <section
          className="overflow-hidden rounded-xl border border-brand-100 bg-white shadow-[var(--shadow-card)]"
          aria-labelledby="work-queue-title"
        >
          <div className="flex flex-wrap items-center justify-between gap-3 px-5 py-4">
            <div>
              <h2 id="work-queue-title" className="text-base font-semibold text-brand-900">
                {isDemo
                  ? isAdmin
                    ? 'Priority work queue'
                    : role === 'REVIEWER'
                      ? 'Review queue'
                      : role === 'MARKETING_EXECUTIVE'
                        ? 'Marketing assignments'
                        : role === 'QUOTATION_MANAGER'
                          ? 'My active quotations'
                          : 'Recent RFQs'
                  : role === 'REVIEWER'
                    ? 'Review queue'
                    : role === 'MARKETING_EXECUTIVE'
                      ? 'Marketing assignments'
                      : role === 'QUOTATION_MANAGER'
                        ? 'My assigned RFQs'
                        : 'Recent RFQs'}
              </h2>
              <p className="mt-1 text-xs text-slate-500">
                {isDemo
                  ? `${demoActiveQueue.length} item${demoActiveQueue.length === 1 ? '' : 's'} requiring attention in this demo view`
                  : `Showing ${rfqs.length} of ${totalElements} request${totalElements === 1 ? '' : 's'} in your access scope`}
              </p>
            </div>
            <Button variant="ghost" size="sm" asChild>
              <Link to="/rfqs">
                View all
                <ArrowRight className="size-4" aria-hidden="true" />
              </Link>
            </Button>
          </div>
          {error ? (
            <div className="p-6 text-sm text-red-brand-700" role="alert">
              <p>{unexpectedErrorMessage(normalizeError(error))}</p>
              <Button className="mt-3" variant="outline" size="sm" onClick={() => void refetch()}>
                Retry
              </Button>
            </div>
          ) : isLoading ? (
            <div className="p-8 text-center text-sm text-slate-500" role="status">
              Loading your RFQ workspace…
            </div>
          ) : (
            <RfqTable
              rfqs={isDemo ? demoActiveQueue.slice(0, 6) : rfqs}
              compact
              emptyMessage="New assignments and review work will appear here."
            />
          )}
        </section>

        <section
          className="rounded-xl border border-brand-100 bg-white p-5 shadow-[var(--shadow-card)]"
          aria-labelledby="activity-title"
        >
          <div className="flex items-center justify-between">
            <div>
              <h2 id="activity-title" className="text-base font-semibold text-brand-900">
                Recent activity
              </h2>
              <p className="mt-1 text-xs text-slate-500">Latest recorded workflow events</p>
            </div>
            <span className="rounded-full bg-brand-50 px-2 py-1 text-[10px] font-bold tracking-wide text-brand-900 uppercase">
              Activity
            </span>
          </div>
          <div className="mt-5 space-y-5">
            {recentActivity.length ? (
              recentActivity.map(({ rfq, event }) => (
                <div key={`${rfq.id}-${event.id}`} className="flex gap-3">
                  <span
                    className={`mt-1.5 size-2 shrink-0 rounded-full ${event.tone === 'red' ? 'bg-red-brand-600' : event.tone === 'green' ? 'bg-emerald-500' : event.tone === 'amber' ? 'bg-amber-500' : 'bg-brand-600'}`}
                  />
                  <div className="min-w-0 flex-1">
                    <div className="flex items-start justify-between gap-2">
                      <p className="text-sm leading-5 font-medium text-slate-800">{event.label}</p>
                      <StatusPill status={rfq.status} />
                    </div>
                    <p className="mt-1 truncate text-xs text-slate-500">
                      {rfq.sourceRfqId} · {event.actor}
                    </p>
                    <time
                      className="mt-1 block text-[11px] text-slate-400"
                      dateTime={event.createdAt}
                    >
                      {new Intl.DateTimeFormat('en-IN', {
                        day: '2-digit',
                        month: 'short',
                        hour: '2-digit',
                        minute: '2-digit',
                      }).format(new Date(event.createdAt))}
                    </time>
                  </div>
                </div>
              ))
            ) : (
              <p className="rounded-lg bg-brand-50/60 px-4 py-6 text-sm text-slate-500">
                {isDemo
                  ? 'No activity is available in this demo view yet.'
                  : 'Activity history is available within each RFQ record.'}
              </p>
            )}
          </div>
          <div className="mt-5 border-t border-brand-100 pt-4">
            <p className="text-xs leading-5 text-slate-500">
              {isDemo
                ? 'Events shown here are sample records for this presentation.'
                : 'Detailed event history is available from each RFQ record.'}
            </p>
          </div>
        </section>
      </div>
    </AppShell>
  )
}
