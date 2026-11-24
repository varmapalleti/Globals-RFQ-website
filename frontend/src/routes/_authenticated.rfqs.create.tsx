import { createFileRoute, Link, useNavigate } from '@tanstack/react-router'
import { zodResolver } from '@hookform/resolvers/zod'
import { ArrowLeft, FileSpreadsheet, Loader2, Plus, ShieldCheck, Trash2 } from 'lucide-react'
import * as React from 'react'
import { useFieldArray, useForm } from 'react-hook-form'
import { toast } from 'sonner'
import { z } from 'zod'
import { AppShell } from '@/components/layout/AppShell'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { useAuth } from '@/features/auth/hooks/useAuth'
import { isDemoSession } from '@/features/auth/demoSession'
import { createRfq } from '@/features/rfq/api/rfqApi'
import { useRfqDemoStore } from '@/features/rfq/store/rfqDemoStore'
import { formatRfqNumber, type RfqRecord } from '@/features/rfq/types'
import { isDemoMode } from '@/lib/env'
import { normalizeError, unexpectedErrorMessage } from '@/lib/errors'

export const Route = createFileRoute('/_authenticated/rfqs/create')({
  component: CreateRfqPage,
})

const createRfqSchema = z.object({
  sourceSystem: z.string().trim().min(2, 'Enter the source system.').max(80),
  sourceRfqId: z.string().trim().min(1, 'PR / RFQ Number is required.').max(120),
  requestTitle: z.string().trim().max(255).optional(),
  dueDate: z.string().optional(),
  requestedDeliveryDate: z.string().optional(),
  customerName: z.string().trim().min(2, 'Customer name is required.').max(190),
  customerCompany: z.string().trim().max(190).optional(),
  customerAddress: z.string().trim().max(500).optional(),
  buyerName: z.string().trim().max(150).optional(),
  buyerEmail: z
    .union([z.literal(''), z.string().email('Enter a valid email address.')])
    .optional(),
  buyerPhone: z.string().trim().max(40).optional(),
  shipAddress: z.string().trim().max(500).optional(),
  shipCity: z.string().trim().max(120).optional(),
  shipState: z.string().trim().max(120).optional(),
  shipZip: z.string().trim().max(40).optional(),
  shipCountry: z.string().trim().max(120).optional(),
  termsAndConditions: z.string().max(100000).optional(),
  lineItems: z
    .array(
      z.object({
        lineNumber: z.number().int().positive(),
        requestedQuantity: z.number().finite().positive('Qty must be greater than zero.'),
        unitOfMeasure: z.string().trim().max(40).optional(),
        requestedPartNumber: z.string().trim().max(255).optional(),
        requestedManufacturer: z.string().trim().max(255).optional(),
        procurementCategory: z.string().trim().max(120).optional(),
        taaRequired: z.boolean(),
        description: z.string().trim().min(2, 'Description is required.').max(20000),
      }),
    )
    .min(1, 'Add at least one RFQ item.'),
})

type CreateRfqFormValues = z.infer<typeof createRfqSchema>

