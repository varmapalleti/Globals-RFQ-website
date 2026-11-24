import { createFileRoute, Link } from '@tanstack/react-router'
import { zodResolver } from '@hookform/resolvers/zod'
import { useQuery } from '@tanstack/react-query'
import {
  AlertTriangle,
  ArrowLeft,
  Building2,
  CalendarDays,
  Check,
  CheckCircle2,
  Circle,
  ClipboardCheck,
  Download,
  FilePlus2,
  FileText,
  Flag,
  History,
  Loader2,
  MapPin,
  Megaphone,
  Paperclip,
  RefreshCw,
  Send,
  ShieldCheck,
  ShoppingCart,
  UploadCloud,
  UserRound,
  XCircle,
} from 'lucide-react'
import * as React from 'react'
import { useFieldArray, useForm } from 'react-hook-form'
import { toast } from 'sonner'
import { z } from 'zod'
import { AppShell } from '@/components/layout/AppShell'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { useAuth } from '@/features/auth/hooks/useAuth'
import { isDemoSession } from '@/features/auth/demoSession'
import { getEligibleUsers } from '@/features/auth/api/userAdminApi'
import {
  completeMarketing,
  createPurchaseOrder,
  createRedFlag,
  downloadAttachment,
  exportRfq,
  getAttachments,
  getHistory,
  getQuotationDraft,
  getQuotationVersions,
  markReadyForMarketing,
  recordCustomerDecision,
  recordCustomerSubmission,
  getReviewerReassignmentRequests,
  requestReviewerReassignment,
  resolveReviewerReassignment,
  resolveWorkflow,
  saveQuotationDraft,
  setReviewerDelegation,
  submitQuotation,
  submitReview,
  assignRfq,
  uploadAttachment,
  type AttachmentDto,
  type QuotationDraftDto,
  type ReviewerReassignmentRequestDto,
} from '@/features/rfq/api/rfqApi'
import { useRfqDetail } from '@/features/rfq/hooks/useRfqDetail'
import { StatusPill } from '@/features/rfq/components/StatusPill'
import { useRfqDemoStore } from '@/features/rfq/store/rfqDemoStore'
import {
  demoUsers,
  formatRfqNumber,
  type QuotationLine,
  type RfqAssignmentRole,
  type RfqRecord,
  type WorkflowEvent,
} from '@/features/rfq/types'
import { queryClient } from '@/lib/query-client'
import { normalizeError, unexpectedErrorMessage } from '@/lib/errors'

export const Route = createFileRoute('/_authenticated/rfqs/$rfqId')({ component: RfqDetailPage })

type DetailTab = 'overview' | 'quotation' | 'files' | 'activity'

function DetailTabs({
  selected,
  onChange,
}: {
  selected: DetailTab
  onChange: (tab: DetailTab) => void
}) {
  const tabs: Array<{ id: DetailTab; label: string; icon: typeof FileText }> = [
    { id: 'overview', label: 'Request details', icon: FileText },
    { id: 'quotation', label: 'Quotation', icon: ClipboardCheck },
    { id: 'files', label: 'Attachments', icon: Paperclip },
    { id: 'activity', label: 'Workflow history', icon: History },
  ]
  return (
    <div className="flex gap-1 overflow-x-auto border-b border-slate-200">
      {tabs.map(({ id, label, icon: Icon }) => (
        <button
          key={id}
          type="button"
          onClick={() => onChange(id)}
          className={`inline-flex shrink-0 items-center gap-2 border-b-2 px-4 py-3 text-sm font-medium ${selected === id ? 'border-blue-700 text-blue-800' : 'border-transparent text-slate-500 hover:text-slate-800'}`}
          aria-current={selected === id ? 'page' : undefined}
        >
          <Icon className="size-4" aria-hidden="true" />
          {label}
        </button>
      ))}
    </div>
  )
}

function RfqDetailPage() {
  const { rfqId } = Route.useParams()
  const { user } = useAuth()
  const { rfq, isDemo, isLoading, error, refetch } = useRfqDetail(rfqId)
  const [tab, setTab] = React.useState<DetailTab>('overview')
  const demo = isDemo && isDemoSession()
  const role = user?.roles.includes('SUPER_ADMIN')
    ? 'SUPER_ADMIN'
    : user?.roles.includes('ADMIN')
      ? 'ADMIN'
      : (user?.roles[0] ?? 'VIEWER')
  const isAdmin = role === 'ADMIN' || role === 'SUPER_ADMIN'
  const invalidate = React.useCallback(async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ['rfqs'] }),
      queryClient.invalidateQueries({ queryKey: ['rfqs', rfqId] }),
    ])
    await refetch()
  }, [rfqId, refetch])
  const attachmentsQuery = useQuery({
    queryKey: ['rfqs', rfqId, 'attachments'],
    queryFn: () => getAttachments(rfqId),
    enabled: Boolean(rfq) && !demo,
  })
  const historyQuery = useQuery({
    queryKey: ['rfqs', rfqId, 'history'],
    queryFn: () => getHistory(rfqId),
    enabled: Boolean(rfq) && !demo,
  })
  const versionsQuery = useQuery({
    queryKey: ['rfqs', rfqId, 'quotation-versions'],
    queryFn: () => getQuotationVersions(rfqId),
    enabled: Boolean(rfq) && !demo,
  })
  const reassignmentQuery = useQuery({
    queryKey: ['rfqs', rfqId, 'reviewer-reassignment-requests'],
    queryFn: () => getReviewerReassignmentRequests(rfqId),
    enabled:
      Boolean(rfq) &&
      !demo &&
      (isAdmin ||
        (role === 'REVIEWER' &&
          Boolean(
            rfq?.assignments.some(
              (assignment) => assignment.role === 'REVIEWER' && assignment.userId === user?.id,
            ),
          ))),
  })
  const [demoFiles, setDemoFiles] = React.useState<
    Array<{ name: string; size: number; category: string; addedAt: string }>
  >([])
  const [savingFile, setSavingFile] = React.useState(false)
  const [exportingFormat, setExportingFormat] = React.useState<'xlsx' | 'pdf' | null>(null)

  async function downloadExport(format: 'xlsx' | 'pdf') {
    if (demo) {
      toast.info('Connect to RFQ Service to generate a server export.')
      return
    }
    setExportingFormat(format)
    try {
      const result = await exportRfq(rfqId, format)
      const objectUrl = URL.createObjectURL(result.blob)
      const anchor = document.createElement('a')
      anchor.href = objectUrl
      anchor.download = `RFQ-${rfqId}.${format}`
      document.body.append(anchor)
      anchor.click()
      anchor.remove()
      window.setTimeout(() => URL.revokeObjectURL(objectUrl), 1000)
      toast.success(`${format.toUpperCase()} export downloaded.`)
    } catch (downloadError) {
      toast.error(unexpectedErrorMessage(normalizeError(downloadError)))
    } finally {
      setExportingFormat(null)
    }
  }

  if (isLoading)
    return (
      <AppShell>
        <div className="py-20 text-center text-sm text-slate-500" role="status">
          Loading RFQ details…
        </div>
      </AppShell>
    )
  if (error)
    return (
      <AppShell>
        <div
          className="mx-auto max-w-xl rounded-xl border border-red-200 bg-white p-8 text-center"
          role="alert"
        >
          <AlertTriangle className="mx-auto size-8 text-red-600" />
          <h1 className="mt-3 text-xl font-bold">Could not load this RFQ</h1>
          <p className="mt-2 text-sm text-slate-500">
            Check your access and confirm RFQ Service is available.
          </p>
          <p className="mt-2 text-xs text-red-700">
            {unexpectedErrorMessage(normalizeError(error))}
          </p>
          <Button className="mt-5" variant="outline" onClick={() => void refetch()}>
            <RefreshCw className="size-4" />
            Try again
          </Button>
        </div>
      </AppShell>
    )
  if (!rfq)
    return (
      <AppShell>
        <div className="mx-auto max-w-xl rounded-xl border border-slate-200 bg-white p-8 text-center">
          <FileText className="mx-auto size-8 text-slate-400" />
          <h1 className="mt-3 text-xl font-bold">RFQ not found</h1>
          <p className="mt-2 text-sm text-slate-500">
            The request does not exist or is outside your authorized scope.
          </p>
          <Button asChild variant="outline" className="mt-5">
            <Link to="/rfqs">
              <ArrowLeft className="size-4" />
              Back to RFQs
            </Link>
          </Button>
        </div>
      </AppShell>
    )

  const currentManager = rfq.assignments.find(
    (assignment) => assignment.role === 'QUOTATION_MANAGER',
  )
  const currentReviewer = rfq.assignments.find((assignment) => assignment.role === 'REVIEWER')
  const currentMarketing = rfq.assignments.find(
    (assignment) => assignment.role === 'MARKETING_EXECUTIVE',
  )
  const isAssignedManager = role === 'QUOTATION_MANAGER' && currentManager?.userId === user?.id
  const isAssignedReviewer = role === 'REVIEWER' && currentReviewer?.userId === user?.id
  const isAssignedMarketing =
    role === 'MARKETING_EXECUTIVE' && currentMarketing?.userId === user?.id
  const versionRows = versionsQuery.data ?? []
  const currentVersion = demo ? rfq.currentQuotationVersion : versionRows.at(0)?.id
  const readText = (value: unknown, fallback = ''): string =>
    typeof value === 'string' ? value : typeof value === 'number' ? String(value) : fallback
  const liveHistory = (historyQuery.data ?? []).map((event, index) => ({
    id: readText(event['id'], String(index)),
    label: readText(event['eventType'], 'Workflow event').replaceAll('_', ' ').toLowerCase(),
    detail: readText(event['reason']) || readText(event['toStatus']),
    actor: readText(event['actorUserId']) ? `User ${readText(event['actorUserId'])}` : 'System',
    createdAt: readText(event['occurredAt']),
    tone: 'blue' as const,
  }))
  const history = demo ? rfq.events : liveHistory

  return (
    <AppShell>
      <div className="mb-4 flex items-center gap-2">
        <Button asChild size="sm" variant="ghost">
          <Link to="/rfqs">
            <ArrowLeft className="size-4" />
            RFQ workspace
          </Link>
        </Button>
        <span className="text-xs text-slate-400">/</span>
        <span className="text-xs text-slate-500">{formatRfqNumber(rfq)}</span>
      </div>
      <div className="mb-5 flex flex-wrap items-start justify-between gap-4">
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-3">
            <h1 className="text-2xl font-bold tracking-tight text-slate-950">
              {formatRfqNumber(rfq)}
            </h1>
            <StatusPill status={rfq.status} />
          </div>
          <p className="mt-1.5 truncate text-base font-medium text-slate-800">{rfq.requestTitle}</p>
          <p className="mt-1 text-sm text-slate-500">
            {rfq.customerName} <span className="mx-1.5 text-slate-300">•</span> Source RFQ{' '}
            {rfq.sourceRfqId}
          </p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <span className="rounded-lg border border-slate-200 bg-white px-3 py-2 text-xs text-slate-500">
            Version {rfq.version}
          </span>
          <Button
            size="sm"
            variant="outline"
            disabled={demo || exportingFormat !== null}
            onClick={() => void downloadExport('xlsx')}
          >
            {exportingFormat === 'xlsx' ? (
              <Loader2 className="size-4 animate-spin" />
            ) : (
              <Download className="size-4" />
            )}
            Excel
          </Button>
          <Button
            size="sm"
            variant="outline"
            disabled={demo || exportingFormat !== null}
            onClick={() => void downloadExport('pdf')}
          >
            {exportingFormat === 'pdf' ? (
              <Loader2 className="size-4 animate-spin" />
            ) : (
              <Download className="size-4" />
            )}
            PDF
          </Button>
          {(isAdmin || isAssignedManager || isAssignedReviewer) && (
            <Button size="sm" onClick={() => setTab('files')}>
              <FilePlus2 className="size-4" />
              Add attachment
            </Button>
          )}
        </div>
      </div>
      <ProgressStrip status={rfq.status} />

      <div className="mt-5 grid items-start gap-5 xl:grid-cols-[minmax(0,1fr)_340px]">
        <section className="overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm">
          <DetailTabs selected={tab} onChange={setTab} />
          <div className="p-4 sm:p-6">
            {tab === 'overview' && <OverviewPanel rfq={rfq} />}
            {tab === 'quotation' && (
              <QuotationPanel
                rfq={rfq}
                demo={demo}
                canEdit={isAssignedManager}
                canReview={isAssignedReviewer}
                isAdmin={isAdmin}
                versions={versionRows}
                invalidate={invalidate}
              />
            )}
            {tab === 'files' && (
              <FilesPanel
                rfq={rfq}
                demo={demo}
                canUpload={isAdmin || isAssignedManager || isAssignedReviewer}
                attachments={attachmentsQuery.data ?? []}
                loading={attachmentsQuery.isLoading}
                demoFiles={demoFiles}
                setDemoFiles={setDemoFiles}
                saving={savingFile}
                setSaving={setSavingFile}
              />
            )}
            {tab === 'activity' && <HistoryPanel events={history} />}
          </div>
        </section>

        <aside className="space-y-4">
          <AssignmentPanel
            rfq={rfq}
            demo={demo}
            userName={user?.fullName ?? 'User'}
            isAdmin={isAdmin}
            isAssignedManager={isAssignedManager}
            invalidate={invalidate}
          />
          {(isAdmin || isAssignedReviewer) && (
            <ReviewerReassignmentPanel
              rfqId={rfq.id}
              demo={demo}
              canRequest={isAssignedReviewer}
              canResolve={isAdmin}
              requests={reassignmentQuery.data ?? []}
              loading={reassignmentQuery.isLoading}
              onChanged={async () => {
                await queryClient.invalidateQueries({
                  queryKey: ['rfqs', rfqId, 'reviewer-reassignment-requests'],
                })
                await invalidate()
              }}
            />
          )}
          {isAssignedReviewer && rfq.status === 'PENDING_REVIEW' && (
            <ReviewActionPanel
              rfq={rfq}
              demo={demo}
              latestVersionId={currentVersion}
              versions={versionRows}
              invalidate={invalidate}
            />
          )}
          {isAdmin && (
            <AdminActionPanel
              rfq={rfq}
              demo={demo}
              latestVersionId={currentVersion}
              userName={user?.fullName ?? 'Administrator'}
              invalidate={invalidate}
            />
          )}
          <MarketingWorkflowPanel
            rfq={rfq}
            demo={demo}
            isAdmin={isAdmin}
            isAssignedMarketing={isAssignedMarketing}
            userName={user?.fullName ?? 'User'}
            invalidate={invalidate}
          />
          <RfqGovernanceQuickPanel
            rfq={rfq}
            demo={demo}
            latestVersionId={currentVersion}
            isAdmin={isAdmin}
            userId={user?.id ?? 1}
            invalidate={invalidate}
          />
          <Card className="border-slate-200 p-4 shadow-none">
            <div className="flex items-center gap-2 text-sm font-semibold">
              <ShieldCheck className="size-4 text-emerald-600" />
              Access & traceability
            </div>
            <p className="mt-2 text-xs leading-5 text-slate-500">
              Role permissions and workflow transitions are enforced by the RFQ Service API.
              Submitted quotation versions and audit events are retained as history.
            </p>
          </Card>
        </aside>
      </div>
    </AppShell>
  )
}

