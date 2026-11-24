import type { RfqRecord, RfqStatus, WorkflowEvent } from '../types'

const now = new Date()
const daysAgo = (days: number) => new Date(now.getTime() - days * 86_400_000).toISOString()
const futureDate = (days: number) =>
  new Date(now.getTime() + days * 86_400_000).toISOString().slice(0, 10)

const item = (
  sourceLineId: string,
  description: string,
  requestedQuantity: number,
  partNumber: string,
  category: string,
) => ({
  sourceLineId,
  lineNumber: Number(sourceLineId),
  description,
  requestedQuantity,
  unitOfMeasure: 'EA',
  requestedManufacturer: 'ABB',
  requestedPartNumber: partNumber,
  procurementCategory: category,
  taaRequired: true,
  technicalSpecifications:
    'Customer supplied technical requirements are retained with the source request.',
})

const quote = (line: ReturnType<typeof item>, price: number) => ({
  sourceLineId: line.sourceLineId,
  description: line.description,
  quantity: line.requestedQuantity,
  unitOfMeasure: line.unitOfMeasure,
  manufacturer: line.requestedManufacturer,
  partNumber: line.requestedPartNumber,
  unitPrice: price,
  taaCompliant: true,
  deliveryTimeline: '15 business days',
  warranty: '12 months',
  note: '',
  productUrl: '',
})

const events = (status: RfqStatus, createdAt: string): WorkflowEvent[] => {
  const trail: WorkflowEvent[] = [
    {
      id: 'created',
      label: 'RFQ imported',
      detail: 'Source request and workbook captured.',
      actor: 'Ritesh Solanke',
      createdAt,
      tone: 'blue',
    },
  ]
  if (status !== 'IMPORTED')
    trail.push({
      id: 'assigned',
      label: 'Quotation manager assigned',
      detail: 'Assigned through the Global RFQ workflow.',
      actor: 'Ritesh Solanke',
      createdAt: daysAgo(2),
      tone: 'blue',
    })
  if (
    [
      'PENDING_REVIEW',
      'REVISION_IN_PROGRESS',
      'APPROVED_BY_REVIEWER',
      'SENT_TO_CUSTOMER',
      'CUSTOMER_APPROVED',
      'CUSTOMER_REJECTED',
      'ASSIGNED_TO_MARKETING',
      'ESCALATED',
    ].includes(status)
  )
    trail.push({
      id: 'submitted',
      label: 'Quotation version submitted',
      detail: 'Immutable version snapshot created for internal review.',
      actor: 'Anita Desai',
      createdAt: daysAgo(1),
      tone: 'amber',
    })
  if (
    [
      'APPROVED_BY_REVIEWER',
      'SENT_TO_CUSTOMER',
      'CUSTOMER_APPROVED',
      'CUSTOMER_REJECTED',
      'ASSIGNED_TO_MARKETING',
    ].includes(status)
  )
    trail.push({
      id: 'reviewed',
      label: 'Reviewer approved',
      detail: 'Required checks passed for the submitted version.',
      actor: 'Priya Nair',
      createdAt: daysAgo(1),
      tone: 'green',
    })
  if (
    [
      'SENT_TO_CUSTOMER',
      'CUSTOMER_APPROVED',
      'CUSTOMER_REJECTED',
      'ASSIGNED_TO_MARKETING',
    ].includes(status)
  )
    trail.push({
      id: 'sent',
      label: 'Recorded as sent to customer',
      detail: 'Admin recorded the external-system reference.',
      actor: 'Ritesh Solanke',
      createdAt: daysAgo(0.8),
      tone: 'blue',
    })
  if (['CUSTOMER_APPROVED', 'ASSIGNED_TO_MARKETING'].includes(status))
    trail.push({
      id: 'customer',
      label: 'Customer approved',
      detail: 'Decision recorded by admin from the source system.',
      actor: 'Ritesh Solanke',
      createdAt: daysAgo(0.4),
      tone: 'green',
    })
  if (status === 'ASSIGNED_TO_MARKETING')
    trail.push({
      id: 'marketing',
      label: 'Assigned to marketing',
      detail: 'Ready for the post-approval business process.',
      actor: 'Ritesh Solanke',
      createdAt: daysAgo(0.2),
      tone: 'blue',
    })
  if (status === 'REVISION_IN_PROGRESS')
    trail.push({
      id: 'rejected',
      label: 'Reviewer requested changes',
      detail: 'Freight and delivery information needs confirmation.',
      actor: 'Priya Nair',
      createdAt: daysAgo(0.6),
      tone: 'red',
    })
  if (status === 'ESCALATED')
    trail.push({
      id: 'escalated',
      label: 'Escalated to admin',
      detail: 'Configured rejection threshold reached.',
      actor: 'Workflow policy',
      createdAt: daysAgo(0.1),
      tone: 'red',
    })
  return trail
}