function CreateRfqPage() {
  const navigate = useNavigate()
  const { user, hasAnyRole } = useAuth()
  const importRfq = useRfqDemoStore((state) => state.importRfq)
  const [busy, setBusy] = React.useState(false)
  const demo = isDemoMode && isDemoSession()

  const {
    register,
    handleSubmit,
    control,
    formState: { errors },
  } = useForm<CreateRfqFormValues>({
    resolver: zodResolver(createRfqSchema),
    defaultValues: {
      sourceSystem: 'CUSTOMER_SOFTWARE',
      sourceRfqId: '',
      requestTitle: '',
      dueDate: '',
      requestedDeliveryDate: '',
      customerName: '',
      customerCompany: '',
      customerAddress: '',
      buyerName: '',
      buyerEmail: '',
      buyerPhone: '',
      shipAddress: '',
      shipCity: '',
      shipState: '',
      shipZip: '',
      shipCountry: '',
      termsAndConditions: '',
      lineItems: [
        {
          lineNumber: 1,
          requestedQuantity: 1,
          unitOfMeasure: 'EA',
          requestedPartNumber: '',
          requestedManufacturer: '',
          procurementCategory: '',
          taaRequired: false,
          description: '',
        },
      ],
    },
  })

  const { fields, append, remove } = useFieldArray({
    control,
    name: 'lineItems',
  })

  if (!hasAnyRole(['ADMIN', 'SUPER_ADMIN'])) {
    return (
      <AppShell>
        <div className="mx-auto max-w-xl rounded-xl border border-red-200 bg-white p-8 text-center">
          <ShieldCheck className="mx-auto size-8 text-red-600" />
          <h1 className="mt-3 text-xl font-bold">Admin access required</h1>
          <p className="mt-2 text-sm text-slate-500">
            Only an authorized administrator can create a new RFQ.
          </p>
        </div>
      </AppShell>
    )
  }

  async function onSubmit(values: CreateRfqFormValues) {
    setBusy(true)
    const shipLines = [
      values.shipAddress?.trim(),
      [values.shipCity?.trim(), values.shipState?.trim(), values.shipZip?.trim()]
        .filter(Boolean)
        .join(', '),
      values.shipCountry?.trim(),
    ].filter(Boolean)
    const shipToText = shipLines.join('\n')
    const resolvedTitle =
      values.requestTitle?.trim() || `Request for Quote ${values.sourceRfqId.trim()}`
    const dueAtLocal = values.dueDate ? `${values.dueDate}T17:00:00` : null

    try {
      let rfqId: string
      let label: string

      if (demo) {
        const id = crypto.randomUUID()
        const createdAt = new Date().toISOString()
        const rfq: RfqRecord = {
          id,
          sourceRfqId: values.sourceRfqId.trim(),
          sourceReference: values.sourceRfqId.trim(),
          customerName: values.customerName.trim(),
          customerCompany: values.customerCompany?.trim() || values.customerName.trim(),
          buyerName: values.buyerName?.trim() ?? '',
          buyerEmail: values.buyerEmail?.trim() ?? '',
          requestTitle: resolvedTitle,
          dueAt: dueAtLocal ?? '',
          dueTimeZone: 'Asia/Kolkata',
          requestedDeliveryDate: values.requestedDeliveryDate ?? '',
          shipToText,
          sourceSystem: values.sourceSystem.trim(),
          sourceFileName: '',
          status: 'IMPORTED',
          customerDecision: 'PENDING',
          quotationManagerCanAssignReviewer: false,
          version: 1,
          rejectionCount: 0,
          currentQuotationVersion: 0,
          lineItemCount: values.lineItems.length,
          quotationVersions: [],
          assignments: [],
          lineItems: values.lineItems.map((line, index) => ({
            sourceLineId: String(line.lineNumber || index + 1),
            lineNumber: line.lineNumber || index + 1,
            description: line.description.trim(),
            requestedQuantity: line.requestedQuantity,
            unitOfMeasure: line.unitOfMeasure?.trim() || 'EA',
            requestedManufacturer: line.requestedManufacturer?.trim() ?? '',
            requestedPartNumber: line.requestedPartNumber?.trim() ?? '',
            procurementCategory: line.procurementCategory?.trim() ?? '',
            taaRequired: line.taaRequired,
            technicalSpecifications: '',
          })),
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
          events: [
            {
              id: crypto.randomUUID(),
              label: 'RFQ created manually',
              detail: `Created directly via Create New RFQ (${values.sourceSystem})`,
              actor: user?.fullName ?? 'Administrator',
              createdAt,
              tone: 'blue',
            },
          ],
          createdAt,
        }
        importRfq(rfq)
        rfqId = rfq.id
        label = formatRfqNumber(rfq)
      } else {
        const created = await createRfq({
          sourceSystem: values.sourceSystem.trim(),
          sourceRfqId: values.sourceRfqId.trim(),
          sourceReference: values.sourceRfqId.trim(),
          customerName: values.customerName.trim(),
          customerCompany: values.customerCompany?.trim() || null,
          buyerName: values.buyerName?.trim() || null,
          buyerEmail: values.buyerEmail?.trim() || null,
          buyerPhone: values.buyerPhone?.trim() || null,
          requestTitle: resolvedTitle,
          dueAtLocal,
          dueTimeZone: dueAtLocal ? 'Asia/Kolkata' : null,
          requestedDeliveryDate: values.requestedDeliveryDate || null,
          shipToText: shipToText || null,
          termsAndConditions: values.termsAndConditions?.trim() || null,
          sourceData: {
            intakeMethod: 'MANUAL_STANDALONE',
            ...(values.customerAddress?.trim()
              ? { customerAddress: values.customerAddress.trim() }
              : {}),
          },
          lineItems: values.lineItems.map((line, index) => ({
            sourceLineId: String(line.lineNumber || index + 1),
            lineNumber: line.lineNumber || index + 1,
            description: line.description.trim(),
            requestedQuantity: line.requestedQuantity,
            unitOfMeasure: line.unitOfMeasure?.trim() || 'EA',
            customerPartNumber: null,
            requestedManufacturer: line.requestedManufacturer?.trim() || null,
            requestedPartNumber: line.requestedPartNumber?.trim() || null,
            procurementCategory: line.procurementCategory?.trim() || null,
            taaRequired: line.taaRequired,
            technicalSpecifications: null,
            sourceData: {},
          })),
        })
        rfqId = created.id
        label = `RFQ-${values.sourceRfqId.trim()}`
      }

      toast.success('RFQ created', { description: `${label} is ready for assignment.` })
      await navigate({ to: '/rfqs/$rfqId', params: { rfqId } })
    } catch (error) {
      toast.error(unexpectedErrorMessage(normalizeError(error)))
    } finally {
      setBusy(false)
    }
  }

  return (
    <AppShell>
      <div className="mb-4 flex flex-wrap items-center justify-between gap-2">
        <Button variant="ghost" size="sm" asChild>
          <Link to="/rfqs">
            <ArrowLeft className="size-4" aria-hidden="true" /> Back to RFQs
          </Link>
        </Button>
        <Button variant="outline" size="sm" asChild>
          <Link to="/rfqs/import">
            <FileSpreadsheet className="size-4" aria-hidden="true" /> Import from Excel / PDF
          </Link>
        </Button>
      </div>

      <h1 className="mb-5 text-2xl font-bold tracking-tight text-slate-950">Create New RFQ</h1>

      <form onSubmit={handleSubmit(onSubmit)} noValidate className="space-y-5">
        <Card className="border-slate-200 p-5 shadow-xs">
          <h2 className="text-base font-semibold text-slate-900">RFQ Information</h2>
          <div className="mt-4 grid gap-4 md:grid-cols-3">
            <Field label="PR Number *" error={errors.sourceRfqId?.message}>
              <Input
                {...register('sourceRfqId')}
                placeholder="PR Number *"
                aria-invalid={!!errors.sourceRfqId}
              />
            </Field>
            <Field label="Due Date" error={errors.dueDate?.message}>
              <Input type="date" {...register('dueDate')} />
            </Field>
            <Field label="Requested Delivery Date" error={errors.requestedDeliveryDate?.message}>
              <Input type="date" {...register('requestedDeliveryDate')} />
            </Field>
          </div>
          <div className="mt-4 grid gap-4 md:grid-cols-2">
            <Field label="Request Title (optional)">
              <Input
                {...register('requestTitle')}
                placeholder="Auto-generated from PR Number if left blank"
              />
            </Field>
            <Field label="Source System *" error={errors.sourceSystem?.message}>
              <Input {...register('sourceSystem')} aria-invalid={!!errors.sourceSystem} />
            </Field>
          </div>
        </Card>

        <Card className="border-slate-200 p-5 shadow-xs">
          <h2 className="text-base font-semibold text-slate-900">Customer Information</h2>
          <div className="mt-4 grid gap-4 md:grid-cols-3">
            <Field label="Name *" error={errors.customerName?.message}>
              <Input
                {...register('customerName')}
                placeholder="Name *"
                aria-invalid={!!errors.customerName}
              />
            </Field>
            <Field label="Company">
              <Input {...register('customerCompany')} placeholder="Company" />
            </Field>
            <Field label="Address">
              <Input {...register('customerAddress')} placeholder="Address" />
            </Field>
          </div>
        </Card>

        <Card className="border-slate-200 p-5 shadow-xs">
          <h2 className="text-base font-semibold text-slate-900">Buyer Information</h2>
          <div className="mt-4 grid gap-4 md:grid-cols-3">
            <Field label="Name">
              <Input {...register('buyerName')} placeholder="Name" />
            </Field>
            <Field label="Email" error={errors.buyerEmail?.message}>
              <Input
                type="email"
                {...register('buyerEmail')}
                placeholder="Email"
                aria-invalid={!!errors.buyerEmail}
              />
            </Field>
            <Field label="Phone">
              <Input type="tel" {...register('buyerPhone')} placeholder="Phone (optional)" />
            </Field>
          </div>
        </Card>

        <Card className="border-slate-200 p-5 shadow-xs">
          <h2 className="text-base font-semibold text-slate-900">Ship-To Information</h2>
          <div className="mt-4 space-y-4">
            <Field label="Address">
              <Input {...register('shipAddress')} placeholder="Address" />
            </Field>
            <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
              <Field label="City">
                <Input {...register('shipCity')} placeholder="City" />
              </Field>
              <Field label="State">
                <Input {...register('shipState')} placeholder="State" />
              </Field>
              <Field label="ZIP">
                <Input {...register('shipZip')} placeholder="ZIP" />
              </Field>
              <Field label="Country">
                <Input {...register('shipCountry')} placeholder="Country" />
              </Field>
            </div>
          </div>
        </Card>

        <Card className="border-slate-200 p-5 shadow-xs">
          <div className="flex items-center justify-between gap-3">
            <h2 className="text-base font-semibold text-slate-900">RFQ Items</h2>
            <Button
              type="button"
              variant="ghost"
              size="sm"
              className="font-semibold text-blue-700 hover:bg-blue-50 hover:text-blue-800"
              onClick={() =>
                append({
                  lineNumber: fields.length + 1,
                  requestedQuantity: 1,
                  unitOfMeasure: 'EA',
                  requestedPartNumber: '',
                  requestedManufacturer: '',
                  procurementCategory: '',
                  taaRequired: false,
                  description: '',
                })
              }
            >
              <Plus className="size-4" /> Add Item
            </Button>
          </div>

          {errors.lineItems?.message && (
            <p className="mt-2 text-xs text-red-700" role="alert">
              {errors.lineItems.message}
            </p>
          )}

          <div className="mt-4 space-y-3">
            {fields.map((field, index) => (
              <div
                key={field.id}
                className="rounded-lg border border-slate-200 bg-slate-50/40 p-4"
              >
                <div className="grid items-start gap-3 md:grid-cols-[80px_110px_110px_1fr_1.6fr_auto]">
                  <Field label="Item #">
                    <Input
                      type="number"
                      min={1}
                      {...register(`lineItems.${index}.lineNumber`, { valueAsNumber: true })}
                    />
                  </Field>
                  <Field
                    label="Qty *"
                    error={errors.lineItems?.[index]?.requestedQuantity?.message}
                  >
                    <Input
                      type="number"
                      min="0.0001"
                      step="any"
                      {...register(`lineItems.${index}.requestedQuantity`, {
                        valueAsNumber: true,
                      })}
                    />
                  </Field>
                  <Field label="UOI">
                    <Input {...register(`lineItems.${index}.unitOfMeasure`)} placeholder="UOI" />
                  </Field>
                  <Field label="Part #">
                    <Input
                      {...register(`lineItems.${index}.requestedPartNumber`)}
                      placeholder="Part #"
                    />
                  </Field>
                  <Field
                    label="Description *"
                    error={errors.lineItems?.[index]?.description?.message}
                  >
                    <Input
                      {...register(`lineItems.${index}.description`)}
                      placeholder="Description"
                      aria-invalid={!!errors.lineItems?.[index]?.description}
                    />
                  </Field>
                  <div className="pt-6">
                    <Button
                      type="button"
                      variant="ghost"
                      size="icon"
                      disabled={fields.length === 1}
                      aria-label={`Remove item ${index + 1}`}
                      onClick={() => remove(index)}
                      className="text-slate-400 hover:text-red-600"
                    >
                      <Trash2 className="size-4" />
                    </Button>
                  </div>
                </div>
                <div className="mt-3 grid gap-3 sm:grid-cols-3">
                  <Input
                    {...register(`lineItems.${index}.requestedManufacturer`)}
                    placeholder="Manufacturer (optional)"
                    className="h-8 text-xs"
                  />
                  <Input
                    {...register(`lineItems.${index}.procurementCategory`)}
                    placeholder="Category (Tools, Consumables…)"
                    className="h-8 text-xs"
                  />
                  <label className="flex items-center gap-2 text-xs text-slate-600">
                    <input
                      type="checkbox"
                      {...register(`lineItems.${index}.taaRequired`)}
                      className="size-3.5 rounded accent-blue-700"
                    />
                    TAA Compliance Required
                  </label>
                </div>
              </div>
            ))}
          </div>
        </Card>

        <div className="flex justify-end gap-3 pt-1">
          <Button
            type="button"
            variant="outline"
            disabled={busy}
            onClick={() => void navigate({ to: '/rfqs' })}
          >
            Cancel
          </Button>
          <Button type="submit" disabled={busy}>
            {busy ? (
              <>
                <Loader2 className="size-4 animate-spin" /> Creating…
              </>
            ) : (
              'Create RFQ'
            )}
          </Button>
        </div>
      </form>
    </AppShell>
  )
}

function Field({
  label,
  error,
  children,
}: {
  label: string
  error?: string
  children: React.ReactNode
}) {
  return (
    <label className="block space-y-1 text-xs font-medium text-slate-600">
      <span>{label}</span>
      {children}
      {error && (
        <span className="block text-[11px] font-normal text-red-700" role="alert">
          {error}
        </span>
      )}
    </label>
  )
}