function ReviewerReassignmentPanel({
  rfqId,
  demo,
  canRequest,
  canResolve,
  requests,
  loading,
  onChanged,
}: {
  rfqId: string
  demo: boolean
  canRequest: boolean
  canResolve: boolean
  requests: ReviewerReassignmentRequestDto[]
  loading: boolean
  onChanged: () => Promise<void>
}) {
  const [requestReason, setRequestReason] = React.useState('')
  const [resolutionReason, setResolutionReason] = React.useState('')
  const [replacementReviewerId, setReplacementReviewerId] = React.useState('')
  const [busy, setBusy] = React.useState(false)
  const pending = requests.filter((request) => request.status === 'PENDING')

  async function createRequest() {
    if (requestReason.trim().length < 5) {
      toast.error('Provide a reassignment reason of at least 5 characters.')
      return
    }
    setBusy(true)
    try {
      await requestReviewerReassignment(rfqId, requestReason.trim())
      setRequestReason('')
      toast.success('Reviewer reassignment request sent to the Admin.')
      await onChanged()
    } catch (error) {
      toast.error(unexpectedErrorMessage(normalizeError(error)))
    } finally {
      setBusy(false)
    }
  }

  async function resolveRequest(requestId: number, decision: 'APPROVE' | 'DECLINE') {
    if (resolutionReason.trim().length < 5) {
      toast.error('Provide an administrative decision reason of at least 5 characters.')
      return
    }
    const replacement = Number(replacementReviewerId)
    if (decision === 'APPROVE' && (!Number.isInteger(replacement) || replacement <= 0)) {
      toast.error('Enter the Identity user ID for the replacement reviewer.')
      return
    }
    setBusy(true)
    try {
      await resolveReviewerReassignment(
        rfqId,
        requestId,
        decision,
        resolutionReason.trim(),
        decision === 'APPROVE' ? replacement : undefined,
      )
      setResolutionReason('')
      setReplacementReviewerId('')
      toast.success(
        decision === 'APPROVE' ? 'Reviewer reassigned.' : 'Reassignment request declined.',
      )
      await onChanged()
    } catch (error) {
      toast.error(unexpectedErrorMessage(normalizeError(error)))
    } finally {
      setBusy(false)
    }
  }

  return (
    <Card className="border-slate-200 p-4 shadow-none">
      <div className="flex items-center gap-2 text-sm font-semibold">
        <UserRound className="size-4 text-blue-700" /> Reviewer reassignment
      </div>
      {demo ? (
        <p className="mt-2 text-xs leading-5 text-slate-500">
          Reviewer reassignment requests use the live RFQ Service workflow and are unavailable in
          demo mode.
        </p>
      ) : (
        <>
          {loading ? (
            <p className="mt-3 flex items-center gap-2 text-xs text-slate-500">
              <Loader2 className="size-3.5 animate-spin" /> Loading requests…
            </p>
          ) : requests.length ? (
            <ul className="mt-3 space-y-2">
              {requests.map((request) => (
                <li key={request.id} className="rounded-lg border border-slate-200 p-3">
                  <div className="flex items-center justify-between gap-2">
                    <span className="text-xs font-semibold">Request #{request.id}</span>
                    <span className="rounded-full bg-slate-100 px-2 py-0.5 text-[10px] font-semibold">
                      {request.status.replaceAll('_', ' ')}
                    </span>
                  </div>
                  <p className="mt-1.5 text-xs leading-5 text-slate-600">{request.requestReason}</p>
                  {request.resolutionReason && (
                    <p className="mt-1 text-xs text-slate-500">Admin: {request.resolutionReason}</p>
                  )}
                </li>
              ))}
            </ul>
          ) : (
            <p className="mt-2 text-xs text-slate-500">No reviewer reassignment requests.</p>
          )}
          {canRequest && (
            <div className="mt-3 space-y-2 border-t border-slate-100 pt-3">
              <label
                className="block text-xs font-medium text-slate-700"
                htmlFor="reviewer-reassignment-reason"
              >
                Request Admin reassignment
              </label>
              <textarea
                id="reviewer-reassignment-reason"
                value={requestReason}
                onChange={(event) => setRequestReason(event.target.value)}
                maxLength={1000}
                rows={3}
                className="w-full rounded-md border border-slate-200 px-2.5 py-2 text-sm"
                placeholder="Explain why a different reviewer is needed"
              />
              <Button
                size="sm"
                className="w-full"
                disabled={busy || requestReason.trim().length < 5}
                onClick={() => void createRequest()}
              >
                Send request
              </Button>
            </div>
          )}
          {canResolve &&
            pending.map((request) => (
              <div
                key={`resolve-${request.id}`}
                className="mt-3 space-y-2 border-t border-slate-100 pt-3"
              >
                <p className="text-xs font-semibold">Admin decision for request #{request.id}</p>
                <label
                  className="block text-xs font-medium text-slate-700"
                  htmlFor={`replacement-reviewer-${request.id}`}
                >
                  Replacement reviewer Identity user ID (required to approve)
                </label>
                <Input
                  id={`replacement-reviewer-${request.id}`}
                  type="number"
                  min={1}
                  value={replacementReviewerId}
                  onChange={(event) => setReplacementReviewerId(event.target.value)}
                />
                <label
                  className="block text-xs font-medium text-slate-700"
                  htmlFor={`reassignment-decision-reason-${request.id}`}
                >
                  Decision reason
                </label>
                <textarea
                  id={`reassignment-decision-reason-${request.id}`}
                  value={resolutionReason}
                  onChange={(event) => setResolutionReason(event.target.value)}
                  maxLength={1000}
                  rows={2}
                  className="w-full rounded-md border border-slate-200 px-2.5 py-2 text-sm"
                />
                <div className="grid grid-cols-2 gap-2">
                  <Button
                    size="sm"
                    disabled={busy || resolutionReason.trim().length < 5}
                    onClick={() => void resolveRequest(request.id, 'APPROVE')}
                  >
                    Approve
                  </Button>
                  <Button
                    size="sm"
                    variant="outline"
                    disabled={busy || resolutionReason.trim().length < 5}
                    onClick={() => void resolveRequest(request.id, 'DECLINE')}
                  >
                    Decline
                  </Button>
                </div>
              </div>
            ))}
        </>
      )}
    </Card>
  )
}

