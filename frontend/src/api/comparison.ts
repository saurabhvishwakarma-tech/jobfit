import { apiClient } from './client'
import type { ScoreComponent } from './matching'

export interface ComparedJob {
  jobId: number
  title: string
  company: string | null
  analysed: boolean
  matchAnalysisId: number | null
  overallScore: number | null
  recommendation: string | null
  categoryScores: ScoreComponent[]
}

export interface SkillComparisonRow {
  skillName: string
  requirementPerJob: (string | null)[]
  resumeStatus: 'EXPLICIT' | 'INFERRED' | 'ABSENT'
}

export interface JobComparison {
  jobs: ComparedJob[]
  skillComparison: SkillComparisonRow[]
}

export function compareJobs(jobIds: number[]) {
  return apiClient.get<JobComparison>('/api/jobs/compare', { params: { jobIds } }).then((r) => r.data)
}
