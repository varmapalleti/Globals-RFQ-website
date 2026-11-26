import { createFileRoute, Link } from '@tanstack/react-router'
import { useQuery } from '@tanstack/react-query'
import { AlertTriangle, ArrowLeft, ClipboardCheck, Download, FilePlus2, FileText, History, Loader2, Paperclip, RefreshCw, ShieldCheck } from 'lucide-react'
import * as React from 'react'
import { toast } from 'sonner'
import { AppShell } from '@/components/layout/AppShell'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { useAuth } from '@/features/auth/hooks/useAuth'
import { isDemoSession } from '@/features/auth/demoSession'
import { exportRfq, getAttachments, getHistory, getQuotationVersions, getReviewerReassignmentRequests } from '@/features/rfq/api/rfqApi'
import { useRfqDetail } from '@/features/rfq/hooks/useRfqDetail'
import { StatusPill } from '@/features/rfq/components/StatusPill'
import { formatRfqNumber } from '@/features/rfq/types'
import { queryClient } from '@/lib/query-client'
import { normalizeError, unexpectedErrorMessage } from '@/lib/errors'
import { ProgressStrip, OverviewPanel } from '@/features/rfq/components/OverviewPanels'
import { AssignmentPanel } from '@/features/rfq/components/AssignmentPanel'
import { QuotationPanel } from '@/features/rfq/components/QuotationEditor'
import { ReviewerReassignmentPanel, ReviewActionPanel, AdminActionPanel } from '@/features/rfq/components/ReviewerCustomerActions'
import { MarketingWorkflowPanel } from '@/features/rfq/components/MarketingHandoffPanel'
import { FilesPanel } from '@/features/rfq/components/RfqAttachmentsPanel'
import { HistoryPanel } from '@/features/rfq/components/RfqHistoryPanel'
import { RfqGovernanceQuickPanel } from '@/features/rfq/components/RfqGovernancePanel'

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
  const userRoles = user?.roles ?? []
  const isAdmin = userRoles.includes('ADMIN') || userRoles.includes('SUPER_ADMIN')
  const hasReviewerRole = userRoles.includes('REVIEWER')
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
        (hasReviewerRole &&
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
  const isAssignedManager = userRoles.includes('QUOTATION_MANAGER') && currentManager?.userId === user?.id
  const isAssignedReviewer = hasReviewerRole && currentReviewer?.userId === user?.id
  const isAssignedMarketing = userRoles.includes('MARKETING_EXECUTIVE') && currentMarketing?.userId === user?.id
  const versionRows = versionsQuery.data ?? []
  const currentVersion = demo ? rfq.currentQuotationVersion : versionRows.at(0)?.id
  const readText = (value: unknown, fallback = ''): string =>
    typeof value === 'string' ? value : typeof value === 'number' ? String(value) : fallback
  const eventDetail = (event: Record<string, unknown>): string => {
    const reason = readText(event['reason'])
    const eventType = readText(event['eventType'])
    if (eventType === 'ASSIGNED' || eventType === 'REASSIGNED') {
      let payload: Record<string, unknown> = {}
      try {
        const parsed = JSON.parse(readText(event['payloadJson'], '{}')) as unknown
        if (typeof parsed === 'object' && parsed !== null) payload = parsed as Record<string, unknown>
      } catch {
        // Keep the workflow event readable if an older event has malformed payload JSON.
      }
      const roleLabel = readText(payload['role'], 'RFQ role').replaceAll('_', ' ').toLowerCase()
      const previousUserId = readText(payload['previousUserId']) || '—'
      const assignedUserId = readText(payload['assignedUserId']) || '—'
      return `${roleLabel}: user ${previousUserId} → user ${assignedUserId}${reason ? ` · ${reason}` : ''}`
    }
    return reason || readText(event['toStatus'])
  }
  const liveHistory = (historyQuery.data ?? []).map((event, index) => ({
    id: readText(event['id'], String(index)),
    label: readText(event['eventType'], 'Workflow event').replaceAll('_', ' ').toLowerCase(),
    detail: eventDetail(event),
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
          {isAdmin &&
            ['REJECTED_BY_REVIEWER', 'ESCALATED'].includes(rfq.status) &&
            versionRows[0]?.review?.decision === 'REJECTED' && (
              <Card className="border-red-200 bg-red-50/60 p-4 shadow-none">
                <div className="flex items-center gap-2 text-sm font-semibold text-red-950">
                  <AlertTriangle className="size-4 text-red-700" /> Reviewer rejection needs attention
                </div>
                <p className="mt-2 text-xs text-red-900">
                  Quotation V{versionRows[0].versionNumber} (version ID {versionRows[0].id}) was rejected
                  by reviewer user ID {versionRows[0].review.reviewerUserId}.
                </p>
                <p className="mt-2 whitespace-pre-wrap rounded-md bg-white/80 p-3 text-sm text-slate-800">
                  {versionRows[0].review.reason || 'No rejection reason was returned.'}
                </p>
                <p className="mt-2 text-xs text-slate-600">
                  Reassign the quotation manager from Assignments below if needed. Earlier submitted
                  versions and their review decisions remain available in the Quotation tab.
                </p>
              </Card>
            )}
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
