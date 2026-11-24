import { create } from 'zustand'
import { initialDemoRfqs } from '../demo/demoData'
import type {
  QuotationLine,
  RfqAssignmentRole,
  RfqQuotationVersion,
  RfqRecord,
  RfqStatus,
  WorkflowEvent,
} from '../types'

const at = () => new Date().toISOString()
const missingRequiredQuotationFields = (draft: RfqRecord['quoteDraft']) => [
  ...draft.items.flatMap(
    (line) =>
      [
        !line.manufacturer && 'Manufacturer',
        !line.partNumber && 'Manufacturer part number',
        !line.unitPrice && 'Unit price',
      ].filter(Boolean) as string[],
  ),
  ...(!draft.deliveryTimeline ? ['Delivery timeline'] : []),
  ...(!draft.warranty ? ['Warranty'] : []),
]
const newEvent = (
  label: string,
  detail: string,
  actor: string,
  tone: WorkflowEvent['tone'] = 'blue',
): WorkflowEvent => ({
  id: crypto.randomUUID(),
  label,
  detail,
  actor,
  createdAt: at(),
  tone,
})

interface RfqDemoState {
  rfqs: RfqRecord[]
  assign: (
    rfqId: string,
    role: RfqAssignmentRole,
    userId: number,
    userName: string,
    reason: string,
    actor: string,
  ) => void
  updateDraft: (
    rfqId: string,
    data: Partial<RfqRecord['quoteDraft']>,
    items?: QuotationLine[],
    actor?: string,
  ) => void
  submitDraft: (rfqId: string, actor: string) => void
  review: (rfqId: string, decision: 'APPROVED' | 'REJECTED', reason: string, actor: string) => void
  markSent: (rfqId: string, reference: string, actor: string) => void
  recordDecision: (
    rfqId: string,
    decision: 'APPROVED' | 'REJECTED',
    reference: string,
    actor: string,
  ) => void
  importRfq: (rfq: RfqRecord) => void
  resolveEscalation: (
    rfqId: string,
    action: 'ALLOW_REVISION' | 'CLOSE',
    reason: string,
    actor: string,
  ) => void
  resolveCustomerRejection: (
    rfqId: string,
    action: 'REOPEN' | 'CLOSE',
    reason: string,
    actor: string,
  ) => void
  setReviewerDelegation: (rfqId: string, allowed: boolean, reason: string, actor: string) => void
}

