import { http } from '@/lib/http'
import type {
  ApiSuccessResponse,
  ApiVoidResponse,
  AuthResponse,
  EmailType,
  ResetTokenResponse,
  UserSummary,
} from '@/types/api'
import { coordinateRefresh } from './refreshCoordinator'

// ── Request type aliases ────────────────────────────────────────────────────

interface LoginRequest {
  officialEmail: string
  password: string
}

interface ForgotPasswordRequest {
  emailType: EmailType
  email: string
}

interface VerifyOtpRequest {
  emailType: EmailType
  email: string
  otp: string
}

interface ResetPasswordRequest {
  resetToken: string
  newPassword: string
  confirmPassword: string
}

interface VerifyEmailRequest {
  token: string
}

interface ResendVerificationRequest {
  emailType: EmailType
  email: string
}

export interface ChangePasswordRequest {
  currentPassword: string
  newPassword: string
  confirmPassword: string
}

export interface AcceptInviteRequest {
  token: string
  newPassword: string
  confirmPassword: string
}

export async function changePassword(data: ChangePasswordRequest): Promise<ApiVoidResponse> {
  const res = await http.post<ApiVoidResponse>('/api/auth/change-password', data)
  return res.data
}

export async function acceptInvite(data: AcceptInviteRequest): Promise<ApiVoidResponse> {
  const res = await http.post<ApiVoidResponse>('/api/auth/accept-invite', data)
  return res.data
}

// ── API calls ───────────────────────────────────────────────────────────────

export async function login(data: LoginRequest): Promise<ApiSuccessResponse<AuthResponse>> {
  const res = await http.post<ApiSuccessResponse<AuthResponse>>('/api/auth/login', data)
  return res.data
}

export async function refresh(): Promise<ApiSuccessResponse<AuthResponse>> {
  return coordinateRefresh(async () => {
    const res = await http.post<ApiSuccessResponse<AuthResponse>>('/api/auth/refresh')
    return res.data
  })
}

export async function logout(): Promise<void> {
  await http.post('/api/auth/logout')
}

export async function getMe(): Promise<ApiSuccessResponse<UserSummary>> {
  const res = await http.get<ApiSuccessResponse<UserSummary>>('/api/auth/me')
  return res.data
}

export async function forgotPassword(data: ForgotPasswordRequest): Promise<ApiVoidResponse> {
  const res = await http.post<ApiVoidResponse>('/api/auth/forgot-password', data)
  return res.data
}

export async function verifyResetOtp(
  data: VerifyOtpRequest,
): Promise<ApiSuccessResponse<ResetTokenResponse>> {
  const res = await http.post<ApiSuccessResponse<ResetTokenResponse>>(
    '/api/auth/verify-reset-otp',
    data,
  )
  return res.data
}

export async function resetPassword(data: ResetPasswordRequest): Promise<ApiVoidResponse> {
  const res = await http.post<ApiVoidResponse>('/api/auth/reset-password', data)
  return res.data
}

export async function verifyEmail(data: VerifyEmailRequest): Promise<ApiVoidResponse> {
  const res = await http.post<ApiVoidResponse>('/api/auth/verify-email', data)
  return res.data
}

export async function resendVerification(
  data: ResendVerificationRequest,
): Promise<ApiVoidResponse> {
  const res = await http.post<ApiVoidResponse>('/api/auth/resend-verification', data)
  return res.data
}
