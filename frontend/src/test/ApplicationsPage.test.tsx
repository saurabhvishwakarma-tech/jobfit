import { describe, expect, it, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { ApplicationsPage } from '../pages/application/ApplicationsPage'
import type { ApplicationSummary } from '../api/application'

const { mockApplications } = vi.hoisted(() => {
  const applications: ApplicationSummary[] = [
    {
      id: 1, jobId: 20, jobTitle: 'Backend Engineer', company: 'Acme',
      resumeId: 10, matchScore: 82, status: 'SAVED', appliedAt: null, updatedAt: new Date().toISOString(),
    },
  ]
  return { mockApplications: applications }
})

vi.mock('../api/application', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api/application')>()
  return {
    ...actual,
    listApplications: vi.fn().mockResolvedValue(mockApplications),
    updateApplicationStatus: vi.fn().mockResolvedValue(undefined),
  }
})

describe('ApplicationsPage', () => {
  it('renders tracked applications with score and lets the user change status', async () => {
    const { updateApplicationStatus } = await import('../api/application')
    render(
      <MemoryRouter>
        <ApplicationsPage />
      </MemoryRouter>,
    )

    await waitFor(() => expect(screen.getByText('Backend Engineer')).toBeInTheDocument())
    expect(screen.getByText('82%')).toBeInTheDocument()

    const user = userEvent.setup()
    await user.selectOptions(screen.getByRole('combobox'), 'INTERVIEW')

    await waitFor(() => expect(updateApplicationStatus).toHaveBeenCalledWith(1, 'INTERVIEW'))
  })
})