function ProgressStrip({ status }: { status: RfqRecord['status'] }) {
  const stages = [
    { label: 'Imported', complete: true },
    {
      label: 'Quotation',
      complete: [
        'PENDING_REVIEW',
        'REJECTED_BY_REVIEWER',
        'AWAITING_REASSIGNMENT',
        'REVISION_IN_PROGRESS',
        'APPROVED_BY_REVIEWER',
        'SENT_TO_CUSTOMER',
        'CUSTOMER_APPROVED',
        'CUSTOMER_REJECTED',
        'READY_FOR_MARKETING',
        'ASSIGNED_TO_MARKETING',
        'ESCALATED',
        'CLOSED',
      ].includes(status),
    },
    {
      label: 'Review',
      complete: [
        'APPROVED_BY_REVIEWER',
        'SENT_TO_CUSTOMER',
        'CUSTOMER_APPROVED',
        'CUSTOMER_REJECTED',
        'READY_FOR_MARKETING',
        'ASSIGNED_TO_MARKETING',
      ].includes(status),
    },
    {
      label: 'Customer',
      complete: [
        'CUSTOMER_APPROVED',
        'CUSTOMER_REJECTED',
        'READY_FOR_MARKETING',
        'ASSIGNED_TO_MARKETING',
      ].includes(status),
    },
    { label: 'Marketing', complete: status === 'ASSIGNED_TO_MARKETING' },
  ]
  return (
    <div className="rounded-xl border border-slate-200 bg-white px-4 py-4 shadow-sm">
      <div className="flex items-center justify-between gap-2">
        {stages.map((stage, index) => (
          <React.Fragment key={stage.label}>
            <div className="flex min-w-0 items-center gap-2">
              <span
                className={`flex size-7 shrink-0 items-center justify-center rounded-full ${stage.complete ? 'bg-emerald-100 text-emerald-700' : 'bg-slate-100 text-slate-400'}`}
              >
                {stage.complete ? (
                  <Check className="size-4" aria-hidden="true" />
                ) : (
                  <span className="size-2 rounded-full bg-current" />
                )}
              </span>
              <span
                className={`hidden truncate text-xs font-medium sm:inline ${stage.complete ? 'text-slate-800' : 'text-slate-400'}`}
              >
                {stage.label}
              </span>
            </div>
            {index < stages.length - 1 && (
              <span
                className={`h-px min-w-3 flex-1 ${stage.complete ? 'bg-emerald-300' : 'bg-slate-200'}`}
              />
            )}
          </React.Fragment>
        ))}
      </div>
    </div>
  )
}

function OverviewPanel({ rfq }: { rfq: RfqRecord }) {
  const buyerDetails = [rfq.buyerName || '—', rfq.buyerEmail || '—']
  return (
    <div className="space-y-6">
      <div>
        <SectionHeading
          title="Request summary"
          description="Customer source data carried into Global RFQ."
        />
        <div className="mt-4 grid gap-3 sm:grid-cols-2">
          <InfoCell icon={Building2} label="Customer" value={rfq.customerName} />
          <InfoCell icon={UserRound} label="Buyer contact" value={buyerDetails.join(' · ')} />
          <InfoCell
            icon={CalendarDays}
            label="Requested delivery"
            value={
              rfq.requestedDeliveryDate
                ? new Intl.DateTimeFormat('en-IN', { dateStyle: 'medium' }).format(
                    new Date(`${rfq.requestedDeliveryDate}T00:00:00`),
                  )
                : 'Not provided'
            }
          />
          <InfoCell icon={MapPin} label="Ship to" value={rfq.shipToText || 'Not provided'} />
        </div>
      </div>
      <div>
        <SectionHeading
          title="Requested line items"
          description={`${rfq.lineItems.length} item${rfq.lineItems.length === 1 ? '' : 's'} from the customer request.`}
        />
        <div className="mt-4 space-y-3">
          {rfq.lineItems.length ? (
            rfq.lineItems.map((line) => (
              <div key={line.sourceLineId} className="rounded-lg border border-slate-200 p-4">
                <div className="flex flex-wrap items-start justify-between gap-2">
                  <div className="flex items-start gap-3">
                    <span className="flex size-8 shrink-0 items-center justify-center rounded-lg bg-blue-50 text-xs font-bold text-blue-800">
                      {line.lineNumber}
                    </span>
                    <div>
                      <p className="text-sm leading-5 font-semibold text-slate-800">
                        {line.description}
                      </p>
                      <p className="mt-2 text-xs text-slate-500">
                        Requested part: {line.requestedPartNumber || '—'}{' '}
                        <span className="mx-1 text-slate-300">·</span> Manufacturer:{' '}
                        {line.requestedManufacturer || '—'}
                      </p>
                      {line.technicalSpecifications && (
                        <p className="mt-2 text-xs leading-5 whitespace-pre-wrap text-slate-500">
                          {line.technicalSpecifications}
                        </p>
                      )}
                    </div>
                  </div>
                  <span className="rounded-md bg-slate-100 px-2 py-1 text-xs font-semibold whitespace-nowrap text-slate-700">
                    {line.requestedQuantity} {line.unitOfMeasure || 'units'}
                  </span>
                </div>
                <div className="mt-3 flex flex-wrap gap-2">
                  {line.procurementCategory && (
                    <span className="rounded bg-blue-50 px-2 py-1 text-[11px] font-medium text-blue-800">
                      {line.procurementCategory}
                    </span>
                  )}
                  {line.taaRequired && (
                    <span className="rounded bg-emerald-50 px-2 py-1 text-[11px] font-medium text-emerald-800">
                      TAA compliance required
                    </span>
                  )}
                </div>
              </div>
            ))
          ) : (
            <EmptyMessage>No line items were returned by the service.</EmptyMessage>
          )}
        </div>
      </div>
      <div className="grid gap-4 sm:grid-cols-2">
        <InfoCell
          icon={FileText}
          label="Source system"
          value={rfq.sourceSystem || 'Source software'}
        />
        <InfoCell
          icon={FileText}
          label="Source file"
          value={rfq.sourceFileName || 'Stored source document'}
        />
      </div>
    </div>
  )
}

function InfoCell({
  icon: Icon,
  label,
  value,
}: {
  icon: typeof FileText
  label: string
  value: string
}) {
  return (
    <div className="flex gap-3 rounded-lg bg-slate-50 p-3.5">
      <span className="flex size-8 shrink-0 items-center justify-center rounded-lg bg-white text-slate-500 shadow-sm">
        <Icon className="size-4" aria-hidden="true" />
      </span>
      <div className="min-w-0">
        <p className="text-[11px] font-medium tracking-wide text-slate-400 uppercase">{label}</p>
        <p className="mt-1 text-sm leading-5 font-medium break-words whitespace-pre-line text-slate-800">
          {value}
        </p>
      </div>
    </div>
  )
}

function SectionHeading({ title, description }: { title: string; description: string }) {
  return (
    <div>
      <h2 className="text-base font-semibold text-slate-900">{title}</h2>
      <p className="mt-1 text-xs text-slate-500">{description}</p>
    </div>
  )
}

function EmptyMessage({ children }: { children: React.ReactNode }) {
  return (
    <div className="rounded-lg border border-dashed border-slate-200 px-4 py-8 text-center text-sm text-slate-500">
      {children}
    </div>
  )
}

