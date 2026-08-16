import { useCallback, useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import {
  deleteResume,
  getResume,
  listResumes,
  updateResume,
  uploadResume,
  type ResumeDetail,
  type ResumeUpdatePayload,
} from '../../api/resume'
import { ResumeUploadCard } from './ResumeUploadCard'
import { ParseStatusBanner } from './ParseStatusBanner'
import { ResumeDetailEditor } from './ResumeDetailEditor'

const POLL_INTERVAL_MS = 2000

export function ResumePage() {
  const [resume, setResume] = useState<ResumeDetail | null>(null)
  const [loading, setLoading] = useState(true)
  const pollRef = useRef<ReturnType<typeof setInterval> | null>(null)

  const stopPolling = useCallback(() => {
    if (pollRef.current) {
      clearInterval(pollRef.current)
      pollRef.current = null
    }
  }, [])

  const loadCurrent = useCallback(async () => {
    const resumes = await listResumes()
    const current = resumes.find((r) => r.current) ?? resumes[0] ?? null
    if (!current) {
      setResume(null)
      return
    }
    const detail = await getResume(current.id)
    setResume(detail)
    return detail
  }, [])

  useEffect(() => {
    loadCurrent().finally(() => setLoading(false))
    return stopPolling
  }, [loadCurrent, stopPolling])

  useEffect(() => {
    if (resume && (resume.parseStatus === 'PENDING' || resume.parseStatus === 'PROCESSING')) {
      pollRef.current = setInterval(async () => {
        const updated = await getResume(resume.id)
        setResume(updated)
        if (updated.parseStatus === 'READY' || updated.parseStatus === 'FAILED') {
          stopPolling()
        }
      }, POLL_INTERVAL_MS)
      return stopPolling
    }
  }, [resume, stopPolling]);

  async function handleUpload(file: File) {
    const summary = await uploadResume(file)
    const detail = await getResume(summary.id)
    setResume(detail)
  }

  async function handleSave(payload: ResumeUpdatePayload) {
    if (!resume) return
    const updated = await updateResume(resume.id, payload)
    setResume(updated)
  }

  async function handleReplace() {
    if (!resume) return
    await deleteResume(resume.id)
    setResume(null)
  }

  if (loading) {
    return <div className="page-loading">Loading...</div>
  }

  return (
    <div className="page">
      <h1>My Resume</h1>

      {!resume && <ResumeUploadCard onUpload={handleUpload} />}

      {resume && (
        <>
          <ParseStatusBanner status={resume.parseStatus} error={resume.parseError} />
          {resume.parseStatus === 'READY' && (
            <p className="upload-another editor-actions">
              <Link to={`/resume/quality/${resume.id}`}>Check resume quality &rarr;</Link>
              <Link to={`/resume/ats/${resume.id}`}>Check ATS score &rarr;</Link>
            </p>
          )}
          {resume.parseStatus === 'READY' && <ResumeDetailEditor resume={resume} onSave={handleSave} />}
          {resume.parseStatus === 'FAILED' && (
            <button onClick={handleReplace}>Delete and try a different file</button>
          )}
          {resume.parseStatus === 'READY' && (
            <p className="muted upload-another">
              <button className="link-button" onClick={handleReplace}>Upload a different resume instead</button>
            </p>
          )}
        </>
      )}
    </div>
  )
}
