import { useQuery } from '@tanstack/react-query'
import { isDemoSession } from '@/features/auth/demoSession'
import { useRfqDemoStore } from '../store/rfqDemoStore'
import { getRfq, type RfqDetailDto } from '../api/rfqApi'
import type { RfqRecord } from '../types'

function toRecord(dto: RfqDetailDto): RfqRecord {
  const lineItems = dto.lineItems.map((item) => ({
    sourceLineId: item.sourceLineId,
    lineNumber: item.lineNumber,
    description: item.description,
    requestedQuantity: Number(item.requestedQuantity),
    unitOfMeasure: item.unitOfMeasure ?? '',
    requestedManufacturer: item.requestedManufacturer ?? '',
    requestedPartNumber: item.requestedPartNumber ?? item.customerPartNumber ?? '',
    procurementCategory: item.procurementCategory ?? '',
    taaRequired: item.taaRequired,
    technicalSpecifications: item.technicalSpecifications ?? '',
  }))
  return {
    id: dto.id,
    sourceRfqId: dto.sourceRfqId,
    sourceReference: dto.sourceReference ?? undefined,
    customerName: dto.customerName,
    customerCompany: dto.customerCompany ?? '',
    buyerName: dto.buyerName ?? '',
    buyerEmail: dto.buyerEmail ?? '',
    requestTitle: dto.requestTitle ?? '',
    dueAt: dto.dueAtLocal ?? '',
    dueTimeZone: dto.dueTimeZone ?? '',
    requestedDeliveryDate: dto.requestedDeliveryDate ?? '',
    shipToText: dto.shipToText ?? '',
    sourceSystem: dto.sourceSystem,
    sourceFileName: '',
    status: dto.status,
    customerDecision: dto.customerDecision,
    quotationManagerCanAssignReviewer: dto.quotationManagerCanAssignReviewer,
    version: dto.version,
    rejectionCount: 0,
    currentQuotationVersion: 0,
    quotationVersions: [],
    assignments: dto.assignments.map((assignment) => ({
      role: assignment.role,
      userId: assignment.userId,
      userName: `User ${assignment.userId}`,
      assignedAt: assignment.assignedAt,
    })),
    lineItems,
    quoteDraft: {
      currency: 'USD',
      freight: 0,
      otherCharges: 0,
      discount: 0,
      taxAmount: 0,
      deliveryTimeline: '',
      warranty: '',
      notes: '',
      items: lineItems.map((item) => ({
        sourceLineId: item.sourceLineId,
        description: item.description,
        quantity: item.requestedQuantity,
        unitOfMeasure: item.unitOfMeasure,
        manufacturer: item.requestedManufacturer,
        partNumber: item.requestedPartNumber,
        unitPrice: 0,
        taaCompliant: null,
        deliveryTimeline: '',
        warranty: '',
        note: '',
        productUrl: '',
      })),
    },
    events: [],
    createdAt: dto.createdAt,
  }
}

export function useRfqDetail(publicId: string) {
  const demoRfqs = useRfqDemoStore((state) => state.rfqs)
  const demo = isDemoSession()
  const query = useQuery({
    queryKey: ['rfqs', publicId, 'detail'],
    queryFn: () => getRfq(publicId),
    enabled: !demo && Boolean(publicId),
  })
  return {
    isDemo: demo,
    rfq: demo
      ? demoRfqs.find((rfq) => rfq.id === publicId)
      : query.data
        ? toRecord(query.data)
        : undefined,
    raw: query.data,
    isLoading: !demo && query.isLoading,
    error: !demo ? query.error : null,
    refetch: query.refetch,
  }
}