function AssignmentPanel({
  rfq,
  demo,
  userName,
  isAdmin,
  isAssignedManager,
  invalidate,
}: {
  rfq: RfqRecord
  demo: boolean
  userName: string
  isAdmin: boolean
  isAssignedManager: boolean
  invalidate: () => Promise<void>
}) {
  const assign = useRfqDemoStore((state) => state.assign)
  const setDelegation = useRfqDemoStore((state) => state.setReviewerDelegation)
  const [assignmentRole, setAssignmentRole] = React.useState<RfqAssignmentRole>('QUOTATION_MANAGER')
  const [assignedUserId, setAssignedUserId] = React.useState('')
  const [manualUserIdEntry, setManualUserIdEntry] = React.useState(false)
  const [reason, setReason] = React.useState('')
  const [busy, setBusy] = React.useState(false)
  const [delegationReason, setDelegationReason] = React.useState(
    'Client delegation approved for this RFQ',
  )
  const eligibleUsersQuery = useQuery({
    queryKey: ['identity', 'eligible-users', assignmentRole],
    queryFn: () => getEligibleUsers(assignmentRole),
    enabled: !demo,
    staleTime: 60_000,
  })
  const current = rfq.assignments.find((assignment) => assignment.role === assignmentRole)
  const canAssignReviewer = isAdmin || (isAssignedManager && rfq.quotationManagerCanAssignReviewer)

  async function assignUser(event: React.FormEvent) {
    event.preventDefault()
    const parsedId = Number(assignedUserId)
    if (!Number.isInteger(parsedId) || parsedId <= 0) {
      toast.error('Select a valid assignee.')
      return
    }
    if (current && reason.trim().length < 5) {
      toast.error('Enter a reason for reassignment (at least 5 characters).')
      return
    }
    const demoUser = demoUsers.find((candidate) => candidate.id === parsedId)
    setBusy(true)
    try {
      if (demo)
        assign(
          rfq.id,
          assignmentRole,
          parsedId,
          demoUser?.fullName ?? `User ${parsedId}`,
          reason.trim(),
          userName,
        )
      else await assignRfq(rfq.id, assignmentRole, parsedId, reason.trim())
      toast.success(`${assignmentRole.replaceAll('_', ' ').toLowerCase()} assigned.`)
      setAssignedUserId('')
      setReason('')
      await invalidate()
    } catch (error) {
      toast.error(unexpectedErrorMessage(normalizeError(error)))
    } finally {
      setBusy(false)
    }
  }

  async function toggleDelegation(allowed: boolean) {
    if (delegationReason.trim().length < 5) {
      toast.error('Enter a reason for the permission change.')
      return
    }
    try {
      if (demo) setDelegation(rfq.id, allowed, delegationReason, userName)
      else await setReviewerDelegation(rfq.id, allowed, delegationReason)
      toast.success(
        allowed
          ? 'Reviewer assignment delegation enabled.'
          : 'Reviewer assignment delegation disabled.',
      )
      await invalidate()
    } catch (error) {
      toast.error(unexpectedErrorMessage(normalizeError(error)))
    }
  }

  if (!isAdmin && !canAssignReviewer)
    return (
      <Card className="border-slate-200 p-4 shadow-none">
        <h2 className="text-sm font-semibold">Current ownership</h2>
        <div className="mt-3 space-y-3">
          {(['QUOTATION_MANAGER', 'REVIEWER', 'MARKETING_EXECUTIVE'] as const).map((key) => {
            const assignment = rfq.assignments.find((item) => item.role === key)
            return (
              <div key={key} className="flex items-center justify-between gap-3 text-xs">
                <span className="text-slate-500">{key.replaceAll('_', ' ')}</span>
                <span className="font-medium text-slate-800">
                  {assignment?.userName ??
                    (assignment ? `User ${assignment.userId}` : 'Unassigned')}
                </span>
              </div>
            )
          })}
        </div>
      </Card>
    )

  const availableRoles: RfqAssignmentRole[] = isAdmin
    ? [
        'QUOTATION_MANAGER',
        'REVIEWER',
        ...(rfq.customerDecision === 'APPROVED' ||
        rfq.status === 'READY_FOR_MARKETING' ||
        rfq.status === 'ASSIGNED_TO_MARKETING'
          ? ['MARKETING_EXECUTIVE' as const]
          : []),
      ]
    : ['REVIEWER']
  const availableUsers =
    assignmentRole === 'QUOTATION_MANAGER'
      ? demoUsers.filter((candidate) => candidate.roles.includes('QUOTATION_MANAGER'))
      : assignmentRole === 'REVIEWER'
        ? demoUsers.filter((candidate) => candidate.roles.includes('REVIEWER'))
        : demoUsers.filter((candidate) => candidate.roles.includes('MARKETING_EXECUTIVE'))
  return (
    <Card className="border-slate-200 p-4 shadow-none">
      <div className="flex items-start justify-between gap-2">
        <div>
          <h2 className="text-sm font-semibold">Assignments</h2>
          <p className="mt-1 text-xs text-slate-500">
            {isAdmin
              ? 'Controlled routing and reassignment.'
              : 'Assign the reviewer only when delegated.'}
          </p>
        </div>
        <UserRound className="size-4 text-slate-400" />
      </div>
      <div className="mt-3 space-y-2">
        {(['QUOTATION_MANAGER', 'REVIEWER', 'MARKETING_EXECUTIVE'] as const).map((key) => {
          const assignment = rfq.assignments.find((item) => item.role === key)
          return (
            <div
              key={key}
              className="flex items-center justify-between gap-3 rounded-md bg-slate-50 px-3 py-2 text-xs"
            >
              <span className="text-slate-500">{key.replaceAll('_', ' ')}</span>
              <span className="font-semibold text-slate-800">
                {assignment?.userName ?? (assignment ? `User ${assignment.userId}` : 'Unassigned')}
              </span>
            </div>
          )
        })}
      </div>
      <form onSubmit={assignUser} className="mt-4 space-y-3 border-t border-slate-100 pt-4">
        <label className="block text-xs font-medium text-slate-700">
          Role
          <select
            value={assignmentRole}
            onChange={(event) => {
              setAssignmentRole(event.target.value as RfqAssignmentRole)
              setAssignedUserId('')
            }}
            className="mt-1.5 h-9 w-full rounded-md border border-slate-200 bg-white px-2.5 text-sm"
          >
            {availableRoles.map((value) => (
              <option key={value} value={value}>
                {value.replaceAll('_', ' ')}
              </option>
            ))}
          </select>
        </label>
        {demo ? (
          <label className="block text-xs font-medium text-slate-700">
            Assign to
            <select
              required
              value={assignedUserId}
              onChange={(event) => setAssignedUserId(event.target.value)}
              className="mt-1.5 h-9 w-full rounded-md border border-slate-200 bg-white px-2.5 text-sm"
            >
              <option value="">Choose a user</option>
              {availableUsers.map((candidate) => (
                <option key={candidate.id} value={candidate.id}>
                  {candidate.fullName}
                </option>
              ))}
            </select>
          </label>
        ) : manualUserIdEntry ||
          eligibleUsersQuery.isError ||
          (eligibleUsersQuery.data && eligibleUsersQuery.data.length === 0) ? (
          <div className="space-y-1.5">
            <div className="flex items-center justify-between">
              <label htmlFor="identity-user-id" className="text-xs font-medium text-slate-700">
                User ID
              </label>
              {eligibleUsersQuery.data && eligibleUsersQuery.data.length > 0 && (
                <button
                  type="button"
                  onClick={() => {
                    setManualUserIdEntry(false)
                    setAssignedUserId('')
                  }}
                  className="text-[11px] font-medium text-blue-700 hover:underline"
                >
                  Choose from directory
                </button>
              )}
            </div>
            <Input
              id="identity-user-id"
              className="h-9"
              type="number"
              min="1"
              value={assignedUserId}
              onChange={(event) => setAssignedUserId(event.target.value)}
              placeholder="Enter identity user ID"
              required
            />
          </div>
        ) : (
          <div className="space-y-1.5">
            <div className="flex items-center justify-between">
              <label htmlFor="eligible-assignee-select" className="text-xs font-medium text-slate-700">
                Assign to
              </label>
              <button
                type="button"
                onClick={() => {
                  setManualUserIdEntry(true)
                  setAssignedUserId('')
                }}
                className="text-[11px] font-medium text-blue-700 hover:underline"
              >
                Enter ID manually
              </button>
            </div>
            <select
              id="eligible-assignee-select"
              required
              disabled={eligibleUsersQuery.isLoading}
              value={assignedUserId}
              onChange={(event) => setAssignedUserId(event.target.value)}
              className="h-9 w-full rounded-md border border-slate-200 bg-white px-2.5 text-sm"
            >
              <option value="">
                {eligibleUsersQuery.isLoading
                  ? 'Loading eligible users…'
                  : 'Choose an active user'}
              </option>
              {(eligibleUsersQuery.data ?? []).map((candidate) => (
                <option key={candidate.id} value={candidate.id}>
                  {candidate.fullName} ({candidate.officialEmail})
                </option>
              ))}
            </select>
          </div>
        )}
        {current && (
          <label className="block text-xs font-medium text-slate-700">
            Reassignment reason *
            <textarea
              value={reason}
              onChange={(event) => setReason(event.target.value)}
              maxLength={1000}
              rows={2}
              required
              className="mt-1.5 w-full rounded-md border border-slate-200 px-2.5 py-2 text-sm"
              placeholder="Explain why ownership is changing"
            />
          </label>
        )}
        <Button type="submit" size="sm" className="w-full" disabled={busy || !assignedUserId}>
          {busy ? <Loader2 className="size-4 animate-spin" /> : <UserRound className="size-4" />}
          {current ? 'Reassign' : 'Assign'}
        </Button>
      </form>
      {isAdmin && (
        <div className="mt-4 border-t border-slate-100 pt-4">
          <label className="block text-xs font-medium text-slate-700">
            Manager reviewer delegation reason
            <textarea
              value={delegationReason}
              onChange={(event) => setDelegationReason(event.target.value)}
              maxLength={1000}
              rows={2}
              className="mt-1.5 w-full rounded-md border border-slate-200 px-2.5 py-2 text-sm"
            />
          </label>
          <Button
            size="sm"
            variant="outline"
            className="mt-2 w-full"
            onClick={() => void toggleDelegation(!rfq.quotationManagerCanAssignReviewer)}
          >
            {rfq.quotationManagerCanAssignReviewer
              ? 'Disable manager reviewer assignment'
              : 'Allow manager to assign reviewer'}
          </Button>
          <p className="mt-2 text-[11px] leading-4 text-slate-500">
            Current setting:{' '}
            {rfq.quotationManagerCanAssignReviewer ? 'enabled' : 'admin controlled'}
          </p>
        </div>
      )}
    </Card>
  )
}

const quotationSchema = z.object({
  currency: z.string().regex(/^[A-Z]{3}$/, 'Use an uppercase ISO currency code, e.g. USD.'),
  freight: z.number().finite().min(0),
  otherCharges: z.number().finite().min(0),
  discount: z.number().finite().min(0),
  taxAmount: z.number().finite().min(0),
  deliveryTimeline: z.string().max(500),
  warranty: z.string().max(500),
  notes: z.string().max(10000),
  items: z
    .array(
      z.object({
        sourceLineId: z.string().min(1),
        description: z.string().max(20000),
        quantity: z.number().finite().positive('Quantity must be greater than zero.'),
        unitPrice: z.number().finite().min(0, 'Price cannot be negative.'),
        unitOfMeasure: z.string().max(40),
        manufacturer: z.string().max(255),
        partNumber: z.string().max(255),
        taaCompliant: z.boolean().nullable(),
        deliveryTimeline: z.string().max(500),
        warranty: z.string().max(500),
        note: z.string().max(4000),
        productUrl: z.union([
          z.literal(''),
          z.string().url('Enter a valid product URL.').max(1000),
        ]),
      }),
    )
    .min(1),
})
type QuotationFormValues = z.infer<typeof quotationSchema>

function toFormDraft(rfq: RfqRecord): QuotationFormValues {
  const items = rfq.quoteDraft.items.length
    ? rfq.quoteDraft.items
    : rfq.lineItems.map((line) => ({
        sourceLineId: line.sourceLineId,
        description: line.description,
        quantity: line.requestedQuantity,
        unitOfMeasure: line.unitOfMeasure,
        manufacturer: line.requestedManufacturer,
        partNumber: line.requestedPartNumber,
        unitPrice: 0,
        taaCompliant: null,
        deliveryTimeline: '',
        warranty: '',
        note: '',
        productUrl: '',
      }))
  return {
    currency: rfq.quoteDraft.currency || 'USD',
    freight: rfq.quoteDraft.freight || 0,
    otherCharges: rfq.quoteDraft.otherCharges || 0,
    discount: rfq.quoteDraft.discount || 0,
    taxAmount: rfq.quoteDraft.taxAmount || 0,
    deliveryTimeline: rfq.quoteDraft.deliveryTimeline || '',
    warranty: rfq.quoteDraft.warranty || '',
    notes: rfq.quoteDraft.notes || '',
    items,
  }
}

