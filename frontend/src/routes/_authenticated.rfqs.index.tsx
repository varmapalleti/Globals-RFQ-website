import { createFileRoute, Link } from '@tanstack/react-router'
import { useQuery } from '@tanstack/react-query'
import {
  ArrowDownWideNarrow,
  ChevronLeft,
  ChevronRight,
  Download,
  FilePlus2,
  FileSpreadsheet,
  Search,
  Upload,
} from 'lucide-react'
import * as React from 'react'
import { toast } from 'sonner'
import { AppShell } from '@/components/layout/AppShell'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { StatusPill } from '@/features/rfq/components/StatusPill'
import { downloadRfqReport, getRfqs } from '@/features/rfq/api/rfqApi'
import { useRfqRecords } from '@/features/rfq/hooks/useRfqRecords'
import { formatRfqNumber, statusLabels, type RfqRecord, type RfqStatus } from '@/features/rfq/types'
import { useAuth } from '@/features/auth/hooks/useAuth'
import { normalizeError, unexpectedErrorMessage } from '@/lib/errors'

export const Route = createFileRoute('/_authenticated/rfqs/')({
  component: RfqWorkspacePage,
})

function scopedRecords(records: RfqRecord[], role: string, userId?: number) {
  if (role === 'ADMIN' || role === 'SUPER_ADMIN' || role === 'VIEWER') return records
  const assignmentRole =
    role === 'QUOTATION_MANAGER'
      ? 'QUOTATION_MANAGER'
      : role === 'REVIEWER'
        ? 'REVIEWER'
        : 'MARKETING_EXECUTIVE'
  return records.filter((rfq) =>
    rfq.assignments.some(
      (assignment) => assignment.role === assignmentRole && assignment.userId === userId,
    ),
  )
}

function stageLabelForStatus(status: RfqStatus): string {
  switch (status) {
    case 'IMPORTED':
    case 'ASSIGNED_TO_QUOTATION_MANAGER':
      return 'Intake'
    case 'QUOTATION_IN_PROGRESS':
    case 'REVISION_IN_PROGRESS':
    case 'REOPENED_FOR_REVISION':
    case 'REJECTED_BY_REVIEWER':
      return 'Quotation'
    case 'PENDING_REVIEW':
    case 'AWAITING_REASSIGNMENT':
    case 'APPROVED_BY_REVIEWER':
    case 'ESCALATED':
      return 'Review'
    case 'SENT_TO_CUSTOMER':
    case 'CUSTOMER_APPROVED':
    case 'CUSTOMER_REJECTED':
      return 'Customer'
    case 'READY_FOR_MARKETING':
    case 'ASSIGNED_TO_MARKETING':
      return 'Marketing'
    default:
      return 'Closed'
  }
}

const allStatuses = Object.keys(statusLabels) as RfqStatus[]

function LookupError({ error }: { error: unknown }) {
  const apiError = normalizeError(error)
  return (
    <p className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-800">
      {apiError.message} <span className="text-xs">({apiError.errorCode})</span>
    </p>
  )
}

