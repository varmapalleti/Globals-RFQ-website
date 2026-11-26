import { z } from 'zod'

// ── Mirrors backend PasswordPolicy.java exactly ────────────────────────────
export const PASSWORD_RULES = {
  minLength: 8,
  maxLength: 64,
} as const

export const passwordSchema = z
  .string()
  .min(1, 'Password is required')
  .min(
    PASSWORD_RULES.minLength,
    `Password must be at least ${PASSWORD_RULES.minLength} characters long`,
  )
  .max(
    PASSWORD_RULES.maxLength,
    `Password must be at most ${PASSWORD_RULES.maxLength} characters long`,
  )
  .refine((v) => /[A-Z]/.test(v), 'Password must contain at least one uppercase letter')
  .refine((v) => /[a-z]/.test(v), 'Password must contain at least one lowercase letter')
  .refine((v) => /[0-9]/.test(v), 'Password must contain at least one number')
  .refine((v) => /[^A-Za-z0-9\s]/.test(v), 'Password must contain at least one special character')
  .refine((v) => !/\s/.test(v), 'Password must not contain spaces')

// ── Login ───────────────────────────────────────────────────────────────────
export const loginSchema = z.object({
  officialEmail: z
    .string()
    .min(1, 'Email is required')
    .email('Enter a valid email address')
    .max(190),
  password: z.string().min(1, 'Password is required').max(128, 'Password is too long'),
})
export type LoginFormValues = z.infer<typeof loginSchema>

// ── Forgot Password — Step 1 ────────────────────────────────────────────────
export const forgotPasswordSchema = z.object({
  emailType: z.enum(['OFFICIAL', 'PERSONAL'], {
    required_error: 'Select official or personal email',
  }),
  email: z.string().min(1, 'Email is required').email('Enter a valid email address').max(190),
})
export type ForgotPasswordFormValues = z.infer<typeof forgotPasswordSchema>

// ── Verify OTP — Step 2 ─────────────────────────────────────────────────────
export const verifyOtpSchema = z.object({
  emailType: z.enum(['OFFICIAL', 'PERSONAL']),
  email: z.string().email().max(190),
  otp: z
    .string()
    .min(1, 'OTP is required')
    .regex(/^\d{6}$/, 'OTP must be 6 digits'),
})
export type VerifyOtpFormValues = z.infer<typeof verifyOtpSchema>

// ── Reset Password — Step 3 ─────────────────────────────────────────────────
export const resetPasswordSchema = z
  .object({
    newPassword: passwordSchema,
    confirmPassword: z.string().min(1, 'Please confirm your password'),
  })
  .refine((data) => data.newPassword === data.confirmPassword, {
    message: 'Passwords do not match',
    // Map to confirmPasswordMatching to match backend @AssertTrue field name
    path: ['confirmPassword'],
  })
export type ResetPasswordFormValues = z.infer<typeof resetPasswordSchema>

// ── Verify Email ─────────────────────────────────────────────────────────────
export const verifyEmailSchema = z.object({
  token: z.string().min(1).max(200),
})

// ── Resend Verification ──────────────────────────────────────────────────────
export const resendVerificationSchema = z.object({
  emailType: z.enum(['OFFICIAL', 'PERSONAL']),
  email: z.string().email().max(190),
})
export type ResendVerificationFormValues = z.infer<typeof resendVerificationSchema>
