import { describe, expect, it, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { ResumeQualityPage } from '../pages/resume/ResumeQualityPage'
import type { ResumeQuality } from '../api/resumeQuality'

const { mockQuality } = vi.hoisted(() => {
  const quality: ResumeQuality = {
    resumeId: 10,
    score: 90,
    highCount: 0,
    mediumCount: 2,
    lowCount: 0,
    issues: [
      { category: 'Contact Info', severity: 'MEDIUM', message: 'No phone number found - some recruiters still prefer to call.', resumeRefType: null, resumeRefId: null },
      { category: 'Structure', severity: 'MEDIUM', message: 'Your resume has only 1 bullet point(s) in total.', resumeRefType: null, resumeRefId: null },
    ],
  }
  return { mockQuality: quality }
})

vi.mock('../api/resumeQuality', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api/resumeQuality')>()
  return { ...actual, getResumeQuality: vi.fn().mockResolvedValue(mockQuality) }
})

describe('ResumeQualityPage', () => {
  it('renders the score, severity counts, and grouped issues', async () => {
    render(
      <MemoryRouter initialEntries={['/resume/quality/10']}>
        <Routes>
          <Route path="/resume/quality/:id" element={<ResumeQualityPage />} />
        </Routes>
      </MemoryRouter>,
    )

    await waitFor(() => expect(screen.getByText('90')).toBeInTheDocument())
    expect(screen.getByText('Excellent')).toBeInTheDocument() // 90 meets the >=90 "excellent" threshold
    expect(screen.getByText('Contact Info')).toBeInTheDocument()
    expect(screen.getByText('Structure')).toBeInTheDocument()
    expect(screen.getByText(/No phone number found/)).toBeInTheDocument()
    expect(screen.getAllByText('MEDIUM')).toHaveLength(2)
  })
})
