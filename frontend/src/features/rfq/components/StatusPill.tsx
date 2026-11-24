import { Badge } from '@/components/ui/badge'
import type { RfqStatus } from '../types'
import { statusLabels } from '../types'

const tones: Record<RfqStatus, string> = {
  IMPORTED: 'border-slate-200 bg-slate-100 text-slate-700',
  ASSIGNED_TO_QUOTATION_MANAGER: 'border-blue-200 bg-blue-50 text-blue-800',
  QUOTATION_IN_PROGRESS: 'border-indigo-200 bg-indigo-50 text-indigo-800',
  PENDING_REVIEW: 'border-amber-200 bg-amber-50 text-amber-800',
  REVISION_IN_PROGRESS: 'border-orange-200 bg-orange-50 text-orange-800',
  APPROVED_BY_REVIEWER: 'border-emerald-200 bg-emerald-50 text-emerald-800',
  SENT_TO_CUSTOMER: 'border-cyan-200 bg-cyan-50 text-cyan-800',
  CUSTOMER_APPROVED: 'border-green-200 bg-green-50 text-green-800',
  CUSTOMER_REJECTED: 'border-rose-200 bg-rose-50 text-rose-800',
  REOPENED_FOR_REVISION: 'border-orange-200 bg-orange-50 text-orange-800',
  READY_FOR_MARKETING: 'border-teal-200 bg-teal-50 text-teal-800',
  ASSIGNED_TO_MARKETING: 'border-violet-200 bg-violet-50 text-violet-800',
  ESCALATED: 'border-red-200 bg-red-50 text-red-800',
  CLOSED: 'border-slate-200 bg-slate-100 text-slate-600',
  CANCELLED: 'border-slate-200 bg-slate-100 text-slate-500',
}

export function StatusPill({ status }: { status: RfqStatus }) {
  return (
    <Badge
      variant="outline"
      className={`rounded-md px-2 py-1 text-[11px] font-semibold whitespace-nowrap ${tones[status]}`}
    >
      {statusLabels[status]}
    </Badge>
  )
}
