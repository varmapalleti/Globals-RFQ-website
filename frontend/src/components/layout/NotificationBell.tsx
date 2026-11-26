import * as React from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from '@tanstack/react-router'
import { Bell, CheckCheck } from 'lucide-react'
import { Button } from '@/components/ui/button'
import {
  getNotifications,
  getUnreadNotificationCount,
  markAllNotificationsRead,
  markNotificationRead,
  type NotificationDto,
} from '@/features/rfq/api/rfqApi'
import { isDemoSession } from '@/features/auth/demoSession'
import { useRfqDemoStore } from '@/features/rfq/store/rfqDemoStore'
import { isDemoMode } from '@/lib/env'

function formatTimeAgo(iso: string) {
  const diffSec = Math.max(0, Math.floor((Date.now() - new Date(iso).getTime()) / 1000))
  if (diffSec < 60) return 'Just now'
  const diffMin = Math.floor(diffSec / 60)
  if (diffMin < 60) return `${diffMin}m ago`
  const diffHr = Math.floor(diffMin / 60)
  if (diffHr < 24) return `${diffHr}h ago`
  return new Intl.DateTimeFormat('en-IN', { day: '2-digit', month: 'short' }).format(new Date(iso))
}

export function NotificationBell() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const demo = isDemoMode && isDemoSession()
  const [open, setOpen] = React.useState(false)
  const containerRef = React.useRef<HTMLDivElement>(null)

  const demoNotifications = useRfqDemoStore((s) => s.notifications)
  const demoMarkRead = useRfqDemoStore((s) => s.markNotificationRead)
  const demoMarkAllRead = useRfqDemoStore((s) => s.markAllNotificationsRead)

  const unreadQuery = useQuery({
    queryKey: ['notifications', 'unread-count'],
    queryFn: getUnreadNotificationCount,
    enabled: !demo,
    refetchInterval: 30_000,
    retry: false,
  })

  const listQuery = useQuery({
    queryKey: ['notifications', 'list'],
    queryFn: () => getNotifications(0, 15),
    enabled: !demo && open,
    retry: false,
  })

  const markOneMutation = useMutation({
    mutationFn: markNotificationRead,
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['notifications'] })
    },
  })

  const markAllMutation = useMutation({
    mutationFn: markAllNotificationsRead,
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['notifications'] })
    },
  })

  React.useEffect(() => {
    if (!open) return
    function onPointerDown(event: MouseEvent) {
      if (containerRef.current && !containerRef.current.contains(event.target as Node)) {
        setOpen(false)
      }
    }
    function onKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape') setOpen(false)
    }
    document.addEventListener('mousedown', onPointerDown)
    document.addEventListener('keydown', onKeyDown)
    return () => {
      document.removeEventListener('mousedown', onPointerDown)
      document.removeEventListener('keydown', onKeyDown)
    }
  }, [open])

  const items: NotificationDto[] = demo ? demoNotifications : (listQuery.data?.content ?? [])
  const unreadCount = demo
    ? demoNotifications.filter((n) => !n.read).length
    : (unreadQuery.data?.unreadCount ?? 0)

  async function handleItemClick(item: NotificationDto) {
    if (!item.read) {
      if (demo) demoMarkRead(item.id)
      else await markOneMutation.mutateAsync(item.id)
    }
    setOpen(false)
    if (item.rfqPublicId) {
      await navigate({ to: '/rfqs/$rfqId', params: { rfqId: item.rfqPublicId } })
    }
  }

  async function handleMarkAllRead() {
    if (demo) demoMarkAllRead()
    else await markAllMutation.mutateAsync()
  }

  return (
    <div ref={containerRef} className="relative">
      <Button
        variant="ghost"
        size="icon"
        aria-label={unreadCount > 0 ? `Notifications (${unreadCount} unread)` : 'Notifications'}
        aria-expanded={open}
        onClick={() => setOpen((prev) => !prev)}
        className="relative text-slate-700"
      >
        <Bell className="size-5" aria-hidden="true" />
        {unreadCount > 0 && (
          <span className="absolute -top-0.5 -right-0.5 flex h-4 min-w-4 items-center justify-center rounded-full bg-red-600 px-1 text-[10px] font-bold text-white">
            {unreadCount > 99 ? '99+' : unreadCount}
          </span>
        )}
      </Button>

      {open && (
        <div
          role="dialog"
          aria-label="Notifications"
          className="absolute right-0 z-50 mt-2 w-80 overflow-hidden rounded-xl border border-slate-200 bg-white shadow-lg sm:w-96"
        >
          <div className="flex items-center justify-between border-b border-slate-100 px-4 py-3">
            <div>
              <h2 className="text-sm font-semibold text-slate-900">Notifications</h2>
              <p className="text-[11px] text-slate-500">
                {unreadCount > 0 ? `${unreadCount} unread` : 'All caught up'}
              </p>
            </div>
            {unreadCount > 0 && (
              <Button
                type="button"
                variant="ghost"
                size="sm"
                disabled={markAllMutation.isPending}
                onClick={() => void handleMarkAllRead()}
                className="h-7 px-2 text-xs text-blue-700 hover:text-blue-800"
              >
                <CheckCheck className="size-3.5" />
                Mark all read
              </Button>
            )}
          </div>

          <div className="max-h-96 overflow-y-auto divide-y divide-slate-100">
            {!demo && listQuery.isLoading ? (
              <p className="px-4 py-8 text-center text-xs text-slate-500">
                Loading notifications…
              </p>
            ) : items.length === 0 ? (
              <p className="px-4 py-8 text-center text-xs text-slate-500">
                No notifications yet. Workflow updates will appear here.
              </p>
            ) : (
              items.map((item) => (
                <button
                  key={item.id}
                  type="button"
                  onClick={() => void handleItemClick(item)}
                  className={`flex w-full items-start gap-3 px-4 py-3 text-left transition hover:bg-slate-50 ${
                    item.read ? 'bg-white' : 'bg-blue-50/40'
                  }`}
                >
                  <span
                    className={`mt-1.5 size-2 shrink-0 rounded-full ${
                      item.read ? 'bg-transparent' : 'bg-blue-600'
                    }`}
                  />
                  <div className="min-w-0 flex-1">
                    <div className="flex items-start justify-between gap-2">
                      <p
                        className={`text-xs leading-4 ${
                          item.read ? 'font-medium text-slate-700' : 'font-semibold text-slate-900'
                        }`}
                      >
                        {item.title}
                      </p>
                      <span className="shrink-0 text-[10px] text-slate-400">
                        {formatTimeAgo(item.createdAt)}
                      </span>
                    </div>
                    <p className="mt-1 line-clamp-2 text-xs leading-4 text-slate-500">
                      {item.message}
                    </p>
                  </div>
                </button>
              ))
            )}
          </div>
        </div>
      )}
    </div>
  )
}