export const useRfqDemoStore = create<RfqDemoState>((set) => ({
  rfqs: initialDemoRfqs,
  assign: (rfqId, role, userId, userName, reason, actor) =>
    set((state) => ({
      rfqs: state.rfqs.map((rfq) => {
        if (rfq.id !== rfqId) return rfq
        const old = rfq.assignments.filter((assignment) => assignment.role !== role)
        const nextStatus: RfqStatus =
          role === 'QUOTATION_MANAGER' && rfq.status === 'IMPORTED'
            ? 'ASSIGNED_TO_QUOTATION_MANAGER'
            : role === 'MARKETING_EXECUTIVE'
              ? 'ASSIGNED_TO_MARKETING'
              : rfq.status
        return {
          ...rfq,
          status: nextStatus,
          assignments: [...old, { role, userId, userName, assignedAt: at() }],
          events: [
            newEvent(
              `${role.replaceAll('_', ' ')} assigned`,
              reason || `Assigned to ${userName}.`,
              actor,
            ),
            ...rfq.events,
          ],
        }
      }),
    })),
  updateDraft: (rfqId, data, items, actor = 'Quotation Manager') =>
    set((state) => ({
      rfqs: state.rfqs.map((rfq) =>
        rfq.id !== rfqId
          ? rfq
          : {
              ...rfq,
              status: [
                'ASSIGNED_TO_QUOTATION_MANAGER',
                'REJECTED_BY_REVIEWER',
                'REVISION_IN_PROGRESS',
                'REOPENED_FOR_REVISION',
              ].includes(rfq.status)
                ? 'QUOTATION_IN_PROGRESS'
                : rfq.status,
              quoteDraft: { ...rfq.quoteDraft, ...data, items: items ?? rfq.quoteDraft.items },
              events: [
                newEvent(
                  'Draft saved',
                  'Quotation draft updated. No permanent version was created.',
                  actor,
                  'slate',
                ),
                ...rfq.events,
              ],
            },
      ),
    })),
  submitDraft: (rfqId, actor) =>
    set((state) => ({
      rfqs: state.rfqs.map((rfq) =>
        rfq.id !== rfqId
          ? rfq
          : (() => {
              const versionNumber = rfq.currentQuotationVersion + 1
              const draftSnapshot = structuredClone(rfq.quoteDraft)
              const version: RfqQuotationVersion = {
                id: versionNumber,
                versionNumber,
                status: 'PENDING_REVIEW',
                submittedAt: at(),
                draftSnapshot,
                missingMandatoryFields: missingRequiredQuotationFields(draftSnapshot),
                reviewDecision: null,
                reviewReason: '',
                reviewerName: '',
              }
              return {
                ...rfq,
                status: 'PENDING_REVIEW',
                currentQuotationVersion: versionNumber,
                quotationVersions: [...rfq.quotationVersions, version],
                events: [
                  newEvent(
                    `Quotation version V${versionNumber} submitted`,
                    'Immutable version snapshot created for reviewer assessment.',
                    actor,
                    'amber',
                  ),
                  ...rfq.events,
                ],
              }
            })(),
      ),
    })),
  review: (rfqId, decision, reason, actor) =>
    set((state) => ({
      rfqs: state.rfqs.map((rfq) => {
        if (rfq.id !== rfqId) return rfq
        const latestVersionIndex = rfq.quotationVersions.length - 1
        const latestVersion = rfq.quotationVersions[latestVersionIndex]
        const quotationVersions = latestVersion
          ? rfq.quotationVersions.map((version, index) =>
              index === latestVersionIndex
                ? {
                    ...version,
                    status: decision === 'APPROVED' ? ('APPROVED' as const) : ('REJECTED' as const),
                    reviewDecision: decision,
                    reviewReason: reason,
                    reviewerName: actor,
                  }
                : version,
            )
          : rfq.quotationVersions
        if (decision === 'APPROVED')
          return {
            ...rfq,
            status: 'APPROVED_BY_REVIEWER',
            quotationVersions,
            events: [
              newEvent(
                'Reviewer approved quotation',
                reason || `Version V${rfq.currentQuotationVersion} approved.`,
                actor,
                'green',
              ),
              ...rfq.events,
            ],
          }
        const rejectionCount = rfq.rejectionCount + 1
        return {
          ...rfq,
          status: rejectionCount >= 3 ? 'ESCALATED' : 'REJECTED_BY_REVIEWER',
          rejectionCount,
          quotationVersions,
          events: [
            newEvent(
              rejectionCount >= 3 ? 'Rejection threshold reached' : 'Reviewer requested changes',
              reason,
              actor,
              'red',
            ),
            ...rfq.events,
          ],
        }
      }),
    })),
  markSent: (rfqId, reference, actor) =>
    set((state) => ({
      rfqs: state.rfqs.map((rfq) =>
        rfq.id !== rfqId
          ? rfq
          : {
              ...rfq,
              status: 'SENT_TO_CUSTOMER',
              events: [
                newEvent('Sent to customer recorded', `External reference: ${reference}`, actor),
                ...rfq.events,
              ],
            },
      ),
    })),
  recordDecision: (rfqId, decision, reference, actor) =>
    set((state) => ({
      rfqs: state.rfqs.map((rfq) =>
        rfq.id !== rfqId
          ? rfq
          : {
              ...rfq,
              status: decision === 'APPROVED' ? 'CUSTOMER_APPROVED' : 'CUSTOMER_REJECTED',
              customerDecision: decision,
              events: [
                newEvent(
                  `Customer ${decision.toLowerCase()}`,
                  reference
                    ? `Source reference: ${reference}`
                    : 'Decision recorded by administrator.',
                  actor,
                  decision === 'APPROVED' ? 'green' : 'red',
                ),
                ...rfq.events,
              ],
            },
      ),
    })),
  importRfq: (rfq) => set((state) => ({ rfqs: [rfq, ...state.rfqs] })),
  resolveEscalation: (rfqId, action, reason, actor) =>
    set((state) => ({
      rfqs: state.rfqs.map((rfq) =>
        rfq.id !== rfqId
          ? rfq
          : {
              ...rfq,
              status: action === 'CLOSE' ? 'CLOSED' : 'REVISION_IN_PROGRESS',
              events: [
                newEvent(
                  action === 'CLOSE' ? 'RFQ closed by admin' : 'Additional revision authorized',
                  reason,
                  actor,
                  action === 'CLOSE' ? 'slate' : 'amber',
                ),
                ...rfq.events,
              ],
            },
      ),
    })),
  resolveCustomerRejection: (rfqId, action, reason, actor) =>
    set((state) => ({
      rfqs: state.rfqs.map((rfq) =>
        rfq.id !== rfqId
          ? rfq
          : {
              ...rfq,
              status: action === 'CLOSE' ? 'CLOSED' : 'REOPENED_FOR_REVISION',
              events: [
                newEvent(
                  action === 'CLOSE' ? 'RFQ closed' : 'RFQ reopened for revision',
                  reason,
                  actor,
                  action === 'CLOSE' ? 'slate' : 'amber',
                ),
                ...rfq.events,
              ],
            },
      ),
    })),
  setReviewerDelegation: (rfqId, allowed, reason, actor) =>
    set((state) => ({
      rfqs: state.rfqs.map((rfq) =>
        rfq.id !== rfqId
          ? rfq
          : {
              ...rfq,
              quotationManagerCanAssignReviewer: allowed,
              events: [
                newEvent('Reviewer assignment permission changed', reason, actor, 'slate'),
                ...rfq.events,
              ],
            },
      ),
    })),
}))
