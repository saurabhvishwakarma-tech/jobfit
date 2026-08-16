import { apiClient } from './client'
import type { ParseStatus } from './resume'

export interface JobSummary {
  id: number
  title: string
  company: string | null
  parseStatus: ParseStatus
  parseError: string | null
  createdAt: string
  parsedAt: string | null
}

export interface JobRequirementItem {
  id: number
  type: string
  text: string
  skillName: string | null
}

export interface JobDetail extends JobSummary {
  rawDescription: string
  sourceUrl: string | null
  requiredSkills: JobRequirementItem[]
  preferredSkills: JobRequirementItem[]
  responsibilities: JobRequirementItem[]
  education: JobRequirementItem[]
  domain: JobRequirementItem[]
  softSkills: JobRequirementItem[]
  experienceYears: JobRequirementItem | null
}

export interface JobCreatePayload {
  title: string
  company?: string
  rawDescription: string
  sourceUrl?: string
}

export function createJob(payload: JobCreatePayload) {
  return apiClient.post<JobSummary>('/api/jobs', payload).then((r) => r.data)
}

export function listJobs() {
  return apiClient.get<JobSummary[]>('/api/jobs').then((r) => r.data)
}

export function getJob(id: number) {
  return apiClient.get<JobDetail>(`/api/jobs/${id}`).then((r) => r.data)
}

export function deleteJob(id: number) {
  return apiClient.delete(`/api/jobs/${id}`)
}
