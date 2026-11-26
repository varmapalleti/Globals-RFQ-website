import { useQuery } from '@tanstack/react-query'
import { getRfqs } from '../api/rfqApi'
import { isDemoSession } from '@/features/auth/demoSession'
import { useRfqDemoStore } from '../store/rfqDemoStore'
import type { RfqRecord, RfqStatus } from '../types'

interface RfqListOptions {
  page?: number
  size?: number
  search?: string
  status?: RfqStatus
  sort?: 'createdAt' | 'requestedDeliveryDate'
  enabled?: boolean
}

function fromSummary(row: Awaited<ReturnType<typeof getRfqs>>['content'][number]): RfqRecord {
  return {
    id: row.id,
    sourceRfqId: row.sourceRfqId,
    sourceReference: row.sourceReference ?? undefined,
    customerName: row.customerName,
    customerCompany: row.customerName,
    buyerName: '',
    buyerEmail: '',
    requestTitle: row.requestTitle,
    dueAt: row.dueAtLocal ?? '',
    dueTimeZone: '',
    requestedDeliveryDate: row.requestedDeliveryDate ?? '',
    shipToText: '',
    sourceSystem: '',
    sourceFileName: '',
    status: row.status,
    customerDecision: row.customerDecision,
    quotationManagerCanAssignReviewer: false,
    version: 0,
    rejectionCount: 0,
    currentQuotationVersion: 0,
    lineItemCount: row.lineItemCount,
    quotationVersions: [],
    assignments:
      row.assigneeUserId && row.assigneeRole
        ? [
            {
              role: row.assigneeRole,
              userId: row.assigneeUserId,
              userName: `User ${row.assigneeUserId}`,
              assignedAt: row.createdAt,
            },
          ]
        : [],
    lineItems: [],
    quoteDraft: {
      currency: 'USD',
      freight: 0,
      otherCharges: 0,
      discount: 0,
      taxAmount: 0,
      deliveryTimeline: '',
      warranty: '',
      notes: '',
      items: [],
    },
    events: [],
    createdAt: row.createdAt,
  }
}

export function useRfqRecords(options: RfqListOptions = {}) {
  const page = options.page ?? 0
  const size = options.size ?? 100
  const demoRfqs = useRfqDemoStore((state) => state.rfqs)
  const demo = isDemoSession()
  const query = useQuery({
    queryKey: ['rfqs', 'list', page, size, options.search, options.status, options.sort],
    queryFn: () =>
      getRfqs(page, size, undefined, {
        search: options.search,
        status: options.status,
        sort: options.sort,
      }),
    enabled: !demo && options.enabled !== false,
    staleTime: 15_000,
    retry: false,
  })
  return {
    isDemo: demo,
    rfqs: demo ? demoRfqs : (query.data?.content.map(fromSummary) ?? []),
    totalElements: demo ? demoRfqs.length : (query.data?.totalElements ?? 0),
    totalPages: demo
      ? Math.max(1, Math.ceil(demoRfqs.length / size))
      : (query.data?.totalPages ?? 0),
    isLoading: !demo && options.enabled !== false && query.isLoading,
    error: !demo && options.enabled !== false ? query.error : null,
    refetch: query.refetch,
  }
}
