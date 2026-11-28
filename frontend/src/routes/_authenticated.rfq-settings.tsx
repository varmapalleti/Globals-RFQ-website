import { createFileRoute } from '@tanstack/react-router'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { AlertTriangle, Loader2, Save, Settings2 } from 'lucide-react'
import * as React from 'react'
import { toast } from 'sonner'
import { AppShell } from '@/components/layout/AppShell'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { useAuth } from '@/features/auth/hooks/useAuth'
import {
  getRfqRuleConfig,
  updateMandatoryFields,
  updateRfqRejectionLimit,
} from '@/features/rfq/api/rfqApi'
import { isDemoSession } from '@/features/auth/demoSession'
import { isDemoMode } from '@/lib/env'
import { normalizeError, unexpectedErrorMessage } from '@/lib/errors'

export const Route = createFileRoute('/_authenticated/rfq-settings')({ component: RfqSettingsPage })

const fieldOptions = [
  ['currency', 'Currency'],
  ['freight', 'Quotation freight'],
  ['otherCharges', 'Other charges'],
  ['discount', 'Discount'],
  ['taxAmount', 'Tax amount'],
  ['items', 'At least one quotation line'],
  ['deliveryTimeline', 'Quotation delivery timeline'],
  ['warranty', 'Quotation warranty'],
  ['notes', 'Quotation notes'],
  ['items.description', 'Line description'],
  ['items.quantity', 'Line quantity'],
  ['items.unitPrice', 'Line unit price'],
  ['items.unitOfMeasure', 'Line unit of measure'],
  ['items.manufacturer', 'Offered manufacturer'],
  ['items.partNumber', 'Offered part or model number'],
  ['items.taaCompliant', 'Line TAA compliance value'],
  ['items.deliveryTimeline', 'Line delivery timeline'],
  ['items.warranty', 'Line warranty'],
  ['items.note', 'Line note'],
  ['items.productUrl', 'Line product URL'],
] as const

