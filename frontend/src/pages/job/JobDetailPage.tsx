import { useCallback, useEffect, useRef, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { getJob, type JobDetail, type JobRequirementItem } from '../../api/job'
import { analyseJob } from '../../api/matching'
import { createApplication } from '../../api/application'
import { ParseStatusBanner } from '../resume/ParseStatusBanner'
import { isAxiosError } from 'axios'

const POLL_INTERVAL_MS = 2000

function RequirementGroup({ title, items }: { title: string; items: JobRequirementItem[] }) {
  if (items.length === 0) return null
  return (
    <section>
      <h3>{title}</h3>
      <ul className="requirement-list">
        {items.map((item) => (
          <li key={item.id}>
            {item.text}
            {item.skillName && <span className="requirement-skill-tag">{item.skillName}</span>}
          </li>
        ))}
      </ul>
    </section>
  )
}

export function JobDetailPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const [job, setJob] = useState<JobDetail | null>(null)
  const [loading, setLoading] = useState(true)
  const [analysing, setAnalysing] = useState(false)
  const [analyseError, setAnalyseError] = useState<string | null>(null)
  const [tracking, setTracking] = useState(false)
  const [trackError, setTrackError] = useState<string | null>(null)
  const pollRef = useRef<ReturnType<typeof setInterval> | null>(null)

  const load = useCallback(async () => {
    if (!id) return
    const detail = await getJob(Number(id))
    setJob(detail)
    return detail
  }, [id])

  useEffect(() => {
    load().finally(() => setLoading(false))
    return () => {
      if (pollRef.current) clearInterval(pollRef.current)
    }
  }, [load])

  useEffect(() => {
    if (job && (job.parseStatus === 'PENDING' || job.parseStatus === 'PROCESSING')) {
      pollRef.current = setInterval(async () => {
        const updated = await load()
        if (updated && (updated.parseStatus === 'READY' || updated.parseStatus === 'FAILED') && pollRef.current) {
          clearInterval(pollRef.current)
        }
      }, POLL_INTERVAL_MS)
      return () => {
        if (pollRef.current) clearInterval(pollRef.current)
      }
    }
  }, [job, load])

  async function handleAnalyse() {
    if (!job) return
    setAnalysing(true)
    setAnalyseError(null)
    try {
      const analysis = await analyseJob(job.id)
      navigate(`/match-analyses/${analysis.id}`)
    } catch (err) {
      if (isAxiosError(err) && err.response?.status === 409) {
        setAnalyseError(err.response.data?.message ?? 'Could not analyse this job yet.')
      } else {
        setAnalyseError('Could not analyse this job. Please try again.')
      }
    } finally {
      setAnalysing(false)
    }
  }

  async function handleTrack() {
    if (!job) return
    setTracking(true)
    setTrackError(null)
    try {
      const application = await createApplication({ jobId: job.id })
      navigate(`/applications/${application.id}`)
    } catch (err) {
      if (isAxiosError(err) && err.response?.status === 409) {
        setTrackError('You already have this job tracked in your applications.')
      } else {
        setTrackError('Could not track this application. Please try again.')
      }
    } finally {
      setTracking(false)
    }
  }

  if (loading) return <div className="page-loading">Loading...</div>
  if (!job) return <div className="page"><p>Job not found.</p></div>

  return (
    <div className="page">
      <button className="link-button" onClick={() => navigate('/jobs')}>&larr; Back to jobs</button>
      <div className="resume-view__header">
        <h1>{job.title}</h1>
        <div className="editor-actions">
          <button className="secondary" onClick={handleTrack} disabled={tracking}>
            {tracking ? 'Tracking...' : 'Track application'}
          </button>
          {job.parseStatus === 'READY' && (
            <button onClick={handleAnalyse} disabled={analysing}>
              {analysing ? 'Analysing...' : 'Analyse fit'}
            </button>
          )}
        </div>
      </div>
      <p className="muted">{job.company ?? 'Unknown company'}</p>
      {analyseError && <div className="form-error" role="alert">{analyseError}</div>}
      {trackError && <div className="form-error" role="alert">{trackError}</div>}

      <ParseStatusBanner status={job.parseStatus} error={job.parseError} />

      {job.parseStatus === 'READY' && (
        <div className="resume-view">
          {job.experienceYears && (
            <section>
              <h3>Experience required</h3>
              <p>{job.experienceYears.text}</p>
            </section>
          )}
          <RequirementGroup title="Required skills" items={job.requiredSkills} />
          <RequirementGroup title="Preferred skills" items={job.preferredSkills} />
          <RequirementGroup title="Responsibilities" items={job.responsibilities} />
          <RequirementGroup title="Education" items={job.education} />
          <RequirementGroup title="Domain knowledge" items={job.domain} />
          <RequirementGroup title="Soft skills" items={job.softSkills} />

          {job.requiredSkills.length === 0 && job.preferredSkills.length === 0 &&
            job.responsibilities.length === 0 && (
              <p className="muted">
                We couldn't identify clearly labelled sections in this job description
                (e.g. "Requirements", "Responsibilities"). Fit scoring will be less precise for this job -
                try a posting with clearer section headings if possible.
              </p>
          )}

          <section>
            <h3>Full description</h3>
            <p className="raw-description">{job.rawDescription}</p>
          </section>
        </div>
      )}

      {job.parseStatus === 'FAILED' && (
        <Link to="/jobs">Go back and try adding this job again</Link>
      )}
    </div>
  )
}
