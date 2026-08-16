import { describe, expect, it, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { DashboardPage } from '../pages/DashboardPage'
import { AuthProvider } from '../context/AuthContext'
import type { Dashboard } from '../api/dashboard'

const { mockDashboard } = vi.hoisted(() => {
  const dashboard: Dashboard = {
    totalJobsAdded: 3,
    jobsAnalysed: 2,
    applicationsTracked: 2,
    interviews: 1,
    offers: 0,
    averageFitScore: 72.5,
    mostRequestedSkills: [{ skillName: 'Java', count: 3 }],
    strongestSkills: ['Git', 'Java'],
    commonSkillGaps: [{ skillName: 'SQL', count: 1 }],
    bestFitRoles: [{ jobId: 10, title: 'Backend Engineer', company: 'Acme', score: 85 }],
  }
  return { mockDashboard: dashboard }
})

vi.mock('../api/dashboard', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api/dashboard')>()
  return {
    ...actual,
    getDashboard: vi.fn().mockResolvedValue(mockDashboard),
  }
})

vi.mock('../api/auth', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api/auth')>()
  return {
    ...actual,
    refresh: vi.fn().mockRejectedValue(new Error('no session')),
  }
})

describe('DashboardPage', () => {
  it('renders aggregate stats, skill lists, and best-fit roles from the dashboard endpoint', async () => {
    render(
      <MemoryRouter>
        <AuthProvider>
          <DashboardPage />
        </AuthProvider>
      </MemoryRouter>,
    )

    await waitFor(() => expect(screen.getByText('Backend Engineer')).toBeInTheDocument())

    expect(screen.getByText('Jobs added').closest('.stat-card')).toHaveTextContent('3')
    expect(screen.getByText('72.5%')).toBeInTheDocument() // average fit score
    // "Java" appears both as a most-requested skill and a strongest skill - that's expected.
    expect(screen.getAllByText('Java')).toHaveLength(2)
    expect(screen.getByText('SQL')).toBeInTheDocument()
    expect(screen.getByText('Git')).toBeInTheDocument()
    expect(screen.getByText('85%')).toBeInTheDocument()
  })

  it('shows an empty-state message when the user has no jobs yet', async () => {
    const { getDashboard } = await import('../api/dashboard')
    vi.mocked(getDashboard).mockResolvedValueOnce({
      ...mockDashboard,
      totalJobsAdded: 0,
      jobsAnalysed: 0,
      applicationsTracked: 0,
      interviews: 0,
      offers: 0,
      averageFitScore: null,
      mostRequestedSkills: [],
      strongestSkills: [],
      commonSkillGaps: [],
      bestFitRoles: [],
    })

    render(
      <MemoryRouter>
        <AuthProvider>
          <DashboardPage />
        </AuthProvider>
      </MemoryRouter>,
    )

    await waitFor(() =>
      expect(screen.getByText(/Add a job and upload your resume/i)).toBeInTheDocument(),
    )
  })
})
