import { Loader2, Megaphone } from 'lucide-react'
import * as React from 'react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { completeMarketing, markReadyForMarketing } from '@/features/rfq/api/rfqApi'
import { useRfqDemoStore } from '@/features/rfq/store/rfqDemoStore'
import { type RfqRecord } from '@/features/rfq/types'
import { normalizeError, unexpectedErrorMessage } from '@/lib/errors'

export function MarketingWorkflowPanel({
  rfq,
  demo,
  isAdmin,
  isAssignedMarketing,
  userName,
  invalidate,
}: {
  rfq: RfqRecord
  demo: boolean
  isAdmin: boolean
  isAssignedMarketing: boolean
  userName: string
  invalidate: () => Promise<void>
}) {
  const markReadyDemo = useRfqDemoStore((state) => state.markReadyForMarketing)
  const completeDemo = useRfqDemoStore((state) => state.completeMarketing)
  const [notes, setNotes] = React.useState('')
  const [outcome, setOutcome] = React.useState('CAMPAIGN_LAUNCHED')
  const [busy, setBusy] = React.useState(false)

  const canMarkReady = isAdmin && rfq.status === 'CUSTOMER_APPROVED'
  const awaitMarketingAssignment = rfq.status === 'READY_FOR_MARKETING'
  const canComplete = (isAdmin || isAssignedMarketing) && rfq.status === 'ASSIGNED_TO_MARKETING'
  const marketingDone = rfq.status === 'CLOSED' && rfq.customerDecision === 'APPROVED'

  if (!canMarkReady && !awaitMarketingAssignment && !canComplete && !marketingDone) {
    return null
  }

  async function handleMarkReady() {
    setBusy(true)
    try {
      if (demo) markReadyDemo(rfq.id, notes.trim() || 'Ready for marketing intake', userName)
      else await markReadyForMarketing(rfq.id, notes.trim() || undefined)
      toast.success('RFQ marked ready for marketing handoff.')
      setNotes('')
      await invalidate()
    } catch (error) {
      toast.error(unexpectedErrorMessage(normalizeError(error)))
    } finally {
      setBusy(false)
    }
  }

  async function handleCompleteMarketing(event: React.FormEvent) {
    event.preventDefault()
    setBusy(true)
    try {
      if (demo)
        completeDemo(
          rfq.id,
          `${outcome}${notes.trim() ? `: ${notes.trim()}` : ''}`,
          userName,
        )
      else await completeMarketing(rfq.id, { outcome, notes: notes.trim() || undefined })
      toast.success('Marketing execution marked complete.')
      setNotes('')
      await invalidate()
    } catch (error) {
      toast.error(unexpectedErrorMessage(normalizeError(error)))
    } finally {
      setBusy(false)
    }
  }

  return (
    <Card className="border-slate-200 p-4 shadow-none">
      <div className="flex items-start justify-between gap-2">
        <div>
          <h2 className="text-sm font-semibold text-slate-900">Marketing handoff</h2>
          <p className="mt-1 text-xs text-slate-500">
            Post-approval marketing routing and campaign completion.
          </p>
        </div>
        <Megaphone className="size-4 text-blue-600" />
      </div>

      {marketingDone ? (
        <div className="mt-3 rounded-md bg-emerald-50 px-3 py-2 text-xs font-medium text-emerald-700">
          Marketing execution is complete for this RFQ.
        </div>
      ) : canMarkReady ? (
        <div className="mt-3 space-y-2.5">
          <textarea
            value={notes}
            onChange={(event) => setNotes(event.target.value)}
            rows={2}
            placeholder="Optional handoff note for marketing…"
            className="w-full rounded-md border border-slate-200 px-3 py-2 text-xs"
          />
          <Button
            type="button"
            size="sm"
            className="w-full"
            disabled={busy}
            onClick={() => void handleMarkReady()}
          >
            {busy ? <Loader2 className="size-3.5 animate-spin" /> : null}
            Mark Ready for Marketing
          </Button>
        </div>
      ) : canComplete ? (
        <form onSubmit={handleCompleteMarketing} className="mt-3 space-y-2.5">
          <label className="block text-xs font-medium text-slate-700">
            Outcome
            <select
              value={outcome}
              onChange={(event) => setOutcome(event.target.value)}
              className="mt-1 h-9 w-full rounded-md border border-slate-200 bg-white px-2.5 text-xs"
            >
              <option value="CAMPAIGN_LAUNCHED">Campaign launched</option>
              <option value="CUSTOMER_ONBOARDED">Customer onboarded</option>
              <option value="HANDOFF_CLOSED">Handoff completed</option>
            </select>
          </label>
          <textarea
            value={notes}
            onChange={(event) => setNotes(event.target.value)}
            rows={2}
            placeholder="Summary of marketing follow-up…"
            className="w-full rounded-md border border-slate-200 px-3 py-2 text-xs"
          />
          <Button type="submit" size="sm" className="w-full" disabled={busy}>
            {busy ? <Loader2 className="size-3.5 animate-spin" /> : null}
            Complete Marketing Handoff
          </Button>
        </form>
      ) : awaitMarketingAssignment ? (
        <div className="mt-3 rounded-md bg-blue-50 px-3 py-2 text-xs text-blue-800">
          Ready for marketing — assign a Marketing Executive in Assignments above to enable
          handoff completion.
        </div>
      ) : null}
    </Card>
  )
}
