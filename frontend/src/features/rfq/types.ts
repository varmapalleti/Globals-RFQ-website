import type { RoleName } from '@/types/api'

export type RfqStatus =
  | 'IMPORTED'
  | 'ASSIGNED_TO_QUOTATION_MANAGER'
  | 'QUOTATION_IN_PROGRESS'
  | 'PENDING_REVIEW'
  | 'REJECTED_BY_REVIEWER'
  | 'AWAITING_REASSIGNMENT'
  | 'REVISION_IN_PROGRESS'
  | 'APPROVED_BY_REVIEWER'
  | 'SENT_TO_CUSTOMER'
  | 'CUSTOMER_APPROVED'
  | 'CUSTOMER_REJECTED'
  | 'REOPENED_FOR_REVISION'
  | 'READY_FOR_MARKETING'
  | 'ASSIGNED_TO_MARKETING'
  | 'ESCALATED'
  | 'CLOSED'
  | 'CANCELLED'

export type RfqAssignmentRole = 'QUOTATION_MANAGER' | 'REVIEWER' | 'MARKETING_EXECUTIVE'
export type CustomerDecision = 'PENDING' | 'APPROVED' | 'REJECTED' | 'CLARIFICATION_REQUIRED'

export interface QuotationLine {
  sourceLineId: string
  description: string
  quantity: number
  unitOfMeasure: string
  manufacturer: string
  partNumber: string
  unitPrice: number
  taaCompliant: boolean | null
  deliveryTimeline: string
  warranty: string
  note: string
  productUrl: string
}

export interface RfqLineItem {
  sourceLineId: string
  lineNumber: number
  description: string
  requestedQuantity: number
  unitOfMeasure: string
  requestedManufacturer: string
  requestedPartNumber: string
  procurementCategory: string
  taaRequired: boolean | null
  technicalSpecifications: string
}

export interface RfqAssignment {
  role: RfqAssignmentRole
  userId: number
  userName: string
  assignedAt: string
}

export interface WorkflowEvent {
  id: string
  label: string
  detail: string
  actor: string
  createdAt: string
  tone: 'blue' | 'green' | 'amber' | 'red' | 'slate'
}

export interface RfqQuotationVersion {
  id: number
  versionNumber: number
  status: 'PENDING_REVIEW' | 'APPROVED' | 'REJECTED'
  submittedAt: string
  draftSnapshot: RfqRecord['quoteDraft']
  missingMandatoryFields: string[]
  reviewDecision: 'APPROVED' | 'REJECTED' | null
  reviewReason: string
  reviewerName: string
}

export interface RfqRecord {
  id: string
  sourceRfqId: string
  sourceReference?: string
  customerName: string
  customerCompany: string
  buyerName: string
  buyerEmail: string
  requestTitle: string
  dueAt: string
  dueTimeZone: string
  requestedDeliveryDate: string
  shipToText: string
  sourceSystem: string
  sourceFileName: string
  status: RfqStatus
  customerDecision: CustomerDecision
  quotationManagerCanAssignReviewer: boolean
  version: number
  rejectionCount: number
  currentQuotationVersion: number
  lineItemCount?: number
  quotationVersions: RfqQuotationVersion[]
  assignments: RfqAssignment[]
  lineItems: RfqLineItem[]
  quoteDraft: {
    currency: string
    freight: number
    otherCharges: number
    discount: number
    taxAmount: number
    deliveryTimeline: string
    warranty: string
    notes: string
    items: QuotationLine[]
  }
  events: WorkflowEvent[]
  createdAt: string
}

export interface DemoUser {
  id: number
  fullName: string
  officialEmail: string
  roles: RoleName[]
}

export const roleTitles: Record<RoleName, string> = {
  SUPER_ADMIN: 'Super administrator',
  ADMIN: 'Administrator',
  QUOTATION_MANAGER: 'Quotation manager',
  REVIEWER: 'Reviewer',
  MARKETING_EXECUTIVE: 'Marketing executive',
  VIEWER: 'Read-only viewer',
}

export const statusLabels: Record<RfqStatus, string> = {
  IMPORTED: 'Imported',
  ASSIGNED_TO_QUOTATION_MANAGER: 'Manager assigned',
  QUOTATION_IN_PROGRESS: 'Quotation in progress',
  PENDING_REVIEW: 'Pending review',
  REJECTED_BY_REVIEWER: 'Rejected by reviewer',
  AWAITING_REASSIGNMENT: 'Awaiting reassignment',
  REVISION_IN_PROGRESS: 'Revision in progress',
  APPROVED_BY_REVIEWER: 'Approved by reviewer',
  SENT_TO_CUSTOMER: 'Sent to customer',
  CUSTOMER_APPROVED: 'Customer approved',
  CUSTOMER_REJECTED: 'Customer rejected',
  REOPENED_FOR_REVISION: 'Reopened for revision',
  READY_FOR_MARKETING: 'Ready for marketing',
  ASSIGNED_TO_MARKETING: 'Assigned to marketing',
  ESCALATED: 'Admin escalation',
  CLOSED: 'Closed',
  CANCELLED: 'Cancelled',
}

export const demoUsers: DemoUser[] = [
  { id: 1, fullName: 'Ritesh Solanke', officialEmail: 'admin@globalrfq.demo', roles: ['ADMIN'] },
  {
    id: 2,
    fullName: 'Anita Desai',
    officialEmail: 'anita@globalrfq.demo',
    roles: ['QUOTATION_MANAGER'],
  },
  {
    id: 3,
    fullName: 'Rahul Mehta',
    officialEmail: 'rahul@globalrfq.demo',
    roles: ['QUOTATION_MANAGER'],
  },
  { id: 4, fullName: 'Priya Nair', officialEmail: 'priya@globalrfq.demo', roles: ['REVIEWER'] },
  { id: 5, fullName: 'Daniel Brooks', officialEmail: 'daniel@globalrfq.demo', roles: ['REVIEWER'] },
  {
    id: 6,
    fullName: 'Maya Shah',
    officialEmail: 'maya@globalrfq.demo',
    roles: ['MARKETING_EXECUTIVE'],
  },
  {
    id: 7,
    fullName: 'Global RFQ Owner',
    officialEmail: 'owner@globalrfq.demo',
    roles: ['SUPER_ADMIN'],
  },
  { id: 8, fullName: 'Client Observer', officialEmail: 'viewer@globalrfq.demo', roles: ['VIEWER'] },
]

export const demoRoleOrder: RoleName[] = [
  'ADMIN',
  'QUOTATION_MANAGER',
  'REVIEWER',
  'MARKETING_EXECUTIVE',
  'VIEWER',
  'SUPER_ADMIN',
]

export const formatRfqNumber = (rfq: Pick<RfqRecord, 'sourceRfqId'>) => `RFQ-${rfq.sourceRfqId}`
