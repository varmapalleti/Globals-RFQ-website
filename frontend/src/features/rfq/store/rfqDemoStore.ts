import { create } from 'zustand'
import { initialDemoRfqs } from '../demo/demoData'
import type {
  NotificationDto,
  PurchaseOrderDto,
  RedFlagDto,
} from '../api/rfqApi'
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

const nextNotificationId = (() => {
  let seq = 100
  return () => ++seq
})()

const makeNotification = (
  type: string,
  title: string,
  message: string,
  rfqPublicId: string | null = null,
): NotificationDto => ({
  id: nextNotificationId(),
  type,
  title,
  message,
  rfqPublicId,
  read: false,
  createdAt: at(),
  readAt: null,
})

const initialDemoNotifications: NotificationDto[] = [
  {
    id: 1,
    type: 'QUOTATION_SUBMITTED',
    title: 'Quotation V1 submitted for RFQ-112477',
    message: 'Anita Desai submitted quotation V1 for V2X Systems Corporation.',
    rfqPublicId: 'demo-112477',
    read: false,
    createdAt: new Date(Date.now() - 3_600_000).toISOString(),
    readAt: null,
  },
  {
    id: 2,
    type: 'QUOTATION_ESCALATED',
    title: 'RFQ-112340 escalated to Admin',
    message: 'Rejection limit threshold reached (3 rejections). Admin decision required.',
    rfqPublicId: 'demo-112340',
    read: false,
    createdAt: new Date(Date.now() - 7_200_000).toISOString(),
    readAt: null,
  },
]

interface RfqDemoState {
  rfqs: RfqRecord[]
  redFlags: RedFlagDto[]
  purchases: PurchaseOrderDto[]
  notifications: NotificationDto[]
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
  markReadyForMarketing: (rfqId: string, notes: string, actor: string) => void
  completeMarketing: (rfqId: string, notes: string, actor: string) => void
  createRedFlag: (
    rfqPublicId: string,
    severity: RedFlagDto['severity'],
    reason: string,
    raisedBy: number,
    assignedTo?: number,
  ) => void
  resolveRedFlag: (
    id: number,
    resolution: 'RESOLVE' | 'DISMISS',
    note: string,
    resolvedBy: number,
  ) => void
  createPurchaseOrder: (args: {
    rfqPublicId: string
    supplierName?: string
    assignedTo?: number
    orderDate?: string
    expectedDelivery?: string
    notes?: string
    createdBy: number
  }) => void
  updatePurchaseOrderStatus: (id: number, status: PurchaseOrderDto['status']) => void
  markNotificationRead: (id: number) => void
  markAllNotificationsRead: () => void
}

