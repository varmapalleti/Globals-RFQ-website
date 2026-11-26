import { describe, expect, it } from 'vitest'
import { useRfqDemoStore } from '@/features/rfq/store/rfqDemoStore'

describe('useRfqDemoStore extended workflow transitions', () => {
  it('supports marketing handoff, red flags, purchase orders, and notifications', () => {
    const store = useRfqDemoStore.getState()
    const targetRfq = store.rfqs[0]
    if (!targetRfq) {
      throw new Error('Expected initial demo RFQ to exist')
    }
    const initialNotifications = store.notifications.length

    store.markReadyForMarketing(targetRfq.id, 'Approved by client', 'System Admin')
    let updated = useRfqDemoStore.getState().rfqs.find((r) => r.id === targetRfq.id)
    expect(updated?.status).toBe('READY_FOR_MARKETING')

    store.completeMarketing(targetRfq.id, 'Campaign executed', 'Marketing Exec')
    updated = useRfqDemoStore.getState().rfqs.find((r) => r.id === targetRfq.id)
    expect(updated?.status).toBe('CLOSED')
    expect(useRfqDemoStore.getState().notifications.length).toBeGreaterThan(initialNotifications)

    useRfqDemoStore
      .getState()
      .createRedFlag(targetRfq.id, 'CRITICAL', 'Supplier lead time exceeds SLA', 1, 2)
    const flags = useRfqDemoStore.getState().redFlags
    const createdFlag = flags[0]
    expect(flags).toHaveLength(1)
    if (!createdFlag) throw new Error('Expected created red flag')
    expect(createdFlag.status).toBe('OPEN')
    expect(createdFlag.sourceRfqId).toBe(targetRfq.sourceRfqId)

    useRfqDemoStore
      .getState()
      .resolveRedFlag(createdFlag.id, 'RESOLVE', 'Alternate vendor approved', 1)
    expect(useRfqDemoStore.getState().redFlags[0]?.status).toBe('RESOLVED')

    useRfqDemoStore.getState().createPurchaseOrder({
      rfqPublicId: targetRfq.id,
      supplierName: 'V2X Supply Co.',
      expectedDelivery: '2026-12-15',
      notes: 'Expedited freight',
      createdBy: 1,
    })
    const pos = useRfqDemoStore.getState().purchases
    const createdPo = pos[0]
    expect(pos).toHaveLength(1)
    if (!createdPo) throw new Error('Expected created purchase order')
    expect(createdPo.status).toBe('PENDING')
    expect(createdPo.orderId.startsWith('PO-')).toBe(true)

    useRfqDemoStore.getState().updatePurchaseOrderStatus(createdPo.id, 'ORDERED')
    expect(useRfqDemoStore.getState().purchases[0]?.status).toBe('ORDERED')

    useRfqDemoStore.getState().markAllNotificationsRead()
    expect(useRfqDemoStore.getState().notifications.every((n) => n.read)).toBe(true)
  })
})
