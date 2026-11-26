import { createFileRoute } from '@tanstack/react-router'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { zodResolver } from '@hookform/resolvers/zod'
import {
  ChevronLeft,
  ChevronRight,
  KeyRound,
  Loader2,
  LockKeyhole,
  Mail,
  RefreshCw,
  Search,
  ShieldCheck,
  UnlockKeyhole,
  UserPlus,
  UserRoundCog,
} from 'lucide-react'
import * as React from 'react'
import { useForm } from 'react-hook-form'
import { z } from 'zod'
import { toast } from 'sonner'
import { AppShell } from '@/components/layout/AppShell'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { useAuth } from '@/features/auth/hooks/useAuth'
import {
  createIdentityUser,
  freezeIdentityUser,
  getAssignableRoles,
  getIdentityAudit,
  getIdentityUsers,
  replaceIdentityUserRoles,
  resendIdentityInvite,
  unlockIdentityUser,
  unfreezeIdentityUser,
  updateIdentityUser,
  type IdentityRoleOption,
  type IdentityUser,
} from '@/features/auth/api/userAdminApi'
import { demoUsers, roleTitles } from '@/features/rfq/types'
import { isDemoSession } from '@/features/auth/demoSession'
import { isDemoMode } from '@/lib/env'
import { normalizeError, unexpectedErrorMessage } from '@/lib/errors'
import type { RoleName, UserStatus } from '@/types/api'

export const Route = createFileRoute('/_authenticated/users')({ component: UserManagementPage })

const userSchema = z
  .object({
    fullName: z.string().trim().min(2, 'Enter at least 2 characters.').max(150),
    officialEmail: z.string().trim().email('Enter a valid work email.').max(190),
    personalEmail: z.string().trim().email('Enter a valid personal email.').max(190),
    phone: z.string().trim().max(25).optional(),
    roles: z
      .array(
        z.enum([
          'SUPER_ADMIN',
          'ADMIN',
          'QUOTATION_MANAGER',
          'REVIEWER',
          'MARKETING_EXECUTIVE',
          'VIEWER',
        ]),
      )
      .min(1, 'Assign at least one role.'),
  })
  .refine((values) => values.officialEmail.toLowerCase() !== values.personalEmail.toLowerCase(), {
    message: 'Work and personal email addresses must be different.',
    path: ['personalEmail'],
  })
type UserForm = z.infer<typeof userSchema>

const roleNames: RoleName[] = [
  'SUPER_ADMIN',
  'ADMIN',
  'QUOTATION_MANAGER',
  'REVIEWER',
  'MARKETING_EXECUTIVE',
  'VIEWER',
]
const statusText: Record<UserStatus, string> = {
  ACTIVE: 'Active',
  PENDING_VERIFICATION: 'Invitation pending',
  LOCKED: 'Locked',
  DISABLED: 'Frozen',
}
const statusStyle: Record<UserStatus, string> = {
  ACTIVE: 'bg-emerald-50 text-emerald-800 ring-emerald-200',
  PENDING_VERIFICATION: 'bg-amber-50 text-amber-800 ring-amber-200',
  LOCKED: 'bg-orange-50 text-orange-800 ring-orange-200',
  DISABLED: 'bg-red-50 text-red-800 ring-red-200',
}

function roleRank(role: RoleName) {
  return role === 'SUPER_ADMIN' ? 1 : role === 'ADMIN' ? 2 : 3
}

function toDemoUser(user: (typeof demoUsers)[number]): IdentityUser {
  const now = new Date().toISOString()
  return {
    ...user,
    officialEmailVerified: true,
    personalEmail: `${user.officialEmail.split('@')[0]}+personal@example.test`,
    personalEmailVerified: true,
    phone: null,
    status: 'ACTIVE',
    statusReason: null,
    statusChangedBy: null,
    statusChangedAt: null,
    mustChangePassword: false,
    createdAt: now,
    updatedAt: now,
  }
}

