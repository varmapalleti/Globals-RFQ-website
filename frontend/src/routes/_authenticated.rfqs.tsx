import { createFileRoute, Link } from '@tanstack/react-router'
import { useQuery } from '@tanstack/react-query'
import * as React from 'react'
import { ArrowDownWideNarrow, ChevronLeft, ChevronRight, FilePlus2, Search } from 'lucide-react'
import { AppShell } from '@/components/layout/AppShell'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Card } from '@/components/ui/card'
import { StatusPill } from '@/features/rfq/components/StatusPill'
import { formatRfqNumber, statusLabels, type RfqRecord, type RfqStatus } from '@/features/rfq/types'
import { useRfqRecords } from '@/features/rfq/hooks/useRfqRecords'
import { getRfqs } from '@/features/rfq/api/rfqApi'
import { useAuth } from '@/features/auth/hooks/useAuth'
import { normalizeError } from '@/lib/errors'

export const Route = createFileRoute('/_authenticated/rfqs')({ component: RfqWorkspacePage })

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
  const canImport = role === 'ADMIN' || role === 'SUPER_ADMIN'
  const [search, setSearch] = React.useState('')
  const [debouncedSearch, setDebouncedSearch] = React.useState('')
  const [lookupInput, setLookupInput] = React.useState('')
  const [submittedLookup, setSubmittedLookup] = React.useState('')
  const [status, setStatus] = React.useState<RfqStatus | 'ALL'>('ALL')
  const [sortOrder, setSortOrder] = React.useState<'newest' | 'delivery'>('newest')
  const [page, setPage] = React.useState(0)
  const [pageSize, setPageSize] = React.useState(20)
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
    enabled: canImport && !isDemo && Boolean(submittedLookup),
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

  return (
    <AppShell>
      <div className="mb-6 flex flex-wrap items-end justify-between gap-4">
        <div>
          <p className="text-xs font-semibold tracking-[0.15em] text-blue-700 uppercase">
            Work management
          </p>
          <h1 className="mt-1 text-2xl font-bold tracking-tight text-slate-950">RFQ workspace</h1>
          <p className="mt-1.5 text-sm text-slate-500">
            Search requests, inspect ownership, and move work through controlled stages.
          </p>
        </div>
        {canImport && (
          <Button asChild>
            <Link to="/rfqs/import">
              <FilePlus2 className="size-4" aria-hidden="true" /> Import customer request
            </Link>
          </Button>
        )}
      </div>

      {canImport && (
        <Card className="mb-5 border-blue-100 p-5 shadow-none">
          <div className="mb-3">
            <h2 className="font-semibold text-slate-900">Check RFQ status</h2>
            <p className="mt-1 text-sm text-slate-500">
              Enter the customer RFQ number to see its latest workflow status.
            </p>
          </div>
          <form onSubmit={submitNumberLookup} className="flex flex-col gap-2 sm:flex-row">
            <Input
              value={lookupInput}
              onChange={(event) => setLookupInput(event.target.value)}
              placeholder="Customer RFQ number, for example 112477"
              aria-label="Customer RFQ number to check"
            />
            <Button type="submit" disabled={!lookupInput.trim() || lookupQuery.isFetching}>
              {lookupQuery.isFetching ? 'Checking…' : 'Check status'}
            </Button>
          </form>
          {submittedLookup && (
            <div className="mt-4" aria-live="polite">
              {lookupQuery.error ? (
                <LookupError error={lookupQuery.error} />
              ) : lookupQuery.isFetching ? null : lookupMatches.length ? (
                <div className="space-y-2">
                  {lookupMatches.map((rfq) => (
                    <div
                      key={rfq.id}
                      className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-slate-200 bg-white px-4 py-3"
                    >
                      <div>
                        <Link
                          to="/rfqs/$rfqId"
                          params={{ rfqId: rfq.id }}
                          className="font-semibold text-blue-800 hover:underline"
                        >
                          {formatRfqNumber(rfq)}
                        </Link>
                        <p className="mt-1 text-sm text-slate-600">{rfq.customerName}</p>
                        <p className="mt-0.5 text-xs text-slate-500">{rfq.requestTitle}</p>
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
                <p className="rounded-lg bg-slate-50 px-4 py-3 text-sm text-slate-600">
                  No RFQ found for number <span className="font-semibold">{submittedLookup}</span>.
                </p>
              )}
            </div>
          )}
        </Card>
      )}

      <section
        className="overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm"
        aria-labelledby="rfq-list-heading"
      >
        <div className="flex flex-wrap items-center justify-between gap-3 px-5 py-4">
          <div>
            <h2 id="rfq-list-heading" className="text-base font-semibold">
              All requests{' '}
              <span className="ml-1 rounded-full bg-slate-100 px-2 py-0.5 text-xs font-medium text-slate-600">
                {resultTotal}
              </span>
            </h2>
            <p className="mt-1 text-xs text-slate-500">
              {isDemo
                ? 'Demo records are filtered in this browser.'
                : 'Results are paginated by RFQ Service and limited to your assigned role scope.'}
            </p>
          </div>
        </div>
        <div className="flex flex-col gap-2 border-y border-slate-100 bg-slate-50/60 p-4 sm:flex-row sm:items-center">
          <div className="relative min-w-0 flex-1">
            <label htmlFor="rfq-search" className="sr-only">
              Search RFQs
            </label>
            <Search
              className="absolute top-1/2 left-3 size-4 -translate-y-1/2 text-slate-400"
              aria-hidden="true"
            />
            <Input
              id="rfq-search"
              value={search}
              onChange={(event) => setSearch(event.target.value)}
              placeholder="Search RFQ number, customer, or request"
              className="pl-9"
            />
          </div>
          <label className="flex items-center gap-2 text-sm text-slate-600">
            <span className="sr-only">Filter by status</span>
            <select
              value={status}
              onChange={(event) => setStatus(event.target.value as RfqStatus | 'ALL')}
              className="h-10 min-w-48 rounded-md border border-slate-200 bg-white px-3 text-sm outline-none focus:ring-2 focus:ring-blue-500"
            >
              <option value="ALL">All workflow statuses</option>
              {allStatuses.map((value) => (
                <option key={value} value={value}>
                  {statusLabels[value]}
                </option>
              ))}
            </select>
          </label>
          <label className="flex items-center gap-2 text-sm text-slate-600">
            <span className="sr-only">Sort RFQs</span>
            <ArrowDownWideNarrow className="size-4 text-slate-400" aria-hidden="true" />
            <select
              value={sortOrder}
              onChange={(event) => setSortOrder(event.target.value as typeof sortOrder)}
              className="h-10 rounded-md border border-slate-200 bg-white px-3 text-sm outline-none focus:ring-2 focus:ring-blue-500"
            >
              <option value="newest">Newest first</option>
              <option value="delivery">Delivery date</option>
            </select>
          </label>
        </div>
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
            <table className="w-full min-w-[740px] border-collapse text-left">
              <thead>
                <tr className="border-b border-slate-100 bg-white text-[11px] font-semibold tracking-wide text-slate-500 uppercase">
                  <th scope="col" className="px-5 py-3">
                    RFQ & customer
                  </th>
                  <th scope="col" className="px-5 py-3">
                    Requirement
                  </th>
                  <th scope="col" className="px-5 py-3">
                    Status
                  </th>
                  <th scope="col" className="px-5 py-3">
                    Delivery due
                  </th>
                  <th scope="col" className="px-5 py-3">
                    <span className="sr-only">Open RFQ</span>
                  </th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {filtered.map((rfq) => {
                  return (
                    <tr key={rfq.id} className="hover:bg-blue-50/30">
                      <td className="px-5 py-4">
                        <Link
                          to="/rfqs/$rfqId"
                          params={{ rfqId: rfq.id }}
                          className="font-semibold text-blue-800 hover:underline"
                        >
                          {formatRfqNumber(rfq)}
                        </Link>
                        <p className="mt-1 max-w-52 truncate text-xs text-slate-500">
                          {rfq.customerName}
                        </p>
                      </td>
                      <td className="px-5 py-4">
                        <p
                          className="max-w-64 truncate text-sm font-medium"
                          title={rfq.requestTitle}
                        >
                          {rfq.requestTitle}
                        </p>
                      </td>
                      <td className="px-5 py-4">
                        <StatusPill status={rfq.status} />
                      </td>
                      <td className="px-5 py-4 text-sm whitespace-nowrap text-slate-600">
                        {rfq.requestedDeliveryDate
                          ? new Intl.DateTimeFormat('en-IN', {
                              day: '2-digit',
                              month: 'short',
                              year: 'numeric',
                            }).format(new Date(`${rfq.requestedDeliveryDate}T00:00:00`))
                          : '—'}
                      </td>
                      <td className="px-5 py-4 text-right">
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
            <Search className="mx-auto size-8 text-slate-300" aria-hidden="true" />
            <p className="mt-3 font-semibold">No matching requests</p>
            <p className="mt-1 text-sm text-slate-500">
              Adjust the search or status filter and try again.
            </p>
          </div>
        )}
        {!error && !isLoading && (
          <div className="flex flex-wrap items-center justify-between gap-3 border-t border-slate-100 px-5 py-3 text-sm text-slate-600">
            <label className="flex items-center gap-2">
              Rows per page
              <select
                value={pageSize}
                onChange={(event) => setPageSize(Number(event.target.value))}
                className="h-9 rounded-md border border-slate-200 bg-white px-2 text-sm"
              >
                {[10, 20, 50].map((size) => (
                  <option key={size} value={size}>
                    {size}
                  </option>
                ))}
              </select>
            </label>
            <div className="flex items-center gap-3">
              <span>
                {resultTotal === 0 ? 0 : page * pageSize + 1}–
                {Math.min((page + 1) * pageSize, resultTotal)} of {resultTotal}
              </span>
              <Button
                size="icon"
                variant="ghost"
                aria-label="Previous page"
                disabled={page === 0}
                onClick={() => setPage((value) => Math.max(0, value - 1))}
              >
                <ChevronLeft className="size-4" />
              </Button>
              <span>
                Page {resultTotal === 0 ? 0 : page + 1} of {resultPages}
              </span>
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
      <p className="mt-3 text-xs text-slate-500">
        All transitions and access checks are enforced by RFQ Service in connected mode. Demo
        actions are simulated locally.
      </p>
    </AppShell>
  )
}
