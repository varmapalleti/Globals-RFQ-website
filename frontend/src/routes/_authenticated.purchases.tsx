import { createFileRoute, Link } from '@tanstack/react-router'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Loader2, Plus, ShoppingCart } from 'lucide-react'
import * as React from 'react'
import { toast } from 'sonner'
import { AppShell } from '@/components/layout/AppShell'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { useAuth } from '@/features/auth/hooks/useAuth'
import { isDemoSession } from '@/features/auth/demoSession'
import {
  createPurchaseOrder,
  getPurchaseOrders,
  getRfqs,
  updatePurchaseOrderStatus,
  type PurchaseOrderDto,
} from '@/features/rfq/api/rfqApi'
import { useRfqDemoStore } from '@/features/rfq/store/rfqDemoStore'
import { isDemoMode } from '@/lib/env'
import { normalizeError, unexpectedErrorMessage } from '@/lib/errors'

export const Route = createFileRoute('/_authenticated/purchases')({
  component: PurchasesPage,
})

const poStatuses: PurchaseOrderDto['status'][] = [
  'PENDING',
  'ORDERED',
  'SHIPPED',
  'DELIVERED',
  'COMPLETED',
  'CANCELLED',
]

const poBadge: Record<PurchaseOrderDto['status'], string> = {
  PENDING: 'bg-slate-100 text-slate-700',
  ORDERED: 'bg-brand-50 text-brand-900',
  SHIPPED: 'bg-indigo-50 text-indigo-700',
  DELIVERED: 'bg-teal-50 text-teal-700',
  COMPLETED: 'bg-emerald-50 text-emerald-700',
  CANCELLED: 'bg-red-brand-50 text-red-brand-700',
}

