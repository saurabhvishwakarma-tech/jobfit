import { apiClient } from './client'

export type ParseStatus = 'PENDING' | 'PROCESSING' | 'READY' | 'FAILED'

export interface ResumeSummary {
  id: number
  versionNo: number
  current: boolean
  originalFilename: string
  parseStatus: ParseStatus
  parseError: string | null
  uploadedAt: string
  parsedAt: string | null
}

export interface ContactInfo {
  fullName: string | null
  email: string | null
  phone: string | null
  location: string | null
  linkedinUrl: string | null
  githubUrl: string | null
  portfolioUrl: string | null
}

export interface Highlight {
  text: string
}

export interface Experience {
  id: number | null
  jobTitle: string
  company: string
  location: string | null
  startDate: string | null
  endDate: string | null
  current: boolean
  highlights: Highlight[]
}

export interface Education {
  id: number | null
  institution: string
  degree: string | null
  fieldOfStudy: string | null
  startDate: string | null
  endDate: string | null
}

export interface Certification {
  id: number | null
  name: string
  issuer: string | null
  issuedDate: string | null
}

export interface ProjectItem {
  id: number | null
  name: string
  description: string | null
  technologies: string | null
}

export interface SkillTag {
  skillId: number
  name: string
  category: string
  source: 'EXPLICIT' | 'INFERRED'
}

export interface ResumeDetail extends ResumeSummary {
  contactInfo: ContactInfo
  experiences: Experience[]
  education: Education[]
  certifications: Certification[]
  projects: ProjectItem[]
  skills: SkillTag[]
}

export interface ResumeUpdatePayload {
  contactInfo: ContactInfo
  experiences: Experience[]
  education: Education[]
  certifications: Certification[]
  projects: ProjectItem[]
}

export function uploadResume(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return apiClient
    .post<ResumeSummary>('/api/resumes', formData)
    .then((r) => r.data)
}

export function listResumes() {
  return apiClient.get<ResumeSummary[]>('/api/resumes').then((r) => r.data)
}

export function getResume(id: number) {
  return apiClient.get<ResumeDetail>(`/api/resumes/${id}`).then((r) => r.data)
}

export function updateResume(id: number, payload: ResumeUpdatePayload) {
  return apiClient.patch<ResumeDetail>(`/api/resumes/${id}`, payload).then((r) => r.data)
}

export function deleteResume(id: number) {
  return apiClient.delete(`/api/resumes/${id}`)
}
