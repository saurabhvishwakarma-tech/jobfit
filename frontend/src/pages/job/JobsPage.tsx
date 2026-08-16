import { useCallback, useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { createJob, deleteJob, listJobs, type JobCreatePayload, type JobSummary } from '../../api/job'
import { AddJobForm } from './AddJobForm'

const STATUS_LABEL: Record<string, string> = {
  PENDING: 'Queued',
  PROCESSING: 'Parsing...',
  READY: 'Ready',
  FAILED: 'Failed',
}

const MAX_COMPARE = 5

export function JobsPage() {
  const navigate = useNavigate()
  const [jobs, setJobs] = useState<JobSummary[]>([])
  const [loading, setLoading] = useState(true)
  const [showForm, setShowForm] = useState(false)
  const [selected, setSelected] = useState<number[]>([])

  const refresh = useCallback(async () => {
    const data = await listJobs()
    setJobs(data)
    return data
  }, [])

  useEffect(() => {
    refresh().finally(() => setLoading(false))
  }, [refresh])

  // Poll while any job is still parsing, so statuses update without a manual refresh.
  useEffect(() => {
    const hasPending = jobs.some((j) => j.parseStatus === 'PENDING' || j.parseStatus === 'PROCESSING')
    if (!hasPending) return
    const interval = setInterval(refresh, 2500)
    return () => clearInterval(interval)
  }, [jobs, refresh])

  async function handleCreate(payload: JobCreatePayload) {
    await createJob(payload)
    setShowForm(false)
    await refresh()
  }

  async function handleDelete(id: number) {
    setSelected((prev) => prev.filter((s) => s !== id))
    await deleteJob(id)
    await refresh()
  }

  function toggleSelected(id: number) {
    setSelected((prev) => {
      if (prev.includes(id)) return prev.filter((s) => s !== id)
      if (prev.length >= MAX_COMPARE) return prev
      return [...prev, id]
    })
  }

  function handleCompare() {
    navigate(`/compare?jobIds=${selected.join(',')}`)
  }

  if (loading) return <div className="page-loading">Loading...</div>

  return (
    <div className="page">
      <div className="resume-view__header">
        <h1>Jobs</h1>
        <div className="editor-actions">
          {selected.length >= 2 && (
            <button className="secondary" onClick={handleCompare}>
              Compare selected ({selected.length})
            </button>
          )}
          {!showForm && <button onClick={() => setShowForm(true)}>+ Add job</button>}
        </div>
      </div>

      {showForm && <AddJobForm onCreate={handleCreate} onCancel={() => setShowForm(false)} />}

      {jobs.length === 0 && !showForm && (
        <p className="muted">No jobs yet. Add one to see its structured requirements and, soon, your fit score.</p>
      )}

      {jobs.length >= 2 && (
        <p className="muted compare-hint">Select up to {MAX_COMPARE} jobs to compare them side by side.</p>
      )}

      <div className="job-list">
        {jobs.map((job) => (
          <div className="job-card" key={job.id}>
            <div className="job-card__select">
              <input
                type="checkbox"
                aria-label={`Select ${job.title} for comparison`}
                checked={selected.includes(job.id)}
                onChange={() => toggleSelected(job.id)}
              />
              <div>
                <Link to={`/jobs/${job.id}`} className="job-card__title">{job.title}</Link>
                <div className="muted">{job.company ?? 'Unknown company'}</div>
              </div>
            </div>
            <div className="job-card__right">
              <span className={`status-pill status-pill--${job.parseStatus.toLowerCase()}`}>
                {STATUS_LABEL[job.parseStatus]}
              </span>
              <button className="link-button link-button--danger" onClick={() => handleDelete(job.id)}>
                Delete
              </button>
            </div>
          </div>
        ))}
      </div>
    </div>
  )
}
