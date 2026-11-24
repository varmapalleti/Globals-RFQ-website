import { createFileRoute, Link, useNavigate } from '@tanstack/react-router'
import { zodResolver } from '@hookform/resolvers/zod'
import {
  AlertTriangle,
  ArrowLeft,
  ArrowRight,
  CheckCircle2,
  FileSpreadsheet,
  FileText,
  Info,
  Loader2,
  Plus,
  ShieldCheck,
  Trash2,
  UploadCloud,
  XCircle,
} from 'lucide-react'
import * as React from 'react'
import { useFieldArray, useForm } from 'react-hook-form'
import { toast } from 'sonner'
import { z } from 'zod'
import { AppShell } from '@/components/layout/AppShell'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Card } from '@/components/ui/card'
import { useAuth } from '@/features/auth/hooks/useAuth'
import { isDemoSession } from '@/features/auth/demoSession'
import {
  confirmImport,
  createRfq,
  previewImport,
  uploadAttachment,
  type ImportPreviewDto,
} from '@/features/rfq/api/rfqApi'
import { useRfqDemoStore } from '@/features/rfq/store/rfqDemoStore'
import { formatRfqNumber, type RfqRecord } from '@/features/rfq/types'
import { isDemoMode } from '@/lib/env'
import { normalizeError, unexpectedErrorMessage } from '@/lib/errors'

export const Route = createFileRoute('/_authenticated/rfqs/import')({ component: ImportRfqPage })

const importSchema = z.object({
  sourceSystem: z.string().trim().min(2, 'Enter the source system name.').max(80),
  sourceRfqId: z.string().trim().min(1, 'Enter the customer’s RFQ number.').max(120),
  customerName: z.string().trim().min(2, 'Enter the customer name.').max(190),
  customerCompany: z.string().trim().max(190).optional(),
  sourceReference: z.string().trim().max(255).optional(),
  sourceQuotationManagerName: z.string().trim().max(190).optional(),
  requestTitle: z.string().trim().min(3, 'Enter a short request title.').max(255),
  requestedDeliveryDate: z.string().optional(),
  dueAtLocal: z
    .string()
    .optional()
    .refine((value) => !value || !Number.isNaN(Date.parse(value)), 'Enter a valid due date and time.'),
  dueTimeZone: z
    .string()
    .trim()
    .max(80)
    .optional()
    .refine((value) => {
      if (!value) return true
      try {
        new Intl.DateTimeFormat('en', { timeZone: value })
        return true
      } catch {
        return false
      }
    }, 'Enter a valid IANA timezone, such as Asia/Kolkata.'),
  buyerName: z.string().trim().max(150).optional(),
  buyerEmail: z.union([z.literal(''), z.string().email('Enter a valid email address.')]).optional(),
  buyerPhone: z.string().trim().max(40).optional(),
  shipToText: z.string().max(20000).optional(),
  sourceUrl: z.union([z.literal(''), z.string().url('Enter a valid source URL.')]).optional(),
  termsAndConditions: z.string().max(100000).optional(),
  lineItems: z
    .array(
      z.object({
        description: z.string().trim().min(3, 'Add a line item description.').max(20000),
        requestedQuantity: z.number().finite().positive('Quantity must be greater than zero.'),
        unitOfMeasure: z.string().trim().max(40).optional(),
        requestedPartNumber: z.string().trim().max(255).optional(),
        requestedManufacturer: z.string().trim().max(255).optional(),
        procurementCategory: z.string().trim().max(120).optional(),
        technicalSpecifications: z.string().max(20000).optional(),
        taaRequired: z.boolean(),
      }),
    )
    .min(1, 'Add at least one requested item.'),
})
const importSourceSchema = z.object({
  sourceSystem: z.string().trim().min(2, 'Enter the source system name.').max(80),
  sourceRfqId: z.string().trim().min(1, 'Enter the customer’s RFQ number.').max(120),
})

type ImportFormValues = z.infer<typeof importSchema>
const defaultValues: ImportFormValues = {
  sourceSystem: 'CUSTOMER_SOFTWARE',
  sourceRfqId: '112477',
  customerName: 'V2X Systems Corporation',
  customerCompany: 'V2X Systems Corporation',
  sourceReference: '',
  sourceQuotationManagerName: 'Palleti',
  requestTitle: 'Three phase blower motor',
  requestedDeliveryDate: '',
  dueAtLocal: '',
  dueTimeZone: '',
  buyerName: 'Marty Barclay',
  buyerEmail: '',
  buyerPhone: '',
  shipToText: 'Oakland Cross-Dock\n9757 San Leandro St\nOakland, CA 94603\nUSA',
  sourceUrl: '',
  termsAndConditions: '',
  lineItems: [
    {
      description: 'Motor, AC, 3 phase, 5.5 kW, 1000 RPM, 6 pole, IEC 132ME, B3, TEFC, IP55',
      requestedQuantity: 2,
      unitOfMeasure: 'EA',
      requestedPartNumber: '6105-09-V30-0008',
      requestedManufacturer: '',
      procurementCategory: 'Materials',
      technicalSpecifications: '',
      taaRequired: true,
    },
  ],
}
const liveDefaultValues: ImportFormValues = {
  sourceSystem: 'CUSTOMER_SOFTWARE',
  sourceRfqId: '',
  customerName: '',
  customerCompany: '',
  sourceReference: '',
  sourceQuotationManagerName: '',
  requestTitle: '',
  requestedDeliveryDate: '',
  dueAtLocal: '',
  dueTimeZone: '',
  buyerName: '',
  buyerEmail: '',
  buyerPhone: '',
  shipToText: '',
  sourceUrl: '',
  termsAndConditions: '',
  lineItems: [
    {
      description: '',
      requestedQuantity: 0,
      unitOfMeasure: 'EA',
      requestedPartNumber: '',
      requestedManufacturer: '',
      procurementCategory: '',
      technicalSpecifications: '',
      taaRequired: false,
    },
  ],
}

