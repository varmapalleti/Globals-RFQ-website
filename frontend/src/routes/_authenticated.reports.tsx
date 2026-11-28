import { createFileRoute } from '@tanstack/react-router'
import { useQuery } from '@tanstack/react-query'
import {
  BarChart3,
  CalendarRange,
  Download,
  FileSpreadsheet,
  Printer,
  TrendingUp,
} from 'lucide-react'
import * as React from 'react'
import { toast } from 'sonner'
import { AppShell } from '@/components/layout/AppShell'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { useAuth } from '@/features/auth/hooks/useAuth'
import { downloadRfqReport, getReportSummary } from '@/features/rfq/api/rfqApi'
import { useRfqRecords } from '@/features/rfq/hooks/useRfqRecords'
import { statusLabels, type RfqStatus } from '@/features/rfq/types'
import { isDemoSession } from '@/features/auth/demoSession'
import { normalizeError, unexpectedErrorMessage } from '@/lib/errors'

export const Route = createFileRoute('/_authenticated/reports')({ component: ReportsPage })

const orderedStatuses = Object.keys(statusLabels) as RfqStatus[]
const stageGroups = [
  { label: 'Intake & assignment', statuses: ['IMPORTED', 'ASSIGNED_TO_QUOTATION_MANAGER'] },
  {
    label: 'Quotation preparation',
    statuses: ['QUOTATION_IN_PROGRESS', 'REJECTED_BY_REVIEWER', 'REVISION_IN_PROGRESS', 'REOPENED_FOR_REVISION'],
  },
  { label: 'Internal review', statuses: ['PENDING_REVIEW', 'APPROVED_BY_REVIEWER', 'AWAITING_REASSIGNMENT', 'ESCALATED'] },
  {
    label: 'Customer response',
    statuses: ['SENT_TO_CUSTOMER', 'CUSTOMER_APPROVED', 'CUSTOMER_REJECTED'],
  },
  {
    label: 'Marketing & closure',
    statuses: ['READY_FOR_MARKETING', 'ASSIGNED_TO_MARKETING', 'CLOSED', 'CANCELLED'],
  },
]

