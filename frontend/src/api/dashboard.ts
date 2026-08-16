import { apiClient } from './client'

export interface SkillFrequency {
  skillName: string
  count: number
}

export interface BestFitRole {
  jobId: number
  title: string | null
  company: string | null
  score: number
}

export interface Dashboard {
  totalJobsAdded: number
  jobsAnalysed: number
  applicationsTracked: number
  interviews: number
  offers: number
  averageFitScore: number | null
  mostRequestedSkills: SkillFrequency[]
  strongestSkills: string[]
  commonSkillGaps: SkillFrequency[]
  bestFitRoles: BestFitRole[]
}

export function getDashboard() {
  return apiClient.get<Dashboard>('/api/dashboard').then((r) => r.data)
}
