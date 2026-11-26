import { http } from '@/lib/http'
import type { ApiSuccessResponse } from '@/types/api'
import type { CustomerDecision, RfqAssignmentRole, RfqStatus } from '../types'

export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}
export interface RfqSummaryDto {
  id: string
  sourceRfqId: string
  customerName: string
  requestTitle: string
  status: RfqStatus
  customerDecision: CustomerDecision
  requestedDeliveryDate: string | null
  createdAt: string
  sourceReference?: string | null
  dueAtLocal?: string | null
  lineItemCount?: number
  assigneeUserId?: number | null
  assigneeRole?: RfqAssignmentRole | null
}
export interface RfqDetailDto extends RfqSummaryDto {
  sourceSystem: string
  sourceReference: string | null
  sourceQuotationManagerName: string | null
  customerCompany: string | null
  buyerName: string | null
  buyerEmail: string | null
  buyerPhone: string | null
  dueAtLocal: string | null
  dueTimeZone: string | null
  shipToText: string | null
  sourceUrl: string | null
  termsAndConditions: string | null
  quotationManagerCanAssignReviewer: boolean
  version: number
  lineItems: Array<{
    sourceLineId: string
    lineNumber: number
    description: string
    requestedQuantity: number
    unitOfMeasure: string | null
    customerPartNumber: string | null
    requestedManufacturer: string | null
    requestedPartNumber: string | null
    procurementCategory: string | null
    taaRequired: boolean | null
    technicalSpecifications: string | null
    sourceDataJson: string | null
  }>
  assignments: Array<{
    role: RfqAssignmentRole
    userId: number
    assignedByUserId: number
    reason: string | null
    assignedAt: string
  }>
}
export interface ImportPreviewDto {
  fileName: string
  detectedFormat: string
  normalizedRfq: Record<string, unknown>
  fieldMappings: Record<string, string>
  warnings: string[]
  previewToken: string
  expiresAt: string
}
export interface CreateRfqRequestDto {
  sourceSystem: string
  sourceRfqId: string
  sourceReference?: string | null
  sourceQuotationManagerName?: string | null
  customerName: string
  customerCompany?: string | null
  buyerName?: string | null
  buyerEmail?: string | null
  buyerPhone?: string | null
  requestTitle?: string | null
  dueAtLocal?: string | null
  dueTimeZone?: string | null
  requestedDeliveryDate?: string | null
  shipToText?: string | null
  sourceUrl?: string | null
  termsAndConditions?: string | null
  sourceData?: Record<string, unknown>
  lineItems: Array<{
    sourceLineId: string
    lineNumber: number
    description: string
    requestedQuantity: number
    unitOfMeasure?: string | null
    customerPartNumber?: string | null
    requestedManufacturer?: string | null
    requestedPartNumber?: string | null
    procurementCategory?: string | null
    taaRequired?: boolean | null
    technicalSpecifications?: string | null
    sourceData?: Record<string, unknown>
  }>
}
export interface AttachmentDto {
  id: string
  fileName: string
  extension: string
  mediaType: string
  sizeBytes: number
  sha256: string
  category: string
  scanState: string
  lineNumber: number | null
  uploadedByUserId: number | null
  createdAt: string
}
export interface RfqOperationalReport {
  generatedAt: string
  createdFrom: string | null
  createdThrough: string | null
  statusFilter: RfqStatus | null
  totalRfqs: number
  statusCounts: Record<string, number>
  customerDecisionCounts: Record<string, number>
  activeAssignmentWorkload: Array<{ role: string; identityUserId: number; activeRfqs: number }>
}
export interface RfqDashboardSummary {
  totalRfqs: number
  statusCounts: Record<string, number>
}

export async function getRfqDashboardSummary() {
  const { data } = await http.get<ApiSuccessResponse<RfqDashboardSummary>>(
    '/api/rfqs/dashboard/summary',
  )
  return data.data
}