function ReportsPage() {
  const { user } = useAuth()
  const isAdmin = user?.roles.some((role) => role === 'ADMIN' || role === 'SUPER_ADMIN') ?? false
  const demo = isDemoSession()
  const { rfqs } = useRfqRecords({ size: 1, enabled: demo })
  const reportQuery = useQuery({
    queryKey: ['rfqs', 'report-summary'],
    queryFn: getReportSummary,
    enabled: isAdmin && !demo,
    staleTime: 30_000,
  })
  const statusCounts = demo
    ? Object.fromEntries(
        orderedStatuses.map((status) => [
          status,
          rfqs.filter((rfq) => rfq.status === status).length,
        ]),
      )
    : (reportQuery.data?.statusCounts ?? {})
  const total = demo ? rfqs.length : (reportQuery.data?.totalRfqs ?? 0)
  const decisionCounts = demo
    ? {
        PENDING: rfqs.filter((rfq) => rfq.customerDecision === 'PENDING').length,
        APPROVED: rfqs.filter((rfq) => rfq.customerDecision === 'APPROVED').length,
        REJECTED: rfqs.filter((rfq) => rfq.customerDecision === 'REJECTED').length,
      }
    : (reportQuery.data?.customerDecisionCounts ?? {})
  const demoWorkload = demo
    ? ['QUOTATION_MANAGER', 'REVIEWER', 'MARKETING_EXECUTIVE'].map((role) => ({
        role,
        amount: rfqs.filter((rfq) => rfq.assignments.some((assignment) => assignment.role === role))
          .length,
      }))
    : []

  async function exportWorkbook() {
    if (demo) {
      const header = [
        'RFQ Number',
        'Customer',
        'Request',
        'Status',
        'Customer Decision',
        'Delivery Date',
        'Quotation Manager',
      ]
      const rows = rfqs.map((rfq) => [
        rfq.sourceRfqId,
        rfq.customerName,
        rfq.requestTitle,
        rfq.status,
        rfq.customerDecision,
        rfq.requestedDeliveryDate,
        rfq.assignments.find((a) => a.role === 'QUOTATION_MANAGER')?.userName ?? '',
      ])
      const csv = [header, ...rows]
        .map((row) => row.map((value) => `"${String(value).replaceAll('"', '""')}"`).join(','))
        .join('\r\n')
      saveBlob(new Blob([csv], { type: 'text/csv;charset=utf-8' }), 'global-rfq-demo-report.csv')
      toast.success('Demo CSV report downloaded.')
      return
    }
    try {
      const { blob, disposition } = await downloadRfqReport('xlsx')
      const fileName =
        disposition?.match(/filename\*?=(?:UTF-8''|")?([^";]+)/i)?.[1] ?? 'global-rfq-report.xlsx'
      saveBlob(blob, decodeURIComponent(fileName.replaceAll('"', '')))
    } catch (error) {
      toast.error(unexpectedErrorMessage(normalizeError(error)))
    }
  }

  if (!isAdmin && !demo) {
    return (
      <AppShell>
        <Card className="mx-auto max-w-xl p-8 text-center">
          <BarChart3 className="mx-auto size-8 text-red-brand-600" aria-hidden="true" />
          <h1 className="mt-3 text-xl font-bold text-brand-900">Administrator access required</h1>
          <p className="mt-2 text-sm text-slate-500">
            Operational reports and exports are restricted to Admin and Super Admin accounts.
          </p>
        </Card>
      </AppShell>
    )
  }

  return (
    <AppShell>
      <div className="mb-6 flex flex-wrap items-end justify-between gap-4">
        <div>
          <p className="text-xs font-semibold tracking-[0.15em] text-red-brand-600 uppercase">
            Operations intelligence
          </p>
          <h1 className="mt-1 text-2xl font-bold tracking-tight text-brand-900">Reports & workload</h1>
          <p className="mt-1.5 text-sm text-slate-600">
            Monitor RFQ stage distribution, customer outcomes, and team workload.
          </p>
        </div>
        <div className="flex gap-2">
          <Button variant="outline" size="sm" onClick={() => window.print()}>
            <Printer className="size-4" />
            Print view
          </Button>
          <Button size="sm" onClick={() => void exportWorkbook()}>
            <Download className="size-4" />
            {demo ? 'Export demo CSV' : 'Export Excel'}
          </Button>
        </div>
      </div>
      {!demo && reportQuery.isLoading && (
        <div className="mb-4 rounded-lg bg-brand-50 p-3 text-sm text-brand-900" role="status">
          Loading operational report…
        </div>
      )}
      {reportQuery.error && (
        <div
          className="mb-4 rounded-lg border border-red-brand-100 bg-red-brand-50 p-3 text-sm text-red-brand-700"
          role="alert"
        >
          {unexpectedErrorMessage(normalizeError(reportQuery.error))}
        </div>
      )}
      <div className="mb-5 grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
        <Metric
          icon={FileSpreadsheet}
          label="Total RFQs"
          value={total}
          note="Records in your permitted scope"
        />
        <Metric
          icon={CalendarRange}
          label="Awaiting review"
          value={statusCounts['PENDING_REVIEW'] ?? 0}
          note="Submitted versions in review"
        />
        <Metric
          icon={TrendingUp}
          label="Customer approved"
          value={decisionCounts['APPROVED'] ?? 0}
          note="Ready for marketing assignment"
        />
        <Metric
          icon={BarChart3}
          label="Admin escalations"
          value={statusCounts['ESCALATED'] ?? 0}
          note="Rejection limit or exception"
          tone="red"
        />
      </div>
      <div className="grid gap-5 xl:grid-cols-[minmax(0,1fr)_340px]">
        <Card className="overflow-hidden">
          <div className="border-b border-brand-100 px-5 py-4">
            <h2 className="font-semibold text-brand-900">Workflow funnel</h2>
            <p className="mt-1 text-xs text-slate-500">
              Counts by current RFQ status. A request contributes to one active status.
            </p>
          </div>
          <div className="space-y-5 p-5">
            {stageGroups.map((group) => {
              const count = group.statuses.reduce(
                (sum, status) => sum + Number(statusCounts[status] ?? 0),
                0,
              )
              const width = total ? Math.min(100, Math.round((count / total) * 100)) : 0
              return (
                <div key={group.label}>
                  <div className="flex items-center justify-between gap-3">
                    <h3 className="text-sm font-semibold text-slate-800">{group.label}</h3>
                    <span className="text-sm font-bold text-brand-900 tabular-nums">{count}</span>
                  </div>
                  <div className="mt-2 h-2 overflow-hidden rounded-full bg-brand-50">
                    <div
                      className="h-full rounded-full bg-brand-900 transition-all"
                      style={{ width: `${width}%` }}
                    />
                  </div>
                  <div className="mt-2 flex flex-wrap gap-x-3 gap-y-1">
                    {group.statuses.map((status) => (
                      <span key={status} className="text-[11px] text-slate-500">
                        {statusLabels[status as RfqStatus]}:{' '}
                        <strong className="text-slate-700">{statusCounts[status] ?? 0}</strong>
                      </span>
                    ))}
                  </div>
                </div>
              )
            })}
          </div>
        </Card>
        <div className="space-y-5">
          <Card className="p-5">
            <h2 className="font-semibold text-brand-900">Customer outcomes</h2>
            <p className="mt-1 text-xs text-slate-500">Separate from internal reviewer decisions</p>
            <div className="mt-5 space-y-4">
              {Object.entries(decisionCounts).map(([label, value]) => {
                const count = Number(value)
                const percent = total ? Math.round((count / total) * 100) : 0
                const color =
                  label === 'APPROVED'
                    ? 'bg-emerald-500'
                    : label === 'REJECTED'
                      ? 'bg-red-brand-600'
                      : 'bg-slate-400'
                return (
                  <div key={label}>
                    <div className="flex justify-between text-xs">
                      <span className="font-medium text-slate-600">
                        {label.replaceAll('_', ' ')}
                      </span>
                      <span className="font-semibold text-slate-800">
                        {count} <span className="font-normal text-slate-400">({percent}%)</span>
                      </span>
                    </div>
                    <div className="mt-2 h-1.5 overflow-hidden rounded-full bg-brand-50">
                      <div
                        className={`h-full rounded-full ${color}`}
                        style={{ width: `${percent}%` }}
                      />
                    </div>
                  </div>
                )
              })}
            </div>
          </Card>
          <Card className="p-5">
            <h2 className="font-semibold text-brand-900">Assignment workload</h2>
            <p className="mt-1 text-xs text-slate-500">Active ownership distribution</p>
            {demo ? (
              <div className="mt-4 space-y-3">
                {demoWorkload.map((entry) => (
                  <div
                    key={entry.role}
                    className="flex items-center justify-between border-b border-brand-100 pb-3 last:border-0 last:pb-0"
                  >
                    <span className="text-xs text-slate-600">
                      {entry.role.replaceAll('_', ' ')}
                    </span>
                    <span className="rounded-md bg-brand-50 px-2 py-1 text-xs font-bold text-brand-900">
                      {entry.amount}
                    </span>
                  </div>
                ))}
              </div>
            ) : (
              <div className="mt-4 space-y-3">
                {(reportQuery.data?.activeAssignmentWorkload ?? []).map((entry) => (
                  <div
                    key={`${entry.role}-${entry.identityUserId}`}
                    className="flex items-center justify-between border-b border-brand-100 pb-3 last:border-0 last:pb-0"
                  >
                    <span className="text-xs text-slate-600">
                      {entry.role.replaceAll('_', ' ')} · User {entry.identityUserId}
                    </span>
                    <span className="rounded-md bg-brand-50 px-2 py-1 text-xs font-bold text-brand-900">
                      {entry.activeRfqs}
                    </span>
                  </div>
                ))}
                {!reportQuery.data?.activeAssignmentWorkload.length && (
                  <p className="py-3 text-xs text-slate-500">No active assignment workload.</p>
                )}
              </div>
            )}
          </Card>
        </div>
      </div>
      <style>
        {
          '@media print { aside, header, button { display:none !important } main { padding:0 !important } body { background:white !important } body::before { display:none !important } }'
        }
      </style>
    </AppShell>
  )
}

function Metric({
  icon: Icon,
  label,
  value,
  note,
  tone = 'blue',
}: {
  icon: typeof FileSpreadsheet
  label: string
  value: number
  note: string
  tone?: 'blue' | 'red'
}) {
  return (
    <Card className="p-4">
      <div className="flex items-start justify-between">
        <div>
          <p className="text-sm text-slate-600">{label}</p>
          <p className="mt-2 text-3xl font-bold text-brand-900">{value}</p>
        </div>
        <span
          className={`flex size-10 items-center justify-center rounded-xl ${tone === 'red' ? 'bg-red-brand-50 text-red-brand-600' : 'bg-brand-50 text-brand-900'}`}
        >
          <Icon className="size-5" />
        </span>
      </div>
      <p className="mt-3 text-xs text-slate-500">{note}</p>
    </Card>
  )
}

function saveBlob(blob: Blob, name: string) {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = name
  link.click()
  window.setTimeout(() => URL.revokeObjectURL(url), 1000)
}