function QuotationPanel({
  rfq,
  demo,
  canEdit,
  canReview,
  isAdmin,
  versions,
  invalidate,
}: {
  rfq: RfqRecord
  demo: boolean
  canEdit: boolean
  canReview: boolean
  isAdmin: boolean
  versions: Awaited<ReturnType<typeof getQuotationVersions>>
  invalidate: () => Promise<void>
}) {
  const [showEditor, setShowEditor] = React.useState(
    canEdit &&
      [
        'ASSIGNED_TO_QUOTATION_MANAGER',
        'QUOTATION_IN_PROGRESS',
        'REJECTED_BY_REVIEWER',
        'REVISION_IN_PROGRESS',
        'REOPENED_FOR_REVISION',
      ].includes(rfq.status),
  )
  const canCreateDraft =
    canEdit &&
    [
      'ASSIGNED_TO_QUOTATION_MANAGER',
      'QUOTATION_IN_PROGRESS',
      'REJECTED_BY_REVIEWER',
      'REVISION_IN_PROGRESS',
      'REOPENED_FOR_REVISION',
    ].includes(rfq.status)
  if (canEdit && canCreateDraft && showEditor)
    return <QuotationEditor key={rfq.id} rfq={rfq} demo={demo} invalidate={invalidate} />
  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <SectionHeading
            title="Quotation versions"
            description="Submitted versions are immutable. Corrections must be saved as a new draft and resubmitted."
          />
        </div>
        {canEdit && canCreateDraft && (
          <Button size="sm" onClick={() => setShowEditor(true)}>
            <FilePlus2 className="size-4" />
            Open quotation draft
          </Button>
        )}
      </div>
      {demo ? (
        rfq.quotationVersions.length ? (
          <div className="space-y-3">
            {[...rfq.quotationVersions].reverse().map((version) => {
              const snapshot = version.draftSnapshot
              const lineTotal = snapshot.items.reduce(
                (total, line) => total + line.quantity * (line.unitPrice || 0),
                0,
              )
              return (
                <div key={version.id} className="rounded-xl border border-slate-200 p-4">
                  <div className="flex flex-wrap items-start justify-between gap-3">
                    <div>
                      <p className="font-semibold">Quotation V{version.versionNumber}</p>
                      <p className="mt-1 text-xs text-slate-500">
                        Submitted{' '}
                        {new Intl.DateTimeFormat('en-IN', {
                          dateStyle: 'medium',
                          timeStyle: 'short',
                        }).format(new Date(version.submittedAt))}{' '}
                        · {version.status.replaceAll('_', ' ').toLowerCase()}
                      </p>
                    </div>
                    <span className="rounded-md bg-slate-100 px-2 py-1 text-xs font-semibold">
                      {snapshot.currency}
                    </span>
                  </div>
                  <div className="mt-4 space-y-2">
                    {snapshot.items.map((line, index) => (
                      <div
                        key={`${line.sourceLineId}-${index}`}
                        className="grid gap-1 border-t border-slate-100 pt-3 text-sm sm:grid-cols-[1fr_auto]"
                      >
                        <div>
                          <p className="font-medium">{line.description}</p>
                          <p className="mt-1 text-xs text-slate-500">
                            {line.manufacturer || 'Manufacturer pending'} ·{' '}
                            {line.partNumber || 'Part number pending'} · Qty {line.quantity}
                          </p>
                        </div>
                        <p className="font-semibold tabular-nums">
                          {line.unitPrice
                            ? new Intl.NumberFormat('en-US', {
                                style: 'currency',
                                currency: snapshot.currency,
                              }).format(line.unitPrice * line.quantity)
                            : 'Price pending'}
                        </p>
                      </div>
                    ))}
                  </div>
                  <div className="mt-4 flex flex-wrap gap-x-5 gap-y-2 border-t border-slate-100 pt-3 text-xs text-slate-500">
                    <span>
                      Items total: {lineTotal.toFixed(2)} {snapshot.currency}
                    </span>
                    <span>
                      Freight: {snapshot.freight.toFixed(2)} {snapshot.currency}
                    </span>
                    <span>Delivery: {snapshot.deliveryTimeline || 'Pending'}</span>
                    <span>Warranty: {snapshot.warranty || 'Pending'}</span>
                  </div>
                  {version.missingMandatoryFields.length > 0 && (
                    <p className="mt-3 rounded-md bg-amber-50 p-2.5 text-xs text-amber-900">
                      Incomplete at submission: {version.missingMandatoryFields.join(', ')}
                    </p>
                  )}
                  {version.reviewDecision && (
                    <p className="mt-3 border-t border-slate-100 pt-3 text-xs text-slate-600">
                      {version.reviewDecision === 'APPROVED' ? 'Approved' : 'Rejected'} by{' '}
                      {version.reviewerName}
                      {version.reviewReason ? ` · ${version.reviewReason}` : ''}
                    </p>
                  )}
                </div>
              )
            })}
          </div>
        ) : (
          <EmptyMessage>No quotation version has been submitted.</EmptyMessage>
        )
      ) : versions.length ? (
        <div className="space-y-3">
          {versions
            .slice()
            .reverse()
            .map((version) => (
              <div
                key={version.id}
                className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-slate-200 p-4"
              >
                <div>
                  <p className="font-semibold">Quotation V{version.versionNumber}</p>
                  <p className="mt-1 text-xs text-slate-500">
                    {new Intl.DateTimeFormat('en-IN', {
                      dateStyle: 'medium',
                      timeStyle: 'short',
                    }).format(new Date(version.submittedAt))}{' '}
                    · {version.currency}{' '}
                    {Number(version.grandTotal).toLocaleString('en-US', {
                      minimumFractionDigits: 2,
                    })}
                  </p>
                  {version.missingMandatoryFields.length > 0 && (
                    <p className="mt-2 text-xs font-medium text-amber-800">
                      Incomplete: {version.missingMandatoryFields.join(', ')}
                    </p>
                  )}
                </div>
                <span
                  className={`rounded-md px-2 py-1 text-xs font-semibold ${version.status === 'APPROVED' ? 'bg-emerald-50 text-emerald-800' : version.status === 'REJECTED' ? 'bg-red-50 text-red-800' : 'bg-amber-50 text-amber-800'}`}
                >
                  {version.status.replaceAll('_', ' ')}
                </span>
              </div>
            ))}
        </div>
      ) : (
        <EmptyMessage>No submitted quotation versions were found.</EmptyMessage>
      )}
      {!canEdit && canReview && (
        <p className="rounded-lg bg-blue-50 p-3 text-xs leading-5 text-blue-900">
          Submitted quotation snapshots are read-only. Use the review controls on this page to
          record your decision.
        </p>
      )}
      {isAdmin && (
        <p className="rounded-lg bg-slate-50 p-3 text-xs leading-5 text-slate-600">
          Administrators can inspect quotation history but do not alter manager drafts or submitted
          snapshots.
        </p>
      )}
      {!canEdit && !canReview && !isAdmin && (
        <p className="text-xs text-slate-500">
          You have read-only access to quotation history for this RFQ.
        </p>
      )}
    </div>
  )
}

function QuotationEditor({
  rfq,
  demo,
  invalidate,
}: {
  rfq: RfqRecord
  demo: boolean
  invalidate: () => Promise<void>
}) {
  const updateDraft = useRfqDemoStore((state) => state.updateDraft)
  const submitDraft = useRfqDemoStore((state) => state.submitDraft)
  const { user } = useAuth()
  const [busy, setBusy] = React.useState(false)
  const liveDraftQuery = useQuery({
    queryKey: ['rfqs', rfq.id, 'quotation-draft'],
    queryFn: () => getQuotationDraft(rfq.id),
    enabled: !demo,
  })
  const {
    register,
    control,
    handleSubmit,
    watch,
    reset,
    formState: { errors },
  } = useForm<QuotationFormValues>({
    resolver: zodResolver(quotationSchema),
    defaultValues: toFormDraft(rfq),
  })
  const { fields } = useFieldArray({ control, name: 'items' })
  React.useEffect(() => {
    if (!demo && liveDraftQuery.data) reset({ ...toFormDraft(rfq), ...liveDraftQuery.data })
  }, [demo, liveDraftQuery.data, reset, rfq])
  const form = watch()
  const subtotal =
    form.items?.reduce(
      (sum, line) => sum + (Number(line.quantity) || 0) * (Number(line.unitPrice) || 0),
      0,
    ) ?? 0
  const total =
    subtotal +
    (Number(form.freight) || 0) +
    (Number(form.otherCharges) || 0) +
    (Number(form.taxAmount) || 0) -
    (Number(form.discount) || 0)

  async function save(values: QuotationFormValues, submit: boolean) {
    setBusy(true)
    const draft: QuotationDraftDto = values
    try {
      if (demo) {
        updateDraft(
          rfq.id,
          {
            currency: values.currency,
            freight: values.freight,
            otherCharges: values.otherCharges,
            discount: values.discount,
            taxAmount: values.taxAmount,
            deliveryTimeline: values.deliveryTimeline,
            warranty: values.warranty,
            notes: values.notes,
          },
          values.items as QuotationLine[],
          user?.fullName,
        )
        if (submit) submitDraft(rfq.id, user?.fullName ?? 'Quotation Manager')
      } else if (submit) await submitQuotation(rfq.id, draft)
      else await saveQuotationDraft(rfq.id, draft)
      toast.success(
        submit
          ? 'Quotation submitted for reviewer assessment.'
          : 'Draft saved. No permanent version was created.',
      )
      if (!demo) await invalidate()
      if (submit) window.scrollTo({ top: 0, behavior: 'smooth' })
    } catch (error) {
      toast.error(unexpectedErrorMessage(normalizeError(error)))
    } finally {
      setBusy(false)
    }
  }

  return (
    <form onSubmit={(event) => event.preventDefault()} className="space-y-5">
      <div>
        <SectionHeading
          title="Quotation draft"
          description="Enter a response against each requested line. Save as often as needed; submission creates a locked version."
        />
        <div className="mt-3 rounded-lg border border-blue-100 bg-blue-50/70 p-3 text-xs leading-5 text-blue-900">
          <strong>Completeness:</strong> missing business fields may be submitted, but reviewer
          approval will be blocked by the mandatory-field rules saved with that version.
        </div>
      </div>
      <div className="grid gap-4 sm:grid-cols-3">
        <Field label="Currency *" error={errors.currency?.message}>
          <Input {...register('currency')} maxLength={3} placeholder="USD" />
        </Field>
        <Field label="Delivery timeline">
          <Input {...register('deliveryTimeline')} maxLength={500} placeholder="15 business days" />
        </Field>
        <Field label="Quotation-level warranty">
          <Input {...register('warranty')} maxLength={500} placeholder="12 months" />
        </Field>
      </div>
      <div className="space-y-3">
        {fields.map((field, index) => (
          <div key={field.id} className="rounded-lg border border-slate-200 p-4">
            <div className="mb-3 flex items-start justify-between gap-3">
              <div>
                <span className="text-[11px] font-semibold tracking-wide text-blue-700 uppercase">
                  Offer for request line {index + 1}
                </span>
                <p className="mt-1 text-sm leading-5 font-medium text-slate-800">
                  {rfq.lineItems[index]?.description ?? field.description}
                </p>
              </div>
              <span className="rounded-md bg-slate-100 px-2 py-1 text-xs whitespace-nowrap text-slate-600">
                Requested {rfq.lineItems[index]?.requestedQuantity ?? field.quantity}{' '}
                {rfq.lineItems[index]?.unitOfMeasure}
              </span>
            </div>
            <div className="grid gap-3 sm:grid-cols-2">
              <Field label="Quoted manufacturer">
                <Input {...register(`items.${index}.manufacturer`)} maxLength={255} />
              </Field>
              <Field label="Manufacturer part number">
                <Input {...register(`items.${index}.partNumber`)} maxLength={255} />
              </Field>
              <Field label="Quoted quantity *" error={errors.items?.[index]?.quantity?.message}>
                <Input
                  type="number"
                  min="0.0001"
                  step="any"
                  {...register(`items.${index}.quantity`, { valueAsNumber: true })}
                />
              </Field>
              <Field label="Unit price *" error={errors.items?.[index]?.unitPrice?.message}>
                <Input
                  type="number"
                  min="0"
                  step="0.01"
                  {...register(`items.${index}.unitPrice`, { valueAsNumber: true })}
                />
              </Field>
              <Field label="Unit of measure">
                <Input {...register(`items.${index}.unitOfMeasure`)} maxLength={40} />
              </Field>
              <Field label="Product URL" error={errors.items?.[index]?.productUrl?.message}>
                <Input
                  {...register(`items.${index}.productUrl`)}
                  type="url"
                  placeholder="https://…"
                />
              </Field>
              <Field label="Line delivery timeline">
                <Input {...register(`items.${index}.deliveryTimeline`)} maxLength={500} />
              </Field>
              <Field label="Line warranty">
                <Input {...register(`items.${index}.warranty`)} maxLength={500} />
              </Field>
              <label className="flex items-center gap-2 text-sm text-slate-700 sm:col-span-2">
                <input
                  type="checkbox"
                  {...register(`items.${index}.taaCompliant`)}
                  className="size-4 accent-blue-700"
                />{' '}
                Offered product is TAA compliant
              </label>
              <div className="sm:col-span-2">
                <Field label="Technical or commercial note">
                  <textarea
                    {...register(`items.${index}.note`)}
                    rows={2}
                    maxLength={4000}
                    className="w-full rounded-md border border-slate-200 px-3 py-2 text-sm"
                  />
                </Field>
              </div>
            </div>
          </div>
        ))}
      </div>
      <div className="grid gap-4 sm:grid-cols-2">
        <Field label="Freight / shipping">
          <Input
            type="number"
            min="0"
            step="0.01"
            {...register('freight', { valueAsNumber: true })}
          />
        </Field>
        <Field label="Other approved charges">
          <Input
            type="number"
            min="0"
            step="0.01"
            {...register('otherCharges', { valueAsNumber: true })}
          />
        </Field>
        <Field label="Discount">
          <Input
            type="number"
            min="0"
            step="0.01"
            {...register('discount', { valueAsNumber: true })}
          />
        </Field>
        <Field label="Tax amount">
          <Input
            type="number"
            min="0"
            step="0.01"
            {...register('taxAmount', { valueAsNumber: true })}
          />
        </Field>
      </div>
      <Field label="Quotation notes">
        <textarea
          {...register('notes')}
          rows={3}
          maxLength={10000}
          className="w-full rounded-md border border-slate-200 px-3 py-2 text-sm"
        />
      </Field>
      <div className="flex flex-wrap items-center justify-between gap-3 rounded-lg bg-slate-50 p-4">
        <div className="text-xs text-slate-500">
          Subtotal{' '}
          <strong className="mx-1 text-slate-700">
            {form.currency} {subtotal.toFixed(2)}
          </strong>
          <span className="mx-2 text-slate-300">·</span>Estimated total{' '}
          <strong className="mx-1 text-slate-900">
            {form.currency} {Math.max(0, total).toFixed(2)}
          </strong>
          <p className="mt-1">Backend recalculates authoritative totals on save and submission.</p>
        </div>
        <div className="flex gap-2">
          <Button
            type="button"
            variant="outline"
            disabled={busy}
            onClick={() => void handleSubmit((values) => save(values, false))()}
          >
            {busy ? <Loader2 className="size-4 animate-spin" /> : 'Save draft'}
          </Button>
          <Button
            type="button"
            disabled={busy}
            onClick={() => void handleSubmit((values) => save(values, true))()}
          >
            <Send className="size-4" />
            Submit for review
          </Button>
        </div>
      </div>
    </form>
  )
}

