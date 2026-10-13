import { describe, it, expect } from 'vitest'
import {
  passwordSchema,
  loginSchema,
  resetPasswordSchema,
} from '@/features/auth/schemas/authSchemas'

describe('passwordSchema', () => {
  it('accepts a valid strong password', () => {
    expect(passwordSchema.safeParse('ValidPass1!').success).toBe(true)
    expect(passwordSchema.safeParse('Abcdef1@xyz').success).toBe(true)
  })

  it('rejects password shorter than 8 chars', () => {
    const result = passwordSchema.safeParse('Aa1!')
    expect(result.success).toBe(false)
    if (!result.success) {
      const msgs = result.error.issues.map((i) => i.message)
      expect(msgs).toContain('Password must be at least 8 characters long')
    }
  })

  it('rejects password longer than 64 chars', () => {
    const long = 'Aa1!' + 'x'.repeat(62)
    const result = passwordSchema.safeParse(long)
    expect(result.success).toBe(false)
    if (!result.success) {
      expect(result.error.issues.map((i) => i.message)).toContain(
        'Password must be at most 64 characters long',
      )
    }
  })

  it('rejects password without uppercase', () => {
    const result = passwordSchema.safeParse('validpass1!')
    expect(result.success).toBe(false)
    if (!result.success) {
      expect(result.error.issues.map((i) => i.message)).toContain(
        'Password must contain at least one uppercase letter',
      )
    }
  })

  it('rejects password without lowercase', () => {
    const result = passwordSchema.safeParse('VALIDPASS1!')
    expect(result.success).toBe(false)
    if (!result.success) {
      expect(result.error.issues.map((i) => i.message)).toContain(
        'Password must contain at least one lowercase letter',
      )
    }
  })

  it('rejects password without digit', () => {
    const result = passwordSchema.safeParse('ValidPass!')
    expect(result.success).toBe(false)
    if (!result.success) {
      expect(result.error.issues.map((i) => i.message)).toContain(
        'Password must contain at least one number',
      )
    }
  })

  it('rejects password without special character', () => {
    const result = passwordSchema.safeParse('ValidPass1')
    expect(result.success).toBe(false)
    if (!result.success) {
      expect(result.error.issues.map((i) => i.message)).toContain(
        'Password must contain at least one special character',
      )
    }
  })

  it('rejects password with spaces', () => {
    const result = passwordSchema.safeParse('Valid Pass1!')
    expect(result.success).toBe(false)
    if (!result.success) {
      expect(result.error.issues.map((i) => i.message)).toContain(
        'Password must not contain spaces',
      )
    }
  })
})

describe('loginSchema', () => {
  it('accepts valid credentials', () => {
    expect(loginSchema.safeParse({ officialEmail: 'a@b.com', password: 'secret' }).success).toBe(
      true,
    )
  })

  it('rejects blank email', () => {
    const r = loginSchema.safeParse({ officialEmail: '', password: 'secret' })
    expect(r.success).toBe(false)
  })

  it('rejects invalid email format', () => {
    const r = loginSchema.safeParse({ officialEmail: 'notanemail', password: 'secret' })
    expect(r.success).toBe(false)
  })

  it('rejects password longer than 128 chars', () => {
    const r = loginSchema.safeParse({ officialEmail: 'a@b.com', password: 'x'.repeat(129) })
    expect(r.success).toBe(false)
  })
})

describe('resetPasswordSchema', () => {
  it('accepts matching strong passwords', () => {
    const r = resetPasswordSchema.safeParse({
      newPassword: 'ValidPass1!',
      confirmPassword: 'ValidPass1!',
    })
    expect(r.success).toBe(true)
  })

  it('rejects non-matching passwords', () => {
    const r = resetPasswordSchema.safeParse({
      newPassword: 'ValidPass1!',
      confirmPassword: 'Different1!',
    })
    expect(r.success).toBe(false)
    if (!r.success) {
      expect(r.error.issues[0]?.message).toBe('Passwords do not match')
    }
  })

  it('rejects weak newPassword', () => {
    const r = resetPasswordSchema.safeParse({
      newPassword: 'weak',
      confirmPassword: 'weak',
    })
    expect(r.success).toBe(false)
  })
})
