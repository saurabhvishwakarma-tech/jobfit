import { apiClient } from './client'

export type ApplicationStatus =
  | 'SAVED' | 'APPLIED' | 'ONLINE_ASSESSMENT' | 'INTERVIEW' | 'OFFER' | 'REJECTED' | 'WITHDRAWN'

export const APPLICATION_STATUSES: ApplicationStatus[] = [
  'SAVED', 'APPLIED', 'ONLINE_ASSESSMENT', 'INTERVIEW', 'OFFER', 'REJECTED', 'WITHDRAWN',
]

export interface ApplicationSummary {
  id: number
  jobId: number
  jobTitle: string | null
  company: string | null
  resumeId: number | null
  matchScore: number | null
  status: ApplicationStatus
  appliedAt: string | null
  updatedAt: string
}

export interface StatusHistoryEntry {
  status: ApplicationStatus
  notes: string | null
  changedAt: string
}

export interface ApplicationDetail {
  id: number
  jobId: number
  jobTitle: string | null
  company: string | null
  resumeId: number | null
  matchAnalysisId: number | null
  matchScore: number | null
  matchRecommendation: string | null
  status: ApplicationStatus
  notes: string | null
  appliedAt: string | null
  createdAt: string
  updatedAt: string
  history: StatusHistoryEntry[]
}

export interface ApplicationCreatePayload {
  jobId: number
  resumeId?: number
  matchAnalysisId?: number
  notes?: string
}

export function createApplication(payload: ApplicationCreatePayload) {
  return apiClient.post<ApplicationDetail>('/api/applications', payload).then((r) => r.data)
}

export function listApplications() {
  return apiClient.get<ApplicationSummary[]>('/api/applications').then((r) => r.data)
}

export function getApplication(id: number) {
  return apiClient.get<ApplicationDetail>(`/api/applications/${id}`).then((r) => r.data)
}

export function updateApplicationStatus(id: number, status: ApplicationStatus, notes?: string) {
  return apiClient.patch<ApplicationDetail>(`/api/applications/${id}/status`, { status, notes }).then((r) => r.data)
}

export function updateApplicationNotes(id: number, notes: string) {
  return apiClient.patch<ApplicationDetail>(`/api/applications/${id}`, { notes }).then((r) => r.data)
}

export function deleteApplication(id: number) {
  return apiClient.delete(`/api/applications/${id}`)
}
