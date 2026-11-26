import { createFileRoute, Link } from '@tanstack/react-router'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { CheckCircle2, Flag, Loader2, Plus, XCircle } from 'lucide-react'
import * as React from 'react'
import { toast } from 'sonner'
import { AppShell } from '@/components/layout/AppShell'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { useAuth } from '@/features/auth/hooks/useAuth'
import { isDemoSession } from '@/features/auth/demoSession'
import {
  createRedFlag,
  getRedFlags,
  getRfqs,
  resolveRedFlag,
  type RedFlagDto,
} from '@/features/rfq/api/rfqApi'
import { useRfqDemoStore } from '@/features/rfq/store/rfqDemoStore'
import { isDemoMode } from '@/lib/env'
import { normalizeError, unexpectedErrorMessage } from '@/lib/errors'

export const Route = createFileRoute('/_authenticated/red-flags')({
  component: RedFlagsPage,
})

const severityBadge: Record<RedFlagDto['severity'], string> = {
  LOW: 'bg-slate-100 text-slate-700',
  MEDIUM: 'bg-amber-100 text-amber-800',
  HIGH: 'bg-orange-100 text-orange-800',
  CRITICAL: 'bg-red-100 text-red-800',
}

const statusBadge: Record<RedFlagDto['status'], string> = {
  OPEN: 'bg-rose-50 text-rose-700 ring-1 ring-rose-200',
  RESOLVED: 'bg-emerald-50 text-emerald-700 ring-1 ring-emerald-200',
  DISMISSED: 'bg-slate-100 text-slate-600 ring-1 ring-slate-200',
}

