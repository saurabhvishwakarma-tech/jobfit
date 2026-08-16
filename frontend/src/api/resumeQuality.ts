import { apiClient } from './client'

export interface QualityIssue {
  category: string
  severity: 'HIGH' | 'MEDIUM' | 'LOW'
  message: string
  resumeRefType: string | null
  resumeRefId: number | null
}

export interface ResumeQuality {
  resumeId: number
  score: number
  highCount: number
  mediumCount: number
  lowCount: number
  issues: QualityIssue[]
}

export function getResumeQuality(resumeId: number) {
  return apiClient.get<ResumeQuality>(`/api/resumes/${resumeId}/quality`).then((r) => r.data)
}