export const useRfqDemoStore = create<RfqDemoState>((set) => ({
  rfqs: initialDemoRfqs,
  redFlags: [],
  purchases: [],
  notifications: initialDemoNotifications,
  assign: (rfqId, role, userId, userName, reason, actor) =>
    set((state) => {
      const targetRfq = state.rfqs.find((r) => r.id === rfqId)
      return {
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
        notifications: targetRfq
          ? [
              makeNotification(
                role === 'MARKETING_EXECUTIVE' ? 'MARKETING_ASSIGNED' : 'RFQ_ASSIGNED',
                `Assigned ${userName} to RFQ-${targetRfq.sourceRfqId}`,
                reason || `${role.replaceAll('_', ' ')} assigned.`,
                targetRfq.id,
              ),
              ...state.notifications,
            ]
          : state.notifications,
      }
    }),
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
    set((state) => {
      const targetRfq = state.rfqs.find((r) => r.id === rfqId)
      const nextVer = (targetRfq?.currentQuotationVersion ?? 0) + 1
      return {
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
        notifications: targetRfq
          ? [
              makeNotification(
                'QUOTATION_SUBMITTED',
                `Quotation V${nextVer} submitted for RFQ-${targetRfq.sourceRfqId}`,
                `${actor} submitted V${nextVer} for review.`,
                targetRfq.id,
              ),
              ...state.notifications,
            ]
          : state.notifications,
      }
    }),
  review: (rfqId, decision, reason, actor) =>
    set((state) => {
      const targetRfq = state.rfqs.find((r) => r.id === rfqId)
      const nextCount = (targetRfq?.rejectionCount ?? 0) + (decision === 'REJECTED' ? 1 : 0)
      return {
        rfqs: state.rfqs.map((rfq) => {
          if (rfq.id !== rfqId) return rfq
          const latestVersionIndex = rfq.quotationVersions.length - 1
          const latestVersion = rfq.quotationVersions[latestVersionIndex]
          const quotationVersions = latestVersion
            ? rfq.quotationVersions.map((version, index) =>
                index === latestVersionIndex
                  ? {
                      ...version,
                      status:
                        decision === 'APPROVED' ? ('APPROVED' as const) : ('REJECTED' as const),
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
        notifications: targetRfq
          ? [
              makeNotification(
                decision === 'APPROVED'
                  ? 'QUOTATION_APPROVED'
                  : nextCount >= 3
                    ? 'QUOTATION_ESCALATED'
                    : 'QUOTATION_REJECTED',
                decision === 'APPROVED'
                  ? `Quotation approved for RFQ-${targetRfq.sourceRfqId}`
                  : nextCount >= 3
                    ? `RFQ-${targetRfq.sourceRfqId} escalated to Admin`
                    : `Quotation rejected for RFQ-${targetRfq.sourceRfqId}`,
                reason || `Decision recorded by ${actor}.`,
                targetRfq.id,
              ),
              ...state.notifications,
            ]
          : state.notifications,
      }
    }),
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
    set((state) => {
      const targetRfq = state.rfqs.find((r) => r.id === rfqId)
      return {
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
        notifications: targetRfq
          ? [
              makeNotification(
                'CUSTOMER_DECISION_RECORDED',
                `Customer ${decision.toLowerCase()} RFQ-${targetRfq.sourceRfqId}`,
                reference ? `Ref: ${reference}` : 'Recorded by administrator.',
                targetRfq.id,
              ),
              ...state.notifications,
            ]
          : state.notifications,
      }
    }),
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
  markReadyForMarketing: (rfqId, notes, actor) =>
    set((state) => ({
      rfqs: state.rfqs.map((rfq) =>
        rfq.id !== rfqId
          ? rfq
          : {
              ...rfq,
              status: 'READY_FOR_MARKETING',
              events: [
                newEvent(
                  'Ready for marketing',
                  notes || 'Marked ready for marketing handoff.',
                  actor,
                  'blue',
                ),
                ...rfq.events,
              ],
            },
      ),
    })),
  completeMarketing: (rfqId, notes, actor) =>
    set((state) => {
      const targetRfq = state.rfqs.find((r) => r.id === rfqId)
      return {
        rfqs: state.rfqs.map((rfq) =>
          rfq.id !== rfqId
            ? rfq
            : {
                ...rfq,
                status: 'CLOSED',
                events: [
                  newEvent(
                    'Marketing completed & closed',
                    notes || 'Marketing activities completed.',
                    actor,
                    'green',
                  ),
                  ...rfq.events,
                ],
              },
        ),
        notifications: targetRfq
          ? [
              makeNotification(
                'GENERAL',
                `Marketing completed for RFQ-${targetRfq.sourceRfqId}`,
                notes || 'RFQ closed after marketing handoff.',
                targetRfq.id,
              ),
              ...state.notifications,
            ]
          : state.notifications,
      }
    }),
  createRedFlag: (rfqPublicId, severity, reason, raisedBy, assignedTo) =>
    set((state) => {
      const rfq = state.rfqs.find((r) => r.id === rfqPublicId)
      const id = Math.max(0, ...state.redFlags.map((f) => f.id)) + 1
      const flag: RedFlagDto = {
        id,
        rfqId: id,
        rfqPublicId,
        sourceRfqId: rfq?.sourceRfqId ?? null,
        customerName: rfq?.customerName ?? null,
        severity,
        reason,
        status: 'OPEN',
        raisedBy,
        assignedTo: assignedTo ?? null,
        resolutionNote: null,
        resolvedBy: null,
        resolvedAt: null,
        createdAt: at(),
      }
      return {
        redFlags: [flag, ...state.redFlags],
        notifications: [
          makeNotification(
            'RED_FLAG_RAISED',
            `Red flag raised on RFQ-${rfq?.sourceRfqId ?? rfqPublicId}`,
            `[${severity}] ${reason}`,
            rfqPublicId,
          ),
          ...state.notifications,
        ],
      }
    }),
  resolveRedFlag: (id, resolution, note, resolvedBy) =>
    set((state) => ({
      redFlags: state.redFlags.map((flag) =>
        flag.id !== id
          ? flag
          : {
              ...flag,
              status: resolution === 'RESOLVE' ? 'RESOLVED' : 'DISMISSED',
              resolutionNote: note,
              resolvedBy,
              resolvedAt: at(),
            },
      ),
    })),
  createPurchaseOrder: (args) =>
    set((state) => {
      const rfq = state.rfqs.find((r) => r.id === args.rfqPublicId)
      const id = Math.max(0, ...state.purchases.map((p) => p.id)) + 1
      const orderId = `PO-${crypto.randomUUID().slice(0, 8).toUpperCase()}`
      const po: PurchaseOrderDto = {
        id,
        orderId,
        rfqId: id,
        rfqPublicId: args.rfqPublicId,
        sourceRfqId: rfq?.sourceRfqId ?? null,
        customerName: rfq?.customerName ?? null,
        quotationVersionId: null,
        supplierName: args.supplierName ?? null,
        status: 'PENDING',
        assignedTo: args.assignedTo ?? null,
        orderDate: args.orderDate ?? null,
        expectedDelivery: args.expectedDelivery ?? null,
        notes: args.notes ?? null,
        createdBy: args.createdBy,
        createdAt: at(),
      }
      return { purchases: [po, ...state.purchases] }
    }),
  updatePurchaseOrderStatus: (id, status) =>
    set((state) => ({
      purchases: state.purchases.map((po) => (po.id !== id ? po : { ...po, status })),
    })),
  markNotificationRead: (id) =>
    set((state) => ({
      notifications: state.notifications.map((n) =>
        n.id === id ? { ...n, read: true, readAt: at() } : n,
      ),
    })),
  markAllNotificationsRead: () =>
    set((state) => ({
      notifications: state.notifications.map((n) => (n.read ? n : { ...n, read: true, readAt: at() })),
    })),
}))
