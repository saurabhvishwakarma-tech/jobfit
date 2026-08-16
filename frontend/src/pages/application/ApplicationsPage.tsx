import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import {
  APPLICATION_STATUSES,
  listApplications,
  updateApplicationStatus,
  type ApplicationStatus,
  type ApplicationSummary,
} from '../../api/application'

const STATUS_LABEL: Record<ApplicationStatus, string> = {
  SAVED: 'Saved',
  APPLIED: 'Applied',
  ONLINE_ASSESSMENT: 'Online Assessment',
  INTERVIEW: 'Interview',
  OFFER: 'Offer',
  REJECTED: 'Rejected',
  WITHDRAWN: 'Withdrawn',
}

export function ApplicationsPage() {
  const [applications, setApplications] = useState<ApplicationSummary[]>([])
  const [loading, setLoading] = useState(true)

  const refresh = useCallback(async () => {
    setApplications(await listApplications())
  }, [])

  useEffect(() => {
    refresh().finally(() => setLoading(false))
  }, [refresh])

  async function handleStatusChange(id: number, status: ApplicationStatus) {
    await updateApplicationStatus(id, status)
    await refresh()
  }

  if (loading) return <div className="page-loading">Loading...</div>

  return (
    <div className="page">
      <h1>Applications</h1>

      {applications.length === 0 && (
        <p className="muted">
          Nothing tracked yet. Analyse a job's fit and track it from there, or track a job directly.
        </p>
      )}

      <div className="application-table">
        {applications.map((app) => (
          <div className="application-row" key={app.id}>
            <div className="application-row__main">
              <Link to={`/applications/${app.id}`} className="job-card__title">{app.jobTitle ?? 'Untitled job'}</Link>
              <div className="muted">{app.company ?? 'Unknown company'}</div>
            </div>
            {app.matchScore !== null && (
              <div className={`mini-score mini-score--${scoreClass(app.matchScore)}`}>{app.matchScore}%</div>
            )}
            <select
              className="status-select"
              value={app.status}
              onChange={(e) => handleStatusChange(app.id, e.target.value as ApplicationStatus)}
            >
              {APPLICATION_STATUSES.map((s) => (
                <option key={s} value={s}>{STATUS_LABEL[s]}</option>
              ))}
            </select>
          </div>
        ))}
      </div>
    </div>
  )
}

function scoreClass(score: number): string {
  if (score >= 80) return 'strong'
  if (score >= 60) return 'reasonable'
  if (score >= 40) return 'stretch'
  return 'poor'
}
