import { CheckCircle2, ClipboardCheck, Loader2, RefreshCw, Send, ShieldCheck, UserRound, XCircle } from 'lucide-react'
import * as React from 'react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { useAuth } from '@/features/auth/hooks/useAuth'
import { recordCustomerDecision, recordCustomerSubmission, requestReviewerReassignment, resolveReviewerReassignment, resolveWorkflow, submitReview, type ReviewerReassignmentRequestDto } from '@/features/rfq/api/rfqApi'
import type { getQuotationVersions } from '@/features/rfq/api/rfqApi'
import { useRfqDemoStore } from '@/features/rfq/store/rfqDemoStore'
import { type RfqRecord } from '@/features/rfq/types'
import { normalizeError, unexpectedErrorMessage } from '@/lib/errors'

export function ReviewerReassignmentPanel({
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
    <Card className="p-4">
      <div className="flex items-center gap-2 text-sm font-semibold text-brand-900">
        <UserRound className="size-4 text-brand-900" /> Reviewer reassignment
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
                <li key={request.id} className="rounded-lg border border-brand-100 p-3">
                  <div className="flex items-center justify-between gap-2">
                    <span className="text-xs font-semibold">Request #{request.id}</span>
                    <span className="rounded-full bg-brand-50 px-2 py-0.5 text-[10px] font-semibold text-brand-900">
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
            <div className="mt-3 space-y-2 border-t border-brand-100 pt-3">
              <label
                className="block text-xs font-semibold text-slate-800"
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
                className="w-full rounded-lg border border-input bg-white px-2.5 py-2 text-sm focus-visible:border-brand-900 focus-visible:ring-2 focus-visible:ring-brand-900/20 focus-visible:outline-none"
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
                className="mt-3 space-y-2 border-t border-brand-100 pt-3"
              >
                <p className="text-xs font-semibold text-brand-900">
                  Admin decision for request #{request.id}
                </p>
                <label
                  className="block text-xs font-semibold text-slate-800"
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
                  className="block text-xs font-semibold text-slate-800"
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
                  className="w-full rounded-lg border border-input bg-white px-2.5 py-2 text-sm focus-visible:border-brand-900 focus-visible:ring-2 focus-visible:ring-brand-900/20 focus-visible:outline-none"
                />
                <div className="grid grid-cols-2 gap-2">
                  <Button
                    size="sm"
                    disabled={busy || resolutionReason.trim().length < 5}
                    onMouseDown={(e) => e.preventDefault()}
                    onClick={() => void resolveRequest(request.id, 'APPROVE')}
                  >
                    Approve
                  </Button>
                  <Button
                    size="sm"
                    variant="outline"
                    disabled={busy || resolutionReason.trim().length < 5}
                    onMouseDown={(e) => e.preventDefault()}
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

export function ReviewActionPanel({
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
    <Card className="border-amber-200 bg-white p-4">
      <div className="flex items-center gap-2">
        <ClipboardCheck className="size-4 text-amber-700" />
        <h2 className="text-sm font-semibold text-brand-900">Reviewer decision</h2>
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
      <label className="mt-3 block text-xs font-semibold text-slate-800">
        Review comments / rejection reason
        <textarea
          value={reason}
          onChange={(event) => setReason(event.target.value)}
          rows={3}
          maxLength={2000}
          className="mt-1.5 w-full rounded-lg border border-input bg-white px-2.5 py-2 text-sm focus-visible:border-brand-900 focus-visible:ring-2 focus-visible:ring-brand-900/20 focus-visible:outline-none"
          placeholder="Required when rejecting"
        />
      </label>
      <div className="mt-3 grid grid-cols-2 gap-2">
        <Button
          type="button"
          size="sm"
          onMouseDown={(e) => e.preventDefault()}
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
          onMouseDown={(e) => e.preventDefault()}
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

export function AdminActionPanel({
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
    <Card className="border-brand-200 bg-white p-4">
      <div className="flex items-center gap-2">
        <ShieldCheck className="size-4 text-brand-900" />
        <h2 className="text-sm font-semibold text-brand-900">Administrator action</h2>
      </div>
      {canMarkSent && (
        <div className="mt-3 space-y-3">
          <p className="text-xs leading-5 text-slate-500">
            After sending the approved version through the existing client software, record that
            action here.
          </p>
          <label
            htmlFor="external-submission-reference"
            className="block text-xs font-semibold text-slate-800"
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
            className="block text-xs font-semibold text-slate-800"
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
            className="block text-xs font-semibold text-slate-800"
          >
            Notes
            <textarea
              id="customer-response-notes"
              value={reason}
              onChange={(event) => setReason(event.target.value)}
              maxLength={2000}
              rows={2}
              className="mt-1.5 w-full rounded-lg border border-input bg-white px-2.5 py-2 text-sm focus-visible:border-brand-900 focus-visible:ring-2 focus-visible:ring-brand-900/20 focus-visible:outline-none"
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
            className="block text-xs font-semibold text-slate-800"
          >
            Decision reason *
            <textarea
              id="admin-workflow-reason"
              value={reason}
              onChange={(event) => setReason(event.target.value)}
              maxLength={1000}
              rows={3}
              className="mt-1.5 w-full rounded-lg border border-input bg-white px-2.5 py-2 text-sm focus-visible:border-brand-900 focus-visible:ring-2 focus-visible:ring-brand-900/20 focus-visible:outline-none"
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
