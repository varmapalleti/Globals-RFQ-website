import { zodResolver } from '@hookform/resolvers/zod'
import { useQuery } from '@tanstack/react-query'
import { FilePlus2, Loader2, Send } from 'lucide-react'
import * as React from 'react'
import { useFieldArray, useForm } from 'react-hook-form'
import { toast } from 'sonner'
import { z } from 'zod'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { useAuth } from '@/features/auth/hooks/useAuth'
import { getQuotationDraft, saveQuotationDraft, submitQuotation, type QuotationDraftDto } from '@/features/rfq/api/rfqApi'
import type { getQuotationVersions } from '@/features/rfq/api/rfqApi'
import { useRfqDemoStore } from '@/features/rfq/store/rfqDemoStore'
import { type QuotationLine, type RfqRecord } from '@/features/rfq/types'
import { normalizeError, unexpectedErrorMessage } from '@/lib/errors'
import { EmptyMessage, SectionHeading } from './OverviewPanels'

export const quotationSchema = z.object({
  currency: z.string().regex(/^[A-Z]{3}$/, 'Use an uppercase ISO currency code, e.g. USD.'),
  freight: z.number().finite().min(0),
  otherCharges: z.number().finite().min(0),
  discount: z.number().finite().min(0),
  taxAmount: z.number().finite().min(0),
  deliveryTimeline: z.string().max(500),
  warranty: z.string().max(500),
  notes: z.string().max(10000),
  items: z
    .array(
      z.object({
        sourceLineId: z.string().min(1),
        description: z.string().max(20000),
        quantity: z.number().finite().positive('Quantity must be greater than zero.'),
        unitPrice: z.number().finite().min(0, 'Price cannot be negative.'),
        unitOfMeasure: z.string().max(40),
        manufacturer: z.string().max(255),
        partNumber: z.string().max(255),
        taaCompliant: z.boolean().nullable(),
        deliveryTimeline: z.string().max(500),
        warranty: z.string().max(500),
        note: z.string().max(4000),
        productUrl: z.union([
          z.literal(''),
          z.string().url('Enter a valid product URL.').max(1000),
        ]),
      }),
    )
    .min(1),
})

export type QuotationFormValues = z.infer<typeof quotationSchema>

export function toFormDraft(rfq: RfqRecord): QuotationFormValues {
  const items = rfq.quoteDraft.items.length
    ? rfq.quoteDraft.items
    : rfq.lineItems.map((line) => ({
        sourceLineId: line.sourceLineId,
        description: line.description,
        quantity: line.requestedQuantity,
        unitOfMeasure: line.unitOfMeasure,
        manufacturer: line.requestedManufacturer,
        partNumber: line.requestedPartNumber,
        unitPrice: 0,
        taaCompliant: null,
        deliveryTimeline: '',
        warranty: '',
        note: '',
        productUrl: '',
      }))
  return {
    currency: rfq.quoteDraft.currency || 'USD',
    freight: rfq.quoteDraft.freight || 0,
    otherCharges: rfq.quoteDraft.otherCharges || 0,
    discount: rfq.quoteDraft.discount || 0,
    taxAmount: rfq.quoteDraft.taxAmount || 0,
    deliveryTimeline: rfq.quoteDraft.deliveryTimeline || '',
    warranty: rfq.quoteDraft.warranty || '',
    notes: rfq.quoteDraft.notes || '',
    items,
  }
}