function RfqSettingsPage() {
  const { hasAnyRole } = useAuth()
  const queryClient = useQueryClient()
  const allowed = hasAnyRole(['ADMIN', 'SUPER_ADMIN'])
  const demo = isDemoMode && isDemoSession()
  const query = useQuery({
    queryKey: ['rfq-admin-rules'],
    queryFn: getRfqRuleConfig,
    enabled: allowed && !demo,
  })
  const [mandatory, setMandatory] = React.useState<string[]>([
    'currency',
    'items.quantity',
    'items.unitPrice',
    'items.manufacturer',
    'items.partNumber',
    'deliveryTimeline',
    'warranty',
  ])
  const [rejectionLimit, setRejectionLimit] = React.useState(3)
  const [fieldReason, setFieldReason] = React.useState('')
  const [limitReason, setLimitReason] = React.useState('')
  const [savingFields, setSavingFields] = React.useState(false)
  const [savingLimit, setSavingLimit] = React.useState(false)
  const [demoAudit, setDemoAudit] = React.useState<
    Array<{ action: string; reason: string; at: string }>
  >([])

  React.useEffect(() => {
    if (!query.data) return
    setMandatory(query.data.mandatoryFields)
    setRejectionLimit(query.data.maxFormalRejections)
  }, [query.data])

  async function saveFields() {
    if (fieldReason.trim().length < 5)
      return toast.error('Provide a reason of at least 5 characters.')
    setSavingFields(true)
    try {
      if (demo) {
        setDemoAudit((events) => [
          {
            action: 'MANDATORY_FIELDS_UPDATED',
            reason: fieldReason.trim(),
            at: new Date().toISOString(),
          },
          ...events,
        ])
        toast.success('Demo quotation rules updated locally.')
      } else {
        await updateMandatoryFields(mandatory, fieldReason.trim())
        await queryClient.invalidateQueries({ queryKey: ['rfq-admin-rules'] })
        toast.success('Mandatory quotation fields updated.')
      }
      setFieldReason('')
    } catch (error) {
      toast.error(unexpectedErrorMessage(normalizeError(error)))
    } finally {
      setSavingFields(false)
    }
  }

  async function saveLimit() {
    if (!Number.isInteger(rejectionLimit) || rejectionLimit < 1 || rejectionLimit > 20) {
      return toast.error('The rejection limit must be a whole number from 1 to 20.')
    }
    if (limitReason.trim().length < 5)
      return toast.error('Provide a reason of at least 5 characters.')
    setSavingLimit(true)
    try {
      if (demo) {
        setDemoAudit((events) => [
          {
            action: 'REJECTION_LIMIT_UPDATED',
            reason: limitReason.trim(),
            at: new Date().toISOString(),
          },
          ...events,
        ])
        toast.success('Demo rejection policy updated locally.')
      } else {
        await updateRfqRejectionLimit(rejectionLimit, limitReason.trim())
        await queryClient.invalidateQueries({ queryKey: ['rfq-admin-rules'] })
        toast.success('Reviewer rejection limit updated.')
      }
      setLimitReason('')
    } catch (error) {
      toast.error(unexpectedErrorMessage(normalizeError(error)))
    } finally {
      setSavingLimit(false)
    }
  }

  if (!allowed)
    return (
      <AppShell>
        <div className="mx-auto max-w-xl rounded-xl border border-red-brand-100 bg-white p-8 text-center shadow-[var(--shadow-card)]">
          <Settings2 className="mx-auto size-8 text-red-brand-600" />
          <h1 className="mt-3 text-xl font-bold text-brand-900">Administrator access required</h1>
          <p className="mt-2 text-sm text-slate-500">
            Only Admin and Super Admin accounts can configure RFQ workflow rules.
          </p>
        </div>
      </AppShell>
    )

  const savedMandatory = demo ? mandatory : (query.data?.mandatoryFields ?? [])
  const savedLimit = demo ? rejectionLimit : (query.data?.maxFormalRejections ?? 3)
  const audit = demo ? [] : (query.data?.audit ?? [])

  return (
    <AppShell>
      <div className="mb-6">
        <p className="text-xs font-semibold tracking-[0.15em] text-red-brand-600 uppercase">
          Workflow governance
        </p>
        <h1 className="mt-1 text-2xl font-bold tracking-tight text-brand-900">RFQ rules</h1>
        <p className="mt-1.5 max-w-3xl text-sm text-slate-600">
          Configure reviewer approval requirements and the formal rejection escalation threshold.
          Submitted quotation versions keep the validation rules captured at submission.
        </p>
      </div>
      {demo && (
        <div className="mb-5 rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-950">
          Demo rules are local sample values and do not update the RFQ Service.
        </div>
      )}
      {!demo && query.isLoading && (
        <div className="mb-4 flex items-center gap-2 text-sm text-slate-500">
          <Loader2 className="size-4 animate-spin" />
          Loading current RFQ rules…
        </div>
      )}
      {!demo && query.error && (
        <div className="mb-4 rounded-lg border border-red-brand-100 bg-red-brand-50 p-4 text-sm text-red-950">
          {unexpectedErrorMessage(normalizeError(query.error))}
        </div>
      )}
      <div className="grid items-start gap-5 xl:grid-cols-[minmax(0,1fr)_360px]">
        <div className="space-y-5">
          <Card className="overflow-hidden">
            <div className="border-b border-brand-100 px-5 py-4">
              <h2 className="font-semibold text-brand-900">Mandatory quotation fields</h2>
              <p className="mt-1 text-xs leading-5 text-slate-500">
                Managers may submit incomplete quotes. Reviewers cannot approve a version that fails
                the rules captured at submission.
              </p>
            </div>
            <fieldset
              disabled={!demo && (query.isLoading || !!query.error)}
              className="grid gap-2 p-5 sm:grid-cols-2"
            >
              <legend className="sr-only">Choose mandatory quotation fields</legend>
              {fieldOptions.map(([key, label]) => {
                const inputId = `mandatory-field-${key.replaceAll('.', '-')}`
                return (
                  <div
                    key={key}
                    className="flex items-start gap-2 rounded-lg border border-brand-100 p-3 text-sm hover:bg-brand-50/60"
                  >
                    <input
                      id={inputId}
                      type="checkbox"
                      checked={mandatory.includes(key)}
                      onChange={(event) =>
                        setMandatory((current) =>
                          event.target.checked
                            ? [...new Set([...current, key])]
                            : current.filter((field) => field !== key),
                        )
                      }
                      className="mt-0.5 size-4 accent-primary"
                    />
                    <label htmlFor={inputId} className="cursor-pointer">
                      <span className="block font-medium">{label}</span>
                      <span className="mt-0.5 block text-xs text-slate-500">{key}</span>
                    </label>
                  </div>
                )
              })}
            </fieldset>
            <div className="space-y-3 border-t border-brand-100 p-5">
              <Field label="Reason for changing these rules">
                <textarea
                  value={fieldReason}
                  onChange={(event) => setFieldReason(event.target.value)}
                  rows={2}
                  maxLength={1000}
                  className="w-full rounded-lg border border-input bg-white px-3 py-2 text-sm focus-visible:border-brand-900 focus-visible:ring-2 focus-visible:ring-brand-900/20 focus-visible:outline-none"
                  placeholder="Explain the business reason (required)"
                />
              </Field>
              <div className="flex items-center justify-between gap-3">
                <span className="text-xs text-slate-500">
                  Current saved fields: {savedMandatory.length}
                </span>
                <Button
                  onClick={() => void saveFields()}
                  disabled={savingFields || (!demo && (query.isLoading || !!query.error))}
                >
                  <Save className="size-4" />
                  {savingFields ? 'Saving…' : 'Save mandatory fields'}
                </Button>
              </div>
            </div>
          </Card>

          <Card className="overflow-hidden">
            <div className="border-b border-brand-100 px-5 py-4">
              <h2 className="font-semibold text-brand-900">Reviewer rejection limit</h2>
              <p className="mt-1 text-xs leading-5 text-slate-500">
                Reaching this count escalates the RFQ to an administrator. Values are constrained by
                the service to 1–20.
              </p>
            </div>
            <div className="grid gap-4 p-5 sm:grid-cols-[180px_1fr]">
              <Field label="Maximum rejections">
                <Input
                  type="number"
                  min={1}
                  max={20}
                  step={1}
                  value={rejectionLimit}
                  onChange={(event) => setRejectionLimit(Number(event.target.value))}
                />
              </Field>
              <Field label="Reason for changing the limit">
                <textarea
                  value={limitReason}
                  onChange={(event) => setLimitReason(event.target.value)}
                  rows={3}
                  maxLength={1000}
                  className="w-full rounded-lg border border-input bg-white px-3 py-2 text-sm focus-visible:border-brand-900 focus-visible:ring-2 focus-visible:ring-brand-900/20 focus-visible:outline-none"
                  placeholder="Explain the business reason (required)"
                />
              </Field>
            </div>
            <div className="flex flex-wrap items-center justify-between gap-3 border-t border-brand-100 p-5">
              <span className="text-xs text-slate-500">Current saved limit: {savedLimit}</span>
              <Button
                onClick={() => void saveLimit()}
                disabled={savingLimit || (!demo && (query.isLoading || !!query.error))}
              >
                <Save className="size-4" />
                {savingLimit ? 'Saving…' : 'Save rejection limit'}
              </Button>
            </div>
          </Card>
        </div>

        <Card className="overflow-hidden">
          <div className="border-b border-brand-100 px-5 py-4">
            <div className="flex items-center gap-2">
              <AlertTriangle className="size-4 text-amber-700" />
              <h2 className="font-semibold text-brand-900">Configuration audit</h2>
            </div>
            <p className="mt-1 text-xs text-slate-500">
              Changes record actor, reason, prior value, and new value.
            </p>
          </div>
          {demo ? (
            demoAudit.length ? (
              <ul className="divide-y divide-brand-100/70">
                {demoAudit.map((event, index) => (
                  <li key={`${event.at}-${index}`} className="p-4">
                    <p className="text-xs font-semibold">{event.action.replaceAll('_', ' ')}</p>
                    <p className="mt-1 text-xs text-slate-600">{event.reason}</p>
                    <p className="mt-1 text-[11px] text-slate-400">
                      {new Date(event.at).toLocaleString()}
                    </p>
                  </li>
                ))}
              </ul>
            ) : (
              <p className="p-5 text-xs text-slate-500">
                Save a demo rule to add a local sample audit event.
              </p>
            )
          ) : audit.length ? (
            <ul className="divide-y divide-brand-100/70">
              {audit.map((event) => (
                <li key={event.id} className="p-4">
                  <p className="text-xs font-semibold">{event.actionType.replaceAll('_', ' ')}</p>
                  <p className="mt-1 text-xs leading-5 text-slate-600">{event.reason}</p>
                  <p className="mt-1 text-[11px] text-slate-400">
                    Admin user {event.actorUserId} · {new Date(event.occurredAt).toLocaleString()}
                  </p>
                </li>
              ))}
            </ul>
          ) : (
            <p className="p-5 text-xs text-slate-500">
              {query.isLoading
                ? 'Loading audit history…'
                : 'No configuration changes recorded yet.'}
            </p>
          )}
        </Card>
      </div>
    </AppShell>
  )
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <label className="block space-y-1.5 text-xs font-semibold text-slate-800">
      <span>{label}</span>
      {children}
    </label>
  )
}
