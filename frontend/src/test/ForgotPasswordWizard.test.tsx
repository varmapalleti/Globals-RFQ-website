import { describe, it, expect, vi, beforeEach } from 'vitest'
import { screen, waitFor, fireEvent } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ForgotPasswordWizard } from '@/features/auth/components/ForgotPasswordWizard'
import { renderWithProviders } from './test-utils'

const mockNavigate = vi.fn()

vi.mock('@tanstack/react-router', () => ({
  useNavigate: () => mockNavigate,
}))

describe('ForgotPasswordWizard Component Flow', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('completes the entire 3-step password reset wizard successfully', async () => {
    const user = userEvent.setup()
    renderWithProviders(<ForgotPasswordWizard />)

    // Step 1: Request OTP
    expect(screen.getByText(/forgot your password\?/i)).toBeInTheDocument()
    expect(screen.getByRole('radio', { name: /official email/i })).toBeChecked()

    const emailInput = screen.getByLabelText(/official email address/i)
    await user.type(emailInput, 'test@company.com')
    await user.click(screen.getByRole('button', { name: /send verification code/i }))

    // Step 2: Verify OTP
    await waitFor(() => {
      expect(screen.getByText(/enter verification code/i)).toBeInTheDocument()
    })
    expect(screen.getByText('test@company.com')).toBeInTheDocument()

    // Paste 6 digits into OTP input (verifies paste support)
    const firstDigit = screen.getByLabelText(/digit 1 of 6/i)
    fireEvent.paste(firstDigit, {
      clipboardData: {
        getData: () => '123456',
      },
    })

    await user.click(screen.getByRole('button', { name: /verify code/i }))

    // Step 3: Set New Password
    await waitFor(() => {
      expect(screen.getByText(/set a new password/i)).toBeInTheDocument()
    })

    const newPassInput = screen.getByLabelText(/^new password/i)
    const confirmPassInput = screen.getByLabelText(/confirm new password/i)

    await user.type(newPassInput, 'NewSecurePass1!')
    await user.type(confirmPassInput, 'NewSecurePass1!')

    await user.click(screen.getByRole('button', { name: /set new password/i }))

    // Step 4: Success state
    await waitFor(() => {
      expect(screen.getByText(/password changed!/i)).toBeInTheDocument()
    })

    await user.click(screen.getByRole('button', { name: /sign in/i }))
    expect(mockNavigate).toHaveBeenCalledWith({ to: '/login' })
  })

  it('displays attempt messaging when invalid OTP is entered', async () => {
    const user = userEvent.setup()
    renderWithProviders(<ForgotPasswordWizard />)

    // Step 1
    await user.type(screen.getByLabelText(/official email address/i), 'test@company.com')
    await user.click(screen.getByRole('button', { name: /send verification code/i }))

    // Step 2
    await waitFor(() => {
      expect(screen.getByText(/enter verification code/i)).toBeInTheDocument()
    })

    const firstDigit = screen.getByLabelText(/digit 1 of 6/i)
    // MSW handler rejects '000000' with OTP_INVALID
    fireEvent.paste(firstDigit, {
      clipboardData: {
        getData: () => '000000',
      },
    })

    await user.click(screen.getByRole('button', { name: /verify code/i }))

    await waitFor(() => {
      expect(screen.getByRole('alert')).toHaveTextContent(/invalid or expired code/i)
    })
  })
})