function Field({
  label,
  error,
  children,
}: {
  label: string
  error?: string
  children: React.ReactNode
}) {
  return (
    <label className="block space-y-1.5 text-xs font-medium text-slate-700">
      {label}
      {children}
      {error && (
        <span className="block font-normal text-red-700" role="alert">
          {error}
        </span>
      )}
    </label>
  )
}

function ReviewActionPanel({
  rfq,
  demo,
  latestVersionId,
  versions,
  invalidate,
}: {
  rfq: RfqRecord
  demo: boolean
  latestVersionId?: number
  versions: Awaited<ReturnType<typeof getQuotationVersions>>
  invalidate: () => Promise<void>
}) {
  const review = useRfqDemoStore((state) => state.review)
  const { user } = useAuth()
  const [reason, setReason] = React.useState('')
  const [busy, setBusy] = React.useState(false)
  // RFQ Service returns versions newest-first.
  const latest = versions.at(0)
  const latestDemoVersion = rfq.quotationVersions.at(-1)
  const missing = demo
    ? (latestDemoVersion?.missingMandatoryFields ?? [])
    : (latest?.missingMandatoryFields ?? [])

  async function decide(decision: 'APPROVED' | 'REJECTED') {
    if (decision === 'REJECTED' && reason.trim().length < 5) {
      toast.error('A rejection reason is required (at least 5 characters).')
      return
    }
    if (decision === 'APPROVED' && missing.length) {
      toast.error(`Cannot approve: complete ${missing.join(', ')}.`)
      return
    }
    if (!demo && !latestVersionId) {
      toast.error('No submitted quotation version is available to review.')
      return
    }
    setBusy(true)
    try {
      if (demo) review(rfq.id, decision, reason.trim(), user?.fullName ?? 'Reviewer')
      else await submitReview(rfq.id, latestVersionId!, decision, reason.trim())
      toast.success(
        decision === 'APPROVED'
          ? 'Quotation approved for customer submission.'
          : 'Quotation rejected and returned for revision.',
      )
      setReason('')
      if (!demo) await invalidate()
    } catch (error) {
      toast.error(unexpectedErrorMessage(normalizeError(error)))
    } finally {
      setBusy(false)
    }
  }

  return (
    <Card className="border-amber-200 bg-white p-4 shadow-none">
      <div className="flex items-center gap-2">
        <ClipboardCheck className="size-4 text-amber-700" />
        <h2 className="text-sm font-semibold">Reviewer decision</h2>
      </div>
      <p className="mt-1 text-xs text-slate-500">
        Reviewing version{' '}
        {demo ? `V${latestDemoVersion?.versionNumber ?? '—'}` : `V${latest?.versionNumber ?? '—'}`}.
      </p>
      {missing.length > 0 && (
        <div className="mt-3 rounded-md bg-amber-50 p-3 text-xs leading-5 text-amber-900">
          <strong>Approval blocked by required fields:</strong> {missing.join(', ')}
        </div>
      )}
      <label className="mt-3 block text-xs font-medium text-slate-700">
        Review comments / rejection reason
        <textarea
          value={reason}
          onChange={(event) => setReason(event.target.value)}
          rows={3}
          maxLength={2000}
          className="mt-1.5 w-full rounded-md border border-slate-200 px-2.5 py-2 text-sm"
          placeholder="Required when rejecting"
        />
      </label>
      <div className="mt-3 grid grid-cols-2 gap-2">
        <Button
          type="button"
          size="sm"
          onClick={() => void decide('APPROVED')}
          disabled={busy || missing.length > 0}
        >
          <CheckCircle2 className="size-4" />
          Approve
        </Button>
        <Button
          type="button"
          size="sm"
          variant="destructive"
          onClick={() => void decide('REJECTED')}
          disabled={busy || reason.trim().length < 5}
        >
          <XCircle className="size-4" />
          Reject
        </Button>
      </div>
      <p className="mt-2 text-[11px] leading-4 text-slate-500">
        The reviewer cannot change submitted quotation fields.
      </p>
    </Card>
  )
}

function AdminActionPanel({
  rfq,
  demo,
  latestVersionId,
  userName,
  invalidate,
}: {
  rfq: RfqRecord
  demo: boolean
  latestVersionId?: number
  userName: string
  invalidate: () => Promise<void>
}) {
  const markSent = useRfqDemoStore((state) => state.markSent)
  const recordDecision = useRfqDemoStore((state) => state.recordDecision)
  const resolveEscalation = useRfqDemoStore((state) => state.resolveEscalation)
  const resolveCustomerRejection = useRfqDemoStore((state) => state.resolveCustomerRejection)
  const [reference, setReference] = React.useState('')
  const [reason, setReason] = React.useState('')
  const [busy, setBusy] = React.useState(false)
  const canMarkSent = rfq.status === 'APPROVED_BY_REVIEWER'
  const canRecordDecision = rfq.status === 'SENT_TO_CUSTOMER'
  const needsEscalation = rfq.status === 'ESCALATED'
  const customerRejected = rfq.status === 'CUSTOMER_REJECTED'
  if (!canMarkSent && !canRecordDecision && !needsEscalation && !customerRejected) return null

  async function action(fn: () => Promise<void>, success: string) {
    setBusy(true)
    try {
      await fn()
      toast.success(success)
      setReference('')
      setReason('')
      if (!demo) await invalidate()
    } catch (error) {
      toast.error(unexpectedErrorMessage(normalizeError(error)))
    } finally {
      setBusy(false)
    }
  }

  return (
    <Card className="border-blue-200 bg-white p-4 shadow-none">
      <div className="flex items-center gap-2">
        <ShieldCheck className="size-4 text-blue-700" />
        <h2 className="text-sm font-semibold">Administrator action</h2>
      </div>
      {canMarkSent && (
        <div className="mt-3 space-y-3">
          <p className="text-xs leading-5 text-slate-500">
            After sending the approved version through the existing client software, record that
            action here.
          </p>
          <label
            htmlFor="external-submission-reference"
            className="block text-xs font-medium text-slate-700"
          >
            Source software reference *
            <Input
              id="external-submission-reference"
              value={reference}
              onChange={(event) => setReference(event.target.value)}
              maxLength={255}
              className="mt-1.5"
              placeholder="Submission / event reference"
            />
          </label>
          <Button
            size="sm"
            className="w-full"
            disabled={busy || reference.trim().length < 2}
            onClick={() =>
              void action(async () => {
                if (demo) markSent(rfq.id, reference, userName)
                else if (latestVersionId)
                  await recordCustomerSubmission(rfq.id, latestVersionId, reference, '')
              }, 'Customer submission recorded.')
            }
          >
            {busy ? <Loader2 className="size-4 animate-spin" /> : <Send className="size-4" />}Record
            sent to customer
          </Button>
        </div>
      )}
      {canRecordDecision && (
        <div className="mt-3 space-y-3">
          <p className="text-xs leading-5 text-slate-500">
            Record the customer response received in the original software. This is separate from
            reviewer approval.
          </p>
          <label
            htmlFor="external-customer-reference"
            className="block text-xs font-medium text-slate-700"
          >
            Customer response reference (optional)
            <Input
              id="external-customer-reference"
              value={reference}
              onChange={(event) => setReference(event.target.value)}
              maxLength={255}
              className="mt-1.5"
            />
          </label>
          <label
            htmlFor="customer-response-notes"
            className="block text-xs font-medium text-slate-700"
          >
            Notes
            <textarea
              id="customer-response-notes"
              value={reason}
              onChange={(event) => setReason(event.target.value)}
              maxLength={2000}
              rows={2}
              className="mt-1.5 w-full rounded-md border border-slate-200 px-2.5 py-2 text-sm"
            />
          </label>
          <div className="grid grid-cols-2 gap-2">
            <Button
              size="sm"
              disabled={busy || (!latestVersionId && !demo)}
              onClick={() =>
                void action(async () => {
                  if (demo) recordDecision(rfq.id, 'APPROVED', reference, userName)
                  else if (latestVersionId)
                    await recordCustomerDecision(
                      rfq.id,
                      latestVersionId,
                      'APPROVED',
                      reference,
                      reason,
                    )
                }, 'Customer approval recorded.')
              }
            >
              <CheckCircle2 className="size-4" />
              Approved
            </Button>
            <Button
              size="sm"
              variant="destructive"
              disabled={busy || (!latestVersionId && !demo)}
              onClick={() =>
                void action(async () => {
                  if (demo) recordDecision(rfq.id, 'REJECTED', reference || reason, userName)
                  else if (latestVersionId)
                    await recordCustomerDecision(
                      rfq.id,
                      latestVersionId,
                      'REJECTED',
                      reference,
                      reason,
                    )
                }, 'Customer rejection recorded.')
              }
            >
              <XCircle className="size-4" />
              Rejected
            </Button>
          </div>
        </div>
      )}
      {(needsEscalation || customerRejected) && (
        <div className="mt-3 space-y-3">
          <div className="rounded-md bg-amber-50 p-3 text-xs leading-5 text-amber-900">
            {needsEscalation
              ? `The configured reviewer rejection limit has been reached (${rfq.rejectionCount}/3 in demo view).`
              : 'Choose whether to reopen this customer-rejected RFQ for a new revision or close it.'}
          </div>
          <label
            htmlFor="admin-workflow-reason"
            className="block text-xs font-medium text-slate-700"
          >
            Decision reason *
            <textarea
              id="admin-workflow-reason"
              value={reason}
              onChange={(event) => setReason(event.target.value)}
              maxLength={1000}
              rows={3}
              className="mt-1.5 w-full rounded-md border border-slate-200 px-2.5 py-2 text-sm"
            />
          </label>
          <div className="grid grid-cols-2 gap-2">
            <Button
              size="sm"
              variant="outline"
              disabled={busy || reason.trim().length < 5}
              onClick={() =>
                void action(async () => {
                  if (demo) {
                    if (needsEscalation)
                      resolveEscalation(rfq.id, 'ALLOW_REVISION', reason, userName)
                    else resolveCustomerRejection(rfq.id, 'REOPEN', reason, userName)
                  } else {
                    await resolveWorkflow(
                      rfq.id,
                      needsEscalation ? 'escalation-resolution' : 'customer-rejection-resolution',
                      'AUTHORIZE_REVISION',
                      reason,
                    )
                  }
                }, 'Revision authorized.')
              }
            >
              <RefreshCw className="size-4" />
              Reopen / revise
            </Button>
            <Button
              size="sm"
              variant="destructive"
              disabled={busy || reason.trim().length < 5}
              onClick={() =>
                void action(async () => {
                  if (demo) {
                    if (needsEscalation) resolveEscalation(rfq.id, 'CLOSE', reason, userName)
                    else resolveCustomerRejection(rfq.id, 'CLOSE', reason, userName)
                  } else {
                    await resolveWorkflow(
                      rfq.id,
                      needsEscalation ? 'escalation-resolution' : 'customer-rejection-resolution',
                      'CLOSE',
                      reason,
                    )
                  }
                }, 'RFQ closed.')
              }
            >
              <XCircle className="size-4" />
              Close RFQ
            </Button>
          </div>
        </div>
      )}
    </Card>
  )
}