export function RedFlagsPage() {
  const { user, hasAnyRole } = useAuth()
  const queryClient = useQueryClient()
  const demo = isDemoMode && isDemoSession()
  const isAdmin = hasAnyRole(['ADMIN', 'SUPER_ADMIN'])
  const [statusFilter, setStatusFilter] = React.useState<RedFlagDto['status'] | ''>('')
  const [severityFilter, setSeverityFilter] = React.useState<RedFlagDto['severity'] | ''>('')
  const [showCreate, setShowCreate] = React.useState(false)
  const [newRfqId, setNewRfqId] = React.useState('')
  const [newSeverity, setNewSeverity] = React.useState<RedFlagDto['severity']>('HIGH')
  const [newReason, setNewReason] = React.useState('')
  const [resolvingId, setResolvingId] = React.useState<number | null>(null)
  const [resolveNote, setResolveNote] = React.useState('')

  const demoRfqs = useRfqDemoStore((s) => s.rfqs)
  const demoRedFlags = useRfqDemoStore((s) => s.redFlags)
  const demoCreate = useRfqDemoStore((s) => s.createRedFlag)
  const demoResolve = useRfqDemoStore((s) => s.resolveRedFlag)

  const flagsQuery = useQuery({
    queryKey: ['red-flags', statusFilter, severityFilter],
    queryFn: () =>
      getRedFlags(0, 50, statusFilter || undefined, severityFilter || undefined),
    enabled: !demo,
    retry: false,
  })

  const rfqChoicesQuery = useQuery({
    queryKey: ['rfqs', 'choices-for-flags'],
    queryFn: () => getRfqs(0, 50),
    enabled: !demo && showCreate,
    retry: false,
  })

  const createMutation = useMutation({
    mutationFn: () => createRedFlag(newRfqId, newSeverity, newReason.trim()),
    onSuccess: async () => {
      toast.success('Red flag raised')
      setShowCreate(false)
      setNewRfqId('')
      setNewReason('')
      await queryClient.invalidateQueries({ queryKey: ['red-flags'] })
    },
    onError: (err) => toast.error(unexpectedErrorMessage(normalizeError(err))),
  })

  const resolveMutation = useMutation({
    mutationFn: (args: { id: number; resolution: 'RESOLVE' | 'DISMISS'; note: string }) =>
      resolveRedFlag(args.id, args.resolution, args.note),
    onSuccess: async () => {
      toast.success('Red flag updated')
      setResolvingId(null)
      setResolveNote('')
      await queryClient.invalidateQueries({ queryKey: ['red-flags'] })
    },
    onError: (err) => toast.error(unexpectedErrorMessage(normalizeError(err))),
  })

  const rows: RedFlagDto[] = demo
    ? demoRedFlags.filter(
        (f) =>
          (!statusFilter || f.status === statusFilter) &&
          (!severityFilter || f.severity === severityFilter),
      )
    : (flagsQuery.data?.content ?? [])

  const rfqChoices = demo
    ? demoRfqs.map((r) => ({ id: r.id, sourceRfqId: r.sourceRfqId, customerName: r.customerName }))
    : (rfqChoicesQuery.data?.content ?? [])

  async function submitNew(e: React.FormEvent) {
    e.preventDefault()
    if (!newRfqId || newReason.trim().length < 5) {
      toast.error('Select an RFQ and enter a reason (at least 5 characters).')
      return
    }
    if (demo) {
      demoCreate(newRfqId, newSeverity, newReason.trim(), user?.id ?? 1)
      toast.success('Red flag raised in demo mode')
      setShowCreate(false)
      setNewRfqId('')
      setNewReason('')
      return
    }
    await createMutation.mutateAsync()
  }

  async function handleResolve(id: number, resolution: 'RESOLVE' | 'DISMISS') {
    if (resolveNote.trim().length < 5) {
      toast.error('Enter a resolution note of at least 5 characters.')
      return
    }
    if (demo) {
      demoResolve(id, resolution, resolveNote.trim(), user?.id ?? 1)
      toast.success('Red flag updated')
      setResolvingId(null)
      setResolveNote('')
      return
    }
    await resolveMutation.mutateAsync({ id, resolution, note: resolveNote.trim() })
  }

  return (
    <AppShell>
      <div className="mb-5 flex flex-wrap items-center justify-between gap-4">
        <h1 className="text-2xl font-bold tracking-tight text-slate-950">Red Flags</h1>
        <Button size="sm" onClick={() => setShowCreate((v) => !v)}>
          <Plus className="size-4" /> Raise Red Flag
        </Button>
      </div>

      {showCreate && (
        <Card className="mb-5 border-red-200 bg-red-50/30 p-5 shadow-none">
          <h2 className="flex items-center gap-2 text-sm font-semibold text-slate-900">
            <Flag className="size-4 text-red-600" /> Raise a new Red Flag
          </h2>
          <form onSubmit={submitNew} className="mt-4 grid gap-4 md:grid-cols-[1fr_180px]">
            <label className="block text-xs font-medium text-slate-700">
              RFQ *
              <select
                value={newRfqId}
                onChange={(e) => setNewRfqId(e.target.value)}
                className="mt-1 h-10 w-full rounded-md border border-slate-200 bg-white px-3 text-sm"
                required
              >
                <option value="">Select an RFQ…</option>
                {rfqChoices.map((rfq) => (
                  <option key={rfq.id} value={rfq.id}>
                    RFQ-{rfq.sourceRfqId} · {rfq.customerName}
                  </option>
                ))}
              </select>
            </label>
            <label className="block text-xs font-medium text-slate-700">
              Severity *
              <select
                value={newSeverity}
                onChange={(e) => setNewSeverity(e.target.value as RedFlagDto['severity'])}
                className="mt-1 h-10 w-full rounded-md border border-slate-200 bg-white px-3 text-sm"
              >
                <option value="LOW">LOW</option>
                <option value="MEDIUM">MEDIUM</option>
                <option value="HIGH">HIGH</option>
                <option value="CRITICAL">CRITICAL</option>
              </select>
            </label>
            <label className="block text-xs font-medium text-slate-700 md:col-span-2">
              Reason / Escalation Details *
              <textarea
                value={newReason}
                onChange={(e) => setNewReason(e.target.value)}
                rows={2}
                maxLength={2000}
                placeholder="Describe the blocking issue, pricing anomaly, or SLA risk"
                className="mt-1 w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm"
                required
              />
            </label>
            <div className="flex justify-end gap-2 md:col-span-2">
              <Button type="button" variant="outline" size="sm" onClick={() => setShowCreate(false)}>
                Cancel
              </Button>
              <Button type="submit" size="sm" variant="destructive" disabled={createMutation.isPending}>
                {createMutation.isPending ? <Loader2 className="size-4 animate-spin" /> : 'Submit Flag'}
              </Button>
            </div>
          </form>
        </Card>
      )}

      <div className="mb-4 flex flex-wrap gap-3">
        <label className="block text-xs font-medium text-slate-600">
          Status
          <select
            value={statusFilter}
            onChange={(e) => setStatusFilter(e.target.value as RedFlagDto['status'] | '')}
            className="mt-1 block h-10 min-w-40 rounded-md border border-slate-200 bg-white px-3 text-sm"
          >
            <option value="">All Statuses</option>
            <option value="OPEN">OPEN</option>
            <option value="RESOLVED">RESOLVED</option>
            <option value="DISMISSED">DISMISSED</option>
          </select>
        </label>
        <label className="block text-xs font-medium text-slate-600">
          Severity
          <select
            value={severityFilter}
            onChange={(e) => setSeverityFilter(e.target.value as RedFlagDto['severity'] | '')}
            className="mt-1 block h-10 min-w-40 rounded-md border border-slate-200 bg-white px-3 text-sm"
          >
            <option value="">All Severities</option>
            <option value="LOW">LOW</option>
            <option value="MEDIUM">MEDIUM</option>
            <option value="HIGH">HIGH</option>
            <option value="CRITICAL">CRITICAL</option>
          </select>
        </label>
      </div>

      <Card className="overflow-hidden border-slate-200 shadow-xs">
        {!demo && flagsQuery.isLoading ? (
          <div className="p-8 text-center text-sm text-slate-500">Loading red flags…</div>
        ) : !demo && flagsQuery.error ? (
          <div className="p-6 text-sm text-red-700" role="alert">
            {unexpectedErrorMessage(normalizeError(flagsQuery.error))}
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[780px] border-collapse text-left">
              <thead>
                <tr className="border-b border-slate-200 bg-white text-xs font-medium text-slate-600">
                  <th className="px-4 py-3">RFQ ID</th>
                  <th className="px-4 py-3">Severity</th>
                  <th className="px-4 py-3">Reason</th>
                  <th className="px-4 py-3">Status</th>
                  <th className="px-4 py-3">Raised By</th>
                  <th className="px-4 py-3">Assigned To</th>
                  <th className="px-4 py-3">Created</th>
                  <th className="px-4 py-3 text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {rows.length === 0 ? (
                  <tr>
                    <td colSpan={8} className="px-4 py-10 text-center text-sm text-slate-500">
                      No red flags found.
                    </td>
                  </tr>
                ) : (
                  rows.map((flag) => (
                    <React.Fragment key={flag.id}>
                      <tr className="hover:bg-slate-50/70">
                        <td className="px-4 py-3 text-sm font-semibold">
                          <Link
                            to="/rfqs/$rfqId"
                            params={{ rfqId: flag.rfqPublicId }}
                            className="text-blue-700 hover:underline"
                          >
                            {flag.sourceRfqId ? `RFQ-${flag.sourceRfqId}` : flag.rfqPublicId.slice(0, 8)}
                          </Link>
                        </td>
                        <td className="px-4 py-3">
                          <span className={`rounded-full px-2 py-0.5 text-[11px] font-semibold ${severityBadge[flag.severity]}`}>
                            {flag.severity}
                          </span>
                        </td>
                        <td className="max-w-xs px-4 py-3 text-sm text-slate-700">
                          <p className="line-clamp-2">{flag.reason}</p>
                          {flag.resolutionNote && (
                            <p className="mt-1 text-xs text-slate-500">Note: {flag.resolutionNote}</p>
                          )}
                        </td>
                        <td className="px-4 py-3">
                          <span className={`rounded-full px-2 py-0.5 text-[11px] font-semibold ${statusBadge[flag.status]}`}>
                            {flag.status}
                          </span>
                        </td>
                        <td className="px-4 py-3 text-xs text-slate-600">User {flag.raisedBy}</td>
                        <td className="px-4 py-3 text-xs text-slate-600">
                          {flag.assignedTo ? `User ${flag.assignedTo}` : '—'}
                        </td>
                        <td className="px-4 py-3 text-xs whitespace-nowrap text-slate-500">
                          {new Intl.DateTimeFormat('en-IN', { dateStyle: 'medium' }).format(
                            new Date(flag.createdAt),
                          )}
                        </td>
                        <td className="px-4 py-3 text-right">
                          {isAdmin && flag.status === 'OPEN' ? (
                            <Button
                              size="sm"
                              variant="outline"
                              onClick={() => {
                                setResolvingId(resolvingId === flag.id ? null : flag.id)
                                setResolveNote('')
                              }}
                            >
                              Resolve
                            </Button>
                          ) : (
                            <span className="text-xs text-slate-400">—</span>
                          )}
                        </td>
                      </tr>
                      {resolvingId === flag.id && (
                        <tr className="bg-slate-50">
                          <td colSpan={8} className="px-4 py-3">
                            <div className="flex flex-wrap items-center gap-2">
                              <Input
                                value={resolveNote}
                                onChange={(e) => setResolveNote(e.target.value)}
                                placeholder="Resolution or dismissal note (min 5 chars)…"
                                className="max-w-md flex-1 bg-white"
                              />
                              <Button
                                size="sm"
                                disabled={resolveMutation.isPending}
                                onClick={() => void handleResolve(flag.id, 'RESOLVE')}
                              >
                                <CheckCircle2 className="size-4" /> Mark Resolved
                              </Button>
                              <Button
                                size="sm"
                                variant="outline"
                                disabled={resolveMutation.isPending}
                                onClick={() => void handleResolve(flag.id, 'DISMISS')}
                              >
                                <XCircle className="size-4" /> Dismiss
                              </Button>
                            </div>
                          </td>
                        </tr>
                      )}
                    </React.Fragment>
                  ))
                )}
              </tbody>
            </table>
          </div>
        )}
      </Card>
    </AppShell>
  )
}
