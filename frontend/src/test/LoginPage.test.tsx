import { describe, expect, it, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { LoginPage } from '../pages/LoginPage'
import { AuthProvider } from '../context/AuthContext'

vi.mock('../api/auth', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api/auth')>()
  return {
    ...actual,
    login: vi.fn(),
    refresh: vi.fn().mockRejectedValue(new Error('no session')),
  }
})

function renderLoginPage() {
  return render(
    <MemoryRouter initialEntries={['/login']}>
      <AuthProvider>
        <LoginPage />
      </AuthProvider>
    </MemoryRouter>,
  )
}

describe('LoginPage', () => {
  it('renders email and password fields', async () => {
    renderLoginPage()
    await waitFor(() => expect(screen.getByLabelText(/email/i)).toBeInTheDocument())
    expect(screen.getByLabelText(/password/i)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /log in/i })).toBeInTheDocument()
  })

  it('lets the user type into the email field', async () => {
    renderLoginPage()
    const user = userEvent.setup()
    const emailInput = await screen.findByLabelText(/email/i)
    await user.type(emailInput, 'test@example.com')
    expect(emailInput).toHaveValue('test@example.com')
  })
})
