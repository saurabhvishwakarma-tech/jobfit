import { describe, expect, it, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { JobAnalysisPage } from '../pages/analysis/JobAnalysisPage'
import type { MatchAnalysisDetail } from '../api/matching'

// vi.mock factories are hoisted above the module's top-level const declarations,
// so the fixture must be built with vi.hoisted() rather than a plain const -
// referencing an ordinary top-level variable here throws a temporal-dead-zone error.
const { mockAnalysis } = vi.hoisted(() => {
  const analysis: MatchAnalysisDetail = {
    id: 1,
    resumeId: 10,
    jobId: 20,
    jobTitle: 'Backend Engineer',
    company: 'Acme',
    overallScore: 82,
    recommendation: 'STRONG_MATCH',
    recommendationReason: 'Job Fit: 82%. You meet 4/5 required skills. This is a strong match - go ahead and apply.',
    createdAt: new Date().toISOString(),
    components: [
      { category: 'Required skills', maxPoints: 35, earnedPoints: 28, explanation: '4 strong, 0 partial, 1 missing (out of 5).' },
    ],
    evidence: [
      {
        requirementId: 1, requirementType: 'REQUIRED_SKILL', requirementText: 'Java',
        matchType: 'EXPLICIT', strength: 'STRONG', resumeRefType: 'SKILL', resumeRefId: 1,
        resumeRefText: 'Java', explanationText: 'Your resume explicitly lists "Java".', confidence: 1,
      },
      {
        requirementId: 2, requirementType: 'REQUIRED_SKILL', requirementText: 'Kubernetes',
        matchType: 'ABSENT', strength: 'MISSING', resumeRefType: null, resumeRefId: null,
        resumeRefText: null, explanationText: 'No evidence of this skill was found in your resume.', confidence: null,
      },
    ],
  }
  return { mockAnalysis: analysis }
})

vi.mock('../api/matching', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api/matching')>()
  return { ...actual, getMatchAnalysis: vi.fn().mockResolvedValue(mockAnalysis) }
})

describe('JobAnalysisPage', () => {
  it('renders the overall score, recommendation, and evidence groups', async () => {
    render(
      <MemoryRouter initialEntries={['/match-analyses/1']}>
        <Routes>
          <Route path="/match-analyses/:id" element={<JobAnalysisPage />} />
        </Routes>
      </MemoryRouter>,
    )

    await waitFor(() => expect(screen.getByText('82%')).toBeInTheDocument())
    expect(screen.getByText('Strong Match')).toBeInTheDocument()
    expect(screen.getByText('Strong matches')).toBeInTheDocument()
    expect(screen.getByText('Missing')).toBeInTheDocument()
    expect(screen.getByText('Kubernetes')).toBeInTheDocument()
  })
})
