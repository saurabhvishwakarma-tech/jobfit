import { describe, expect, it, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AddJobForm } from '../pages/job/AddJobForm'

describe('AddJobForm', () => {
  it('submits title, company, and description to onCreate', async () => {
    const onCreate = vi.fn().mockResolvedValue(undefined)
    render(<AddJobForm onCreate={onCreate} onCancel={vi.fn()} />)
    const user = userEvent.setup()

    await user.type(screen.getByPlaceholderText('Job title'), 'Backend Engineer')
    await user.type(screen.getByPlaceholderText('Company (optional)'), 'Acme')
    await user.type(
      screen.getByPlaceholderText(/paste the full job description/i),
      'Requirements\n- 5+ years experience',
    )
    await user.click(screen.getByRole('button', { name: /save & analyse/i }))

    await waitFor(() =>
      expect(onCreate).toHaveBeenCalledWith({
        title: 'Backend Engineer',
        company: 'Acme',
        rawDescription: 'Requirements\n- 5+ years experience',
      }),
    )
  })

  it('calls onCancel when Cancel is clicked', async () => {
    const onCancel = vi.fn()
    render(<AddJobForm onCreate={vi.fn()} onCancel={onCancel} />)
    const user = userEvent.setup()

    await user.click(screen.getByRole('button', { name: /cancel/i }))
    expect(onCancel).toHaveBeenCalled()
  })
})
