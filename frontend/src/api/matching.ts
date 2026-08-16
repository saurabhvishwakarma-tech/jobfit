import { apiClient } from './client'

export interface ScoreComponent {
  category: string
  maxPoints: number
  earnedPoints: number
  explanation: string
}

export interface EvidenceItem {
  requirementId: number
  requirementType: string
  requirementText: string
  matchType: 'EXPLICIT' | 'INFERRED' | 'ABSENT'
  strength: 'STRONG' | 'PARTIAL' | 'MISSING'
  resumeRefType: string | null
  resumeRefId: number | null
  resumeRefText: string | null
  explanationText: string
  confidence: number | null
}

export interface MatchAnalysisDetail {
  id: number
  resumeId: number
  jobId: number
  jobTitle: string
  company: string | null
  overallScore: number
  recommendation: 'STRONG_MATCH' | 'REASONABLE_MATCH' | 'STRETCH_APPLICATION' | 'POOR_MATCH'
  recommendationReason: string
  createdAt: string
  components: ScoreComponent[]
  evidence: EvidenceItem[]
}

export function analyseJob(jobId: number, resumeId?: number) {
  return apiClient
    .post<MatchAnalysisDetail>(`/api/jobs/${jobId}/analyse`, resumeId ? { resumeId } : {})
    .then((r) => r.data)
}

export function getMatchAnalysis(id: number) {
  return apiClient.get<MatchAnalysisDetail>(`/api/match-analyses/${id}`).then((r) => r.data)
}