function UserManagementPage() {
  const { hasAnyRole } = useAuth()
  const queryClient = useQueryClient()
  const isAdmin = hasAnyRole(['ADMIN', 'SUPER_ADMIN'])
  const isSuperAdmin = hasAnyRole(['SUPER_ADMIN'])
  const demo = isDemoMode && isDemoSession()
  const actorRole = isSuperAdmin ? 'SUPER_ADMIN' : 'ADMIN'
  const [search, setSearch] = React.useState('')
  const [debouncedSearch, setDebouncedSearch] = React.useState('')
  const [roleFilter, setRoleFilter] = React.useState<RoleName | ''>('')
  const [statusFilter, setStatusFilter] = React.useState<UserStatus | ''>('')
  const [page, setPage] = React.useState(0)
  const [pageSize, setPageSize] = React.useState(10)
  const [selectedId, setSelectedId] = React.useState<number | null>(null)
  const [demoRecords, setDemoRecords] = React.useState(() => demoUsers.map(toDemoUser))
  const [freezeReason, setFreezeReason] = React.useState('')
  const [showFreezeForm, setShowFreezeForm] = React.useState(false)
  const [busy, setBusy] = React.useState(false)

  React.useEffect(() => {
    const timer = window.setTimeout(() => setDebouncedSearch(search.trim()), 250)
    return () => window.clearTimeout(timer)
  }, [search])

  React.useEffect(() => {
    setPage(0)
  }, [debouncedSearch, roleFilter, statusFilter, pageSize])

  const rolesQuery = useQuery({
    queryKey: ['identity', 'assignable-roles'],
    queryFn: getAssignableRoles,
    enabled: isAdmin && !demo,
  })
  const usersQuery = useQuery({
    queryKey: ['identity', 'users', debouncedSearch, roleFilter, statusFilter, page, pageSize],
    queryFn: () =>
      getIdentityUsers({
        search: debouncedSearch || undefined,
        role: roleFilter,
        status: statusFilter,
        page,
        size: pageSize,
      }),
    enabled: isAdmin && !demo,
  })
  const auditQuery = useQuery({
    queryKey: ['identity', 'audit', 0, 10],
    queryFn: () => getIdentityAudit({ page: 0, size: 10 }),
    enabled: isSuperAdmin && !demo,
  })

  const availableRoles: IdentityRoleOption[] = demo
    ? roleNames
        .filter(
          (role) => role !== 'SUPER_ADMIN' && (actorRole === 'SUPER_ADMIN' || role !== 'ADMIN'),
        )
        .map((name) => ({ name, description: `Identity-managed ${roleTitles[name]} access` }))
    : (rolesQuery.data ?? [])
  const actorRank = actorRole === 'SUPER_ADMIN' ? 1 : 2
  const visibleDemoUsers = demoRecords.filter((candidate) => {
    const targetRank = Math.min(...candidate.roles.map(roleRank))
    const searchText =
      `${candidate.fullName} ${candidate.officialEmail} ${candidate.roles.join(' ')}`.toLowerCase()
    return (
      targetRank > actorRank &&
      searchText.includes(search.trim().toLowerCase()) &&
      (!roleFilter || candidate.roles.includes(roleFilter)) &&
      (!statusFilter || candidate.status === statusFilter)
    )
  })
  const rows = demo
    ? visibleDemoUsers.slice(page * pageSize, (page + 1) * pageSize)
    : (usersQuery.data?.content ?? [])
  const totalElements = demo ? visibleDemoUsers.length : (usersQuery.data?.totalElements ?? 0)
  const totalPages = demo
    ? Math.max(1, Math.ceil(totalElements / pageSize))
    : (usersQuery.data?.totalPages ?? 0)
  const selectedUser =
    rows.find((candidate) => candidate.id === selectedId) ??
    (demo ? demoRecords.find((candidate) => candidate.id === selectedId) : undefined)

  const createForm = useForm<UserForm>({
    resolver: zodResolver(userSchema),
    defaultValues: { fullName: '', officialEmail: '', personalEmail: '', phone: '', roles: [] },
  })
  const createMutation = useMutation({
    mutationFn: createIdentityUser,
    onSuccess: async () => {
      toast.success('User invitation created', {
        description: 'Identity Service is sending the invitation.',
      })
      createForm.reset()
      await queryClient.invalidateQueries({ queryKey: ['identity', 'users'] })
    },
    onError: (error) => toast.error(unexpectedErrorMessage(normalizeError(error))),
  })

  React.useEffect(() => setPage(0), [search, roleFilter, statusFilter])
  React.useEffect(() => {
    setShowFreezeForm(false)
    setFreezeReason('')
  }, [selectedUser?.id])

  async function submitCreate(values: UserForm) {
    if (
      !availableRoles.length ||
      values.roles.some((role) => !availableRoles.some((option) => option.name === role))
    ) {
      toast.error('Choose only roles authorized for your account.')
      return
    }
    if (!demo) {
      try {
        await createMutation.mutateAsync({ ...values, phone: values.phone || undefined })
      } catch {
        /* Service error is shown by the mutation handler. */
      }
      return
    }
    const now = new Date().toISOString()
    const next: IdentityUser = {
      id: Math.max(0, ...demoRecords.map((candidate) => candidate.id)) + 1,
      fullName: values.fullName,
      officialEmail: values.officialEmail,
      personalEmail: values.personalEmail,
      phone: values.phone || null,
      officialEmailVerified: false,
      personalEmailVerified: false,
      status: 'PENDING_VERIFICATION',
      statusReason: null,
      statusChangedBy: null,
      statusChangedAt: null,
      mustChangePassword: true,
      roles: values.roles,
      createdAt: now,
      updatedAt: now,
    }
    setDemoRecords((current) => [next, ...current])
    createForm.reset()
    toast.success('Demo invitation created', {
      description: 'Sample action only; no email was sent.',
    })
  }

  async function saveUserAccess(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!selectedUser) return
    const form = new FormData(event.currentTarget)
    const textValue = (key: string) => {
      const value = form.get(key)
      return typeof value === 'string' ? value.trim() : ''
    }
    const fullName = textValue('fullName')
    const phone = textValue('phone')
    const personalEmail = textValue('personalEmail')
    const roles = form.getAll('editRole').map(String) as RoleName[]
    if (fullName.length < 2 || fullName.length > 150)
      return toast.error('Name must contain 2–150 characters.')
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(personalEmail) || personalEmail.length > 190)
      return toast.error('Enter a valid personal email address.')
    if (personalEmail.toLowerCase() === selectedUser.officialEmail.toLowerCase())
      return toast.error('Work and personal email addresses must be different.')
    if (
      !roles.length ||
      roles.some((role) => !availableRoles.some((option) => option.name === role))
    )
      return toast.error('Select at least one role authorized for your account.')
    const patch = {
      ...(fullName !== selectedUser.fullName ? { fullName } : {}),
      ...(phone !== (selectedUser.phone ?? '') ? { phone } : {}),
      ...(personalEmail !== selectedUser.personalEmail ? { personalEmail } : {}),
    }
    const rolesChanged =
      roles.slice().sort().join(',') !== selectedUser.roles.slice().sort().join(',')
    if (!Object.keys(patch).length && !rolesChanged)
      return toast.info('There are no changes to save.')
    setBusy(true)
    try {
      if (demo) {
        setDemoRecords((current) =>
          current.map((candidate) =>
            candidate.id === selectedUser.id
              ? {
                  ...candidate,
                  fullName,
                  phone: phone || null,
                  personalEmail,
                  roles,
                  updatedAt: new Date().toISOString(),
                }
              : candidate,
          ),
        )
      } else {
        if (Object.keys(patch).length) await updateIdentityUser(selectedUser.id, patch)
        if (rolesChanged) await replaceIdentityUserRoles(selectedUser.id, roles)
        await queryClient.invalidateQueries({ queryKey: ['identity', 'users'] })
      }
      toast.success('Profile and role access updated.')
    } catch (error) {
      toast.error(unexpectedErrorMessage(normalizeError(error)))
    } finally {
      setBusy(false)
    }
  }

  async function accountAction(action: 'freeze' | 'unfreeze' | 'unlock' | 'resend') {
    if (!selectedUser) return
    if (action === 'freeze' && freezeReason.trim().length < 5)
      return toast.error('Enter a reason of at least 5 characters.')
    setBusy(true)
    try {
      if (demo) {
        const status: UserStatus =
          action === 'freeze'
            ? 'DISABLED'
            : action === 'unfreeze' || action === 'unlock'
              ? 'ACTIVE'
              : selectedUser.status
        setDemoRecords((current) =>
          current.map((candidate) =>
            candidate.id === selectedUser.id
              ? {
                  ...candidate,
                  status,
                  statusReason: action === 'freeze' ? freezeReason.trim() : null,
                }
              : candidate,
          ),
        )
        toast.success(
          action === 'resend'
            ? 'Demo resend simulated; no email was sent.'
            : `Demo account ${action} simulated.`,
        )
      } else {
        if (action === 'freeze') await freezeIdentityUser(selectedUser.id, freezeReason.trim())
        if (action === 'unfreeze') await unfreezeIdentityUser(selectedUser.id)
        if (action === 'unlock') await unlockIdentityUser(selectedUser.id)
        if (action === 'resend') await resendIdentityInvite(selectedUser.id)
        await queryClient.invalidateQueries({ queryKey: ['identity', 'users'] })
        toast.success(action === 'resend' ? 'Invitation resent.' : `Account ${action} completed.`)
      }
      setShowFreezeForm(false)
      setFreezeReason('')
    } catch (error) {
      toast.error(unexpectedErrorMessage(normalizeError(error)))
    } finally {
      setBusy(false)
    }
  }

  if (!isAdmin)
    return (
      <AppShell>
        <AccessDenied />
      </AppShell>
    )

  return (
    <AppShell>
      <div className="mb-6 flex flex-wrap items-end justify-between gap-4">
        <div>
          <p className="text-xs font-semibold tracking-[0.15em] text-blue-700 uppercase">
            Identity administration
          </p>
          <h1 className="mt-1 text-2xl font-bold tracking-tight text-slate-950">Users and roles</h1>
          <p className="mt-1.5 max-w-3xl text-sm text-slate-500">
            Invite team members and assign roles that your Identity account is authorized to
            delegate.
          </p>
        </div>
        <Button
          variant="outline"
          size="sm"
          onClick={() => void queryClient.invalidateQueries({ queryKey: ['identity'] })}
        >
          <RefreshCw className="size-4" />
          Refresh
        </Button>
      </div>
      {demo && (
        <div className="mb-5 rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-950">
          Demo access is local and simulated. Changes here do not call Identity Service or send
          invitations.
        </div>
      )}

      <div className="grid items-start gap-5 xl:grid-cols-[minmax(0,1.15fr)_minmax(360px,0.85fr)]">
        <div className="space-y-5">
          <Card className="overflow-hidden border-slate-200 shadow-none">
            <div className="border-b border-slate-100 px-5 py-4">
              <div className="flex items-center gap-2">
                <UserPlus className="size-4 text-blue-700" />
                <h2 className="font-semibold">Invite a team member</h2>
              </div>
              <p className="mt-1 text-xs leading-5 text-slate-500">
                Identity creates a pending account and sends the invitation. The user chooses their
                password through its secure invite link.
              </p>
            </div>
            <form
              onSubmit={createForm.handleSubmit(submitCreate)}
              noValidate
              className="space-y-4 p-5"
            >
              <div className="grid gap-3 sm:grid-cols-2">
                <Field label="Full name" error={createForm.formState.errors.fullName?.message}>
                  <Input {...createForm.register('fullName')} autoComplete="name" maxLength={150} />
                </Field>
                <Field label="Phone (optional)" error={createForm.formState.errors.phone?.message}>
                  <Input {...createForm.register('phone')} autoComplete="tel" maxLength={25} />
                </Field>
                <Field
                  label="Official work email"
                  error={createForm.formState.errors.officialEmail?.message}
                >
                  <Input
                    {...createForm.register('officialEmail')}
                    type="email"
                    autoComplete="email"
                    maxLength={190}
                  />
                </Field>
                <Field
                  label="Personal email"
                  error={createForm.formState.errors.personalEmail?.message}
                >
                  <Input
                    {...createForm.register('personalEmail')}
                    type="email"
                    autoComplete="email"
                    maxLength={190}
                  />
                </Field>
              </div>
              <fieldset>
                <legend className="text-sm font-semibold">Assign role(s)</legend>
                {rolesQuery.isLoading && !demo ? (
                  <p className="mt-2 text-xs text-slate-500">
                    Loading roles permitted for your account…
                  </p>
                ) : availableRoles.length ? (
                  <div className="mt-2 grid gap-2 sm:grid-cols-2">
                    {availableRoles.map((role) => (
                      <label
                        key={role.name}
                        className="flex cursor-pointer items-start gap-2 rounded-lg border border-slate-200 p-3 text-sm hover:bg-slate-50"
                      >
                        <input
                          type="checkbox"
                          value={role.name}
                          {...createForm.register('roles')}
                          className="mt-0.5 size-4 accent-blue-700"
                        />
                        <span>
                          <span className="block font-medium">{roleTitles[role.name]}</span>
                          <span className="mt-0.5 block text-xs leading-4 text-slate-500">
                            {role.description}
                          </span>
                        </span>
                      </label>
                    ))}
                  </div>
                ) : (
                  <p className="mt-2 rounded-md bg-amber-50 p-3 text-xs text-amber-900">
                    {rolesQuery.error
                      ? unexpectedErrorMessage(normalizeError(rolesQuery.error))
                      : 'No assignable roles were returned for this account.'}
                  </p>
                )}
                {createForm.formState.errors.roles?.message && (
                  <p className="mt-1.5 text-xs text-red-700" role="alert">
                    {createForm.formState.errors.roles.message}
                  </p>
                )}
              </fieldset>
              <div className="flex justify-end">
                <Button
                  type="submit"
                  disabled={
                    createMutation.isPending ||
                    (!demo && rolesQuery.isLoading) ||
                    !availableRoles.length
                  }
                >
                  {createMutation.isPending ? (
                    <Loader2 className="size-4 animate-spin" />
                  ) : (
                    <Mail className="size-4" />
                  )}
                  Create invitation
                </Button>
              </div>
            </form>
          </Card>

          <Card className="overflow-hidden border-slate-200 shadow-none">
            <div className="border-b border-slate-100 px-5 py-4">
              <div className="flex items-center justify-between gap-3">
                <div>
                  <h2 className="font-semibold">Managed accounts</h2>
                  <p className="mt-1 text-xs text-slate-500">
                    {totalElements} account(s) in your management scope
                  </p>
                </div>
                {!demo && usersQuery.isFetching && (
                  <Loader2
                    className="size-4 animate-spin text-blue-700"
                    aria-label="Loading accounts"
                  />
                )}
              </div>
              <div className="mt-4 grid gap-2 sm:grid-cols-[minmax(0,1fr)_180px_190px]">
                <div className="relative">
                  <label htmlFor="identity-user-search" className="sr-only">
                    Search users
                  </label>
                  <Search className="absolute top-3 left-3 size-4 text-slate-400" />
                  <Input
                    id="identity-user-search"
                    className="pl-9"
                    value={search}
                    onChange={(event) => setSearch(event.target.value)}
                    placeholder="Search name or email"
                  />
                </div>
                <label>
                  <span className="sr-only">Filter by role</span>
                  <select
                    value={roleFilter}
                    onChange={(event) => setRoleFilter(event.target.value as RoleName | '')}
                    className="h-10 w-full rounded-md border border-slate-200 bg-white px-3 text-sm"
                  >
                    <option value="">All roles</option>
                    {availableRoles.map((role) => (
                      <option key={role.name} value={role.name}>
                        {roleTitles[role.name]}
                      </option>
                    ))}
                  </select>
                </label>
                <label>
                  <span className="sr-only">Filter by status</span>
                  <select
                    value={statusFilter}
                    onChange={(event) => setStatusFilter(event.target.value as UserStatus | '')}
                    className="h-10 w-full rounded-md border border-slate-200 bg-white px-3 text-sm"
                  >
                    <option value="">All statuses</option>
                    {Object.entries(statusText).map(([value, label]) => (
                      <option key={value} value={value}>
                        {label}
                      </option>
                    ))}
                  </select>
                </label>
              </div>
            </div>
            {usersQuery.isLoading && !demo ? (
              <div className="flex items-center justify-center gap-2 p-10 text-sm text-slate-500">
                <Loader2 className="size-4 animate-spin" />
                Loading accounts…
              </div>
            ) : usersQuery.error && !demo ? (
              <div className="p-5 text-sm text-red-800" role="alert">
                {unexpectedErrorMessage(normalizeError(usersQuery.error))}
              </div>
            ) : rows.length ? (
              <div className="divide-y divide-slate-100">
                {rows.map((candidate) => (
                  <button
                    key={candidate.id}
                    type="button"
                    onClick={() => setSelectedId(candidate.id)}
                    className={`flex w-full flex-col gap-3 px-5 py-4 text-left transition hover:bg-slate-50 sm:flex-row sm:items-center sm:justify-between ${selectedId === candidate.id ? 'bg-blue-50/70' : ''}`}
                  >
                    <span className="min-w-0">
                      <span className="block truncate text-sm font-semibold text-slate-900">
                        {candidate.fullName}
                      </span>
                      <span className="mt-0.5 block truncate text-xs text-slate-500">
                        {candidate.officialEmail}
                      </span>
                      <span className="mt-0.5 block text-[11px] text-slate-400">
                        Identity user ID: {candidate.id}
                      </span>
                      <span className="mt-2 flex flex-wrap gap-1.5">
                        {candidate.roles.map((role) => (
                          <span
                            key={role}
                            className="rounded bg-slate-100 px-2 py-0.5 text-[11px] font-medium text-slate-700"
                          >
                            {roleTitles[role]}
                          </span>
                        ))}
                      </span>
                    </span>
                    <span
                      className={`shrink-0 rounded-full px-2.5 py-1 text-xs font-semibold ring-1 ring-inset ${statusStyle[candidate.status]}`}
                    >
                      {statusText[candidate.status]}
                    </span>
                  </button>
                ))}
              </div>
            ) : (
              <div className="p-10 text-center text-sm text-slate-500">
                No accounts match these filters.
              </div>
            )}
            <div className="flex items-center justify-between border-t border-slate-100 px-5 py-3 text-xs text-slate-500">
              <label className="flex items-center gap-2">
                Rows per page
                <select
                  value={pageSize}
                  onChange={(event) => setPageSize(Number(event.target.value))}
                  className="h-9 rounded-md border border-slate-200 bg-white px-2 text-xs"
                >
                  {[10, 20, 50].map((size) => (
                    <option key={size} value={size}>
                      {size}
                    </option>
                  ))}
                </select>
              </label>
              <span>
                {totalElements === 0 ? 0 : page * pageSize + 1}–
                {Math.min((page + 1) * pageSize, totalElements)} of {totalElements} · Page{' '}
                {totalPages ? page + 1 : 0} of {totalPages}
              </span>
              <div className="flex gap-2">
                <Button
                  variant="outline"
                  size="sm"
                  disabled={page <= 0}
                  onClick={() => setPage((current) => Math.max(0, current - 1))}
                >
                  <ChevronLeft className="size-4" />
                  Previous
                </Button>
                <Button
                  variant="outline"
                  size="sm"
                  disabled={page + 1 >= totalPages}
                  onClick={() => setPage((current) => current + 1)}
                >
                  Next
                  <ChevronRight className="size-4" />
                </Button>
              </div>
            </div>
          </Card>
        </div>

        <div className="space-y-5">
          {selectedUser ? (
            <Card className="overflow-hidden border-slate-200 shadow-none">
              <div className="border-b border-slate-100 px-5 py-4">
                <div className="flex items-center gap-2">
                  <UserRoundCog className="size-4 text-blue-700" />
                  <h2 className="font-semibold">Manage account</h2>
                </div>
                <p className="mt-1 text-xs text-slate-500">
                  ID {selectedUser.id} · Created{' '}
                  {new Date(selectedUser.createdAt).toLocaleDateString()}
                </p>
              </div>
              <form key={selectedUser.id} onSubmit={saveUserAccess} className="space-y-4 p-5">
                <Field label="Full name">
                  <Input
                    name="fullName"
                    defaultValue={selectedUser.fullName}
                    maxLength={150}
                    required
                  />
                </Field>
                <Field label="Official email">
                  <Input value={selectedUser.officialEmail} disabled />
                </Field>
                <Field label="Personal email">
                  <Input
                    name="personalEmail"
                    type="email"
                    defaultValue={selectedUser.personalEmail}
                    maxLength={190}
                    required
                  />
                </Field>
                <Field label="Phone">
                  <Input name="phone" defaultValue={selectedUser.phone ?? ''} maxLength={25} />
                </Field>
                <fieldset>
                  <legend className="text-sm font-semibold">Role access</legend>
                  <div className="mt-2 space-y-2">
                    {availableRoles.map((role) => (
                      <label key={role.name} className="flex items-center gap-2 text-sm">
                        <input
                          name="editRole"
                          type="checkbox"
                          value={role.name}
                          defaultChecked={selectedUser.roles.includes(role.name)}
                          className="size-4 accent-blue-700"
                        />
                        {roleTitles[role.name]}
                      </label>
                    ))}
                  </div>
                </fieldset>
                <Button type="submit" className="w-full" disabled={busy}>
                  <ShieldCheck className="size-4" />
                  Save profile and roles
                </Button>
              </form>
              <div className="space-y-2 border-t border-slate-100 p-5">
                {selectedUser.status === 'PENDING_VERIFICATION' && (
                  <Button
                    variant="outline"
                    className="w-full"
                    disabled={busy}
                    onClick={() => void accountAction('resend')}
                  >
                    <Mail className="size-4" />
                    Resend invitation
                  </Button>
                )}
                {selectedUser.status === 'LOCKED' && (
                  <Button
                    variant="outline"
                    className="w-full"
                    disabled={busy}
                    onClick={() => void accountAction('unlock')}
                  >
                    <UnlockKeyhole className="size-4" />
                    Unlock account
                  </Button>
                )}
                {isSuperAdmin && selectedUser.status === 'DISABLED' && (
                  <Button
                    variant="outline"
                    className="w-full"
                    disabled={busy}
                    onClick={() => void accountAction('unfreeze')}
                  >
                    <UnlockKeyhole className="size-4" />
                    Unfreeze account
                  </Button>
                )}
                {isSuperAdmin &&
                  selectedUser.status !== 'DISABLED' &&
                  (showFreezeForm ? (
                    <div className="space-y-2 rounded-lg border border-red-200 bg-red-50 p-3">
                      <label
                        htmlFor="freeze-reason"
                        className="block text-xs font-semibold text-red-900"
                      >
                        Reason for freezing this account
                      </label>
                      <textarea
                        id="freeze-reason"
                        value={freezeReason}
                        onChange={(event) => setFreezeReason(event.target.value)}
                        maxLength={500}
                        rows={3}
                        className="w-full rounded-md border border-red-200 bg-white px-3 py-2 text-sm"
                      />
                      <div className="flex justify-end gap-2">
                        <Button
                          type="button"
                          size="sm"
                          variant="outline"
                          onClick={() => setShowFreezeForm(false)}
                        >
                          Cancel
                        </Button>
                        <Button
                          type="button"
                          size="sm"
                          variant="destructive"
                          disabled={busy || freezeReason.trim().length < 5}
                          onClick={() => void accountAction('freeze')}
                        >
                          Confirm freeze
                        </Button>
                      </div>
                    </div>
                  ) : (
                    <Button
                      variant="outline"
                      className="w-full border-red-200 text-red-800 hover:bg-red-50"
                      disabled={busy}
                      onClick={() => setShowFreezeForm(true)}
                    >
                      <LockKeyhole className="size-4" />
                      Freeze account
                    </Button>
                  ))}
              </div>
            </Card>
          ) : (
            <Card className="border-slate-200 p-6 text-center shadow-none">
              <UserRoundCog className="mx-auto size-8 text-slate-300" />
              <p className="mt-3 text-sm font-semibold">Select a managed account</p>
              <p className="mt-1 text-xs leading-5 text-slate-500">
                Profile, role, invitation, and account actions appear here.
              </p>
            </Card>
          )}

          <Card className="border-blue-100 bg-blue-50/60 p-5 shadow-none">
            <h2 className="text-sm font-semibold text-blue-950">
              Role delegation follows Identity policy
            </h2>
            <p className="mt-2 text-xs leading-5 text-blue-900">
              Admins can delegate business roles. Super Admins can also create Admin accounts. The
              service blocks assigning roles at or above the acting user’s privilege level.
            </p>
            <p className="mt-2 text-xs leading-5 text-blue-900">
              Roles are currently fixed in Identity Service. This page creates users and assigns
              configured roles; it does not create new role definitions.
            </p>
          </Card>

          {isSuperAdmin && !demo && (
            <Card className="overflow-hidden border-slate-200 shadow-none">
              <div className="border-b border-slate-100 px-5 py-4">
                <div className="flex items-center gap-2">
                  <KeyRound className="size-4 text-slate-600" />
                  <h2 className="font-semibold">Recent Identity audit events</h2>
                </div>
                <p className="mt-1 text-xs text-slate-500">
                  Read-only events from Identity Service
                </p>
              </div>
              {auditQuery.isLoading ? (
                <p className="p-4 text-xs text-slate-500">Loading audit events…</p>
              ) : auditQuery.error ? (
                <p className="p-4 text-xs text-red-800">
                  {unexpectedErrorMessage(normalizeError(auditQuery.error))}
                </p>
              ) : auditQuery.data?.content.length ? (
                <ul className="divide-y divide-slate-100">
                  {auditQuery.data.content.map((event) => (
                    <li key={event.id} className="px-5 py-3">
                      <p className="text-xs font-semibold">
                        {event.eventType.replaceAll('_', ' ')}
                      </p>
                      <p className="mt-1 text-xs text-slate-500">
                        {event.action} · actor {event.actorUserId ?? 'system'} · target{' '}
                        {event.targetUserId ?? '—'}
                      </p>
                      <p className="mt-1 text-[11px] text-slate-400">
                        {new Date(event.createdAt).toLocaleString()} · {event.outcome}
                      </p>
                    </li>
                  ))}
                </ul>
              ) : (
                <p className="p-5 text-xs text-slate-500">No audit events returned.</p>
              )}
            </Card>
          )}
        </div>
      </div>
    </AppShell>
  )
}

function Field({
  label,
  error,
  children,
}: {
  label: string
  error?: string
  children: React.ReactNode
}) {
  return (
    <label className="block space-y-1.5 text-xs font-medium text-slate-700">
      <span>{label}</span>
      {children}
      {error && (
        <span className="block text-red-700" role="alert">
          {error}
        </span>
      )}
    </label>
  )
}

function AccessDenied() {
  return (
    <div className="mx-auto max-w-xl rounded-xl border border-red-200 bg-white p-8 text-center">
      <ShieldCheck className="mx-auto size-8 text-red-600" />
      <h1 className="mt-3 text-xl font-bold">Administrator access required</h1>
      <p className="mt-2 text-sm text-slate-500">
        Identity Service manages access to this page. Your role cannot manage users.
      </p>
    </div>
  )
}