function RfqWorkspacePage() {
  const { user } = useAuth()
  const role = user?.roles.includes('SUPER_ADMIN')
    ? 'SUPER_ADMIN'
    : user?.roles.includes('ADMIN')
      ? 'ADMIN'
      : (user?.roles[0] ?? 'VIEWER')
  const isAdmin = role === 'ADMIN' || role === 'SUPER_ADMIN'
  const [search, setSearch] = React.useState('')
  const [debouncedSearch, setDebouncedSearch] = React.useState('')
  const [lookupInput, setLookupInput] = React.useState('')
  const [submittedLookup, setSubmittedLookup] = React.useState('')
  const [status, setStatus] = React.useState<RfqStatus | 'ALL'>('ALL')
  const [sortOrder, setSortOrder] = React.useState<'newest' | 'delivery'>('newest')
  const [page, setPage] = React.useState(0)
  const [pageSize, setPageSize] = React.useState(10)
  const [exporting, setExporting] = React.useState(false)

  const {
    rfqs: allRfqs,
    isDemo,
    isLoading,
    error,
    refetch,
    totalElements,
    totalPages,
  } = useRfqRecords({
    page,
    size: pageSize,
    search: debouncedSearch || undefined,
    status: status === 'ALL' ? undefined : status,
    sort: sortOrder === 'newest' ? 'createdAt' : 'requestedDeliveryDate',
  })

  const records = isDemo ? scopedRecords(allRfqs, role, user?.id) : allRfqs

  React.useEffect(() => {
    const timer = window.setTimeout(() => setDebouncedSearch(search.trim()), 250)
    return () => window.clearTimeout(timer)
  }, [search])

  React.useEffect(() => {
    setPage(0)
  }, [debouncedSearch, status, sortOrder, pageSize])

  const lookupQuery = useQuery({
    queryKey: ['rfqs', 'number-lookup', submittedLookup],
    queryFn: () => getRfqs(0, 20, submittedLookup),
    enabled: isAdmin && !isDemo && Boolean(submittedLookup),
    retry: false,
  })
  const lookupMatches = isDemo
    ? allRfqs.filter((rfq) => rfq.sourceRfqId.toLowerCase() === submittedLookup.toLowerCase())
    : (lookupQuery.data?.content ?? [])

  const demoFiltered = React.useMemo(() => {
    if (!isDemo) return []
    const normalizedSearch = debouncedSearch.toLowerCase()
    return records
      .filter((rfq) => {
        const matchesSearch =
          !normalizedSearch ||
          [
            rfq.sourceRfqId,
            rfq.sourceReference ?? '',
            rfq.customerName,
            rfq.requestTitle,
            ...rfq.assignments.map((assignment) => assignment.userName),
          ].some((value) => value.toLowerCase().includes(normalizedSearch))
        const matchesStatus = status === 'ALL' || rfq.status === status
        return matchesSearch && matchesStatus
      })
      .sort((a, b) =>
        sortOrder === 'newest'
          ? b.createdAt.localeCompare(a.createdAt)
          : a.requestedDeliveryDate.localeCompare(b.requestedDeliveryDate),
      )
  }, [records, debouncedSearch, status, sortOrder, isDemo])

  const filtered = isDemo ? demoFiltered.slice(page * pageSize, (page + 1) * pageSize) : allRfqs
  const resultTotal = isDemo ? demoFiltered.length : totalElements
  const resultPages = isDemo ? Math.max(1, Math.ceil(resultTotal / pageSize)) : totalPages
  const listError = error ? normalizeError(error) : null

  function submitNumberLookup(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const normalized = lookupInput.trim().replace(/^RFQ[\s-]*/i, '')
    setSubmittedLookup(normalized)
  }

  function clearFilters() {
    setSearch('')
    setDebouncedSearch('')
    setStatus('ALL')
    setSortOrder('newest')
    setSubmittedLookup('')
    setLookupInput('')
    setPage(0)
  }

  async function handleExportExcel() {
    if (isDemo || !isAdmin) {
      handleExportCsv()
      return
    }
    setExporting(true)
    try {
      const { blob, disposition } = await downloadRfqReport('xlsx')
      const fileName =
        disposition?.match(/filename\*?=(?:UTF-8''|")?([^";]+)/i)?.[1] ?? 'global-rfqs.xlsx'
      triggerDownload(blob, decodeURIComponent(fileName.replaceAll('"', '')))
      toast.success('Excel export downloaded')
    } catch (err) {
      toast.error(unexpectedErrorMessage(normalizeError(err)))
    } finally {
      setExporting(false)
    }
  }

  function handleExportCsv() {
    const header = [
      'RFQ #',
      'PR #',
      'Customer',
      'Due Date',
      'Status',
      'Stage',
      'Assignee',
      'Items',
      'Created',
    ]
    const csvRows = (isDemo ? demoFiltered : filtered).map((rfq) => {
      const assignee = rfq.assignments[0]?.userName ?? 'Unassigned'
      const itemCount = rfq.lineItemCount ?? rfq.lineItems.length
      return [
        formatRfqNumber(rfq),
        rfq.sourceReference || rfq.sourceRfqId,
        rfq.customerName,
        rfq.dueAt || rfq.requestedDeliveryDate || '',
        rfq.status,
        stageLabelForStatus(rfq.status),
        assignee,
        String(itemCount),
        rfq.createdAt,
      ]
    })
    const csv = [header, ...csvRows]
      .map((row) => row.map((v) => `"${String(v).replaceAll('"', '""')}"`).join(','))
      .join('\r\n')
    triggerDownload(new Blob([csv], { type: 'text/csv;charset=utf-8' }), 'global-rfqs.csv')
    toast.success('CSV export downloaded')
  }

  return (
    <AppShell>
      <div className="mb-5 flex flex-wrap items-center justify-between gap-4">
        <h1 className="text-2xl font-bold tracking-tight text-slate-950">RFQs</h1>
        <div className="flex flex-wrap items-center gap-2">
          <Button
            variant="outline"
            size="sm"
            disabled={exporting}
            onClick={() => void handleExportExcel()}
          >
            <FileSpreadsheet className="size-4" aria-hidden="true" /> Export Excel
          </Button>
          <Button variant="outline" size="sm" onClick={handleExportCsv}>
            <Download className="size-4" aria-hidden="true" /> Export CSV
          </Button>
          {isAdmin && (
            <>
              <Button variant="outline" size="sm" asChild>
                <Link to="/rfqs/import">
                  <Upload className="size-4" aria-hidden="true" /> Import
                </Link>
              </Button>
              <Button size="sm" asChild>
                <Link to="/rfqs/create">
                  <FilePlus2 className="size-4" aria-hidden="true" /> + Create RFQ
                </Link>
              </Button>
            </>
          )}
        </div>
      </div>

      <Card className="mb-5 border-slate-200 p-4 shadow-xs">
        <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
          <div className="relative min-w-0 flex-1 sm:max-w-xs">
            <label htmlFor="rfq-search" className="sr-only">
              Search
            </label>
            <Search
              className="absolute top-1/2 left-3 size-4 -translate-y-1/2 text-slate-400"
              aria-hidden="true"
            />
            <Input
              id="rfq-search"
              value={search}
              onChange={(event) => setSearch(event.target.value)}
              placeholder="Search"
              className="pl-9"
            />
          </div>
          <label className="flex items-center gap-2 text-sm text-slate-600">
            <span className="sr-only">Status</span>
            <select
              aria-label="Status"
              value={status}
              onChange={(event) => setStatus(event.target.value as RfqStatus | 'ALL')}
              className="h-10 min-w-44 rounded-md border border-slate-200 bg-white px-3 text-sm outline-none focus:ring-2 focus:ring-blue-500"
            >
              <option value="ALL">Status</option>
              {allStatuses.map((value) => (
                <option key={value} value={value}>
                  {statusLabels[value]}
                </option>
              ))}
            </select>
          </label>
          <label className="flex items-center gap-2 text-sm text-slate-600">
            <ArrowDownWideNarrow className="size-4 text-slate-400" aria-hidden="true" />
            <select
              aria-label="Sort RFQs"
              value={sortOrder}
              onChange={(event) => setSortOrder(event.target.value as typeof sortOrder)}
              className="h-10 rounded-md border border-slate-200 bg-white px-3 text-sm outline-none focus:ring-2 focus:ring-blue-500"
            >
              <option value="newest">Newest first</option>
              <option value="delivery">Delivery date</option>
            </select>
          </label>
          <Button type="button" variant="ghost" size="sm" className="text-blue-700" onClick={clearFilters}>
            Clear Filters
          </Button>
        </div>

        {isAdmin && (
          <form
            onSubmit={submitNumberLookup}
            className="mt-3 flex flex-col gap-2 border-t border-slate-100 pt-3 sm:flex-row sm:items-center"
          >
            <Input
              value={lookupInput}
              onChange={(event) => setLookupInput(event.target.value)}
              placeholder="Exact PR / RFQ # lookup (e.g. 112477)"
              aria-label="Customer RFQ number to check"
              className="sm:max-w-xs"
            />
            <Button
              type="submit"
              variant="outline"
              size="sm"
              disabled={!lookupInput.trim() || lookupQuery.isFetching}
            >
              {lookupQuery.isFetching ? 'Checking…' : 'Check status'}
            </Button>
          </form>
        )}

        {submittedLookup && (
          <div className="mt-3" aria-live="polite">
            {lookupQuery.error ? (
              <LookupError error={lookupQuery.error} />
            ) : lookupQuery.isFetching ? null : lookupMatches.length ? (
              <div className="space-y-2">
                {lookupMatches.map((rfq) => (
                  <div
                    key={rfq.id}
                    className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-slate-200 bg-slate-50 px-4 py-2.5"
                  >
                    <div>
                      <Link
                        to="/rfqs/$rfqId"
                        params={{ rfqId: rfq.id }}
                        className="font-semibold text-blue-800 hover:underline"
                      >
                        {formatRfqNumber(rfq)}
                      </Link>
                      <span className="ml-2 text-xs text-slate-600">{rfq.customerName}</span>
                    </div>
                    <div className="flex items-center gap-3">
                      <StatusPill status={rfq.status} />
                      <Button asChild size="sm" variant="outline">
                        <Link to="/rfqs/$rfqId" params={{ rfqId: rfq.id }}>
                          Open
                        </Link>
                      </Button>
                    </div>
                  </div>
                ))}
              </div>
            ) : (
              <p className="rounded-lg bg-slate-50 px-4 py-2.5 text-sm text-slate-600">
                No RFQ found for number <span className="font-semibold">{submittedLookup}</span>.
              </p>
            )}
          </div>
        )}
      </Card>

      <section
        className="overflow-hidden rounded-xl border border-slate-200 bg-white shadow-xs"
        aria-label="RFQs table"
      >
        {error ? (
          <div className="p-8 text-sm text-red-700" role="alert">
            <p className="font-semibold">Could not load RFQ data.</p>
            <p className="mt-1">{listError?.message}</p>
            {listError && (
              <p className="mt-1 text-xs">
                {listError.errorCode} · HTTP {listError.httpStatus || 'network error'}
              </p>
            )}
            <Button className="mt-3" size="sm" variant="outline" onClick={() => void refetch()}>
              Retry
            </Button>
          </div>
        ) : isLoading ? (
          <div className="p-10 text-center text-sm text-slate-500" role="status">
            Loading RFQs…
          </div>
        ) : filtered.length ? (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[960px] border-collapse text-left">
              <thead>
                <tr className="border-b border-slate-200 bg-white text-xs font-medium text-slate-600">
                  <th scope="col" className="px-4 py-3">RFQ #</th>
                  <th scope="col" className="px-4 py-3">PR #</th>
                  <th scope="col" className="px-4 py-3">Customer</th>
                  <th scope="col" className="px-4 py-3">Due Date</th>
                  <th scope="col" className="px-4 py-3">Status</th>
                  <th scope="col" className="px-4 py-3">Stage</th>
                  <th scope="col" className="px-4 py-3">Assignee</th>
                  <th scope="col" className="px-4 py-3">Items</th>
                  <th scope="col" className="px-4 py-3">Created</th>
                  <th scope="col" className="px-4 py-3 text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {filtered.map((rfq) => {
                  const assignee = rfq.assignments[0]
                  const dueDate = rfq.dueAt?.slice(0, 10) || rfq.requestedDeliveryDate
                  const itemCount = rfq.lineItemCount ?? rfq.lineItems.length
                  return (
                    <tr key={rfq.id} className="hover:bg-blue-50/30">
                      <td className="px-4 py-3.5 text-sm font-semibold whitespace-nowrap">
                        <Link
                          to="/rfqs/$rfqId"
                          params={{ rfqId: rfq.id }}
                          className="text-blue-700 hover:underline"
                        >
                          {formatRfqNumber(rfq)}
                        </Link>
                      </td>
                      <td className="px-4 py-3.5 text-xs text-slate-600 whitespace-nowrap">
                        {rfq.sourceReference || rfq.sourceRfqId}
                      </td>
                      <td className="max-w-48 truncate px-4 py-3.5 text-sm text-slate-800">
                        {rfq.customerName}
                      </td>
                      <td className="px-4 py-3.5 text-xs whitespace-nowrap text-slate-600">
                        {dueDate
                          ? new Intl.DateTimeFormat('en-IN', {
                              day: '2-digit',
                              month: 'short',
                              year: 'numeric',
                            }).format(new Date(`${dueDate}T00:00:00`))
                          : '—'}
                      </td>
                      <td className="px-4 py-3.5">
                        <StatusPill status={rfq.status} />
                      </td>
                      <td className="px-4 py-3.5 text-xs font-medium text-slate-700">
                        {stageLabelForStatus(rfq.status)}
                      </td>
                      <td className="px-4 py-3.5 text-xs text-slate-600 whitespace-nowrap">
                        {assignee?.userName ?? <span className="text-slate-400">Unassigned</span>}
                      </td>
                      <td className="px-4 py-3.5 text-xs font-medium text-slate-700">
                        {itemCount}
                      </td>
                      <td className="px-4 py-3.5 text-xs whitespace-nowrap text-slate-500">
                        {new Intl.DateTimeFormat('en-IN', {
                          day: '2-digit',
                          month: 'short',
                          year: 'numeric',
                        }).format(new Date(rfq.createdAt))}
                      </td>
                      <td className="px-4 py-3.5 text-right whitespace-nowrap">
                        <Button asChild size="sm" variant="ghost">
                          <Link to="/rfqs/$rfqId" params={{ rfqId: rfq.id }}>
                            Open
                          </Link>
                        </Button>
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        ) : (
          <div className="px-6 py-16 text-center">
            <p className="text-sm font-semibold text-slate-700">No RFQs found</p>
            <p className="mt-1 text-xs text-slate-500">Try adjusting your filters</p>
          </div>
        )}

        {!error && !isLoading && (
          <div className="flex flex-wrap items-center justify-end gap-4 border-t border-slate-100 px-5 py-3 text-xs text-slate-600">
            <label className="flex items-center gap-2">
              Rows per page:
              <select
                value={pageSize}
                onChange={(event) => setPageSize(Number(event.target.value))}
                className="h-8 rounded border border-slate-200 bg-white px-2 text-xs"
              >
                {[10, 20, 50].map((size) => (
                  <option key={size} value={size}>
                    {size}
                  </option>
                ))}
              </select>
            </label>
            <span>
              {resultTotal === 0 ? 0 : page * pageSize + 1}–
              {Math.min((page + 1) * pageSize, resultTotal)} of {resultTotal}
            </span>
            <div className="flex items-center gap-1">
              <Button
                size="icon"
                variant="ghost"
                aria-label="Previous page"
                disabled={page === 0}
                onClick={() => setPage((value) => Math.max(0, value - 1))}
              >
                <ChevronLeft className="size-4" />
              </Button>
              <Button
                size="icon"
                variant="ghost"
                aria-label="Next page"
                disabled={page + 1 >= resultPages}
                onClick={() => setPage((value) => Math.min(resultPages - 1, value + 1))}
              >
                <ChevronRight className="size-4" />
              </Button>
            </div>
          </div>
        )}
      </section>
    </AppShell>
  )
}

function triggerDownload(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.append(link)
  link.click()
  link.remove()
  window.setTimeout(() => URL.revokeObjectURL(url), 1000)
}
