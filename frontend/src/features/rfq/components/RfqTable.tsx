import { Link } from '@tanstack/react-router'
import { ArrowUpRight, CalendarDays, Inbox } from 'lucide-react'
import { StatusPill } from './StatusPill'
import { formatRfqNumber, type RfqRecord } from '../types'

function formatDate(value: string) {
  if (!value) return 'Not specified'
  return new Intl.DateTimeFormat('en-IN', {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
  }).format(new Date(`${value}T00:00:00`))
}

export function RfqTable({
  rfqs,
  compact = false,
  emptyMessage = 'No RFQs match this view.',
}: {
  rfqs: RfqRecord[]
  compact?: boolean
  emptyMessage?: string
}) {
  if (!rfqs.length)
    return (
      <div className="flex flex-col items-center justify-center px-6 py-14 text-center">
        <span className="mb-3 flex size-11 items-center justify-center rounded-full bg-brand-50 text-brand-900">
          <Inbox className="size-5" aria-hidden="true" />
        </span>
        <p className="text-sm font-semibold text-slate-800">Nothing to work on right now</p>
        <p className="mt-1 max-w-xs text-sm text-slate-500">{emptyMessage}</p>
      </div>
    )

  return (
    <div className="overflow-x-auto">
      <table className="w-full min-w-[760px] border-collapse text-left">
        <thead>
          <tr className="border-y border-brand-100 bg-brand-50/70 text-[11px] font-semibold tracking-wide text-brand-900 uppercase">
            <th scope="col" className="px-5 py-3">
              RFQ / customer
            </th>
            <th scope="col" className="px-5 py-3">
              Request
            </th>
            <th scope="col" className="px-5 py-3">
              Workflow status
            </th>
            {!compact && (
              <th scope="col" className="px-5 py-3">
                Quotation manager
              </th>
            )}
            <th scope="col" className="px-5 py-3">
              Delivery date
            </th>
            <th scope="col" className="px-5 py-3">
              <span className="sr-only">Open</span>
            </th>
          </tr>
        </thead>
        <tbody className="divide-y divide-brand-100/70">
          {rfqs.map((rfq) => {
            const manager = rfq.assignments.find(
              (assignment) => assignment.role === 'QUOTATION_MANAGER',
            )
            return (
              <tr key={rfq.id} className="group transition hover:bg-brand-50/50">
                <td className="px-5 py-4 align-top">
                  <Link
                    to="/rfqs/$rfqId"
                    params={{ rfqId: rfq.id }}
                    className="font-semibold text-brand-900 hover:text-red-brand-600 hover:underline"
                  >
                    {formatRfqNumber(rfq)}
                  </Link>
                  <p className="mt-1 max-w-52 truncate text-xs text-slate-500">
                    {rfq.customerName}
                  </p>
                </td>
                <td className="px-5 py-4 align-top">
                  <p
                    className="max-w-64 truncate text-sm font-medium text-slate-800"
                    title={rfq.requestTitle}
                  >
                    {rfq.requestTitle}
                  </p>
                </td>
                <td className="px-5 py-4 align-top">
                  <StatusPill status={rfq.status} />
                </td>
                {!compact && (
                  <td className="px-5 py-4 align-top text-sm text-slate-600">
                    {manager?.userName ?? <span className="text-slate-400">Unassigned</span>}
                  </td>
                )}
                <td className="px-5 py-4 align-top">
                  <span className="flex items-center gap-1.5 text-sm whitespace-nowrap text-slate-600">
                    <CalendarDays className="size-3.5 text-slate-400" aria-hidden="true" />
                    {formatDate(rfq.requestedDeliveryDate)}
                  </span>
                </td>
                <td className="px-5 py-4 text-right align-top">
                  <Link
                    to="/rfqs/$rfqId"
                    params={{ rfqId: rfq.id }}
                    aria-label={`Open ${formatRfqNumber(rfq)}`}
                    className="inline-flex size-8 items-center justify-center rounded-md text-slate-400 hover:bg-white hover:text-brand-900"
                  >
                    <ArrowUpRight className="size-4" aria-hidden="true" />
                  </Link>
                </td>
              </tr>
            )
          })}
        </tbody>
      </table>
    </div>
  )
}
