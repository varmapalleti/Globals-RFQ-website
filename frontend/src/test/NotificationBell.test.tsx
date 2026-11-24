import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { NotificationBell } from '@/components/layout/NotificationBell'

const navigateMock = vi.fn().mockResolvedValue(undefined)
const markNotificationReadMock = vi.fn<(id: number) => Promise<Record<string, unknown>>>(() =>
  Promise.resolve({}),
)
const markAllNotificationsReadMock = vi.fn<() => Promise<{ markedRead: number }>>(() =>
  Promise.resolve({ markedRead: 1 }),
)

vi.mock('@tanstack/react-router', () => ({
  useNavigate: () => navigateMock,
}))

vi.mock('@/features/auth/demoSession', () => ({
  isDemoSession: () => false,
}))

vi.mock('@/features/rfq/api/rfqApi', () => ({
  getUnreadNotificationCount: vi.fn().mockResolvedValue({ unreadCount: 1 }),
  getNotifications: vi.fn().mockResolvedValue({
    content: [
      {
        id: 101,
        type: 'QUOTATION_SUBMITTED',
        title: 'Quotation V1 submitted',
        message: 'Manager submitted quotation V1 for RFQ-2026-001.',
        rfqPublicId: 'rfq-abc-123',
        read: false,
        createdAt: new Date().toISOString(),
        readAt: null,
      },
    ],
    page: 0,
    size: 15,
    totalElements: 1,
    totalPages: 1,
  }),
  markNotificationRead: (id: number) => markNotificationReadMock(id),
  markAllNotificationsRead: () => markAllNotificationsReadMock(),
}))

describe('NotificationBell', () => {
  it('displays unread count, renders notifications dropdown, and marks items read on click', async () => {
    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    })

    render(
      <QueryClientProvider client={queryClient}>
        <NotificationBell />
      </QueryClientProvider>,
    )

    const bellButton = await screen.findByRole('button', {
      name: /Notifications \(1 unread\)/i,
    })
    fireEvent.click(bellButton)

    const itemButton = await screen.findByText('Quotation V1 submitted')
    expect(screen.getByRole('dialog', { name: 'Notifications' })).toBeInTheDocument()

    const markAllButton = screen.getByRole('button', { name: /Mark all read/i })
    fireEvent.click(markAllButton)
    await waitFor(() => expect(markAllNotificationsReadMock).toHaveBeenCalledTimes(1))

    fireEvent.click(itemButton)
    await waitFor(() => expect(markNotificationReadMock).toHaveBeenCalledWith(101))
    expect(navigateMock).toHaveBeenCalledWith({
      to: '/rfqs/$rfqId',
      params: { rfqId: 'rfq-abc-123' },
    })
  })
})
