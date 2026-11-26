import { Building2, CalendarDays, Check, FileText, MapPin, UserRound } from 'lucide-react'
import * as React from 'react'
import { type RfqRecord } from '@/features/rfq/types'

export function ProgressStrip({ status }: { status: RfqRecord['status'] }) {
  const stages = [
    { label: 'Imported', complete: true },
    {
      label: 'Quotation',
      complete: [
        'PENDING_REVIEW',
        'REJECTED_BY_REVIEWER',
        'AWAITING_REASSIGNMENT',
        'REVISION_IN_PROGRESS',
        'APPROVED_BY_REVIEWER',
        'SENT_TO_CUSTOMER',
        'CUSTOMER_APPROVED',
        'CUSTOMER_REJECTED',
        'READY_FOR_MARKETING',
        'ASSIGNED_TO_MARKETING',
        'ESCALATED',
        'CLOSED',
      ].includes(status),
    },
    {
      label: 'Review',
      complete: [
        'APPROVED_BY_REVIEWER',
        'SENT_TO_CUSTOMER',
        'CUSTOMER_APPROVED',
        'CUSTOMER_REJECTED',
        'READY_FOR_MARKETING',
        'ASSIGNED_TO_MARKETING',
      ].includes(status),
    },
    {
      label: 'Customer',
      complete: [
        'CUSTOMER_APPROVED',
        'CUSTOMER_REJECTED',
        'READY_FOR_MARKETING',
        'ASSIGNED_TO_MARKETING',
      ].includes(status),
    },
    { label: 'Marketing', complete: status === 'ASSIGNED_TO_MARKETING' },
  ]
  return (
    <div className="rounded-xl border border-slate-200 bg-white px-4 py-4 shadow-sm">
      <div className="flex items-center justify-between gap-2">
        {stages.map((stage, index) => (
          <React.Fragment key={stage.label}>
            <div className="flex min-w-0 items-center gap-2">
              <span
                className={`flex size-7 shrink-0 items-center justify-center rounded-full ${stage.complete ? 'bg-emerald-100 text-emerald-700' : 'bg-slate-100 text-slate-400'}`}
              >
                {stage.complete ? (
                  <Check className="size-4" aria-hidden="true" />
                ) : (
                  <span className="size-2 rounded-full bg-current" />
                )}
              </span>
              <span
                className={`hidden truncate text-xs font-medium sm:inline ${stage.complete ? 'text-slate-800' : 'text-slate-400'}`}
              >
                {stage.label}
              </span>
            </div>
            {index < stages.length - 1 && (
              <span
                className={`h-px min-w-3 flex-1 ${stage.complete ? 'bg-emerald-300' : 'bg-slate-200'}`}
              />
            )}
          </React.Fragment>
        ))}
      </div>
    </div>
  )
}