const makeRfq = (args: {
  sourceRfqId: string
  customerName: string
  requestTitle: string
  status: RfqStatus
  managerId?: number
  reviewerId?: number
  marketingId?: number
  customerDecision?: RfqRecord['customerDecision']
  daysOld: number
  items: ReturnType<typeof item>[]
  price: number
  rejectionCount?: number
}): RfqRecord => {
  const createdAt = daysAgo(args.daysOld)
  const quoteDraft: RfqRecord['quoteDraft'] = {
    currency: 'USD',
    freight: args.price > 0 ? 185 : 0,
    otherCharges: 0,
    discount: 0,
    taxAmount: 0,
    deliveryTimeline: '15 business days',
    warranty: '12 months',
    notes: '',
    items: args.items.map((requested) => quote(requested, args.price)),
  }
  const hasSubmittedVersion = [
    'PENDING_REVIEW',
    'REVISION_IN_PROGRESS',
    'APPROVED_BY_REVIEWER',
    'SENT_TO_CUSTOMER',
    'CUSTOMER_APPROVED',
    'CUSTOMER_REJECTED',
    'ASSIGNED_TO_MARKETING',
    'ESCALATED',
  ].includes(args.status)
  const reviewedAsRejected = ['REVISION_IN_PROGRESS', 'ESCALATED'].includes(args.status)
  const quotationVersions = hasSubmittedVersion
    ? [
        {
          id: 1,
          versionNumber: 1,
          status:
            args.status === 'PENDING_REVIEW'
              ? ('PENDING_REVIEW' as const)
              : reviewedAsRejected
                ? ('REJECTED' as const)
                : ('APPROVED' as const),
          submittedAt: daysAgo(Math.max(0, args.daysOld - 0.5)),
          draftSnapshot: { ...quoteDraft, items: quoteDraft.items.map((line) => ({ ...line })) },
          missingMandatoryFields: [],
          reviewDecision:
            args.status === 'PENDING_REVIEW'
              ? null
              : reviewedAsRejected
                ? ('REJECTED' as const)
                : ('APPROVED' as const),
          reviewReason: reviewedAsRejected
            ? 'Freight and delivery information needs confirmation.'
            : '',
          reviewerName: args.reviewerId === 5 ? 'Daniel Brooks' : 'Priya Nair',
        },
      ]
    : []
  return {
    id: `demo-${args.sourceRfqId}`,
    sourceRfqId: args.sourceRfqId,
    customerName: args.customerName,
    customerCompany: args.customerName,
    buyerName: 'Marty Barclay',
    buyerEmail: 'buyer@example.customer',
    requestTitle: args.requestTitle,
    dueAt: futureDate(3),
    dueTimeZone: 'Asia/Kolkata',
    requestedDeliveryDate: futureDate(40),
    shipToText: 'Oakland Cross-Dock\n9757 San Leandro St\nOakland, CA 94603\nUSA',
    sourceSystem: 'CUSTOMER_SOFTWARE',
    sourceFileName: `RFQ ${args.sourceRfqId} source request.xlsx`,
    status: args.status,
    customerDecision: args.customerDecision ?? 'PENDING',
    quotationManagerCanAssignReviewer: false,
    version: 1,
    rejectionCount: args.rejectionCount ?? (args.status === 'REVISION_IN_PROGRESS' ? 1 : 0),
    currentQuotationVersion: quotationVersions.length,
    quotationVersions,
    assignments: [
      ...(args.managerId
        ? [
            {
              role: 'QUOTATION_MANAGER' as const,
              userId: args.managerId,
              userName: args.managerId === 2 ? 'Anita Desai' : 'Rahul Mehta',
              assignedAt: daysAgo(Math.max(0, args.daysOld - 1)),
            },
          ]
        : []),
      ...(args.reviewerId
        ? [
            {
              role: 'REVIEWER' as const,
              userId: args.reviewerId,
              userName: args.reviewerId === 4 ? 'Priya Nair' : 'Daniel Brooks',
              assignedAt: daysAgo(Math.max(0, args.daysOld - 1)),
            },
          ]
        : []),
      ...(args.marketingId
        ? [
            {
              role: 'MARKETING_EXECUTIVE' as const,
              userId: args.marketingId,
              userName: 'Maya Shah',
              assignedAt: daysAgo(0.2),
            },
          ]
        : []),
    ],
    lineItems: args.items,
    quoteDraft,
    events: events(args.status, createdAt),
    createdAt,
  }
}