export async function getRfqs(
  page = 0,
  size = 100,
  sourceRfqId?: string,
  filters?: { search?: string; status?: RfqStatus; sort?: 'createdAt' | 'requestedDeliveryDate' },
) {
  const { data } = await http.get<ApiSuccessResponse<PageResponse<RfqSummaryDto>>>('/api/rfqs', {
    params: {
      page,
      size,
      sourceRfqId: sourceRfqId || undefined,
      search: filters?.search || undefined,
      status: filters?.status || undefined,
      sort: filters?.sort || undefined,
    },
  })
  return data.data
}
export async function getRfq(publicId: string) {
  const { data } = await http.get<ApiSuccessResponse<RfqDetailDto>>(`/api/rfqs/${publicId}`)
  return data.data
}
export async function createRfq(request: CreateRfqRequestDto) {
  const { data } = await http.post<ApiSuccessResponse<RfqDetailDto>>('/api/rfqs', request)
  return data.data
}
export async function getHistory(publicId: string) {
  const { data } = await http.get<ApiSuccessResponse<Array<Record<string, unknown>>>>(
    `/api/rfqs/${publicId}/history`,
  )
  return data.data
}
export async function getQuotationDraft(publicId: string) {
  const { data } = await http.get<ApiSuccessResponse<{ draft: QuotationDraftDto }>>(
    `/api/rfqs/${publicId}/quotation-draft`,
  )
  return data.data.draft
}
export async function setReviewerDelegation(publicId: string, allowed: boolean, reason: string) {
  const { data } = await http.patch<
    ApiSuccessResponse<{ quotationManagerCanAssignReviewer: boolean }>
  >(`/api/rfqs/${publicId}/reviewer-assignment-delegation`, {
    quotationManagerCanAssignReviewer: allowed,
    reason,
  })
  return data.data
}
export interface RfqAdminAuditEvent {
  id: number
  actionType: string
  actorUserId: number
  reason: string
  beforeJson: string | null
  afterJson: string | null
  correlationId: string | null
  occurredAt: string
}
export async function getRfqRuleConfig() {
  const [mandatory, rejectionLimit, audit] = await Promise.all([
    http.get<ApiSuccessResponse<string[]>>('/api/admin/rfq-rules/mandatory-fields'),
    http.get<ApiSuccessResponse<{ maxFormalRejections: number }>>(
      '/api/admin/rfq-rules/rejection-limit',
    ),
    http.get<ApiSuccessResponse<RfqAdminAuditEvent[]>>('/api/admin/rfq-rules/audit'),
  ])
  return {
    mandatoryFields: mandatory.data.data,
    maxFormalRejections: rejectionLimit.data.data.maxFormalRejections,
    audit: audit.data.data,
  }
}
export async function updateMandatoryFields(requiredFields: string[], reason: string) {
  const { data } = await http.put<ApiSuccessResponse<string[]>>(
    '/api/admin/rfq-rules/mandatory-fields',
    { requiredFields, reason },
  )
  return data.data
}
export async function updateRfqRejectionLimit(maxFormalRejections: number, reason: string) {
  const { data } = await http.patch<ApiSuccessResponse<{ maxFormalRejections: number }>>(
    '/api/admin/rfq-rules/rejection-limit',
    { maxFormalRejections, reason },
  )
  return data.data
}
export interface ReviewerReassignmentRequestDto {
  id: number
  rfqId: string
  requestedByReviewerId: number
  requestReason: string
  status: string
  resolvedByAdminId: number | null
  resolutionReason: string | null
  replacementReviewerId: number | null
  requestedAt: string
  resolvedAt: string | null
}
export async function requestReviewerReassignment(publicId: string, reason: string) {
  const { data } = await http.post<ApiSuccessResponse<ReviewerReassignmentRequestDto>>(
    `/api/rfqs/${publicId}/reviewer-reassignment-requests`,
    { reason },
    { headers: { 'Idempotency-Key': crypto.randomUUID() } },
  )
  return data.data
}
export async function getReviewerReassignmentRequests(publicId: string) {
  const { data } = await http.get<ApiSuccessResponse<ReviewerReassignmentRequestDto[]>>(
    `/api/rfqs/${publicId}/reviewer-reassignment-requests`,
  )
  return data.data
}
export async function resolveReviewerReassignment(
  publicId: string,
  requestId: number,
  decision: 'APPROVE' | 'DECLINE',
  reason: string,
  replacementReviewerId?: number,
) {
  const { data } = await http.post<ApiSuccessResponse<ReviewerReassignmentRequestDto>>(
    `/api/rfqs/${publicId}/reviewer-reassignment-requests/${requestId}/resolve`,
    { decision, replacementReviewerId, reason },
  )
  return data.data
}
export async function exportRfq(
  publicId: string,
  format: 'xlsx' | 'pdf',
  quotationVersionId?: number,
) {
  const response = await http.get<Blob>(`/api/rfqs/${publicId}/export`, {
    params: { format, quotationVersionId },
    responseType: 'blob',
  })
  return {
    blob: response.data,
    disposition: response.headers['content-disposition'] as string | undefined,
  }
}
export async function resolveWorkflow(
  publicId: string,
  endpoint: 'escalation-resolution' | 'customer-rejection-resolution',
  resolution: 'AUTHORIZE_REVISION' | 'CLOSE',
  reason: string,
) {
  const { data } = await http.post<ApiSuccessResponse<RfqDetailDto>>(
    `/api/rfqs/${publicId}/${endpoint}`,
    { resolution, reason },
  )
  return data.data
}
export async function previewImport(file: File, sourceSystem: string, sourceRfqId?: string) {
  const form = new FormData()
  form.append('file', file)
  form.append('sourceSystem', sourceSystem)
  if (sourceRfqId) form.append('sourceRfqId', sourceRfqId)
  const { data } = await http.post<ApiSuccessResponse<ImportPreviewDto>>(
    '/api/rfqs/imports/preview',
    form,
  )
  return data.data
}
export async function confirmImport(previewToken: string, normalizedRfq: Record<string, unknown>) {
  const { data } = await http.post<ApiSuccessResponse<{ rfq: RfqDetailDto }>>(
    '/api/rfqs/imports/confirm',
    { previewToken, normalizedRfq },
  )
  return data.data
}
export async function assignRfq(
  publicId: string,
  assignmentRole: RfqAssignmentRole,
  assignedUserId: number,
  reason?: string,
) {
  const { data } = await http.post<ApiSuccessResponse<RfqDetailDto>>(
    `/api/rfqs/${publicId}/assignments`,
    { assignmentRole, assignedUserId, reason },
  )
  return data.data
}
export interface QuotationDraftDto {
  currency: string
  items: Array<{
    sourceLineId: string
    description: string
    quantity: number
    unitPrice: number
    unitOfMeasure: string
    manufacturer: string
    partNumber: string
    taaCompliant: boolean | null
    deliveryTimeline: string
    warranty: string
    note: string
    productUrl: string
  }>
  freight: number
  otherCharges: number
  discount: number
  taxAmount: number
  deliveryTimeline: string
  warranty: string
  notes: string
}
export interface QuotationVersionDto {
  id: number
  versionNumber: number
  status: string
  currency: string
  subtotal: number
  freight: number
  otherCharges: number
  discount: number
  taxAmount: number
  grandTotal: number
  quotation: {
    currency: string
    items: Array<{
      sourceLineId: string
      description: string
      quantity: number
      unitPrice: number
      lineTotal: number
      unitOfMeasure: string | null
      manufacturer: string | null
      partNumber: string | null
      taaCompliant: boolean | null
      deliveryTimeline: string | null
      warranty: string | null
      note: string | null
      productUrl: string | null
    }>
    freight: number
    otherCharges: number
    discount: number
    taxAmount: number
    subtotal: number
    grandTotal: number
    deliveryTimeline: string | null
    warranty: string | null
    notes: string | null
  }
  mandatoryFieldsSnapshot: string[]
  missingMandatoryFields: string[]
  submittedByUserId: number
  submittedAt: string
  review: {
    decision: string
    reviewerUserId: number
    reason: string | null
    decidedAt: string
  } | null
}
export async function saveQuotationDraft(publicId: string, draft: QuotationDraftDto) {
  const { data } = await http.put<ApiSuccessResponse<unknown>>(
    `/api/rfqs/${publicId}/quotation-draft`,
    draft,
  )
  return data.data
}
export async function submitQuotation(publicId: string, draft: QuotationDraftDto) {
  const { data } = await http.post<ApiSuccessResponse<{ id: number; versionNumber: number }>>(
    `/api/rfqs/${publicId}/quotation-versions`,
    draft,
    { headers: { 'Idempotency-Key': crypto.randomUUID() } },
  )
  return data.data
}
export async function getQuotationVersions(publicId: string) {
  const { data } = await http.get<
    ApiSuccessResponse<QuotationVersionDto[]>
  >(`/api/rfqs/${publicId}/quotation-versions`)
  return data.data
}
export async function submitReview(
  publicId: string,
  versionId: number,
  decision: 'APPROVED' | 'REJECTED',
  reason: string,
) {
  const { data } = await http.post<ApiSuccessResponse<unknown>>(
    `/api/rfqs/${publicId}/quotation-versions/${versionId}/review`,
    { decision, reason },
  )
  return data.data
}
export async function recordCustomerSubmission(
  publicId: string,
  quotationVersionId: number,
  externalReference: string,
  notes: string,
) {
  const { data } = await http.post<ApiSuccessResponse<RfqDetailDto>>(
    `/api/rfqs/${publicId}/customer-submissions`,
    { quotationVersionId, externalReference, notes },
    { headers: { 'Idempotency-Key': crypto.randomUUID() } },
  )
  return data.data
}
export async function recordCustomerDecision(
  publicId: string,
  quotationVersionId: number,
  decision: CustomerDecision,
  sourceReference: string,
  notes: string,
) {
  const { data } = await http.post<ApiSuccessResponse<RfqDetailDto>>(
    `/api/rfqs/${publicId}/customer-decision`,
    { quotationVersionId, decision, sourceReference, notes },
    { headers: { 'Idempotency-Key': crypto.randomUUID() } },
  )
  return data.data
}
export async function getAttachments(publicId: string) {
  const { data } = await http.get<ApiSuccessResponse<AttachmentDto[]>>(
    `/api/rfqs/${publicId}/attachments`,
  )
  return data.data
}
export async function uploadAttachment(
  publicId: string,
  file: File,
  category: string,
  lineNumber?: number,
) {
  const form = new FormData()
  form.append('file', file)
  form.append('category', category)
  if (lineNumber) form.append('lineNumber', String(lineNumber))
  const { data } = await http.post<ApiSuccessResponse<AttachmentDto>>(
    `/api/rfqs/${publicId}/attachments`,
    form,
  )
  return data.data
}
export async function downloadAttachment(publicId: string, attachmentId: string) {
  const { data } = await http.get<Blob>(
    `/api/rfqs/${publicId}/attachments/${attachmentId}/download`,
    { responseType: 'blob' },
  )
  return data
}
export async function getReportSummary() {
  const { data } = await http.get<ApiSuccessResponse<RfqOperationalReport>>(
    '/api/reports/rfqs/summary',
  )
  return data.data
}
export async function downloadRfqReport(format: 'xlsx' | 'csv' = 'xlsx') {
  const response = await http.get<Blob>('/api/reports/rfqs/export', {
    params: { format },
    responseType: 'blob',
  })
  return {
    blob: response.data,
    disposition: response.headers['content-disposition'] as string | undefined,
  }
}


