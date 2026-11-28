import { Link } from '@tanstack/react-router'
import { Flag, ShoppingCart } from 'lucide-react'
import * as React from 'react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { createPurchaseOrder, createRedFlag } from '@/features/rfq/api/rfqApi'
import { useRfqDemoStore } from '@/features/rfq/store/rfqDemoStore'
import { type RfqRecord } from '@/features/rfq/types'
import { normalizeError, unexpectedErrorMessage } from '@/lib/errors'

export function RfqGovernanceQuickPanel({
  rfq,
  demo,
  latestVersionId,
  isAdmin,
  userId,
  invalidate,
}: {
  rfq: RfqRecord
  demo: boolean
  latestVersionId?: number
  isAdmin: boolean
  userId: number
  invalidate: () => Promise<void>
}) {
  const createDemoRedFlag = useRfqDemoStore((state) => state.createRedFlag)
  const createDemoPO = useRfqDemoStore((state) => state.createPurchaseOrder)
  const [showFlagForm, setShowFlagForm] = React.useState(false)
  const [severity, setSeverity] = React.useState<'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'>('HIGH')
  const [flagReason, setFlagReason] = React.useState('')
  const [showPoForm, setShowPoForm] = React.useState(false)
  const [supplierName, setSupplierName] = React.useState('')
  const [expectedDelivery, setExpectedDelivery] = React.useState('')
  const [poNotes, setPoNotes] = React.useState('')
  const [busy, setBusy] = React.useState(false)

  const canCreatePo =
    isAdmin &&
    (['CUSTOMER_APPROVED', 'READY_FOR_MARKETING', 'ASSIGNED_TO_MARKETING'].includes(rfq.status) ||
      (rfq.status === 'CLOSED' && rfq.customerDecision === 'APPROVED'))

  async function handleRaiseFlag(event: React.FormEvent) {
    event.preventDefault()
    if (flagReason.trim().length < 5) {
      toast.error('Provide a reason (at least 5 characters).')
      return
    }
    setBusy(true)
    try {
      if (demo) {
        createDemoRedFlag(rfq.id, severity, flagReason.trim(), userId)
      } else {
        await createRedFlag(rfq.id, severity, flagReason.trim())
      }
      toast.success('Red flag raised for this RFQ.')
      setFlagReason('')
      setShowFlagForm(false)
      await invalidate()
    } catch (error) {
      toast.error(unexpectedErrorMessage(normalizeError(error)))
    } finally {
      setBusy(false)
    }
  }

  async function handleCreatePo(event: React.FormEvent) {
    event.preventDefault()
    if (!supplierName.trim()) {
      toast.error('Enter a supplier name.')
      return
    }
    setBusy(true)
    try {
      if (demo) {
        createDemoPO({
          rfqPublicId: rfq.id,
          supplierName: supplierName.trim(),
          expectedDelivery: expectedDelivery || undefined,
          notes: poNotes.trim() || undefined,
          createdBy: userId,
        })
      } else {
        await createPurchaseOrder(
          rfq.id,
          latestVersionId,
          supplierName.trim(),
          undefined,
          undefined,
          expectedDelivery || undefined,
          poNotes.trim() || undefined,
        )
      }
      toast.success('Purchase order created.')
      setSupplierName('')
      setExpectedDelivery('')
      setPoNotes('')
      setShowPoForm(false)
      await invalidate()
    } catch (error) {
      toast.error(unexpectedErrorMessage(normalizeError(error)))
    } finally {
      setBusy(false)
    }
  }

  return (
    <Card className="p-4">
      <h2 className="text-sm font-semibold text-brand-900">Escalation &amp; Procurement</h2>
      <div className="mt-3 flex flex-wrap gap-2">
        <Button
          type="button"
          size="sm"
          variant="outline"
          onClick={() => setShowFlagForm((prev) => !prev)}
        >
          <Flag className="size-3.5 text-red-brand-600" />
          Raise Red Flag
        </Button>
        {canCreatePo && (
          <Button
            type="button"
            size="sm"
            variant="outline"
            onClick={() => setShowPoForm((prev) => !prev)}
          >
            <ShoppingCart className="size-3.5 text-brand-900" />
            Create Purchase Order
          </Button>
        )}
      </div>

      {showFlagForm && (
        <form onSubmit={handleRaiseFlag} className="mt-3 space-y-2.5 border-t border-brand-100 pt-3">
          <select
            value={severity}
            onChange={(event) =>
              setSeverity(event.target.value as 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL')
            }
            className="h-9 w-full rounded-lg border border-input bg-white px-2.5 text-xs focus-visible:border-brand-900 focus-visible:ring-2 focus-visible:ring-brand-900/20 focus-visible:outline-none"
          >
            <option value="LOW">Low severity</option>
            <option value="MEDIUM">Medium severity</option>
            <option value="HIGH">High severity</option>
            <option value="CRITICAL">Critical severity</option>
          </select>
          <textarea
            required
            rows={2}
            value={flagReason}
            onChange={(event) => setFlagReason(event.target.value)}
            placeholder="Describe the blocker, pricing risk, or compliance issue…"
            className="w-full rounded-lg border border-input bg-white px-3 py-2 text-xs focus-visible:border-brand-900 focus-visible:ring-2 focus-visible:ring-brand-900/20 focus-visible:outline-none"
          />
          <div className="flex justify-end gap-2">
            <Button type="button" size="sm" variant="ghost" onClick={() => setShowFlagForm(false)}>
              Cancel
            </Button>
            <Button type="submit" size="sm" variant="destructive" disabled={busy}>
              Submit Flag
            </Button>
          </div>
        </form>
      )}

      {showPoForm && (
        <form onSubmit={handleCreatePo} className="mt-3 space-y-2.5 border-t border-brand-100 pt-3">
          <Input
            required
            placeholder="Supplier name"
            value={supplierName}
            onChange={(event) => setSupplierName(event.target.value)}
            className="h-9 text-xs"
          />
          <Input
            type="date"
            aria-label="Expected delivery date"
            value={expectedDelivery}
            onChange={(event) => setExpectedDelivery(event.target.value)}
            className="h-9 text-xs"
          />
          <textarea
            rows={2}
            placeholder="PO notes (optional)"
            value={poNotes}
            onChange={(event) => setPoNotes(event.target.value)}
            className="w-full rounded-lg border border-input bg-white px-3 py-2 text-xs focus-visible:border-brand-900 focus-visible:ring-2 focus-visible:ring-brand-900/20 focus-visible:outline-none"
          />
          <div className="flex justify-end gap-2">
            <Button type="button" size="sm" variant="ghost" onClick={() => setShowPoForm(false)}>
              Cancel
            </Button>
            <Button type="submit" size="sm" disabled={busy}>
              Create PO
            </Button>
          </div>
        </form>
      )}

      <div className="mt-3 flex flex-wrap gap-3 border-t border-brand-100 pt-2.5 text-xs">
        <Link to="/red-flags" className="font-semibold text-brand-900 hover:text-red-brand-600 hover:underline">
          View all Red Flags →
        </Link>
        <Link to="/purchases" className="font-semibold text-brand-900 hover:text-red-brand-600 hover:underline">
          View Purchases →
        </Link>
      </div>
    </Card>
  )
}
