import * as React from 'react'
import { act, fireEvent, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { toast } from 'sonner'
import { useAuthStore } from '@/features/auth/store/authStore'
import type { RoleName } from '@/types/api'
import { server } from '@/test/mocks/server'
import { renderWithProviders } from '@/test/test-utils'
import { CreateRfqPage } from '@/routes/_authenticated.rfqs.create'
import { ImportRfqPage } from '@/routes/_authenticated.rfqs.import'
import { RedFlagsPage } from '@/routes/_authenticated.red-flags'
import { PurchasesPage } from '@/routes/_authenticated.purchases'
import { AssignmentPanel } from '@/features/rfq/components/AssignmentPanel'
import type { RfqRecord } from '@/features/rfq/types'

vi.mock('@tanstack/react-router', async () => {
  const ReactModule = await import('react')
  return {
    createFileRoute: () => () => ({}),
    useNavigate: () => vi.fn(),
    Link: ({ children, to }: { children: React.ReactNode; to?: string }) =>
      ReactModule.createElement('a', { href: to ?? '#' }, children),
  }
})

vi.mock('@/components/layout/AppShell', async () => {
  const ReactModule = await import('react')
  return {
    AppShell: ({ children }: { children: React.ReactNode }) =>
      ReactModule.createElement('div', null, children),
  }
})

vi.mock('sonner', () => ({
  toast: { success: vi.fn(), error: vi.fn(), info: vi.fn(), warning: vi.fn() },
}))

const adminUser = {
  id: 1,
  fullName: 'System Administrator',
  officialEmail: 'admin@company.test',
  officialEmailVerified: true,
  personalEmailVerified: false,
  status: 'ACTIVE' as const,
  mustChangePassword: false,
  roles: ['ADMIN' as RoleName],
}

describe('RFQ pages', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    act(() => useAuthStore.getState().setSession('test-token', 3600, adminUser))
  })

  afterEach(() => {
    act(() => useAuthStore.getState().clearSession())
  })

  it('loads the reviewer directory for a delegated quotation manager and shows reviewer IDs', async () => {
    const manager = {
      ...adminUser,
      id: 2,
      fullName: 'Quotation Manager',
      officialEmail: 'manager@company.test',
      roles: ['QUOTATION_MANAGER' as RoleName],
    }
    act(() => useAuthStore.getState().setSession('manager-token', 3600, manager))
    let requestedRole = ''
    server.use(
      http.get('*/api/users/eligible', ({ request }) => {
        requestedRole = new URL(request.url).searchParams.get('role') ?? ''
        return HttpResponse.json({
          success: true,
          message: 'Eligible users',
          data: [{ id: 12, fullName: 'Reviewer One', officialEmail: 'reviewer@company.test' }],
        })
      }),
    )
    const rfq = {
      id: 'rfq-public-id',
      status: 'QUOTATION_IN_PROGRESS',
      customerDecision: 'PENDING',
      quotationManagerCanAssignReviewer: true,
      assignments: [
        { role: 'QUOTATION_MANAGER', userId: 2, userName: 'Quotation Manager', assignedAt: new Date().toISOString() },
      ],
    } as unknown as RfqRecord

    renderWithProviders(
      <AssignmentPanel
        rfq={rfq}
        demo={false}
        userName="Quotation Manager"
        isAdmin={false}
        isAssignedManager
        invalidate={() => Promise.resolve()}
      />,
    )

    expect(
      await screen.findByRole('option', { name: /Reviewer One \(ID: 12\)/ }),
    ).toBeInTheDocument()
    expect(requestedRole).toBe('REVIEWER')
  })

  it('creates an RFQ from the validated manual-entry form', async () => {
    const user = userEvent.setup()
    let submittedBody: Record<string, unknown> | null = null
    server.use(
      http.post('*/api/rfqs', async ({ request }) => {
        submittedBody = (await request.json()) as Record<string, unknown>
        return HttpResponse.json(
          { success: true, message: 'RFQ created', data: { id: 'created-rfq-id' } },
          { status: 201 },
        )
      }),
    )
    renderWithProviders(<CreateRfqPage />)

    await user.type(screen.getByPlaceholderText('PR Number *'), '112477')
    await user.type(screen.getByPlaceholderText('Name *'), 'V2X Systems Corporation')
    await user.type(screen.getByPlaceholderText('Description'), 'Three phase blower motor')
    const createButton = screen.getByRole('button', { name: 'Create RFQ' })
    await user.click(createButton)

    await waitFor(() => expect(submittedBody).not.toBeNull())
    expect(submittedBody).toMatchObject({ sourceRfqId: '112477', customerName: 'V2X Systems Corporation' })
    expect(submittedBody?.['lineItems']).toHaveLength(1)
    await waitFor(() => expect(toast.success).toHaveBeenCalledWith('RFQ created', expect.any(Object)))
    await waitFor(() => expect(createButton).toBeEnabled())
  })

  it('rejects unsupported source files before attempting import preview', async () => {
    const { container } = renderWithProviders(<ImportRfqPage />)
    const fileInput = container.querySelector('input[type="file"]')
    expect(fileInput).not.toBeNull()
    const unsupportedFile = new File(['payload'], 'request.exe', { type: 'application/octet-stream' })

    fireEvent.change(fileInput as HTMLInputElement, { target: { files: [unsupportedFile] } })

    expect(await screen.findByText(/Supported source files:/)).toBeInTheDocument()
  })

  it('raises a red flag for an RFQ with a required reason', async () => {
    const user = userEvent.setup()
    let submittedBody: Record<string, unknown> | null = null
    server.use(
      http.post('*/api/red-flags', async ({ request }) => {
        submittedBody = (await request.json()) as Record<string, unknown>
        return HttpResponse.json(
          { success: true, message: 'Red flag created', data: { id: 1 } },
          { status: 201 },
        )
      }),
    )
    renderWithProviders(<RedFlagsPage />)

    await user.click(screen.getByRole('button', { name: /Raise Red Flag/i }))
    const rfqSelect = screen.getByLabelText('RFQ *')
    await waitFor(() => expect(within(rfqSelect).getByRole('option', { name: /RFQ-112477/ })).toBeInTheDocument())
    await user.selectOptions(rfqSelect, 'rfq-public-id-100')
    await user.type(screen.getByPlaceholderText(/Describe the blocking issue/i), 'Pricing requires admin review')
    const submitFlagButton = screen.getByRole('button', { name: 'Submit Flag' })
    await user.click(submitFlagButton)

    await waitFor(() => expect(submittedBody).not.toBeNull())
    expect(submittedBody).toMatchObject({ rfqPublicId: 'rfq-public-id-100', severity: 'HIGH' })
    await waitFor(() => expect(toast.success).toHaveBeenCalledWith('Red flag raised'))
    await waitFor(() => expect(submitFlagButton).toBeEnabled())
  })

  it('creates a purchase order only against an approved RFQ', async () => {
    const user = userEvent.setup()
    let submittedBody: Record<string, unknown> | null = null
    server.use(
      http.post('*/api/purchases', async ({ request }) => {
        submittedBody = (await request.json()) as Record<string, unknown>
        return HttpResponse.json(
          {
            success: true,
            message: 'Purchase order created',
            data: { id: 1, orderId: 'PO-1001', status: 'PENDING' },
          },
          { status: 201 },
        )
      }),
    )
    renderWithProviders(<PurchasesPage />)

    await user.click(screen.getByRole('button', { name: /Create Purchase Order/i }))
    const approvedRfq = await screen.findByRole('option', { name: /RFQ-112477/ })
    await user.selectOptions(screen.getByLabelText('Approved RFQ *'), approvedRfq.getAttribute('value') ?? '')
    await user.type(screen.getByPlaceholderText('Supplier or distributor'), 'ACME Supply')
    const createOrderButton = screen.getByRole('button', { name: 'Create Order' })
    await user.click(createOrderButton)

    await waitFor(() => expect(submittedBody).not.toBeNull())
    expect(submittedBody).toMatchObject({ rfqPublicId: 'rfq-public-id-100', supplierName: 'ACME Supply' })
    await waitFor(() => expect(toast.success).toHaveBeenCalledWith('Purchase order created'))
    await waitFor(() => expect(createOrderButton).toBeEnabled())
  })
})
