import { useQuery } from '@tanstack/react-query'
import { Loader2, UserRound } from 'lucide-react'
import * as React from 'react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { getEligibleUsers } from '@/features/auth/api/userAdminApi'
import { setReviewerDelegation, assignRfq } from '@/features/rfq/api/rfqApi'
import { useRfqDemoStore } from '@/features/rfq/store/rfqDemoStore'
import { demoUsers, type RfqAssignmentRole, type RfqRecord } from '@/features/rfq/types'
import { normalizeError, unexpectedErrorMessage } from '@/lib/errors'

export function AssignmentPanel({
  rfq,
  demo,
  userName,
  isAdmin,
  isAssignedManager,
  invalidate,
}: {
  rfq: RfqRecord
  demo: boolean
  userName: string
  isAdmin: boolean
  isAssignedManager: boolean
  invalidate: () => Promise<void>
}) {
  const assign = useRfqDemoStore((state) => state.assign)
  const setDelegation = useRfqDemoStore((state) => state.setReviewerDelegation)
  const [assignmentRole, setAssignmentRole] = React.useState<RfqAssignmentRole>('QUOTATION_MANAGER')
  const [assignedUserId, setAssignedUserId] = React.useState('')
  const [manualUserIdEntry, setManualUserIdEntry] = React.useState(false)
  const [reason, setReason] = React.useState('')
  const [busy, setBusy] = React.useState(false)
  const [delegationReason, setDelegationReason] = React.useState(
    'Client delegation approved for this RFQ',
  )
  const canAssignReviewer = isAdmin || (isAssignedManager && rfq.quotationManagerCanAssignReviewer)
  // Managers may only assign reviewers, regardless of the Admin's last selected role.
  const selectedRole: RfqAssignmentRole = isAdmin ? assignmentRole : 'REVIEWER'
  const eligibleUsersQuery = useQuery({
    queryKey: ['identity', 'eligible-users', selectedRole],
    queryFn: () => getEligibleUsers(selectedRole),
    enabled: !demo && (isAdmin || canAssignReviewer),
    staleTime: 60_000,
  })
  const current = rfq.assignments.find((assignment) => assignment.role === selectedRole)

  async function assignUser(event: React.FormEvent) {
    event.preventDefault()
    const parsedId = Number(assignedUserId)
    if (!Number.isInteger(parsedId) || parsedId <= 0) {
      toast.error('Select a valid assignee.')
      return
    }
    if (current && reason.trim().length < 5) {
      toast.error('Enter a reason for reassignment (at least 5 characters).')
      return
    }
    const demoUser = demoUsers.find((candidate) => candidate.id === parsedId)
    setBusy(true)
    try {
      if (demo)
        assign(
          rfq.id,
          selectedRole,
          parsedId,
          demoUser?.fullName ?? `User ${parsedId}`,
          reason.trim(),
          userName,
        )
      else await assignRfq(rfq.id, selectedRole, parsedId, reason.trim())
      toast.success(`${selectedRole.replaceAll('_', ' ').toLowerCase()} assigned.`)
      setAssignedUserId('')
      setReason('')
      await invalidate()
    } catch (error) {
      toast.error(unexpectedErrorMessage(normalizeError(error)))
    } finally {
      setBusy(false)
    }
  }

  async function toggleDelegation(allowed: boolean) {
    if (delegationReason.trim().length < 5) {
      toast.error('Enter a reason for the permission change.')
      return
    }
    try {
      if (demo) setDelegation(rfq.id, allowed, delegationReason, userName)
      else await setReviewerDelegation(rfq.id, allowed, delegationReason)
      toast.success(
        allowed
          ? 'Reviewer assignment delegation enabled.'
          : 'Reviewer assignment delegation disabled.',
      )
      await invalidate()
    } catch (error) {
      toast.error(unexpectedErrorMessage(normalizeError(error)))
    }
  }

  if (!isAdmin && !canAssignReviewer)
    return (
      <Card className="p-4">
        <h2 className="text-sm font-semibold text-brand-900">Current ownership</h2>
        <div className="mt-3 space-y-3">
          {(['QUOTATION_MANAGER', 'REVIEWER', 'MARKETING_EXECUTIVE'] as const).map((key) => {
            const assignment = rfq.assignments.find((item) => item.role === key)
            return (
              <div key={key} className="flex items-center justify-between gap-3 text-xs">
                <span className="text-slate-500">{key.replaceAll('_', ' ')}</span>
                <span className="font-medium text-slate-800">
                  {assignment?.userName ??
                    (assignment ? `User ${assignment.userId}` : 'Unassigned')}
                </span>
              </div>
            )
          })}
        </div>
      </Card>
    )

  const availableRoles: RfqAssignmentRole[] = isAdmin
    ? [
        'QUOTATION_MANAGER',
        'REVIEWER',
        ...(rfq.customerDecision === 'APPROVED' ||
        rfq.status === 'READY_FOR_MARKETING' ||
        rfq.status === 'ASSIGNED_TO_MARKETING'
          ? ['MARKETING_EXECUTIVE' as const]
          : []),
      ]
    : ['REVIEWER']
  const availableUsers =
    selectedRole === 'QUOTATION_MANAGER'
      ? demoUsers.filter((candidate) => candidate.roles.includes('QUOTATION_MANAGER'))
      : selectedRole === 'REVIEWER'
        ? demoUsers.filter((candidate) => candidate.roles.includes('REVIEWER'))
        : demoUsers.filter((candidate) => candidate.roles.includes('MARKETING_EXECUTIVE'))
  return (
    <Card className="p-4">
      <div className="flex items-start justify-between gap-2">
        <div>
          <h2 className="text-sm font-semibold text-brand-900">Assignments</h2>
          <p className="mt-1 text-xs text-slate-500">
            {isAdmin
              ? 'Controlled routing and reassignment.'
              : 'Assign the reviewer only when delegated.'}
          </p>
        </div>
        <UserRound className="size-4 text-brand-900" />
      </div>
      <div className="mt-3 space-y-2">
        {(['QUOTATION_MANAGER', 'REVIEWER', 'MARKETING_EXECUTIVE'] as const).map((key) => {
          const assignment = rfq.assignments.find((item) => item.role === key)
          return (
            <div
              key={key}
              className="flex items-center justify-between gap-3 rounded-md bg-brand-50/60 px-3 py-2 text-xs"
            >
              <span className="text-slate-500">{key.replaceAll('_', ' ')}</span>
              <span className="font-semibold text-slate-800">
                {assignment?.userName ?? (assignment ? `User ${assignment.userId}` : 'Unassigned')}
              </span>
            </div>
          )
        })}
      </div>
      <form onSubmit={assignUser} className="mt-4 space-y-3 border-t border-brand-100 pt-4">
        <label className="block text-xs font-semibold text-slate-800">
          Role
          <select
            value={selectedRole}
            onChange={(event) => {
              setAssignmentRole(event.target.value as RfqAssignmentRole)
              setAssignedUserId('')
            }}
            className="mt-1.5 h-9 w-full rounded-lg border border-input bg-white px-2.5 text-sm focus-visible:border-brand-900 focus-visible:ring-2 focus-visible:ring-brand-900/20 focus-visible:outline-none"
          >
            {availableRoles.map((value) => (
              <option key={value} value={value}>
                {value.replaceAll('_', ' ')}
              </option>
            ))}
          </select>
        </label>
        {demo ? (
          <label className="block text-xs font-semibold text-slate-800">
            Assign to
            <select
              required
              value={assignedUserId}
              onChange={(event) => setAssignedUserId(event.target.value)}
              className="mt-1.5 h-9 w-full rounded-lg border border-input bg-white px-2.5 text-sm focus-visible:border-brand-900 focus-visible:ring-2 focus-visible:ring-brand-900/20 focus-visible:outline-none"
            >
              <option value="">Choose a user</option>
              {availableUsers.map((candidate) => (
                <option key={candidate.id} value={candidate.id}>
                  {candidate.fullName}
                </option>
              ))}
            </select>
          </label>
        ) : manualUserIdEntry ||
          eligibleUsersQuery.isError ||
          (eligibleUsersQuery.data && eligibleUsersQuery.data.length === 0) ? (
          <div className="space-y-1.5">
            {eligibleUsersQuery.isError && (
              <div className="rounded-md border border-amber-200 bg-amber-50 p-2.5 text-xs leading-5 text-amber-950" role="alert">
                <p>
                  The {selectedRole.replaceAll('_', ' ').toLowerCase()} directory could not be loaded:{' '}
                  {unexpectedErrorMessage(normalizeError(eligibleUsersQuery.error))}
                </p>
                <button
                  type="button"
                  onClick={() => void eligibleUsersQuery.refetch()}
                  className="mt-1 font-semibold text-brand-900 underline underline-offset-2"
                >
                  Retry loading users
                </button>
              </div>
            )}
            {eligibleUsersQuery.data?.length === 0 && (
              <p className="rounded-md bg-brand-50/60 p-2.5 text-xs text-slate-600" role="status">
                No active users currently have the {selectedRole.replaceAll('_', ' ')} role.
              </p>
            )}
            <div className="flex items-center justify-between">
              <label htmlFor="identity-user-id" className="text-xs font-semibold text-slate-800">
                User ID
              </label>
              {eligibleUsersQuery.data && eligibleUsersQuery.data.length > 0 && (
                <button
                  type="button"
                  onClick={() => {
                    setManualUserIdEntry(false)
                    setAssignedUserId('')
                  }}
                  className="text-[11px] font-semibold text-red-brand-600 hover:underline"
                >
                  Choose from directory
                </button>
              )}
            </div>
            <Input
              id="identity-user-id"
              className="h-9"
              type="number"
              min="1"
              value={assignedUserId}
              onChange={(event) => setAssignedUserId(event.target.value)}
              placeholder="Enter identity user ID"
              required
            />
          </div>
        ) : (
          <div className="space-y-1.5">
            <div className="flex items-center justify-between">
              <label htmlFor="eligible-assignee-select" className="text-xs font-semibold text-slate-800">
                Assign to
              </label>
              <button
                type="button"
                onClick={() => {
                  setManualUserIdEntry(true)
                  setAssignedUserId('')
                }}
                className="text-[11px] font-semibold text-red-brand-600 hover:underline"
              >
                Enter ID manually
              </button>
            </div>
            <select
              id="eligible-assignee-select"
              required
              disabled={eligibleUsersQuery.isLoading}
              value={assignedUserId}
              onChange={(event) => setAssignedUserId(event.target.value)}
              className="h-9 w-full rounded-lg border border-input bg-white px-2.5 text-sm focus-visible:border-brand-900 focus-visible:ring-2 focus-visible:ring-brand-900/20 focus-visible:outline-none"
            >
              <option value="">
                {eligibleUsersQuery.isLoading
                  ? 'Loading eligible users…'
                  : 'Choose an active user'}
              </option>
              {(eligibleUsersQuery.data ?? []).map((candidate) => (
                <option key={candidate.id} value={candidate.id}>
                  {candidate.fullName} (ID: {candidate.id}) — {candidate.officialEmail}
                </option>
              ))}
            </select>
          </div>
        )}
        {current && (
          <label className="block text-xs font-semibold text-slate-800">
            Reassignment reason *
            <textarea
              value={reason}
              onChange={(event) => setReason(event.target.value)}
              maxLength={1000}
              rows={2}
              required
              className="mt-1.5 w-full rounded-lg border border-input bg-white px-2.5 py-2 text-sm focus-visible:border-brand-900 focus-visible:ring-2 focus-visible:ring-brand-900/20 focus-visible:outline-none"
              placeholder="Explain why ownership is changing"
            />
          </label>
        )}
        <Button type="submit" size="sm" className="w-full" disabled={busy || !assignedUserId}>
          {busy ? <Loader2 className="size-4 animate-spin" /> : <UserRound className="size-4" />}
          {current ? 'Reassign' : 'Assign'}
        </Button>
      </form>
      {isAdmin && (
        <div className="mt-4 border-t border-brand-100 pt-4">
          <label className="block text-xs font-semibold text-slate-800">
            Manager reviewer delegation reason
            <textarea
              value={delegationReason}
              onChange={(event) => setDelegationReason(event.target.value)}
              maxLength={1000}
              rows={2}
              className="mt-1.5 w-full rounded-lg border border-input bg-white px-2.5 py-2 text-sm focus-visible:border-brand-900 focus-visible:ring-2 focus-visible:ring-brand-900/20 focus-visible:outline-none"
            />
          </label>
          <Button
            size="sm"
            variant="outline"
            className="mt-2 w-full"
            onClick={() => void toggleDelegation(!rfq.quotationManagerCanAssignReviewer)}
          >
            {rfq.quotationManagerCanAssignReviewer
              ? 'Disable manager reviewer assignment'
              : 'Allow manager to assign reviewer'}
          </Button>
          <p className="mt-2 text-[11px] leading-4 text-slate-500">
            Current setting:{' '}
            {rfq.quotationManagerCanAssignReviewer ? 'enabled' : 'admin controlled'}
          </p>
        </div>
      )}
    </Card>
  )
}