export const initialDemoRfqs: RfqRecord[] = [
  makeRfq({
    sourceRfqId: '112477',
    customerName: 'V2X Systems Corporation',
    requestTitle: 'Three phase blower motor',
    status: 'PENDING_REVIEW',
    managerId: 2,
    reviewerId: 4,
    daysOld: 2,
    items: [
      item(
        '1',
        'Motor, AC, 3 phase, 5.5 kW, 1000 RPM, 6 pole, IEC 132ME, B3, TEFC, IP55',
        2,
        'EMM13556-S-A',
        'Heavy Equipment',
      ),
    ],
    price: 1851.43,
  }),
  makeRfq({
    sourceRfqId: '112508',
    customerName: 'Global Services Solution',
    requestTitle: 'TAA compliant field equipment',
    status: 'REVISION_IN_PROGRESS',
    managerId: 2,
    reviewerId: 4,
    daysOld: 4,
    items: [
      item(
        '1',
        'Portable power distribution unit with environmental enclosure',
        4,
        'PDU-480-XT',
        'TAA Compliant',
      ),
    ],
    price: 720.5,
  }),
  makeRfq({
    sourceRfqId: '112531',
    customerName: 'Defense Logistics Office',
    requestTitle: 'Workshop tools and consumables',
    status: 'ASSIGNED_TO_QUOTATION_MANAGER',
    managerId: 3,
    daysOld: 1,
    items: [
      item('1', 'Industrial torque wrench set, calibrated', 8, 'TW-250-L', 'Tools'),
      item('2', 'Heavy duty absorbent spill kit', 12, 'SK-40-IND', 'Consumables'),
    ],
    price: 245,
  }),
  makeRfq({
    sourceRfqId: '112466',
    customerName: 'V2X Systems Corporation',
    requestTitle: 'Electrical panel replacement materials',
    status: 'APPROVED_BY_REVIEWER',
    managerId: 3,
    reviewerId: 5,
    daysOld: 5,
    items: [item('1', 'Low-voltage control panel assembly', 1, 'LVC-200-UL', 'Materials')],
    price: 4320,
  }),
  makeRfq({
    sourceRfqId: '112401',
    customerName: 'Federal Facilities Group',
    requestTitle: 'Ground support equipment',
    status: 'SENT_TO_CUSTOMER',
    managerId: 2,
    reviewerId: 4,
    daysOld: 8,
    items: [item('1', 'Aircraft tow bar assembly', 2, 'ATB-2200', 'Heavy Equipment')],
    price: 6100,
  }),
  makeRfq({
    sourceRfqId: '112372',
    customerName: 'V2X Systems Corporation',
    requestTitle: 'Replacement electrical motors',
    status: 'ASSIGNED_TO_MARKETING',
    managerId: 3,
    reviewerId: 5,
    marketingId: 6,
    customerDecision: 'APPROVED',
    daysOld: 12,
    items: [
      item('1', 'Three phase industrial motor, TEFC enclosure', 6, 'MTR-3P-220', 'Materials'),
    ],
    price: 940,
  }),
  makeRfq({
    sourceRfqId: '112340',
    customerName: 'Government Procurement Office',
    requestTitle: 'Portable maintenance tool kits',
    status: 'ESCALATED',
    managerId: 2,
    reviewerId: 4,
    daysOld: 16,
    items: [item('1', 'Portable hydraulic service toolkit', 2, 'HST-440', 'Tools')],
    price: 1880,
    rejectionCount: 3,
  }),
  makeRfq({
    sourceRfqId: '112299',
    customerName: 'Global Services Solution',
    requestTitle: 'Bulk shop consumables',
    status: 'IMPORTED',
    daysOld: 0,
    items: [item('1', 'Industrial cleaning cloth rolls', 50, 'CCR-1000', 'Consumables')],
    price: 0,
  }),
]
