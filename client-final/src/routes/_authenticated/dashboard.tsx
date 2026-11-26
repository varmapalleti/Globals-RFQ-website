import { createFileRoute } from '@tanstack/react-router'
import { AlertCircle, ClipboardPen, FileText, Flag, ShoppingCart } from 'lucide-react'
import { HomeLayout } from '@/features/home/components/HomeLayout'
import { useAuth } from '@/features/auth/hooks/useAuth'
import { cn } from '@/lib/utils'

export const Route = createFileRoute('/_authenticated/dashboard')({
  component: DashboardPage,
})

// ── Static demo data (UI only — no API calls yet) ───────────────────────────
const STATS = [
  { label: 'Active / In Progress', value: 1, icon: FileText, color: 'text-blue-700' },
  { label: 'Under Review', value: 0, icon: ClipboardPen, color: 'text-orange-500' },
  { label: 'Accepted / Purchase', value: 1, icon: ShoppingCart, color: 'text-green-700' },
  { label: 'Open Red Flags', value: 0, icon: Flag, color: 'text-red-600' },
] as const

type StatusTone = 'orange' | 'blue' | 'green' | 'gray'

const RECENT_RFQS: ReadonlyArray<{
  rfq: string
  pr: string
  customer: string
  status: string
  tone: StatusTone
  assignee: string
  created: string
}> = [
  {
    rfq: 'RFQ-2026-00010',
    pr: '110572',
    customer: 'V2X Systems Corporation',
    status: 'Quotation in progress',
    tone: 'orange',
    assignee: 'Quotation Manager',
    created: 'Aug 20, 2026',
  },
  {
    rfq: 'RFQ-2026-00008',
    pr: '1234039',
    customer: 'V2X Systems Corporation',
    status: 'Awaiting customer decision',
    tone: 'blue',
    assignee: 'Quotation Manager',
    created: 'Aug 20, 2026',
  },
  {
    rfq: 'RFQ-2026-00006',
    pr: '110572',
    customer: 'V2X Systems Corporation',
    status: 'Customer accepted',
    tone: 'green',
    assignee: '',
    created: 'Aug 20, 2026',
  },
  {
    rfq: 'RFQ-2026-00004',
    pr: '110572',
    customer: 'V2X Systems Corporation',
    status: 'Cancelled',
    tone: 'gray',
    assignee: 'Unassigned',
    created: 'Aug 20, 2026',
  },
]

const TONE_CLASSES: Record<StatusTone, string> = {
  orange: 'bg-orange-600 text-white',
  blue: 'bg-sky-600 text-white',
  green: 'bg-green-700 text-white',
  gray: 'bg-slate-200 text-slate-600',
}

function DashboardPage() {
  const { user, mustChangePassword } = useAuth()

  return (
    <HomeLayout>
      {mustChangePassword && (
        <div
          role="alert"
          className="mb-6 flex items-start gap-3 rounded-lg border border-amber-200 bg-amber-50 p-4 text-sm text-amber-800"
        >
          <AlertCircle className="mt-0.5 h-4 w-4 shrink-0 text-amber-500" aria-hidden="true" />
          <div>
            <p className="font-semibold">Password change required</p>
            <p className="mt-0.5">
              Your account requires a password change. The change-password API is not yet available
              — please contact an administrator.
            </p>
          </div>
        </div>
      )}

      <h1 className="text-[28px] leading-tight font-bold text-slate-900">Dashboard</h1>
      <p className="mt-1 text-sm text-slate-500">Welcome back, {user?.fullName ?? 'User'}</p>

      {/* Stat cards */}
      <section aria-label="Summary" className="mt-6 grid gap-5 sm:grid-cols-2 xl:grid-cols-4">
        {STATS.map(({ label, value, icon: Icon, color }) => (
          <div
            key={label}
            className="flex items-start justify-between rounded-xl bg-white p-5 shadow-[0_1px_4px_rgba(0,0,0,0.12)]"
          >
            <div>
              <p className="text-xs text-slate-600">{label}</p>
              <p className="mt-2 text-3xl font-bold text-slate-900">{value}</p>
            </div>
            <Icon className={cn('h-7 w-7', color)} aria-hidden="true" />
          </div>
        ))}
      </section>

      {/* Recent RFQs */}
      <section aria-labelledby="recent-rfqs" className="mt-9">
        <div className="flex items-center justify-between">
          <h2 id="recent-rfqs" className="text-base font-bold text-slate-900">
            Recent RFQs
          </h2>
          <button
            type="button"
            className="rounded-md border border-blue-700 px-3 py-1 text-[11px] font-bold text-blue-800 hover:bg-blue-50"
          >
            View All RFQs
          </button>
        </div>

        <div className="mt-3 overflow-x-auto rounded-xl bg-white shadow-[0_1px_4px_rgba(0,0,0,0.12)]">
          <table className="w-full min-w-[820px] text-left text-xs">
            <thead>
              <tr className="border-b border-slate-200 text-slate-600">
                {['RFQ #', 'PR #', 'Customer / Company', 'Status', 'Assignee', 'Created Date'].map(
                  (h) => (
                    <th key={h} scope="col" className="px-4 py-4 font-medium">
                      {h}
                    </th>
                  ),
                )}
              </tr>
            </thead>
            <tbody>
              {RECENT_RFQS.map((r) => (
                <tr key={r.rfq} className="border-b border-slate-200 text-slate-700 last:border-0">
                  <td className="px-4 py-4">{r.rfq}</td>
                  <td className="px-4 py-4">{r.pr}</td>
                  <td className="px-4 py-4">{r.customer}</td>
                  <td className="px-4 py-4">
                    <span
                      className={cn(
                        'inline-block rounded-full px-2.5 py-1 text-[10px] font-bold tracking-wide uppercase',
                        TONE_CLASSES[r.tone],
                      )}
                    >
                      {r.status}
                    </span>
                  </td>
                  <td className="px-4 py-4">{r.assignee}</td>
                  <td className="px-4 py-4">{r.created}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>
    </HomeLayout>
  )
}
