import { describe, expect, it, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { JobComparisonPage } from '../pages/job/JobComparisonPage'
import type { JobComparison } from '../api/comparison'

const { mockComparison } = vi.hoisted(() => {
  const comparison: JobComparison = {
    jobs: [
      {
        jobId: 10, title: 'Backend Engineer', company: 'Acme', analysed: true,
        matchAnalysisId: 500, overallScore: 85, recommendation: 'STRONG_MATCH',
        categoryScores: [{ category: 'Required skills', maxPoints: 35, earnedPoints: 30, explanation: '' }],
      },
      {
        jobId: 11, title: 'Platform Engineer', company: 'Beta', analysed: false,
        matchAnalysisId: null, overallScore: null, recommendation: null, categoryScores: [],
      },
    ],
    skillComparison: [
      { skillName: 'Java', requirementPerJob: ['REQUIRED', 'REQUIRED'], resumeStatus: 'EXPLICIT' },
      { skillName: 'SQL', requirementPerJob: ['REQUIRED', null], resumeStatus: 'ABSENT' },
    ],
  }
  return { mockComparison: comparison }
})

vi.mock('../api/comparison', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api/comparison')>()
  return { ...actual, compareJobs: vi.fn().mockResolvedValue(mockComparison) }
})

describe('JobComparisonPage', () => {
  it('renders each job column and the skill overlap table', async () => {
    render(
      <MemoryRouter initialEntries={['/compare?jobIds=10,11']}>
        <JobComparisonPage />
      </MemoryRouter>,
    )

    // "Backend Engineer" appears in the header card and as a column header in both
    // comparison tables - that repetition is expected, not a bug.
    await waitFor(() => expect(screen.getAllByText('Backend Engineer').length).toBeGreaterThan(0))
    expect(screen.getAllByText('Platform Engineer').length).toBeGreaterThan(0)
    expect(screen.getByText('85%')).toBeInTheDocument()
    expect(screen.getByText('Not analysed yet')).toBeInTheDocument()

    expect(screen.getByText('Java')).toBeInTheDocument()
    expect(screen.getByText('SQL')).toBeInTheDocument()
    expect(screen.getByText('Missing')).toBeInTheDocument() // SQL's resume status for job 11's gap
  })

  it('prompts the user to pick jobs when fewer than two are selected', () => {
    render(
      <MemoryRouter initialEntries={['/compare?jobIds=10']}>
        <JobComparisonPage />
      </MemoryRouter>,
    )

    expect(screen.getByText(/Select at least two jobs/i)).toBeInTheDocument()
  })
})
