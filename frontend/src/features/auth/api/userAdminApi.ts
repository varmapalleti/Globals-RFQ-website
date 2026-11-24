import { http } from '@/lib/http'
import type { ApiSuccessResponse, RoleName, UserStatus } from '@/types/api'

export interface IdentityRoleOption {
  name: RoleName
  description: string
}

export interface IdentityUser {
  id: number
  fullName: string
  officialEmail: string
  officialEmailVerified: boolean
  personalEmail: string
  personalEmailVerified: boolean
  phone: string | null
  status: UserStatus
  statusReason: string | null
  statusChangedBy: number | null
  statusChangedAt: string | null
  mustChangePassword: boolean
  roles: RoleName[]
  createdAt: string
  updatedAt: string
}

export interface IdentityPage<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface IdentityAuditEvent {
  id: number
  eventType: string
  actorUserId: number | null
  targetUserId: number | null
  action: string
  outcome: 'SUCCESS' | 'FAILURE'
  ipAddress: string | null
  userAgent: string | null
  correlationId: string | null
  metadata: Record<string, unknown> | null
  createdAt: string
}

export interface CreateIdentityUserRequest {
  fullName: string
  officialEmail: string
  personalEmail: string
  phone?: string
  roles: RoleName[]
}

export async function getAssignableRoles() {
  const { data } = await http.get<ApiSuccessResponse<IdentityRoleOption[]>>('/api/roles')
  return data.data
}

export async function getIdentityUsers(params: {
  search?: string
  role?: RoleName | ''
  status?: UserStatus | ''
  page?: number
  size?: number
}) {
  const { data } = await http.get<ApiSuccessResponse<IdentityPage<IdentityUser>>>('/api/users', {
    params: {
      ...params,
      role: params.role || undefined,
      status: params.status || undefined,
    },
  })
  return data.data
}

export async function createIdentityUser(request: CreateIdentityUserRequest) {
  const { data } = await http.post<ApiSuccessResponse<IdentityUser>>('/api/users', request)
  return data.data
}

export async function updateIdentityUser(
  id: number,
  patch: { fullName?: string; phone?: string; personalEmail?: string },
) {
  const { data } = await http.patch<ApiSuccessResponse<IdentityUser>>(`/api/users/${id}`, patch)
  return data.data
}

export async function replaceIdentityUserRoles(id: number, roles: RoleName[]) {
  const { data } = await http.put<ApiSuccessResponse<IdentityUser>>(`/api/users/${id}/roles`, {
    roles,
  })
  return data.data
}

export async function freezeIdentityUser(id: number, reason: string) {
  const { data } = await http.post<ApiSuccessResponse<IdentityUser>>(`/api/users/${id}/freeze`, {
    reason,
  })
  return data.data
}

export async function unfreezeIdentityUser(id: number) {
  const { data } = await http.post<ApiSuccessResponse<IdentityUser>>(`/api/users/${id}/unfreeze`)
  return data.data
}

export async function unlockIdentityUser(id: number) {
  const { data } = await http.post<ApiSuccessResponse<IdentityUser>>(`/api/users/${id}/unlock`)
  return data.data
}

export async function resendIdentityInvite(id: number) {
  await http.post<ApiSuccessResponse<void>>(`/api/users/${id}/resend-invite`)
}

export async function getIdentityAudit(params: {
  page?: number
  size?: number
  userId?: number
  from?: string
  to?: string
  eventType?: string
}) {
  const { data } = await http.get<ApiSuccessResponse<IdentityPage<IdentityAuditEvent>>>(
    '/api/audit-logs',
    { params },
  )
  return data.data
}