export function QuotationPanel({
  rfq,
  demo,
  canEdit,
  canReview,
  isAdmin,
  versions,
  invalidate,
}: {
  rfq: RfqRecord
  demo: boolean
  canEdit: boolean
  canReview: boolean
  isAdmin: boolean
  versions: Awaited<ReturnType<typeof getQuotationVersions>>
  invalidate: () => Promise<void>
}) {
  const [showEditor, setShowEditor] = React.useState(
    canEdit &&
      [
        'ASSIGNED_TO_QUOTATION_MANAGER',
        'QUOTATION_IN_PROGRESS',
        'REJECTED_BY_REVIEWER',
        'REVISION_IN_PROGRESS',
        'REOPENED_FOR_REVISION',
      ].includes(rfq.status),
  )
  const canCreateDraft =
    canEdit &&
    [
      'ASSIGNED_TO_QUOTATION_MANAGER',
      'QUOTATION_IN_PROGRESS',
      'REJECTED_BY_REVIEWER',
      'REVISION_IN_PROGRESS',
      'REOPENED_FOR_REVISION',
    ].includes(rfq.status)
  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <SectionHeading
            title="Quotation versions"
            description="Submitted versions are immutable. Corrections must be saved as a new draft and resubmitted."
          />
        </div>
        {canEdit && canCreateDraft && (
          <Button size="sm" variant={showEditor ? 'outline' : 'default'} onClick={() => setShowEditor((open) => !open)}>
            <FilePlus2 className="size-4" />
            {showEditor ? 'Hide quotation draft' : 'Open quotation draft'}
          </Button>
        )}
      </div>
      {canEdit && canCreateDraft && showEditor && (
        <section className="rounded-xl border border-brand-200 bg-brand-50/40 p-4">
          <h3 className="mb-3 text-sm font-semibold text-brand-900">Current editable draft</h3>
          <QuotationEditor key={rfq.id} rfq={rfq} demo={demo} invalidate={invalidate} />
        </section>
      )}
      {demo ? (
        rfq.quotationVersions.length ? (
          <div className="space-y-3">
            {[...rfq.quotationVersions].reverse().map((version) => {
              const snapshot = version.draftSnapshot
              const lineTotal = snapshot.items.reduce(
                (total, line) => total + line.quantity * (line.unitPrice || 0),
                0,
              )
              return (
                <div key={version.id} className="rounded-xl border border-brand-100 p-4">
                  <div className="flex flex-wrap items-start justify-between gap-3">
                    <div>
                      <p className="font-semibold text-brand-900">Quotation V{version.versionNumber}</p>
                      <p className="mt-1 text-xs text-slate-500">
                        Submitted{' '}
                        {new Intl.DateTimeFormat('en-IN', {
                          dateStyle: 'medium',
                          timeStyle: 'short',
                        }).format(new Date(version.submittedAt))}{' '}
                        · {version.status.replaceAll('_', ' ').toLowerCase()}
                      </p>
                    </div>
                    <span className="rounded-md bg-brand-50 px-2 py-1 text-xs font-semibold text-brand-900">
                      {snapshot.currency}
                    </span>
                  </div>
                  <div className="mt-4 space-y-2">
                    {snapshot.items.map((line, index) => (
                      <div
                        key={`${line.sourceLineId}-${index}`}
                        className="grid gap-1 border-t border-brand-100 pt-3 text-sm sm:grid-cols-[1fr_auto]"
                      >
                        <div>
                          <p className="font-medium">{line.description}</p>
                          <p className="mt-1 text-xs text-slate-500">
                            {line.manufacturer || 'Manufacturer pending'} ·{' '}
                            {line.partNumber || 'Part number pending'} · Qty {line.quantity}
                          </p>
                        </div>
                        <p className="font-semibold tabular-nums">
                          {line.unitPrice
                            ? new Intl.NumberFormat('en-US', {
                                style: 'currency',
                                currency: snapshot.currency,
                              }).format(line.unitPrice * line.quantity)
                            : 'Price pending'}
                        </p>
                      </div>
                    ))}
                  </div>
                  <div className="mt-4 flex flex-wrap gap-x-5 gap-y-2 border-t border-brand-100 pt-3 text-xs text-slate-500">
                    <span>
                      Items total: {lineTotal.toFixed(2)} {snapshot.currency}
                    </span>
                    <span>
                      Freight: {snapshot.freight.toFixed(2)} {snapshot.currency}
                    </span>
                    <span>Delivery: {snapshot.deliveryTimeline || 'Pending'}</span>
                    <span>Warranty: {snapshot.warranty || 'Pending'}</span>
                  </div>
                  {version.missingMandatoryFields.length > 0 && (
                    <p className="mt-3 rounded-md bg-amber-50 p-2.5 text-xs text-amber-900">
                      Incomplete at submission: {version.missingMandatoryFields.join(', ')}
                    </p>
                  )}
                  {version.reviewDecision && (
                    <p className="mt-3 border-t border-brand-100 pt-3 text-xs text-slate-600">
                      {version.reviewDecision === 'APPROVED' ? 'Approved' : 'Rejected'} by{' '}
                      {version.reviewerName}
                      {version.reviewReason ? ` · ${version.reviewReason}` : ''}
                    </p>
                  )}
                </div>
              )
            })}
          </div>
        ) : (
          <EmptyMessage>No quotation version has been submitted.</EmptyMessage>
        )
      ) : versions.length ? (
        <div className="space-y-3">
          {versions.map((version, index) => {
            const snapshot = version.quotation
            const currency = snapshot.currency || version.currency || 'USD'
            const formatMoney = (value: number) =>
              new Intl.NumberFormat('en-US', {
                style: 'currency',
                currency,
                minimumFractionDigits: 2,
              }).format(Number(value ?? 0))
            return (
              <details
                key={version.id}
                open={index === 0}
                className="overflow-hidden rounded-xl border border-brand-100 bg-white"
              >
                <summary className="flex cursor-pointer list-none flex-wrap items-center justify-between gap-3 p-4 hover:bg-brand-50/60">
                  <div>
                    <p className="font-semibold text-brand-900">Quotation V{version.versionNumber}</p>
                    <p className="mt-1 text-xs text-slate-500">
                      Submitted{' '}
                      {new Intl.DateTimeFormat('en-IN', {
                        dateStyle: 'medium',
                        timeStyle: 'short',
                      }).format(new Date(version.submittedAt))}
                      {' · '}{formatMoney(version.grandTotal)}
                    </p>
                  </div>
                  <span
                    className={`rounded-md px-2 py-1 text-xs font-semibold ${version.status === 'APPROVED' ? 'bg-emerald-50 text-emerald-800' : version.status === 'REJECTED' ? 'bg-red-brand-50 text-red-brand-700' : 'bg-amber-50 text-amber-800'}`}
                  >
                    {version.status.replaceAll('_', ' ')}
                  </span>
                </summary>
                <div className="space-y-4 border-t border-brand-100 p-4">
                  <div className="grid gap-2 rounded-lg bg-brand-50/60 p-3 text-xs sm:grid-cols-2">
                    <p><span className="text-slate-500">Quotation version ID:</span> <strong>{version.id}</strong></p>
                    <p><span className="text-slate-500">Submitted by user ID:</span> <strong>{version.submittedByUserId}</strong></p>
                    {version.review && (
                      <p><span className="text-slate-500">Reviewer user ID:</span> <strong>{version.review.reviewerUserId}</strong></p>
                    )}
                    <p><span className="text-slate-500">Submitted:</span> {new Date(version.submittedAt).toLocaleString()}</p>
                    {version.review && (
                      <p><span className="text-slate-500">Decision recorded:</span> {new Date(version.review.decidedAt).toLocaleString()}</p>
                    )}
                  </div>
                  {version.missingMandatoryFields.length > 0 && (
                    <p className="rounded-md bg-amber-50 p-3 text-xs text-amber-900">
                      Missing mandatory fields at submission: {version.missingMandatoryFields.join(', ')}
                    </p>
                  )}
                  <div className="grid gap-2 sm:grid-cols-2 lg:grid-cols-3">
                    {[
                      ['Subtotal', snapshot.subtotal],
                      ['Freight', snapshot.freight],
                      ['Other charges', snapshot.otherCharges],
                      ['Discount', snapshot.discount],
                      ['Tax', snapshot.taxAmount],
                      ['Grand total', snapshot.grandTotal],
                    ].map(([label, amount]) => (
                      <div key={String(label)} className="rounded-lg border border-brand-100 p-3">
                        <p className="text-[11px] text-slate-500">{label}</p>
                        <p className="mt-1 text-sm font-semibold text-brand-900 tabular-nums">{formatMoney(Number(amount))}</p>
                      </div>
                    ))}
                  </div>
                  <div className="grid gap-3 text-sm sm:grid-cols-2">
                    <p><span className="font-medium text-slate-700">Delivery:</span> {snapshot.deliveryTimeline || '—'}</p>
                    <p><span className="font-medium text-slate-700">Warranty:</span> {snapshot.warranty || '—'}</p>
                    {snapshot.notes && <p className="whitespace-pre-wrap sm:col-span-2"><span className="font-medium text-slate-700">Quotation notes:</span> {snapshot.notes}</p>}
                  </div>
                  <div>
                    <h4 className="mb-2 text-sm font-semibold text-brand-900">Quoted line items</h4>
                    <div className="overflow-x-auto rounded-lg border border-brand-100">
                      <table className="min-w-[1050px] w-full text-left text-xs">
                        <thead className="bg-brand-50/70 text-brand-900">
                          <tr>
                            {['Item / source ID', 'Requested item', 'Offered item', 'Qty / UOM', 'Unit price', 'Line total', 'TAA', 'Delivery / warranty', 'Notes / link'].map((heading) => (
                              <th key={heading} className="px-3 py-2.5 font-semibold">{heading}</th>
                            ))}
                          </tr>
                        </thead>
                        <tbody className="divide-y divide-brand-100">
                          {snapshot.items.map((line, lineIndex) => {
                            const requested = rfq.lineItems.find((item) => item.sourceLineId === line.sourceLineId)
                            return (
                              <tr key={`${line.sourceLineId}-${lineIndex}`} className="align-top">
                                <td className="px-3 py-3">
                                  <p className="font-semibold">Item {requested?.lineNumber ?? lineIndex + 1}</p>
                                  <p className="mt-1 text-slate-500">{line.sourceLineId}</p>
                                </td>
                                <td className="max-w-56 whitespace-pre-wrap px-3 py-3">
                                  <p>{requested?.description ?? 'Source item unavailable'}</p>
                                  {requested?.customerPartNumber && <p className="mt-1 text-slate-500">V2X / customer part: {requested.customerPartNumber}</p>}
                                  {requested?.requestedPartNumber && <p className="mt-1 text-slate-500">Part: {requested.requestedPartNumber}</p>}
                                </td>
                                <td className="max-w-56 whitespace-pre-wrap px-3 py-3">
                                  <p className="font-medium">{line.description || '—'}</p>
                                  <p className="mt-1 text-slate-600">{line.manufacturer || 'Manufacturer —'} · {line.partNumber || 'Part number —'}</p>
                                </td>
                                <td className="px-3 py-3 tabular-nums">{line.quantity} / {line.unitOfMeasure || '—'}</td>
                                <td className="px-3 py-3 tabular-nums">{formatMoney(line.unitPrice)}</td>
                                <td className="px-3 py-3 font-semibold tabular-nums">{formatMoney(line.lineTotal)}</td>
                                <td className="px-3 py-3">{line.taaCompliant === null ? '—' : line.taaCompliant ? 'Yes' : 'No'}</td>
                                <td className="max-w-48 whitespace-pre-wrap px-3 py-3">{line.deliveryTimeline || snapshot.deliveryTimeline || '—'}<br />{line.warranty || snapshot.warranty || '—'}</td>
                                <td className="max-w-56 whitespace-pre-wrap px-3 py-3">
                                  {line.note || '—'}
                                  {line.productUrl && <a href={line.productUrl} target="_blank" rel="noreferrer" className="mt-1 block font-medium text-brand-900 underline hover:text-red-brand-600">View product</a>}
                                </td>
                              </tr>
                            )
                          })}
                        </tbody>
                      </table>
                    </div>
                  </div>
                  {version.review ? (
                    <div className={`rounded-lg p-3 text-sm ${version.review.decision === 'REJECTED' ? 'border border-red-brand-100 bg-red-brand-50 text-red-950' : 'border border-emerald-200 bg-emerald-50 text-emerald-950'}`}>
                      <p className="font-semibold">Reviewer {version.review.decision.toLowerCase()}</p>
                      <p className="mt-1 text-xs">Reviewer user ID {version.review.reviewerUserId} · {new Date(version.review.decidedAt).toLocaleString()}</p>
                      {version.review.reason && <p className="mt-2 whitespace-pre-wrap text-sm">{version.review.reason}</p>}
                    </div>
                  ) : (
                    <p className="rounded-lg bg-amber-50 p-3 text-xs text-amber-900">This submitted version is awaiting reviewer decision.</p>
                  )}
                </div>
              </details>
            )
          })}
        </div>
      ) : (
        <EmptyMessage>No submitted quotation versions were found.</EmptyMessage>
      )}
      {!canEdit && canReview && (
        <p className="rounded-lg bg-brand-50 p-3 text-xs leading-5 text-brand-900">
          Submitted quotation snapshots are read-only. Use the review controls on this page to
          record your decision.
        </p>
      )}
      {isAdmin && (
        <p className="rounded-lg bg-brand-50/60 p-3 text-xs leading-5 text-slate-600">
          Administrators can inspect quotation history but do not alter manager drafts or submitted
          snapshots.
        </p>
      )}
      {!canEdit && !canReview && !isAdmin && (
        <p className="text-xs text-slate-500">
          You have read-only access to quotation history for this RFQ.
        </p>
      )}
    </div>
  )
}