// ─── Marketing Workflow API ─────────────────────────────────────────────────
export async function markReadyForMarketing(publicId: string, notes?: string) {
  const { data } = await http.post<ApiSuccessResponse<RfqDetailDto>>(
    `/api/rfqs/${publicId}/ready-for-marketing`,
    notes ? { notes } : {},
  )
  return data.data
}
export async function assignMarketing(publicId: string, assignedUserId: number, reason?: string) {
  const { data } = await http.post<ApiSuccessResponse<RfqDetailDto>>(
    `/api/rfqs/${publicId}/marketing-assignment`,
    { assignedUserId, reason },
  )
  return data.data
}
export async function completeMarketing(publicId: string, payload?: { notes?: string; outcome?: string }) {
  const { data } = await http.post<ApiSuccessResponse<RfqDetailDto>>(
    `/api/rfqs/${publicId}/marketing-complete`,
    payload ?? {},
  )
  return data.data
}

// ─── Red Flags API ──────────────────────────────────────────────────────────
export interface RedFlagDto {
  id: number
  rfqId: number
  rfqPublicId: string
  sourceRfqId: string | null
  customerName: string | null
  severity: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'
  reason: string
  status: 'OPEN' | 'RESOLVED' | 'DISMISSED'
  raisedBy: number
  assignedTo: number | null
  resolutionNote: string | null
  resolvedBy: number | null
  resolvedAt: string | null
  createdAt: string
}
export async function getRedFlags(
  page = 0,
  size = 20,
  status?: 'OPEN' | 'RESOLVED' | 'DISMISSED',
  severity?: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL',
) {
  const { data } = await http.get<ApiSuccessResponse<PageResponse<RedFlagDto>>>('/api/red-flags', {
    params: { page, size, status: status || undefined, severity: severity || undefined },
  })
  return data.data
}
export async function createRedFlag(rfqPublicId: string, severity: string, reason: string, assignedTo?: number) {
  const { data } = await http.post<ApiSuccessResponse<RedFlagDto>>('/api/red-flags', {
    rfqPublicId, severity, reason, assignedTo,
  })
  return data.data
}
export async function resolveRedFlag(id: number, resolution: 'RESOLVE' | 'DISMISS', note: string) {
  const { data } = await http.post<ApiSuccessResponse<RedFlagDto>>(`/api/red-flags/${id}/resolve`, {
    resolution, note,
  })
  return data.data
}
export async function getRedFlagsSummary() {
  const { data } = await http.get<ApiSuccessResponse<{ openCount: number }>>('/api/red-flags/summary')
  return data.data
}