function FilesPanel({
  rfq,
  demo,
  canUpload,
  attachments,
  loading,
  demoFiles,
  setDemoFiles,
  saving,
  setSaving,
}: {
  rfq: RfqRecord
  demo: boolean
  canUpload: boolean
  attachments: AttachmentDto[]
  loading: boolean
  demoFiles: Array<{ name: string; size: number; category: string; addedAt: string }>
  setDemoFiles: React.Dispatch<
    React.SetStateAction<Array<{ name: string; size: number; category: string; addedAt: string }>>
  >
  saving: boolean
  setSaving: (saving: boolean) => void
}) {
  const [category, setCategory] = React.useState('OTHER')
  const [selected, setSelected] = React.useState<File | null>(null)
  const [fileError, setFileError] = React.useState('')
  const [busyDownload, setBusyDownload] = React.useState<string | null>(null)
  const allowed = [
    'pdf',
    'png',
    'jpg',
    'jpeg',
    'webp',
    'doc',
    'docx',
    'xls',
    'xlsx',
    'csv',
    'txt',
    'ppt',
    'pptx',
  ]
  const stored = demo ? [] : attachments
  const displayed = [
    ...(rfq.sourceFileName
      ? [
          {
            id: 'source',
            fileName: rfq.sourceFileName,
            category: 'CUSTOMER_REQUEST',
            scanState: 'CLEAN',
            sizeBytes: 0,
            createdAt: rfq.createdAt,
          } as AttachmentDto,
        ]
      : []),
    ...stored,
  ]

  function chooseFile(file?: File) {
    setFileError('')
    setSelected(null)
    if (!file) return
    const extension = file.name.split('.').pop()?.toLowerCase()
    if (!allowed.includes(extension ?? '')) {
      setFileError(
        'This file type is not supported. Use PDF, image, Word, Excel, PowerPoint, CSV, or text.',
      )
      return
    }
    if (!file.size || file.size > 25 * 1024 * 1024) {
      setFileError(file.size ? 'Maximum attachment size is 25 MB.' : 'The selected file is empty.')
      return
    }
    setSelected(file)
  }
  async function uploadSelected() {
    if (!selected) return
    setSaving(true)
    try {
      if (demo)
        setDemoFiles((files) => [
          ...files,
          { name: selected.name, size: selected.size, category, addedAt: new Date().toISOString() },
        ])
      else {
        await uploadAttachment(rfq.id, selected, category)
        await queryClient.invalidateQueries({ queryKey: ['rfqs', rfq.id, 'attachments'] })
      }
      toast.success('Attachment added. It is quarantined until malware scanning completes.')
      setSelected(null)
    } catch (error) {
      toast.error(unexpectedErrorMessage(normalizeError(error)))
    } finally {
      setSaving(false)
    }
  }
  async function download(row: AttachmentDto) {
    if (row.id === 'source') {
      toast.info('Demo source file is represented by sample metadata.')
      return
    }
    setBusyDownload(row.id)
    try {
      const blob = await downloadAttachment(rfq.id, row.id)
      const url = URL.createObjectURL(blob)
      const anchor = document.createElement('a')
      anchor.href = url
      anchor.download = row.fileName
      anchor.click()
      URL.revokeObjectURL(url)
    } catch (error) {
      toast.error(unexpectedErrorMessage(normalizeError(error)))
    } finally {
      setBusyDownload(null)
    }
  }

  return (
    <div className="space-y-5">
      <div>
        <SectionHeading
          title="RFQ attachments"
          description="Source documents, line-item images, customer responses, and quotation support files."
        />
      </div>
      {canUpload && (
        <div className="rounded-xl border border-dashed border-slate-300 bg-slate-50/60 p-4">
          <div className="grid gap-3 sm:grid-cols-[1fr_180px_auto]">
            <label className="flex min-w-0 cursor-pointer items-center gap-3 rounded-lg border border-slate-200 bg-white px-3 py-3">
              <UploadCloud className="size-5 shrink-0 text-blue-700" />
              <span className="min-w-0">
                <span className="block truncate text-sm font-medium">
                  {selected?.name ?? 'Choose supporting file'}
                </span>
                <span className="mt-0.5 block text-xs text-slate-500">
                  PDF, image, Word, Excel, PowerPoint, CSV, text · max 25 MB
                </span>
              </span>
              <input
                type="file"
                accept=".pdf,.png,.jpg,.jpeg,.webp,.doc,.docx,.xls,.xlsx,.csv,.txt,.ppt,.pptx"
                onChange={(event) => chooseFile(event.target.files?.[0])}
                className="sr-only"
              />
            </label>
            <select
              value={category}
              onChange={(event) => setCategory(event.target.value)}
              className="h-11 rounded-lg border border-slate-200 bg-white px-3 text-sm"
            >
              <option value="OTHER">Other document</option>
              <option value="CUSTOMER_REQUEST">Customer request</option>
              <option value="QUOTATION_SUPPORT">Quotation support</option>
              <option value="REVIEW_SUPPORT">Review support</option>
              <option value="CUSTOMER_RESPONSE">Customer response</option>
            </select>
            <Button
              type="button"
              onClick={() => void uploadSelected()}
              disabled={!selected || saving}
            >
              {saving ? (
                <Loader2 className="size-4 animate-spin" />
              ) : (
                <UploadCloud className="size-4" />
              )}
              Upload
            </Button>
          </div>
          {fileError && (
            <p className="mt-2 text-xs text-red-700" role="alert">
              {fileError}
            </p>
          )}
          <p className="mt-2 flex items-start gap-2 text-[11px] leading-4 text-slate-500">
            <ShieldCheck className="mt-0.5 size-3.5 shrink-0 text-emerald-600" />
            The server validates content and stores the file in a quarantined state. Download
            remains unavailable until it is marked clean by the scanner.
          </p>
        </div>
      )}
      {loading ? (
        <p className="py-8 text-center text-sm text-slate-500">Loading attachments…</p>
      ) : displayed.length || demoFiles.length ? (
        <div className="space-y-2">
          {[
            ...displayed.map((row) => ({ ...row, demo: false })),
            ...demoFiles.map(
              (row, index) =>
                ({
                  id: `demo-${index}`,
                  fileName: row.name,
                  category: row.category,
                  scanState: 'PENDING_SCAN',
                  sizeBytes: row.size,
                  createdAt: row.addedAt,
                  demo: true,
                }) as AttachmentDto & { demo: boolean },
            ),
          ].map((row) => (
            <div
              key={row.id}
              className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-slate-200 p-3"
            >
              <div className="flex min-w-0 items-center gap-3">
                <span className="flex size-9 shrink-0 items-center justify-center rounded-lg bg-slate-100 text-slate-600">
                  <FileText className="size-4" />
                </span>
                <div className="min-w-0">
                  <p className="truncate text-sm font-medium">{row.fileName}</p>
                  <p className="mt-0.5 text-[11px] text-slate-500">
                    {row.category.replaceAll('_', ' ')} ·{' '}
                    {row.sizeBytes ? `${(row.sizeBytes / 1024 / 1024).toFixed(2)} MB · ` : ''}
                    {new Intl.DateTimeFormat('en-IN', { dateStyle: 'medium' }).format(
                      new Date(row.createdAt),
                    )}
                  </p>
                </div>
              </div>
              <div className="flex items-center gap-2">
                <span
                  className={`rounded px-2 py-1 text-[10px] font-semibold ${row.scanState === 'CLEAN' ? 'bg-emerald-50 text-emerald-800' : row.scanState === 'INFECTED' ? 'bg-red-50 text-red-800' : 'bg-amber-50 text-amber-800'}`}
                >
                  {row.scanState === 'CLEAN' ? 'Clean' : row.scanState.replaceAll('_', ' ')}
                </span>
                {row.scanState === 'CLEAN' && (
                  <Button
                    type="button"
                    size="icon"
                    variant="ghost"
                    aria-label={`Download ${row.fileName}`}
                    disabled={busyDownload === row.id}
                    onClick={() => void download(row)}
                  >
                    <Download className="size-4" />
                  </Button>
                )}
              </div>
            </div>
          ))}
        </div>
      ) : (
        <EmptyMessage>No files have been attached yet.</EmptyMessage>
      )}
      {demo && demoFiles.length > 0 && (
        <p className="text-xs text-slate-500">
          Demo uploads are only recorded in this open screen; they are not sent to storage.
        </p>
      )}
    </div>
  )
}

function HistoryPanel({ events }: { events: WorkflowEvent[] }) {
  return (
    <div>
      <SectionHeading
        title="Workflow history"
        description="Traceable RFQ events, actors, timestamps, and decision context."
      />
      <div className="mt-5">
        {events.length ? (
          <ol className="space-y-0">
            {events.map((event, index) => (
              <li key={event.id} className="relative flex gap-4 pb-6 last:pb-0">
                <div className="flex flex-col items-center">
                  <span
                    className={`z-10 flex size-8 shrink-0 items-center justify-center rounded-full ${event.tone === 'green' ? 'bg-emerald-100 text-emerald-700' : event.tone === 'red' ? 'bg-red-100 text-red-700' : event.tone === 'amber' ? 'bg-amber-100 text-amber-700' : 'bg-blue-100 text-blue-700'}`}
                  >
                    <Circle className="size-2.5 fill-current" />
                  </span>
                  {index < events.length - 1 && (
                    <span className="absolute top-8 bottom-0 w-px bg-slate-200" />
                  )}
                </div>
                <div className="min-w-0 flex-1 pt-0.5">
                  <div className="flex flex-wrap items-start justify-between gap-2">
                    <h3 className="text-sm font-semibold text-slate-800 capitalize">
                      {event.label}
                    </h3>
                    <time dateTime={event.createdAt} className="text-[11px] text-slate-400">
                      {event.createdAt
                        ? new Intl.DateTimeFormat('en-IN', {
                            dateStyle: 'medium',
                            timeStyle: 'short',
                          }).format(new Date(event.createdAt))
                        : ''}
                    </time>
                  </div>
                  <p className="mt-1 text-sm leading-5 whitespace-pre-wrap text-slate-600">
                    {event.detail}
                  </p>
                  <p className="mt-1.5 text-xs text-slate-400">Recorded by {event.actor}</p>
                </div>
              </li>
            ))}
          </ol>
        ) : (
          <EmptyMessage>Workflow events will appear after import and assignment.</EmptyMessage>
        )}
      </div>
    </div>
  )
}

