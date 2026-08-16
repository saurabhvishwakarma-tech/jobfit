import { apiClient } from './client'

export type AtsCheckStatus = 'PASS' | 'WARN' | 'FAIL'

export interface AtsCheck {
  label: string
  status: AtsCheckStatus
  detail: string
}

export interface AtsScore {
  resumeId: number
  score: number
  checks: AtsCheck[]
}

export function getAtsScore(resumeId: number) {
  return apiClient.get<AtsScore>(`/api/resumes/${resumeId}/ats-score`).then((r) => r.data)
}