export function OverviewPanel({ rfq }: { rfq: RfqRecord }) {
  const buyerDetails = [rfq.buyerName || '—', rfq.buyerEmail || '—']
  return (
    <div className="space-y-6">
      <div>
        <SectionHeading
          title="Request summary"
          description="Customer source data carried into Global RFQ."
        />
        <div className="mt-4 grid gap-3 sm:grid-cols-2">
          <InfoCell icon={Building2} label="Customer" value={rfq.customerName} />
          <InfoCell icon={UserRound} label="Buyer contact" value={buyerDetails.join(' · ')} />
          <InfoCell
            icon={CalendarDays}
            label="Requested delivery"
            value={
              rfq.requestedDeliveryDate
                ? new Intl.DateTimeFormat('en-IN', { dateStyle: 'medium' }).format(
                    new Date(`${rfq.requestedDeliveryDate}T00:00:00`),
                  )
                : 'Not provided'
            }
          />
          <InfoCell icon={MapPin} label="Ship to" value={rfq.shipToText || 'Not provided'} />
        </div>
      </div>
      <div>
        <SectionHeading
          title="Requested line items"
          description={`${rfq.lineItems.length} item${rfq.lineItems.length === 1 ? '' : 's'} from the customer request.`}
        />
        <div className="mt-4 space-y-3">
          {rfq.lineItems.length ? (
            rfq.lineItems.map((line) => (
              <div key={line.sourceLineId} className="rounded-lg border border-slate-200 p-4">
                <div className="flex flex-wrap items-start justify-between gap-2">
                  <div className="flex items-start gap-3">
                    <span className="flex size-8 shrink-0 items-center justify-center rounded-lg bg-blue-50 text-xs font-bold text-blue-800">
                      {line.lineNumber}
                    </span>
                    <div>
                      <p className="text-sm leading-5 font-semibold text-slate-800">
                        {line.description}
                      </p>
                      <p className="mt-2 text-xs text-slate-500">
                        V2X / customer part: {line.customerPartNumber || '—'}
                        <span className="mx-1 text-slate-300">·</span> Requested part:{' '}
                        {line.requestedPartNumber || '—'}{' '}
                        <span className="mx-1 text-slate-300">·</span> Manufacturer:{' '}
                        {line.requestedManufacturer || '—'}
                      </p>
                      {line.technicalSpecifications && (
                        <p className="mt-2 text-xs leading-5 whitespace-pre-wrap text-slate-500">
                          {line.technicalSpecifications}
                        </p>
                      )}
                    </div>
                  </div>
                  <span className="rounded-md bg-slate-100 px-2 py-1 text-xs font-semibold whitespace-nowrap text-slate-700">
                    {line.requestedQuantity} {line.unitOfMeasure || 'units'}
                  </span>
                </div>
                <div className="mt-3 flex flex-wrap gap-2">
                  {line.procurementCategory && (
                    <span className="rounded bg-blue-50 px-2 py-1 text-[11px] font-medium text-blue-800">
                      {line.procurementCategory}
                    </span>
                  )}
                  {line.taaRequired && (
                    <span className="rounded bg-emerald-50 px-2 py-1 text-[11px] font-medium text-emerald-800">
                      TAA compliance required
                    </span>
                  )}
                </div>
              </div>
            ))
          ) : (
            <EmptyMessage>No line items were returned by the service.</EmptyMessage>
          )}
        </div>
      </div>
      <div className="grid gap-4 sm:grid-cols-2">
        <InfoCell
          icon={FileText}
          label="Source system"
          value={rfq.sourceSystem || 'Source software'}
        />
        <InfoCell
          icon={FileText}
          label="Source file"
          value={rfq.sourceFileName || 'Stored source document'}
        />
      </div>
    </div>
  )
}

export function InfoCell({
  icon: Icon,
  label,
  value,
}: {
  icon: typeof FileText
  label: string
  value: string
}) {
  return (
    <div className="flex gap-3 rounded-lg bg-slate-50 p-3.5">
      <span className="flex size-8 shrink-0 items-center justify-center rounded-lg bg-white text-slate-500 shadow-sm">
        <Icon className="size-4" aria-hidden="true" />
      </span>
      <div className="min-w-0">
        <p className="text-[11px] font-medium tracking-wide text-slate-400 uppercase">{label}</p>
        <p className="mt-1 text-sm leading-5 font-medium break-words whitespace-pre-line text-slate-800">
          {value}
        </p>
      </div>
    </div>
  )
}

export function SectionHeading({ title, description }: { title: string; description: string }) {
  return (
    <div>
      <h2 className="text-base font-semibold text-slate-900">{title}</h2>
      <p className="mt-1 text-xs text-slate-500">{description}</p>
    </div>
  )
}

export function EmptyMessage({ children }: { children: React.ReactNode }) {
  return (
    <div className="rounded-lg border border-dashed border-slate-200 px-4 py-8 text-center text-sm text-slate-500">
      {children}
    </div>
  )
}