export function QuotationEditor({
  rfq,
  demo,
  invalidate,
}: {
  rfq: RfqRecord
  demo: boolean
  invalidate: () => Promise<void>
}) {
  const updateDraft = useRfqDemoStore((state) => state.updateDraft)
  const submitDraft = useRfqDemoStore((state) => state.submitDraft)
  const { user } = useAuth()
  const [busy, setBusy] = React.useState(false)
  const liveDraftQuery = useQuery({
    queryKey: ['rfqs', rfq.id, 'quotation-draft'],
    queryFn: () => getQuotationDraft(rfq.id),
    enabled: !demo,
  })
  const {
    register,
    control,
    handleSubmit,
    watch,
    reset,
    formState: { errors },
  } = useForm<QuotationFormValues>({
    resolver: zodResolver(quotationSchema),
    defaultValues: toFormDraft(rfq),
  })
  const { fields } = useFieldArray({ control, name: 'items' })
  React.useEffect(() => {
    if (!demo && liveDraftQuery.data) reset({ ...toFormDraft(rfq), ...liveDraftQuery.data })
  }, [demo, liveDraftQuery.data, reset, rfq])
  const form = watch()
  const subtotal =
    form.items?.reduce(
      (sum, line) => sum + (Number(line.quantity) || 0) * (Number(line.unitPrice) || 0),
      0,
    ) ?? 0
  const total =
    subtotal +
    (Number(form.freight) || 0) +
    (Number(form.otherCharges) || 0) +
    (Number(form.taxAmount) || 0) -
    (Number(form.discount) || 0)

  async function save(values: QuotationFormValues, submit: boolean) {
    setBusy(true)
    const draft: QuotationDraftDto = values
    try {
      if (demo) {
        updateDraft(
          rfq.id,
          {
            currency: values.currency,
            freight: values.freight,
            otherCharges: values.otherCharges,
            discount: values.discount,
            taxAmount: values.taxAmount,
            deliveryTimeline: values.deliveryTimeline,
            warranty: values.warranty,
            notes: values.notes,
          },
          values.items as QuotationLine[],
          user?.fullName,
        )
        if (submit) submitDraft(rfq.id, user?.fullName ?? 'Quotation Manager')
      } else if (submit) await submitQuotation(rfq.id, draft)
      else await saveQuotationDraft(rfq.id, draft)
      toast.success(
        submit
          ? 'Quotation submitted for reviewer assessment.'
          : 'Draft saved. No permanent version was created.',
      )
      if (!demo) await invalidate()
      if (submit) window.scrollTo({ top: 0, behavior: 'smooth' })
    } catch (error) {
      toast.error(unexpectedErrorMessage(normalizeError(error)))
    } finally {
      setBusy(false)
    }
  }

  return (
    <form onSubmit={(event) => event.preventDefault()} className="space-y-5">
      <div>
        <SectionHeading
          title="Quotation draft"
          description="Enter a response against each requested line. Save as often as needed; submission creates a locked version."
        />
        <div className="mt-3 rounded-lg border border-brand-100 bg-brand-50/70 p-3 text-xs leading-5 text-brand-900">
          <strong>Completeness:</strong> missing business fields may be submitted, but reviewer
          approval will be blocked by the mandatory-field rules saved with that version.
        </div>
      </div>
      <div className="grid gap-4 sm:grid-cols-3">
        <Field label="Currency *" error={errors.currency?.message}>
          <Input {...register('currency')} maxLength={3} placeholder="USD" />
        </Field>
        <Field label="Delivery timeline">
          <Input {...register('deliveryTimeline')} maxLength={500} placeholder="15 business days" />
        </Field>
        <Field label="Quotation-level warranty">
          <Input {...register('warranty')} maxLength={500} placeholder="12 months" />
        </Field>
      </div>
      <div className="space-y-3">
        {fields.map((field, index) => (
          <div key={field.id} className="rounded-lg border border-brand-100 bg-white p-4">
            <div className="mb-3 flex items-start justify-between gap-3">
              <div>
                <span className="text-[11px] font-semibold tracking-wide text-red-brand-600 uppercase">
                  Offer for request line {index + 1}
                </span>
                <p className="mt-1 text-sm leading-5 font-medium text-slate-800">
                  {rfq.lineItems[index]?.description ?? field.description}
                </p>
              </div>
              <span className="rounded-md bg-brand-50 px-2 py-1 text-xs whitespace-nowrap text-brand-900">
                Requested {rfq.lineItems[index]?.requestedQuantity ?? field.quantity}{' '}
                {rfq.lineItems[index]?.unitOfMeasure}
              </span>
            </div>
            <div className="grid gap-3 sm:grid-cols-2">
              <Field label="Quoted manufacturer">
                <Input {...register(`items.${index}.manufacturer`)} maxLength={255} />
              </Field>
              <Field label="Manufacturer part number">
                <Input {...register(`items.${index}.partNumber`)} maxLength={255} />
              </Field>
              <Field label="Quoted quantity *" error={errors.items?.[index]?.quantity?.message}>
                <Input
                  type="number"
                  min="0.0001"
                  step="any"
                  {...register(`items.${index}.quantity`, { valueAsNumber: true })}
                />
              </Field>
              <Field label="Unit price *" error={errors.items?.[index]?.unitPrice?.message}>
                <Input
                  type="number"
                  min="0"
                  step="0.01"
                  {...register(`items.${index}.unitPrice`, { valueAsNumber: true })}
                />
              </Field>
              <Field label="Unit of measure">
                <Input {...register(`items.${index}.unitOfMeasure`)} maxLength={40} />
              </Field>
              <Field label="Product URL" error={errors.items?.[index]?.productUrl?.message}>
                <Input
                  {...register(`items.${index}.productUrl`)}
                  type="url"
                  placeholder="https://…"
                />
              </Field>
              <Field label="Line delivery timeline">
                <Input {...register(`items.${index}.deliveryTimeline`)} maxLength={500} />
              </Field>
              <Field label="Line warranty">
                <Input {...register(`items.${index}.warranty`)} maxLength={500} />
              </Field>
              <label className="flex items-center gap-2 text-sm text-slate-700 sm:col-span-2">
                <input
                  type="checkbox"
                  {...register(`items.${index}.taaCompliant`)}
                  className="size-4 accent-primary"
                />{' '}
                Offered product is TAA compliant
              </label>
              <div className="sm:col-span-2">
                <Field label="Technical or commercial note">
                  <textarea
                    {...register(`items.${index}.note`)}
                    rows={2}
                    maxLength={4000}
                    className="w-full rounded-lg border border-input bg-white px-3 py-2 text-sm focus-visible:border-brand-900 focus-visible:ring-2 focus-visible:ring-brand-900/20 focus-visible:outline-none"
                  />
                </Field>
              </div>
            </div>
          </div>
        ))}
      </div>
      <div className="grid gap-4 sm:grid-cols-2">
        <Field label="Freight / shipping">
          <Input
            type="number"
            min="0"
            step="0.01"
            {...register('freight', { valueAsNumber: true })}
          />
        </Field>
        <Field label="Other approved charges">
          <Input
            type="number"
            min="0"
            step="0.01"
            {...register('otherCharges', { valueAsNumber: true })}
          />
        </Field>
        <Field label="Discount">
          <Input
            type="number"
            min="0"
            step="0.01"
            {...register('discount', { valueAsNumber: true })}
          />
        </Field>
        <Field label="Tax amount">
          <Input
            type="number"
            min="0"
            step="0.01"
            {...register('taxAmount', { valueAsNumber: true })}
          />
        </Field>
      </div>
      <Field label="Quotation notes">
        <textarea
          {...register('notes')}
          rows={3}
          maxLength={10000}
          className="w-full rounded-lg border border-input bg-white px-3 py-2 text-sm focus-visible:border-brand-900 focus-visible:ring-2 focus-visible:ring-brand-900/20 focus-visible:outline-none"
        />
      </Field>
      <div className="flex flex-wrap items-center justify-between gap-3 rounded-lg bg-brand-50/60 p-4">
        <div className="text-xs text-slate-500">
          Subtotal{' '}
          <strong className="mx-1 text-slate-700">
            {form.currency} {subtotal.toFixed(2)}
          </strong>
          <span className="mx-2 text-slate-300">·</span>Estimated total{' '}
          <strong className="mx-1 text-brand-900">
            {form.currency} {Math.max(0, total).toFixed(2)}
          </strong>
          <p className="mt-1">Backend recalculates authoritative totals on save and submission.</p>
        </div>
        <div className="flex gap-2">
          <Button
            type="button"
            variant="outline"
            disabled={busy}
            onMouseDown={(e) => e.preventDefault()}
            onClick={() => void handleSubmit((values) => save(values, false))()}
          >
            {busy ? <Loader2 className="size-4 animate-spin" /> : 'Save draft'}
          </Button>
          <Button
            type="button"
            disabled={busy}
            onMouseDown={(e) => e.preventDefault()}
            onClick={() => void handleSubmit((values) => save(values, true))()}
          >
            <Send className="size-4" />
            Submit for review
          </Button>
        </div>
      </div>
    </form>
  )
}

export function Field({
  label,
  error,
  children,
}: {
  label: string
  error?: string
  children: React.ReactNode
}) {
  return (
    <label className="block space-y-1.5 text-xs font-semibold text-slate-800">
      {label}
      {children}
      {error && (
        <span className="block font-normal text-red-brand-700" role="alert">
          {error}
        </span>
      )}
    </label>
  )
}