export function PurchasesPage() {
  const { user, hasAnyRole } = useAuth()
  const queryClient = useQueryClient()
  const demo = isDemoMode && isDemoSession()
  const canAccess = hasAnyRole(['ADMIN', 'SUPER_ADMIN', 'MARKETING_EXECUTIVE'])
  const [statusFilter, setStatusFilter] = React.useState<PurchaseOrderDto['status'] | 'ALL'>('ALL')
  const [showCreate, setShowCreate] = React.useState(false)
  const [rfqPublicId, setRfqPublicId] = React.useState('')
  const [supplierName, setSupplierName] = React.useState('')
  const [orderDate, setOrderDate] = React.useState('')
  const [expectedDelivery, setExpectedDelivery] = React.useState('')
  const [notes, setNotes] = React.useState('')

  const demoRfqs = useRfqDemoStore((s) => s.rfqs)
  const demoPurchases = useRfqDemoStore((s) => s.purchases)
  const demoCreatePo = useRfqDemoStore((s) => s.createPurchaseOrder)
  const demoUpdatePoStatus = useRfqDemoStore((s) => s.updatePurchaseOrderStatus)

  const purchasesQuery = useQuery({
    queryKey: ['purchases', statusFilter],
    queryFn: () => getPurchaseOrders(0, 50, statusFilter === 'ALL' ? undefined : statusFilter),
    enabled: canAccess && !demo,
    retry: false,
  })

  const purchasableRfqsQuery = useQuery({
    queryKey: ['rfqs', 'purchasable'],
    queryFn: () => getRfqs(0, 50),
    enabled: canAccess && !demo && showCreate,
    retry: false,
  })

  const createMutation = useMutation({
    mutationFn: () =>
      createPurchaseOrder(
        rfqPublicId,
        undefined,
        supplierName.trim() || undefined,
        undefined,
        orderDate || undefined,
        expectedDelivery || undefined,
        notes.trim() || undefined,
      ),
    onSuccess: async () => {
      toast.success('Purchase order created')
      setShowCreate(false)
      setRfqPublicId('')
      setSupplierName('')
      setOrderDate('')
      setExpectedDelivery('')
      setNotes('')
      await queryClient.invalidateQueries({ queryKey: ['purchases'] })
    },
    onError: (err) => toast.error(unexpectedErrorMessage(normalizeError(err))),
  })

  const statusMutation = useMutation({
    mutationFn: (args: { id: number; status: PurchaseOrderDto['status'] }) =>
      updatePurchaseOrderStatus(args.id, args.status),
    onSuccess: async () => {
      toast.success('Purchase order status updated')
      await queryClient.invalidateQueries({ queryKey: ['purchases'] })
    },
    onError: (err) => toast.error(unexpectedErrorMessage(normalizeError(err))),
  })

  if (!canAccess) {
    return (
      <AppShell>
        <Card className="mx-auto max-w-xl p-8 text-center">
          <ShoppingCart className="mx-auto size-8 text-red-brand-600" />
          <h1 className="mt-3 text-xl font-bold text-brand-900">Access restricted</h1>
          <p className="mt-2 text-sm text-slate-500">
            Purchase orders are accessible to Admin, Super Admin, and Marketing users.
          </p>
        </Card>
      </AppShell>
    )
  }

  const purchasableStatuses = new Set([
    'CUSTOMER_APPROVED',
    'READY_FOR_MARKETING',
    'ASSIGNED_TO_MARKETING',
    'CLOSED',
  ])
  const purchasableRfqs = demo
    ? demoRfqs.filter((r) => purchasableStatuses.has(r.status))
    : (purchasableRfqsQuery.data?.content ?? []).filter((r) => purchasableStatuses.has(r.status))

  const rows: PurchaseOrderDto[] = demo
    ? demoPurchases.filter((po) => statusFilter === 'ALL' || po.status === statusFilter)
    : (purchasesQuery.data?.content ?? [])

  async function submitCreate(e: React.FormEvent) {
    e.preventDefault()
    if (!rfqPublicId) {
      toast.error('Choose a customer-approved RFQ.')
      return
    }
    if (demo) {
      demoCreatePo({
        rfqPublicId,
        supplierName: supplierName.trim() || undefined,
        orderDate: orderDate || undefined,
        expectedDelivery: expectedDelivery || undefined,
        notes: notes.trim() || undefined,
        createdBy: user?.id ?? 1,
      })
      toast.success('Purchase order created in demo mode')
      setShowCreate(false)
      setRfqPublicId('')
      setSupplierName('')
      return
    }
    await createMutation.mutateAsync()
  }

  return (
    <AppShell>
      <div className="mb-5 flex flex-wrap items-center justify-between gap-4">
        <h1 className="text-2xl font-bold tracking-tight text-brand-900">Purchases</h1>
        <Button size="sm" onClick={() => setShowCreate((v) => !v)}>
          <Plus className="size-4" /> Create Purchase Order
        </Button>
      </div>

      {showCreate && (
        <Card className="mb-5 border-brand-200 bg-brand-50/40 p-5">
          <h2 className="text-sm font-semibold text-brand-900">
            Create Purchase Order (Customer-Approved RFQs)
          </h2>
          <form onSubmit={submitCreate} className="mt-4 grid gap-4 md:grid-cols-2 lg:grid-cols-4">
            <label className="block text-xs font-semibold text-slate-800 lg:col-span-2">
              Approved RFQ *
              <select
                value={rfqPublicId}
                onChange={(e) => setRfqPublicId(e.target.value)}
                className="mt-1 h-11 w-full rounded-lg border border-input bg-white px-3 text-sm focus-visible:border-brand-900 focus-visible:ring-2 focus-visible:ring-brand-900/20 focus-visible:outline-none"
                required
              >
                <option value="">Select approved RFQ…</option>
                {purchasableRfqs.map((rfq) => (
                  <option key={rfq.id} value={rfq.id}>
                    RFQ-{rfq.sourceRfqId} · {rfq.customerName}
                  </option>
                ))}
              </select>
            </label>
            <label
              htmlFor="po-supplier-name"
              className="block text-xs font-semibold text-slate-800 lg:col-span-2"
            >
              Supplier Name
              <Input
                id="po-supplier-name"
                value={supplierName}
                onChange={(e) => setSupplierName(e.target.value)}
                placeholder="Supplier or distributor"
                className="mt-1 bg-white"
              />
            </label>
            <label htmlFor="po-order-date" className="block text-xs font-semibold text-slate-800">
              Order Date
              <Input
                id="po-order-date"
                type="date"
                value={orderDate}
                onChange={(e) => setOrderDate(e.target.value)}
                className="mt-1 bg-white"
              />
            </label>
            <label htmlFor="po-expected-delivery" className="block text-xs font-semibold text-slate-800">
              Expected Delivery
              <Input
                id="po-expected-delivery"
                type="date"
                value={expectedDelivery}
                onChange={(e) => setExpectedDelivery(e.target.value)}
                className="mt-1 bg-white"
              />
            </label>
            <label htmlFor="po-notes" className="block text-xs font-semibold text-slate-800 lg:col-span-2">
              Notes
              <Input
                id="po-notes"
                value={notes}
                onChange={(e) => setNotes(e.target.value)}
                placeholder="Fulfillment or logistics notes"
                className="mt-1 bg-white"
              />
            </label>
            <div className="flex justify-end gap-2 md:col-span-2 lg:col-span-4">
              <Button type="button" variant="outline" size="sm" onClick={() => setShowCreate(false)}>
                Cancel
              </Button>
              <Button type="submit" size="sm" disabled={createMutation.isPending}>
                {createMutation.isPending ? <Loader2 className="size-4 animate-spin" /> : 'Create Order'}
              </Button>
            </div>
          </form>
        </Card>
      )}

      <div className="mb-4">
        <label className="inline-block text-xs font-semibold text-slate-800">
          Status
          <select
            value={statusFilter}
            onChange={(e) => setStatusFilter(e.target.value as PurchaseOrderDto['status'] | 'ALL')}
            className="mt-1 block h-11 min-w-44 rounded-lg border border-input bg-white px-3 text-sm focus-visible:border-brand-900 focus-visible:ring-2 focus-visible:ring-brand-900/20 focus-visible:outline-none"
          >
            <option value="ALL">ALL</option>
            {poStatuses.map((s) => (
              <option key={s} value={s}>
                {s}
              </option>
            ))}
          </select>
        </label>
      </div>

      <Card className="overflow-hidden">
        {!demo && purchasesQuery.isLoading ? (
          <div className="p-8 text-center text-sm text-slate-500">Loading purchase orders…</div>
        ) : !demo && purchasesQuery.error ? (
          <div className="p-6 text-sm text-red-brand-700" role="alert">
            {unexpectedErrorMessage(normalizeError(purchasesQuery.error))}
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[780px] border-collapse text-left">
              <thead>
                <tr className="border-b border-brand-100 bg-brand-50/70 text-xs font-semibold text-brand-900">
                  <th className="px-4 py-3">RFQ ID</th>
                  <th className="px-4 py-3">Order ID</th>
                  <th className="px-4 py-3">Supplier</th>
                  <th className="px-4 py-3">Status</th>
                  <th className="px-4 py-3">Assignee</th>
                  <th className="px-4 py-3">Order Date</th>
                  <th className="px-4 py-3">Expected Delivery</th>
                  <th className="px-4 py-3 text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-brand-100/70">
                {rows.length === 0 ? (
                  <tr>
                    <td colSpan={8} className="px-4 py-10 text-center text-sm text-slate-500">
                      No purchases found.
                    </td>
                  </tr>
                ) : (
                  rows.map((po) => (
                    <tr key={po.id} className="hover:bg-brand-50/50">
                      <td className="px-4 py-3 text-sm font-semibold">
                        <Link
                          to="/rfqs/$rfqId"
                          params={{ rfqId: po.rfqPublicId }}
                          className="text-brand-900 hover:text-red-brand-600 hover:underline"
                        >
                          {po.sourceRfqId ? `RFQ-${po.sourceRfqId}` : po.rfqPublicId.slice(0, 8)}
                        </Link>
                      </td>
                      <td className="px-4 py-3 text-sm font-medium text-slate-900">{po.orderId}</td>
                      <td className="px-4 py-3 text-sm text-slate-700">{po.supplierName ?? '—'}</td>
                      <td className="px-4 py-3">
                        <span className={`rounded-full px-2 py-0.5 text-[11px] font-semibold ${poBadge[po.status]}`}>
                          {po.status}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-xs text-slate-600">
                        {po.assignedTo ? `User ${po.assignedTo}` : '—'}
                      </td>
                      <td className="px-4 py-3 text-xs whitespace-nowrap text-slate-600">
                        {po.orderDate ?? '—'}
                      </td>
                      <td className="px-4 py-3 text-xs whitespace-nowrap text-slate-600">
                        {po.expectedDelivery ?? '—'}
                      </td>
                      <td className="px-4 py-3 text-right">
                        <select
                          aria-label={`Update status for ${po.orderId}`}
                          value={po.status}
                          disabled={statusMutation.isPending}
                          onChange={(e) => {
                            const nextStatus = e.target.value as PurchaseOrderDto['status']
                            if (demo) demoUpdatePoStatus(po.id, nextStatus)
                            else void statusMutation.mutate({ id: po.id, status: nextStatus })
                          }}
                          className="h-8 rounded-md border border-input bg-white px-2 text-xs focus-visible:border-brand-900 focus-visible:outline-none"
                        >
                          {poStatuses.map((s) => (
                            <option key={s} value={s}>
                              {s}
                            </option>
                          ))}
                        </select>
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        )}
      </Card>
    </AppShell>
  )
}