// ─── Purchase Orders API ────────────────────────────────────────────────────
export interface PurchaseOrderDto {
  id: number
  orderId: string
  rfqId: number
  rfqPublicId: string
  sourceRfqId: string | null
  customerName: string | null
  quotationVersionId: number | null
  supplierName: string | null
  status: 'PENDING' | 'ORDERED' | 'SHIPPED' | 'DELIVERED' | 'COMPLETED' | 'CANCELLED'
  assignedTo: number | null
  orderDate: string | null
  expectedDelivery: string | null
  notes: string | null
  createdBy: number
  createdAt: string
}
export async function getPurchaseOrders(
  page = 0,
  size = 20,
  status?: PurchaseOrderDto['status'],
) {
  const { data } = await http.get<ApiSuccessResponse<PageResponse<PurchaseOrderDto>>>('/api/purchases', {
    params: { page, size, status: status || undefined },
  })
  return data.data
}
export async function createPurchaseOrder(
  rfqPublicId: string,
  quotationVersionId?: number,
  supplierName?: string,
  assignedTo?: number,
  orderDate?: string,
  expectedDelivery?: string,
  notes?: string,
) {
  const { data } = await http.post<ApiSuccessResponse<PurchaseOrderDto>>('/api/purchases', {
    rfqPublicId, quotationVersionId, supplierName, assignedTo, orderDate, expectedDelivery, notes,
  })
  return data.data
}
export async function updatePurchaseOrderStatus(id: number, status: PurchaseOrderDto['status']) {
  const { data } = await http.patch<ApiSuccessResponse<PurchaseOrderDto>>(`/api/purchases/${id}/status`, { status })
  return data.data
}


// ─── Notifications API ──────────────────────────────────────────────────────
export interface NotificationDto {
  id: number
  type: string
  title: string
  message: string
  rfqPublicId: string | null
  read: boolean
  createdAt: string
  readAt: string | null
}
export async function getNotifications(page = 0, size = 20) {
  const { data } = await http.get<ApiSuccessResponse<PageResponse<NotificationDto>>>('/api/notifications', {
    params: { page, size },
  })
  return data.data
}
export async function getUnreadNotificationCount() {
  const { data } = await http.get<ApiSuccessResponse<{ unreadCount: number }>>('/api/notifications/unread-count')
  return data.data
}
export async function markNotificationRead(id: number) {
  const { data } = await http.patch<ApiSuccessResponse<NotificationDto>>(`/api/notifications/${id}/read`)
  return data.data
}
export async function markAllNotificationsRead() {
  const { data } = await http.post<ApiSuccessResponse<{ markedRead: number }>>('/api/notifications/mark-all-read')
  return data.data
}