function MarketingWorkflowPanel({
  rfq,
  demo,
  isAdmin,
  isAssignedMarketing,
  userName,
  invalidate,
}: {
  rfq: RfqRecord
  demo: boolean
  isAdmin: boolean
  isAssignedMarketing: boolean
  userName: string
  invalidate: () => Promise<void>
}) {
  const markReadyDemo = useRfqDemoStore((state) => state.markReadyForMarketing)
  const completeDemo = useRfqDemoStore((state) => state.completeMarketing)
  const [notes, setNotes] = React.useState('')
  const [outcome, setOutcome] = React.useState('CAMPAIGN_LAUNCHED')
  const [busy, setBusy] = React.useState(false)

  const canMarkReady = isAdmin && rfq.status === 'CUSTOMER_APPROVED'
  const awaitMarketingAssignment = rfq.status === 'READY_FOR_MARKETING'
  const canComplete = (isAdmin || isAssignedMarketing) && rfq.status === 'ASSIGNED_TO_MARKETING'
  const marketingDone = rfq.status === 'CLOSED' && rfq.customerDecision === 'APPROVED'

  if (!canMarkReady && !awaitMarketingAssignment && !canComplete && !marketingDone) {
    return null
  }

  async function handleMarkReady() {
    setBusy(true)
    try {
      if (demo) markReadyDemo(rfq.id, notes.trim() || 'Ready for marketing intake', userName)
      else await markReadyForMarketing(rfq.id, notes.trim() || undefined)
      toast.success('RFQ marked ready for marketing handoff.')
      setNotes('')
      await invalidate()
    } catch (error) {
      toast.error(unexpectedErrorMessage(normalizeError(error)))
    } finally {
      setBusy(false)
    }
  }

  async function handleCompleteMarketing(event: React.FormEvent) {
    event.preventDefault()
    setBusy(true)
    try {
      if (demo)
        completeDemo(
          rfq.id,
          `${outcome}${notes.trim() ? `: ${notes.trim()}` : ''}`,
          userName,
        )
      else await completeMarketing(rfq.id, { outcome, notes: notes.trim() || undefined })
      toast.success('Marketing execution marked complete.')
      setNotes('')
      await invalidate()
    } catch (error) {
      toast.error(unexpectedErrorMessage(normalizeError(error)))
    } finally {
      setBusy(false)
    }
  }

  return (
    <Card className="border-slate-200 p-4 shadow-none">
      <div className="flex items-start justify-between gap-2">
        <div>
          <h2 className="text-sm font-semibold text-slate-900">Marketing handoff</h2>
          <p className="mt-1 text-xs text-slate-500">
            Post-approval marketing routing and campaign completion.
          </p>
        </div>
        <Megaphone className="size-4 text-blue-600" />
      </div>

      {marketingDone ? (
        <div className="mt-3 rounded-md bg-emerald-50 px-3 py-2 text-xs font-medium text-emerald-700">
          Marketing execution is complete for this RFQ.
        </div>
      ) : canMarkReady ? (
        <div className="mt-3 space-y-2.5">
          <textarea
            value={notes}
            onChange={(event) => setNotes(event.target.value)}
            rows={2}
            placeholder="Optional handoff note for marketing…"
            className="w-full rounded-md border border-slate-200 px-3 py-2 text-xs"
          />
          <Button
            type="button"
            size="sm"
            className="w-full"
            disabled={busy}
            onClick={() => void handleMarkReady()}
          >
            {busy ? <Loader2 className="size-3.5 animate-spin" /> : null}
            Mark Ready for Marketing
          </Button>
        </div>
      ) : canComplete ? (
        <form onSubmit={handleCompleteMarketing} className="mt-3 space-y-2.5">
          <label className="block text-xs font-medium text-slate-700">
            Outcome
            <select
              value={outcome}
              onChange={(event) => setOutcome(event.target.value)}
              className="mt-1 h-9 w-full rounded-md border border-slate-200 bg-white px-2.5 text-xs"
            >
              <option value="CAMPAIGN_LAUNCHED">Campaign launched</option>
              <option value="CUSTOMER_ONBOARDED">Customer onboarded</option>
              <option value="HANDOFF_CLOSED">Handoff completed</option>
            </select>
          </label>
          <textarea
            value={notes}
            onChange={(event) => setNotes(event.target.value)}
            rows={2}
            placeholder="Summary of marketing follow-up…"
            className="w-full rounded-md border border-slate-200 px-3 py-2 text-xs"
          />
          <Button type="submit" size="sm" className="w-full" disabled={busy}>
            {busy ? <Loader2 className="size-3.5 animate-spin" /> : null}
            Complete Marketing Handoff
          </Button>
        </form>
      ) : awaitMarketingAssignment ? (
        <div className="mt-3 rounded-md bg-blue-50 px-3 py-2 text-xs text-blue-800">
          Ready for marketing — assign a Marketing Executive in Assignments above to enable
          handoff completion.
        </div>
      ) : null}
    </Card>
  )
}

function RfqGovernanceQuickPanel({
  rfq,
  demo,
  latestVersionId,
  isAdmin,
  userId,
  invalidate,
}: {
  rfq: RfqRecord
  demo: boolean
  latestVersionId?: number
  isAdmin: boolean
  userId: number
  invalidate: () => Promise<void>
}) {
  const createDemoRedFlag = useRfqDemoStore((state) => state.createRedFlag)
  const createDemoPO = useRfqDemoStore((state) => state.createPurchaseOrder)
  const [showFlagForm, setShowFlagForm] = React.useState(false)
  const [severity, setSeverity] = React.useState<'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'>('HIGH')
  const [flagReason, setFlagReason] = React.useState('')
  const [showPoForm, setShowPoForm] = React.useState(false)
  const [supplierName, setSupplierName] = React.useState('')
  const [expectedDelivery, setExpectedDelivery] = React.useState('')
  const [poNotes, setPoNotes] = React.useState('')
  const [busy, setBusy] = React.useState(false)

  const canCreatePo =
    isAdmin &&
    (['CUSTOMER_APPROVED', 'READY_FOR_MARKETING', 'ASSIGNED_TO_MARKETING'].includes(rfq.status) ||
      (rfq.status === 'CLOSED' && rfq.customerDecision === 'APPROVED'))

  async function handleRaiseFlag(event: React.FormEvent) {
    event.preventDefault()
    if (flagReason.trim().length < 5) {
      toast.error('Provide a reason (at least 5 characters).')
      return
    }
    setBusy(true)
    try {
      if (demo) {
        createDemoRedFlag(rfq.id, severity, flagReason.trim(), userId)
      } else {
        await createRedFlag(rfq.id, severity, flagReason.trim())
      }
      toast.success('Red flag raised for this RFQ.')
      setFlagReason('')
      setShowFlagForm(false)
      await invalidate()
    } catch (error) {
      toast.error(unexpectedErrorMessage(normalizeError(error)))
    } finally {
      setBusy(false)
    }
  }

  async function handleCreatePo(event: React.FormEvent) {
    event.preventDefault()
    if (!supplierName.trim()) {
      toast.error('Enter a supplier name.')
      return
    }
    setBusy(true)
    try {
      if (demo) {
        createDemoPO({
          rfqPublicId: rfq.id,
          supplierName: supplierName.trim(),
          expectedDelivery: expectedDelivery || undefined,
          notes: poNotes.trim() || undefined,
          createdBy: userId,
        })
      } else {
        await createPurchaseOrder(
          rfq.id,
          latestVersionId,
          supplierName.trim(),
          undefined,
          undefined,
          expectedDelivery || undefined,
          poNotes.trim() || undefined,
        )
      }
      toast.success('Purchase order created.')
      setSupplierName('')
      setExpectedDelivery('')
      setPoNotes('')
      setShowPoForm(false)
      await invalidate()
    } catch (error) {
      toast.error(unexpectedErrorMessage(normalizeError(error)))
    } finally {
      setBusy(false)
    }
  }

  return (
    <Card className="border-slate-200 p-4 shadow-none">
      <h2 className="text-sm font-semibold text-slate-900">Escalation &amp; Procurement</h2>
      <div className="mt-3 flex flex-wrap gap-2">
        <Button
          type="button"
          size="sm"
          variant="outline"
          onClick={() => setShowFlagForm((prev) => !prev)}
        >
          <Flag className="size-3.5 text-red-600" />
          Raise Red Flag
        </Button>
        {canCreatePo && (
          <Button
            type="button"
            size="sm"
            variant="outline"
            onClick={() => setShowPoForm((prev) => !prev)}
          >
            <ShoppingCart className="size-3.5 text-blue-600" />
            Create Purchase Order
          </Button>
        )}
      </div>

      {showFlagForm && (
        <form onSubmit={handleRaiseFlag} className="mt-3 space-y-2.5 border-t border-slate-100 pt-3">
          <select
            value={severity}
            onChange={(event) =>
              setSeverity(event.target.value as 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL')
            }
            className="h-9 w-full rounded-md border border-slate-200 bg-white px-2.5 text-xs"
          >
            <option value="LOW">Low severity</option>
            <option value="MEDIUM">Medium severity</option>
            <option value="HIGH">High severity</option>
            <option value="CRITICAL">Critical severity</option>
          </select>
          <textarea
            required
            rows={2}
            value={flagReason}
            onChange={(event) => setFlagReason(event.target.value)}
            placeholder="Describe the blocker, pricing risk, or compliance issue…"
            className="w-full rounded-md border border-slate-200 px-3 py-2 text-xs"
          />
          <div className="flex justify-end gap-2">
            <Button type="button" size="sm" variant="ghost" onClick={() => setShowFlagForm(false)}>
              Cancel
            </Button>
            <Button type="submit" size="sm" variant="destructive" disabled={busy}>
              Submit Flag
            </Button>
          </div>
        </form>
      )}

      {showPoForm && (
        <form onSubmit={handleCreatePo} className="mt-3 space-y-2.5 border-t border-slate-100 pt-3">
          <Input
            required
            placeholder="Supplier name"
            value={supplierName}
            onChange={(event) => setSupplierName(event.target.value)}
            className="h-9 text-xs"
          />
          <Input
            type="date"
            aria-label="Expected delivery date"
            value={expectedDelivery}
            onChange={(event) => setExpectedDelivery(event.target.value)}
            className="h-9 text-xs"
          />
          <textarea
            rows={2}
            placeholder="PO notes (optional)"
            value={poNotes}
            onChange={(event) => setPoNotes(event.target.value)}
            className="w-full rounded-md border border-slate-200 px-3 py-2 text-xs"
          />
          <div className="flex justify-end gap-2">
            <Button type="button" size="sm" variant="ghost" onClick={() => setShowPoForm(false)}>
              Cancel
            </Button>
            <Button type="submit" size="sm" disabled={busy}>
              Create PO
            </Button>
          </div>
        </form>
      )}

      <div className="mt-3 flex flex-wrap gap-3 border-t border-slate-100 pt-2.5 text-xs">
        <Link to="/red-flags" className="font-medium text-blue-700 hover:underline">
          View all Red Flags →
        </Link>
        <Link to="/purchases" className="font-medium text-blue-700 hover:underline">
          View Purchases →
        </Link>
      </div>
    </Card>
  )
}
