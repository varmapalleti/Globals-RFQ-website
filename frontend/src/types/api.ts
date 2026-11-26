/** Role names as defined in backend RoleName enum + seeded in V2 migration. */
export type RoleName =
  | 'SUPER_ADMIN'
  | 'ADMIN'
  | 'QUOTATION_MANAGER'
  | 'REVIEWER'
  | 'MARKETING_EXECUTIVE'
  | 'VIEWER'

/** UserStatus enum values from backend UserStatus.java */
export type UserStatus = 'ACTIVE' | 'PENDING_VERIFICATION' | 'DISABLED' | 'LOCKED'

/** EmailType enum from backend */
export type EmailType = 'OFFICIAL' | 'PERSONAL'

/** Matches backend UserSummary record */
export interface UserSummary {
  id: number
  fullName: string
  officialEmail: string
  officialEmailVerified: boolean
  personalEmailVerified: boolean
  status: UserStatus
  mustChangePassword: boolean
  roles: RoleName[]
}

/** Matches backend AuthResponse record */
export interface AuthResponse {
  accessToken: string
  tokenType: string
  expiresInSeconds: number
  user: UserSummary
}

/** Matches backend ApiResponse<T> */
export interface ApiSuccessResponse<T> {
  success: true
  message: string
  data: T
  timestamp: string
  correlationId: string | null
}

/** Matches backend ApiResponse<Void> (no data field) */
export interface ApiVoidResponse {
  success: true
  message: string
  timestamp: string
  correlationId: string | null
}

/** Matches backend ResetTokenResponse */
export interface ResetTokenResponse {
  resetToken: string
  expiresInSeconds: number
}
