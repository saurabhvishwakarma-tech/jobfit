import { useCallback, useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import {
  APPLICATION_STATUSES,
  deleteApplication,
  getApplication,
  updateApplicationNotes,
  updateApplicationStatus,
  type ApplicationDetail,
  type ApplicationStatus,
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

export function ApplicationDetailPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const [application, setApplication] = useState<ApplicationDetail | null>(null)
  const [notes, setNotes] = useState('')
  const [loading, setLoading] = useState(true)
  const [savingNotes, setSavingNotes] = useState(false)

  const load = useCallback(async () => {
    if (!id) return
    const detail = await getApplication(Number(id))
    setApplication(detail)
    setNotes(detail.notes ?? '')
  }, [id])

  useEffect(() => {
    load().finally(() => setLoading(false))
  }, [load])

  async function handleStatusChange(status: ApplicationStatus) {
    if (!application) return
    await updateApplicationStatus(application.id, status)
    await load()
  }

  async function handleSaveNotes() {
    if (!application) return
    setSavingNotes(true)
    try {
      await updateApplicationNotes(application.id, notes)
      await load()
    } finally {
      setSavingNotes(false)
    }
  }

  async function handleDelete() {
    if (!application) return
    await deleteApplication(application.id)
    navigate('/applications')
  }

  if (loading) return <div className="page-loading">Loading...</div>
  if (!application) return <div className="page"><p>Application not found.</p></div>

  return (
    <div className="page">
      <button className="link-button" onClick={() => navigate('/applications')}>&larr; Back to applications</button>

      <div className="resume-view__header">
        <h1>{application.jobTitle ?? 'Untitled job'}</h1>
        <button className="link-button link-button--danger" onClick={handleDelete}>Remove</button>
      </div>
      <p className="muted">{application.company ?? 'Unknown company'}</p>

      <section>
        <h3>Status</h3>
        <select
          className="status-select"
          value={application.status}
          onChange={(e) => handleStatusChange(e.target.value as ApplicationStatus)}
        >
          {APPLICATION_STATUSES.map((s) => (
            <option key={s} value={s}>{STATUS_LABEL[s]}</option>
          ))}
        </select>
        {application.matchScore !== null && (
          <p className="muted" style={{ marginTop: 10 }}>
            Fit score at time of analysis: <strong>{application.matchScore}%</strong>
            {application.matchAnalysisId && (
              <> — <a href={`/match-analyses/${application.matchAnalysisId}`}>view analysis</a></>
            )}
          </p>
        )}
      </section>

      <section>
        <h3>Notes</h3>
        <textarea
          className="jd-textarea"
          style={{ minHeight: 120 }}
          value={notes}
          onChange={(e) => setNotes(e.target.value)}
          placeholder="Recruiter contact, interview prep notes, follow-ups..."
        />
        <button onClick={handleSaveNotes} disabled={savingNotes}>{savingNotes ? 'Saving...' : 'Save notes'}</button>
      </section>

      <section>
        <h3>History</h3>
        <ul className="history-list">
          {application.history.map((h, i) => (
            <li key={i}>
              <span className="status-pill status-pill--ready">{STATUS_LABEL[h.status]}</span>
              <span className="muted">{new Date(h.changedAt).toLocaleString()}</span>
              {h.notes && <span className="history-note">{h.notes}</span>}
            </li>
          ))}
        </ul>
      </section>
    </div>
  )
}
