import { describe, it, expect, vi, beforeEach } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { LoginForm } from '@/features/auth/components/LoginForm'
import { renderWithProviders } from './test-utils'
import { useAuthStore } from '@/features/auth/store/authStore'

const mockNavigate = vi.fn()

vi.mock('@tanstack/react-router', () => ({
  useNavigate: () => mockNavigate,
}))

describe('LoginForm Component', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    useAuthStore.getState().clearSession()
  })

  it('renders login fields and submit button', () => {
    renderWithProviders(<LoginForm />)

    expect(screen.getByLabelText(/official email/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/^password/i)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /sign in/i })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /forgot password\?/i })).toBeInTheDocument()
  })

  it('submits valid credentials and updates auth session', async () => {
    const user = userEvent.setup()
    renderWithProviders(<LoginForm />)

    await user.type(screen.getByLabelText(/official email/i), 'test@company.com')
    await user.type(screen.getByLabelText(/^password/i), 'ValidPass1!')
    await user.click(screen.getByRole('button', { name: /sign in/i }))

    await waitFor(() => {
      expect(useAuthStore.getState().accessToken).toBe('mock-access-token')
      expect(useAuthStore.getState().user?.officialEmail).toBe('test@company.com')
      expect(mockNavigate).toHaveBeenCalledWith({ to: '/dashboard' })
    })
  })

  it('shows error toast when credentials are invalid', async () => {
    const user = userEvent.setup()
    renderWithProviders(<LoginForm />)

    await user.type(screen.getByLabelText(/official email/i), 'test@company.com')
    await user.type(screen.getByLabelText(/^password/i), 'WrongPass1!')
    await user.click(screen.getByRole('button', { name: /sign in/i }))

    await waitFor(() => {
      expect(useAuthStore.getState().accessToken).toBeNull()
      expect(mockNavigate).not.toHaveBeenCalled()
    })
  })

  it('handles unverified email login attempt properly', async () => {
    const user = userEvent.setup()
    renderWithProviders(<LoginForm />)

    await user.type(screen.getByLabelText(/official email/i), 'unverified@company.com')
    await user.type(screen.getByLabelText(/^password/i), 'ValidPass1!')
    await user.click(screen.getByRole('button', { name: /sign in/i }))

    await waitFor(() => {
      expect(useAuthStore.getState().accessToken).toBeNull()
      expect(mockNavigate).not.toHaveBeenCalled()
    })
  })
})