function ImportRfqPage() {
  const navigate = useNavigate()
  const { user, hasAnyRole } = useAuth()
  const importRfq = useRfqDemoStore((state) => state.importRfq)
  const [file, setFile] = React.useState<File | null>(null)
  const [draggingFile, setDraggingFile] = React.useState(false)
  const [fileError, setFileError] = React.useState('')
  const [preview, setPreview] = React.useState<ImportPreviewDto | null>(null)
  const [previewed, setPreviewed] = React.useState(false)
  const [manualIntake, setManualIntake] = React.useState(false)
  const [busy, setBusy] = React.useState(false)
  const demo = isDemoMode && isDemoSession()
  const {
    register,
    handleSubmit,
    getValues,
    setError,
    setValue,
    watch,
    control,
    formState: { errors },
  } = useForm<ImportFormValues>({
    resolver: zodResolver(importSchema),
    defaultValues: demo ? defaultValues : liveDefaultValues,
  })
  const watchedValues = watch()
  const {
    fields: lineFields,
    append,
    remove,
    replace,
  } = useFieldArray({
    control,
    name: 'lineItems',
  })

  if (!hasAnyRole(['ADMIN', 'SUPER_ADMIN']))
    return (
      <AppShell>
        <div className="mx-auto max-w-xl rounded-xl border border-red-200 bg-white p-8 text-center">
          <ShieldCheck className="mx-auto size-8 text-red-600" />
          <h1 className="mt-3 text-xl font-bold">Admin access required</h1>
          <p className="mt-2 text-sm text-slate-500">
            Only an authorized administrator can import a customer RFQ.
          </p>
        </div>
      </AppShell>
    )

  function handleFile(selected: File | undefined) {
    setPreview(null)
    setPreviewed(false)
    setFileError('')
    if (!selected) {
      setFile(null)
      return
    }
    const extension = selected.name.split('.').pop()?.toLowerCase()
    const supportedAttachments = [
      'xlsx',
      'xls',
      'pdf',
      'doc',
      'docx',
      'csv',
      'txt',
      'png',
      'jpg',
      'jpeg',
    ]
    const machineReadableFormats = ['xlsx', 'xls', 'pdf']
    if (!supportedAttachments.includes(extension ?? '')) {
      setFileError('Supported source files: Excel, PDF, Word, CSV, text, PNG, and JPG/JPEG.')
      setFile(null)
      return
    }
    if (selected.size === 0 || selected.size > 25 * 1024 * 1024) {
      setFileError(
        selected.size === 0
          ? 'The selected file is empty.'
          : 'The source file exceeds the 25 MB upload limit.',
      )
      setFile(null)
      return
    }
    setFile(selected)
    const requiresManualIntake = !machineReadableFormats.includes(extension ?? '')
    if (!manualIntake) {
      setManualIntake(requiresManualIntake)
      setPreviewed(requiresManualIntake)
    }
  }

  async function onPreview(values: ImportFormValues) {
    if (manualIntake) {
      setPreviewed(true)
      return
    }
    if (!file) {
      setFileError('Choose the customer source workbook or PDF first.')
      return
    }
    setBusy(true)
    try {
      if (demo) {
        setPreviewed(true)
        toast.success('Demo preview is ready', {
          description: 'Review and correct the mapped fields before importing.',
        })
      } else {
        const data = await previewImport(file, values.sourceSystem, values.sourceRfqId)
        setPreview(data)
        const normalized = data.normalizedRfq as {
          sourceRfqId?: string
          customerName?: string
          customerCompany?: string
          sourceReference?: string
          sourceQuotationManagerName?: string
          requestTitle?: string
          dueAtLocal?: string
          dueTimeZone?: string
          requestedDeliveryDate?: string
          buyerName?: string
          buyerEmail?: string
          buyerPhone?: string
          shipToText?: string
          sourceUrl?: string
          termsAndConditions?: string
          lineItems?: Array<{
            sourceLineId?: string
            lineNumber?: number
            description?: string
            requestedQuantity?: number
            unitOfMeasure?: string
            requestedPartNumber?: string
            requestedManufacturer?: string
            procurementCategory?: string
            technicalSpecifications?: string
            taaRequired?: boolean
          }>
        }
        const parsedLines = normalized.lineItems?.length ? normalized.lineItems : [{}]
        setValue('sourceRfqId', normalized.sourceRfqId ?? values.sourceRfqId)
        setValue('customerName', normalized.customerName ?? values.customerName)
        setValue('customerCompany', normalized.customerCompany ?? '')
        setValue('sourceReference', normalized.sourceReference ?? '')
        setValue('sourceQuotationManagerName', normalized.sourceQuotationManagerName ?? '')
        setValue('requestTitle', normalized.requestTitle ?? values.requestTitle)
        setValue('dueAtLocal', normalized.dueAtLocal ?? '')
        setValue('dueTimeZone', normalized.dueTimeZone ?? '')
        setValue('requestedDeliveryDate', normalized.requestedDeliveryDate ?? '')
        setValue('buyerName', normalized.buyerName ?? '')
        setValue('buyerEmail', normalized.buyerEmail ?? '')
        setValue('buyerPhone', normalized.buyerPhone ?? '')
        setValue('shipToText', normalized.shipToText ?? '')
        setValue('sourceUrl', normalized.sourceUrl ?? '')
        setValue('termsAndConditions', normalized.termsAndConditions ?? '')
        replace(
          parsedLines.map((line) => ({
            description: line.description ?? '',
            requestedQuantity: line.requestedQuantity ?? 0,
            unitOfMeasure: line.unitOfMeasure ?? 'EA',
            requestedPartNumber: line.requestedPartNumber ?? '',
            requestedManufacturer: line.requestedManufacturer ?? '',
            procurementCategory: line.procurementCategory ?? '',
            technicalSpecifications: line.technicalSpecifications ?? '',
            taaRequired: line.taaRequired ?? false,
          })),
        )
        setPreviewed(true)
        toast.success('Server import preview is ready', {
          description: `${data.fieldMappings ? Object.keys(data.fieldMappings).length : 0} source fields were mapped.`,
        })
      }
    } catch (error) {
      const apiError = normalizeError(error)
      toast.error(unexpectedErrorMessage(apiError), {
        description: apiError.errorCode !== 'UNKNOWN_ERROR' ? apiError.errorCode : undefined,
      })
    } finally {
      setBusy(false)
    }
  }

  async function onConfirm(values: ImportFormValues) {
    if (!file && !manualIntake) {
      setFileError('Choose the original source file before confirming.')
      return
    }
    if (!demo && !manualIntake && !preview?.previewToken) {
      setPreviewed(false)
      setPreview(null)
      toast.error(
        'The server preview is missing or expired. Preview this file again before confirming.',
      )
      return
    }
    setBusy(true)
    try {
      let rfqId: string
      let label: string
      if (demo) {
        const id = crypto.randomUUID()
        const createdAt = new Date().toISOString()
        const rfq: RfqRecord = {
          id,
          sourceRfqId: values.sourceRfqId,
          customerName: values.customerName,
          customerCompany: values.customerName,
          buyerName: values.buyerName ?? '',
          buyerEmail: values.buyerEmail ?? '',
          requestTitle: values.requestTitle,
          dueAt: '',
          dueTimeZone: 'Asia/Kolkata',
          requestedDeliveryDate: values.requestedDeliveryDate ?? '',
          shipToText: values.shipToText ?? '',
          sourceSystem: values.sourceSystem,
          sourceFileName: file?.name ?? '',
          status: 'IMPORTED',
          customerDecision: 'PENDING',
          quotationManagerCanAssignReviewer: false,
          version: 1,
          rejectionCount: 0,
          currentQuotationVersion: 0,
          quotationVersions: [],
          assignments: [],
          lineItems: values.lineItems.map((line, index) => ({
            sourceLineId: String(index + 1),
            lineNumber: index + 1,
            description: line.description,
            requestedQuantity: line.requestedQuantity,
            unitOfMeasure: line.unitOfMeasure || 'EA',
            requestedManufacturer: line.requestedManufacturer || '',
            requestedPartNumber: line.requestedPartNumber || '',
            procurementCategory: line.procurementCategory || '',
            taaRequired: line.taaRequired,
            technicalSpecifications: line.technicalSpecifications || '',
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
              label: 'RFQ imported',
              detail: file
                ? `${file.name} · ${values.sourceSystem}`
                : `Entered manually · ${values.sourceSystem}`,
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
      } else if (preview?.previewToken && !manualIntake) {
        const original = preview.normalizedRfq
        const originalLines = Array.isArray(original['lineItems'])
          ? (original['lineItems'] as Array<Record<string, unknown>>)
          : []
        const normalizedRfq = {
          ...original,
          sourceSystem: values.sourceSystem,
          sourceRfqId: values.sourceRfqId,
          customerName: values.customerName,
          customerCompany: values.customerCompany || null,
          sourceReference: values.sourceReference || null,
          sourceQuotationManagerName: values.sourceQuotationManagerName || null,
          requestTitle: values.requestTitle,
          dueAtLocal: values.dueAtLocal || null,
          dueTimeZone: values.dueTimeZone || null,
          requestedDeliveryDate: values.requestedDeliveryDate || null,
          buyerName: values.buyerName || null,
          buyerEmail: values.buyerEmail || null,
          buyerPhone: values.buyerPhone || null,
          shipToText: values.shipToText || null,
          sourceUrl: values.sourceUrl || null,
          termsAndConditions: values.termsAndConditions || null,
          lineItems: values.lineItems.map((line, index) => {
            const originalLine = originalLines[index] ?? {}
            const sourceLineId = originalLine['sourceLineId']
            return {
              ...originalLine,
              sourceLineId: typeof sourceLineId === 'string' ? sourceLineId : String(index + 1),
              lineNumber: Number(originalLine['lineNumber'] ?? index + 1),
              description: line.description,
              requestedQuantity: line.requestedQuantity,
              unitOfMeasure: line.unitOfMeasure || 'EA',
              requestedPartNumber: line.requestedPartNumber || null,
              requestedManufacturer: line.requestedManufacturer || null,
              procurementCategory: line.procurementCategory || null,
              technicalSpecifications: line.technicalSpecifications || null,
              taaRequired: line.taaRequired,
            }
          }),
        }
        const result = await confirmImport(preview.previewToken, normalizedRfq)
        rfqId = result.rfq.id
        label = `RFQ-${values.sourceRfqId}`
      } else {
        const created = await createRfq({
          sourceSystem: values.sourceSystem,
          sourceRfqId: values.sourceRfqId,
          sourceReference: values.sourceReference || null,
          sourceQuotationManagerName: values.sourceQuotationManagerName || null,
          customerName: values.customerName,
          customerCompany: values.customerCompany || null,
          buyerName: values.buyerName || null,
          buyerEmail: values.buyerEmail || null,
          buyerPhone: values.buyerPhone || null,
          requestTitle: values.requestTitle,
          dueAtLocal: values.dueAtLocal || null,
          dueTimeZone: values.dueTimeZone || null,
          requestedDeliveryDate: values.requestedDeliveryDate || null,
          shipToText: values.shipToText || null,
          sourceUrl: values.sourceUrl || null,
          termsAndConditions: values.termsAndConditions || null,
          sourceData: {
            intakeMethod: 'MANUAL',
            ...(file ? { originalFileName: file.name } : {}),
          },
          lineItems: values.lineItems.map((line, index) => ({
            sourceLineId: String(index + 1),
            lineNumber: index + 1,
            description: line.description,
            requestedQuantity: line.requestedQuantity,
            unitOfMeasure: line.unitOfMeasure || 'EA',
            customerPartNumber: null,
            requestedManufacturer: line.requestedManufacturer || null,
            requestedPartNumber: line.requestedPartNumber || null,
            procurementCategory: line.procurementCategory || null,
            taaRequired: line.taaRequired,
            technicalSpecifications: line.technicalSpecifications || null,
            sourceData: {},
          })),
        })
        rfqId = created.id
        label = `RFQ-${values.sourceRfqId}`
        if (file) {
          try {
            await uploadAttachment(rfqId, file, 'CUSTOMER_REQUEST')
          } catch (attachmentError) {
            const apiError = normalizeError(attachmentError)
            toast.warning('RFQ created, but the source file was not attached.', {
              description: `${unexpectedErrorMessage(apiError)} Reopen the RFQ and upload the source from its Attachments section.`,
              duration: 12_000,
            })
          }
        }
      }
      if (!manualIntake) {
        toast.success('RFQ imported', { description: `${label} is now in the Imported stage.` })
      } else {
        toast.success('RFQ created from manual intake', {
          description: `${label} was created and its source document was attached when available.`,
        })
      }
      await navigate({ to: '/rfqs/$rfqId', params: { rfqId } })
    } catch (error) {
      const apiError = normalizeError(error)
      toast.error(unexpectedErrorMessage(apiError), {
        description: apiError.errorCode !== 'UNKNOWN_ERROR' ? apiError.errorCode : undefined,
      })
      if (
        ['TOKEN_INVALID', 'RESOURCE_NOT_FOUND', 'INVALID_WORKFLOW_TRANSITION'].includes(
          apiError.errorCode,
        )
      ) {
        setPreview(null)
        setPreviewed(false)
      }
    } finally {
      setBusy(false)
    }
  }

  function handleImportFormSubmit(event: React.FormEvent<HTMLFormElement>) {
    if (previewed) {
      void handleSubmit(onConfirm)(event)
      return
    }
    event.preventDefault()
    const parsed = importSourceSchema.safeParse(getValues())
    if (!parsed.success) {
      for (const issue of parsed.error.issues) {
        const field = issue.path[0]
        if (field === 'sourceSystem' || field === 'sourceRfqId') {
          setError(field, { type: 'validate', message: issue.message })
        }
      }
      return
    }
    void onPreview({ ...getValues(), ...parsed.data })
  }

  return (
    <AppShell>
      <div className="mb-5">
        <Button variant="ghost" size="sm" onClick={() => void navigate({ to: '/rfqs' })}>
          <ArrowLeft className="size-4" aria-hidden="true" /> Back to RFQs
        </Button>
      </div>
      <div className="mb-6 flex flex-wrap items-start justify-between gap-4">
        <div>
          <p className="text-xs font-semibold tracking-[0.15em] text-blue-700 uppercase">
            Customer request intake
          </p>
          <h1 className="mt-1 text-2xl font-bold tracking-tight">Import RFQs</h1>
          <p className="mt-1.5 max-w-3xl text-sm text-slate-500">
            Upload an Excel (.xlsx/.xls), CSV, or PDF request file to detect fields and validate
            missing columns before creating the RFQ.
          </p>
        </div>
        <Button asChild variant="outline" size="sm">
          <Link to="/rfqs/create">
            <FileText className="size-4" aria-hidden="true" /> Create RFQ manually
          </Link>
        </Button>
      </div>
      <nav aria-label="Import progress" className="mb-6 rounded-xl border border-slate-200 bg-white px-5 py-3.5">
        <ol className="flex flex-wrap items-center gap-3 text-xs font-medium sm:gap-5">
          <li className="flex items-center gap-2 text-slate-900">
            <span
              className={`flex size-6 items-center justify-center rounded-full text-xs font-bold ${
                !previewed ? 'bg-blue-700 text-white' : 'bg-emerald-600 text-white'
              }`}
            >
              1
            </span>
            Upload File
          </li>
          <span className="hidden h-px flex-1 bg-slate-200 sm:block" />
          <li className={`flex items-center gap-2 ${previewed ? 'text-slate-900' : 'text-slate-400'}`}>
            <span
              className={`flex size-6 items-center justify-center rounded-full text-xs font-bold ${
                previewed ? 'bg-blue-700 text-white' : 'bg-slate-200 text-slate-600'
              }`}
            >
              2
            </span>
            Preview &amp; Validate Fields
          </li>
          <span className="hidden h-px flex-1 bg-slate-200 sm:block" />
          <li className="flex items-center gap-2 text-slate-400">
            <span className="flex size-6 items-center justify-center rounded-full bg-slate-200 text-xs font-bold text-slate-600">
              3
            </span>
            Confirm &amp; Create
          </li>
        </ol>
      </nav>
      <div className="mb-5 grid gap-3 sm:grid-cols-2" aria-label="Choose RFQ intake method">
        <button
          type="button"
          aria-pressed={!manualIntake}
          disabled={busy}
          onClick={() => {
            const extension = file?.name.split('.').pop()?.toLowerCase()
            if (file && !['xlsx', 'xls', 'pdf'].includes(extension ?? '')) {
              setFile(null)
              setFileError('Select an Excel workbook or PDF to use the import preview.')
            }
            setManualIntake(false)
            setPreview(null)
            setPreviewed(false)
          }}
          className={`rounded-xl border p-4 text-left transition ${!manualIntake ? 'border-blue-500 bg-blue-50 ring-2 ring-blue-100' : 'border-slate-200 bg-white hover:border-blue-300'}`}
        >
          <span className="flex items-center gap-2 font-semibold text-slate-900">
            <FileSpreadsheet className="size-5 text-blue-700" /> Import Excel or PDF
          </span>
          <span className="mt-1 block text-sm text-slate-600">
            Upload, preview extracted fields, correct them, and confirm.
          </span>
        </button>
        <button
          type="button"
          aria-pressed={manualIntake}
          disabled={busy}
          onClick={() => {
            setManualIntake(true)
            setPreview(null)
            setPreviewed(true)
            setFileError('')
          }}
          className={`rounded-xl border p-4 text-left transition ${manualIntake ? 'border-blue-500 bg-blue-50 ring-2 ring-blue-100' : 'border-slate-200 bg-white hover:border-blue-300'}`}
        >
          <span className="flex items-center gap-2 font-semibold text-slate-900">
            <FileText className="size-5 text-blue-700" /> Enter details manually
          </span>
          <span className="mt-1 block text-sm text-slate-600">
            Create the RFQ from form fields. Attaching a file is optional.
          </span>
        </button>
      </div>
      <div className="grid items-start gap-5 xl:grid-cols-[minmax(0,1fr)_320px]">
        <form onSubmit={handleImportFormSubmit} noValidate className="space-y-5">
          <Card className="overflow-hidden border-slate-200 shadow-none">
            <div className="border-b border-slate-100 px-5 py-4">
              <h2 className="font-semibold">1. Source document {manualIntake ? '(optional)' : ''}</h2>
              <p className="mt-1 text-xs text-slate-500">
                Excel and text-readable PDFs can be parsed. In manual mode, you can create the RFQ
                without a file or attach a source document for reference.
              </p>
            </div>
            <div className="p-5">
              <label
                htmlFor="source-file"
                onDragOver={(event) => {
                  event.preventDefault()
                  setDraggingFile(true)
                }}
                onDragLeave={() => setDraggingFile(false)}
                onDrop={(event) => {
                  event.preventDefault()
                  setDraggingFile(false)
                  handleFile(event.dataTransfer.files[0])
                }}
                className={`flex cursor-pointer flex-col items-center justify-center rounded-xl border-2 border-dashed px-5 py-8 text-center transition ${fileError ? 'border-red-300 bg-red-50/40' : draggingFile ? 'border-blue-600 bg-blue-50' : file ? 'border-emerald-300 bg-emerald-50/40' : 'border-slate-300 bg-slate-50 hover:border-blue-400 hover:bg-blue-50/30'}`}
              >
                {file ? (
                  <>
                    <span className="flex size-12 items-center justify-center rounded-xl bg-emerald-100 text-emerald-700">
                      {file.name.toLowerCase().endsWith('.pdf') ? (
                        <FileText className="size-6" />
                      ) : (
                        <FileSpreadsheet className="size-6" />
                      )}
                    </span>
                    <span className="mt-3 text-sm font-semibold text-slate-800">{file.name}</span>
                    <span className="mt-1 text-xs text-slate-500">
                      {(file.size / 1024 / 1024).toFixed(2)} MB · Select another file to replace
                    </span>
                  </>
                ) : (
                  <>
                    <span className="flex size-12 items-center justify-center rounded-xl bg-blue-100 text-blue-700">
                      <UploadCloud className="size-6" />
                    </span>
                    <span className="mt-3 text-sm font-semibold">
                      Drop a source document here or browse
                    </span>
                    <span className="mt-1 text-xs text-slate-500">
                      Maximum 25 MB · Excel, PDF, Word, CSV, text, PNG, or JPG/JPEG
                    </span>
                  </>
                )}
                <input
                  id="source-file"
                  type="file"
                  accept=".xlsx,.xls,.pdf,.doc,.docx,.csv,.txt,.png,.jpg,.jpeg,application/pdf,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet,application/vnd.ms-excel,application/msword,application/vnd.openxmlformats-officedocument.wordprocessingml.document,image/png,image/jpeg,text/csv,text/plain"
                  onChange={(event) => {
                    handleFile(event.currentTarget.files?.[0])
                    event.currentTarget.value = ''
                  }}
                  className="sr-only"
                  aria-describedby={fileError ? 'file-error' : undefined}
                />
              </label>
              {fileError && (
                <p id="file-error" className="mt-2 text-sm text-red-700" role="alert">
                  {fileError}
                </p>
              )}
              <div className="mt-4 grid gap-4 sm:grid-cols-2">
                <Field label="Source system" error={errors.sourceSystem?.message}>
                  <Input {...register('sourceSystem')} aria-invalid={!!errors.sourceSystem} />
                </Field>
                <Field label="Customer RFQ number" error={errors.sourceRfqId?.message}>
                  <Input {...register('sourceRfqId')} aria-invalid={!!errors.sourceRfqId} />
                </Field>
              </div>
            </div>
          </Card>

          {previewed && (
            <>
              <ExcelFieldDetectionCard
                values={watchedValues}
                preview={preview}
                manualIntake={manualIntake}
              />
              <Card className="overflow-hidden border-slate-200 shadow-none">
                <div className="border-b border-slate-100 px-5 py-4">
                  <h2 className="font-semibold">2. Verify request details</h2>
                  <p className="mt-1 text-xs text-slate-500">
                    Correct any values that were read incorrectly. Long product and V2X names are
                    kept intact.
                  </p>
                </div>
                <div className="grid gap-4 p-5 sm:grid-cols-2">
                  <Field label="Customer name *" error={errors.customerName?.message}>
                    <Input {...register('customerName')} aria-invalid={!!errors.customerName} />
                  </Field>
                  <Field label="Customer company">
                    <Input {...register('customerCompany')} />
                  </Field>
                  <Field label="Request title *" error={errors.requestTitle?.message}>
                    <Input {...register('requestTitle')} aria-invalid={!!errors.requestTitle} />
                  </Field>
                  <Field label="Source reference / PR number">
                    <Input {...register('sourceReference')} />
                  </Field>
                  <Field label="Source quotation manager">
                    <Input {...register('sourceQuotationManagerName')} />
                  </Field>
                  <Field label="Buyer / contact">
                    <Input {...register('buyerName')} />
                  </Field>
                  <Field label="Buyer email" error={errors.buyerEmail?.message}>
                    <Input
                      {...register('buyerEmail')}
                      type="email"
                      aria-invalid={!!errors.buyerEmail}
                    />
                  </Field>
                  <Field label="Buyer phone">
                    <Input {...register('buyerPhone')} type="tel" />
                  </Field>
                  <Field label="Customer due date and time">
                    <Input {...register('dueAtLocal')} type="datetime-local" />
                  </Field>
                  <Field label="Due-time timezone (optional)">
                    <Input
                      {...register('dueTimeZone')}
                      placeholder="Uses service default, e.g. Asia/Kolkata"
                    />
                  </Field>
                  <Field label="Requested delivery date">
                    <Input {...register('requestedDeliveryDate')} type="date" />
                  </Field>
                  <Field label="Source-system URL" error={errors.sourceUrl?.message}>
                    <Input {...register('sourceUrl')} type="url" aria-invalid={!!errors.sourceUrl} />
                  </Field>
                  <div className="sm:col-span-2">
                    <Field label="Ship-to address">
                      <textarea
                        {...register('shipToText')}
                        rows={3}
                        className="w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-blue-500"
                      />
                    </Field>
                  </div>
                  <div className="sm:col-span-2">
                    <Field label="Terms and conditions">
                      <textarea
                        {...register('termsAndConditions')}
                        rows={4}
                        maxLength={100000}
                        className="w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-blue-500"
                      />
                    </Field>
                  </div>
                </div>
              </Card>
              <Card className="overflow-hidden border-slate-200 shadow-none">
                <div className="flex flex-wrap items-center justify-between gap-3 border-b border-slate-100 px-5 py-4">
                  <div>
                    <h2 className="font-semibold">3. Request line items</h2>
                    <p className="mt-1 text-xs text-slate-500">
                      Verify each requested product. Original workbook rows remain attached to this
                      RFQ.
                    </p>
                  </div>
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    onClick={() =>
                      append({
                        description: '',
                        requestedQuantity: 0,
                        unitOfMeasure: 'EA',
                        requestedPartNumber: '',
                        requestedManufacturer: '',
                        procurementCategory: '',
                        technicalSpecifications: '',
                        taaRequired: false,
                      })
                    }
                  >
                    <Plus className="size-4" /> Add item
                  </Button>
                </div>
                <div className="space-y-4 p-5">
                  {lineFields.map((field, index) => (
                    <div key={field.id} className="rounded-xl border border-slate-200 p-4">
                      <div className="mb-4 flex items-center justify-between">
                        <h3 className="text-sm font-semibold text-slate-800">Item {index + 1}</h3>
                        <Button
                          type="button"
                          variant="ghost"
                          size="sm"
                          disabled={lineFields.length === 1}
                          aria-label={`Remove item ${index + 1}`}
                          onClick={() => remove(index)}
                        >
                          <Trash2 className="size-4" /> Remove
                        </Button>
                      </div>
                      <div className="grid gap-4 sm:grid-cols-2">
                        <div className="sm:col-span-2">
                          <Field
                            label="Product / requirement description *"
                            error={errors.lineItems?.[index]?.description?.message}
                          >
                            <textarea
                              {...register(`lineItems.${index}.description`)}
                              rows={4}
                              maxLength={20000}
                              aria-invalid={!!errors.lineItems?.[index]?.description}
                              className="w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-blue-500"
                            />
                          </Field>
                        </div>
                        <Field
                          label="Requested quantity *"
                          error={errors.lineItems?.[index]?.requestedQuantity?.message}
                        >
                          <Input
                            type="number"
                            min="0.0001"
                            step="any"
                            {...register(`lineItems.${index}.requestedQuantity`, {
                              valueAsNumber: true,
                            })}
                            aria-invalid={!!errors.lineItems?.[index]?.requestedQuantity}
                          />
                        </Field>
                        <Field label="Unit of measure">
                          <Input {...register(`lineItems.${index}.unitOfMeasure`)} />
                        </Field>
                        <Field label="Requested manufacturer">
                          <Input {...register(`lineItems.${index}.requestedManufacturer`)} />
                        </Field>
                        <Field label="Requested part / model number">
                          <Input {...register(`lineItems.${index}.requestedPartNumber`)} />
                        </Field>
                        <Field label="Procurement category">
                          <Input
                            {...register(`lineItems.${index}.procurementCategory`)}
                            placeholder="TAA Compliant, Tools, Materials…"
                          />
                        </Field>
                        <label className="flex items-center gap-2 self-end pb-2 text-sm text-slate-700">
                          <input
                            type="checkbox"
                            {...register(`lineItems.${index}.taaRequired`)}
                            className="size-4 rounded border-slate-300 accent-blue-700"
                          />{' '}
                          TAA compliance required
                        </label>
                        <div className="sm:col-span-2">
                          <Field label="Technical specifications">
                            <textarea
                              {...register(`lineItems.${index}.technicalSpecifications`)}
                              rows={3}
                              maxLength={20000}
                              className="w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-blue-500"
                            />
                          </Field>
                        </div>
                      </div>
                    </div>
                  ))}
                </div>
              </Card>
              {preview && !demo && (
                <Card className="border-amber-200 bg-amber-50/50 p-5 shadow-none">
                  <h2 className="flex items-center gap-2 text-sm font-semibold text-amber-900">
                    <AlertTriangle className="size-4" /> Server preview notes
                  </h2>
                  <div className="mt-3 space-y-1.5 text-sm text-amber-900">
                    {preview.warnings.length ? (
                      preview.warnings.map((warning, index) => <p key={index}>• {warning}</p>)
                    ) : (
                      <p>No parser warnings. Confirm the mapped values below before import.</p>
                    )}
                  </div>
                  <p className="mt-3 text-xs text-amber-800">
                    Preview expires{' '}
                    {new Intl.DateTimeFormat('en-IN', {
                      dateStyle: 'medium',
                      timeStyle: 'short',
                    }).format(new Date(preview.expiresAt))}
                    .
                  </p>
                </Card>
              )}
            </>
          )}

          <div className="flex flex-wrap justify-between gap-2">
            {previewed && (
              <Button
                type="button"
                variant="outline"
                onClick={() => {
                  setFile(null)
                  setFileError('')
                  setPreviewed(false)
                  setManualIntake(false)
                  setPreview(null)
                }}
                disabled={busy}
              >
                {file ? 'Replace source file' : 'Change intake method'}
              </Button>
            )}
            <Button type="submit" size="lg" disabled={busy || (!file && !manualIntake)} className="ml-auto">
              {busy ? (
                <>
                  <Loader2 className="size-4 animate-spin" /> Working…
                </>
              ) : previewed ? (
                <>
                  <CheckCircle2 className="size-4" />
                  {manualIntake
                    ? file
                      ? 'Create RFQ and attach source'
                      : 'Create RFQ'
                    : 'Confirm and create RFQ'}
                </>
              ) : (
                <>
                  Preview import
                  <ArrowRight className="size-4" />
                </>
              )}
            </Button>
          </div>
        </form>

        <aside className="space-y-4">
          <Card className="border-blue-100 bg-blue-50/70 p-5 shadow-none">
            <div className="flex items-start gap-3">
              <Info className="mt-0.5 size-5 shrink-0 text-blue-700" />
              <div>
                <h2 className="text-sm font-semibold text-blue-950">Import safely</h2>
                <ul className="mt-3 space-y-2 text-xs leading-5 text-blue-900">
                  <li>• Original document is preserved as a customer request attachment.</li>
                  <li>• Duplicate source RFQs are checked by the server.</li>
                  <li>• Preview warnings must be reviewed before confirming.</li>
                  <li>
                    • File contents are inspected on the backend; this screen’s checks are only an
                    early guard.
                  </li>
                </ul>
              </div>
            </div>
          </Card>
          <Card className="border-slate-200 p-5 shadow-none">
            <h2 className="text-sm font-semibold">Common supported fields</h2>
            <p className="mt-2 text-xs leading-5 text-slate-500">
              Customer, buyer contact, source reference, due and delivery dates, ship-to address,
              line items, long descriptions, part numbers, category, TAA requirement, and source
              document.
            </p>
            <div className="mt-4 flex items-start gap-2 border-t border-slate-100 pt-4 text-xs leading-5 text-slate-500">
              <ShieldCheck className="mt-0.5 size-4 shrink-0 text-emerald-600" />
              Admin actions are authenticated and audited in connected mode.
            </div>
          </Card>
          <Card className="border-slate-200 p-5 shadow-none">
            <h2 className="text-sm font-semibold">Other supporting files?</h2>
            <p className="mt-2 text-xs leading-5 text-slate-500">
              Word documents, product photos, drawings, scanned PDFs, and other supported files can
              be used as manually reviewed source attachments or added after import. The server
              checks type, size, hash, and scan state.
            </p>
          </Card>
        </aside>
      </div>
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
    <label className="block space-y-1.5 text-sm font-medium text-slate-700">
      {label}
      {children}
      {error && (
        <span className="block text-xs font-normal text-red-700" role="alert">
          {error}
        </span>
      )}
    </label>
  )
}

function ExcelFieldDetectionCard({
  values,
  preview,
  manualIntake,
}: {
  values: ImportFormValues
  preview: ImportPreviewDto | null
  manualIntake: boolean
}) {
  const hasText = (v?: string) => Boolean(v && v.trim().length > 0)
  const requiredChecks = [
    { label: 'PR / Source RFQ #', present: hasText(values.sourceRfqId) },
    { label: 'Customer Name', present: hasText(values.customerName) },
    { label: 'Request Title', present: hasText(values.requestTitle) },
    { label: 'Source System', present: hasText(values.sourceSystem) },
    {
      label: 'Line Items (>= 1 with description & qty)',
      present:
        values.lineItems.length > 0 &&
        values.lineItems.some((item) => hasText(item.description) && item.requestedQuantity > 0),
    },
  ]
  const optionalChecks = [
    { label: 'Customer Company', present: hasText(values.customerCompany) },
    { label: 'PR / Source Reference', present: hasText(values.sourceReference) },
    { label: 'Source Manager', present: hasText(values.sourceQuotationManagerName) },
    { label: 'Buyer Name', present: hasText(values.buyerName) },
    { label: 'Buyer Email', present: hasText(values.buyerEmail) },
    { label: 'Due Date / Time', present: hasText(values.dueAtLocal) },
    { label: 'Delivery Date', present: hasText(values.requestedDeliveryDate) },
    { label: 'Ship-To Address', present: hasText(values.shipToText) },
    { label: 'Terms & Conditions', present: hasText(values.termsAndConditions) },
  ]
  const lineCount = values.lineItems.length || 1
  const lineCoverage = [
    {
      label: 'Description',
      count: values.lineItems.filter((item) => hasText(item.description)).length,
    },
    {
      label: 'Quantity > 0',
      count: values.lineItems.filter((item) => item.requestedQuantity > 0).length,
    },
    { label: 'UOM', count: values.lineItems.filter((item) => hasText(item.unitOfMeasure)).length },
    {
      label: 'Part #',
      count: values.lineItems.filter((item) => hasText(item.requestedPartNumber)).length,
    },
    {
      label: 'Manufacturer',
      count: values.lineItems.filter((item) => hasText(item.requestedManufacturer)).length,
    },
    {
      label: 'Category',
      count: values.lineItems.filter((item) => hasText(item.procurementCategory)).length,
    },
  ]

  const missingRequired = requiredChecks.filter((c) => !c.present)
  const missingOptional = optionalChecks.filter((c) => !c.present)
  const mappings = preview?.fieldMappings ? Object.entries(preview.fieldMappings) : []

  return (
    <Card className="overflow-hidden border-slate-200 shadow-none">
      <div className="flex flex-wrap items-center justify-between gap-2 border-b border-slate-100 bg-slate-50/70 px-5 py-3.5">
        <div>
          <h2 className="text-sm font-semibold text-slate-900">
            Excel / Source Field Detection &amp; Validation
          </h2>
          <p className="mt-0.5 text-xs text-slate-500">
            {manualIntake
              ? 'Live field completeness check before creating the RFQ.'
              : `Format: ${preview?.detectedFormat ?? 'Spreadsheet'} · Verified ${requiredChecks.length - missingRequired.length}/${requiredChecks.length} required header checks`}
          </p>
        </div>
        <span
          className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-semibold ${
            missingRequired.length === 0
              ? 'bg-emerald-50 text-emerald-700'
              : 'bg-amber-50 text-amber-800'
          }`}
        >
          {missingRequired.length === 0 ? (
            <>
              <CheckCircle2 className="size-3.5" /> Ready to confirm
            </>
          ) : (
            <>
              <AlertTriangle className="size-3.5" /> {missingRequired.length} required field
              {missingRequired.length > 1 ? 's' : ''} missing
            </>
          )}
        </span>
      </div>

      <div className="grid gap-4 p-5 md:grid-cols-2">
        <div>
          <h3 className="text-xs font-semibold tracking-wide text-slate-500 uppercase">
            Required fields ({requiredChecks.length - missingRequired.length}/
            {requiredChecks.length})
          </h3>
          <ul className="mt-2 space-y-1.5">
            {requiredChecks.map((check) => (
              <li key={check.label} className="flex items-center gap-2 text-xs">
                {check.present ? (
                  <CheckCircle2 className="size-3.5 shrink-0 text-emerald-600" />
                ) : (
                  <XCircle className="size-3.5 shrink-0 text-red-600" />
                )}
                <span className={check.present ? 'text-slate-700' : 'font-medium text-red-700'}>
                  {check.label} — {check.present ? 'Detected' : 'Missing'}
                </span>
              </li>
            ))}
          </ul>
        </div>

        <div>
          <h3 className="text-xs font-semibold tracking-wide text-slate-500 uppercase">
            Recommended metadata ({optionalChecks.length - missingOptional.length}/
            {optionalChecks.length})
          </h3>
          <div className="mt-2 flex flex-wrap gap-1.5">
            {optionalChecks.map((check) => (
              <span
                key={check.label}
                className={`inline-flex items-center gap-1 rounded-md px-2 py-0.5 text-[11px] font-medium ${
                  check.present
                    ? 'bg-emerald-50 text-emerald-700'
                    : 'bg-slate-100 text-slate-600'
                }`}
              >
                {check.present ? '✓' : '—'} {check.label}
              </span>
            ))}
          </div>
        </div>
      </div>

      <div className="border-t border-slate-100 bg-slate-50/40 px-5 py-3">
        <p className="text-xs font-semibold text-slate-600">
          Line-item column detection ({values.lineItems.length} item
          {values.lineItems.length === 1 ? '' : 's'})
        </p>
        <div className="mt-2 flex flex-wrap gap-2">
          {lineCoverage.map((col) => (
            <span
              key={col.label}
              className={`rounded-md border px-2 py-0.5 text-[11px] font-medium ${
                col.count === lineCount && values.lineItems.length > 0
                  ? 'border-emerald-200 bg-emerald-50 text-emerald-700'
                  : col.count > 0
                    ? 'border-amber-200 bg-amber-50 text-amber-800'
                    : 'border-slate-200 bg-white text-slate-500'
              }`}
            >
              {col.label}: {col.count}/{values.lineItems.length}
            </span>
          ))}
        </div>
        {mappings.length > 0 && (
          <div className="mt-3 border-t border-slate-200/70 pt-2.5">
            <p className="text-[11px] font-semibold text-slate-500">
              Mapped Excel columns ({mappings.length})
            </p>
            <div className="mt-1.5 flex flex-wrap gap-1.5">
              {mappings.map(([target, source]) => (
                <span
                  key={target}
                  className="rounded bg-blue-50 px-2 py-0.5 text-[11px] text-blue-800"
                >
                  {source} → {target}
                </span>
              ))}
            </div>
          </div>
        )}
      </div>
    </Card>
  )
}
