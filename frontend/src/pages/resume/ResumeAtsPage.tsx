import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { getAtsScore, type AtsCheck, type AtsScore } from '../../api/atsScore'

const GRADE_LABEL: Record<string, string> = {
  excellent: 'Excellent',
  good: 'Good',
  needs_work: 'Needs Work',
  poor: 'Needs Improvement',
}

function grade(score: number): string {
  if (score >= 90) return 'excellent'
  if (score >= 70) return 'good'
  if (score >= 50) return 'needs_work'
  return 'poor'
}

const STATUS_ICON: Record<AtsCheck['status'], string> = {
  PASS: '✓',
  WARN: '!',
  FAIL: '✗',
}

function CheckCard({ check }: { check: AtsCheck }) {
  return (
    <div className={`ats-check ats-check--${check.status.toLowerCase()}`}>
      <span className="ats-check__icon" aria-hidden="true">{STATUS_ICON[check.status]}</span>
      <div>
        <div className="ats-check__label">{check.label}</div>
        <div className="ats-check__detail">{check.detail}</div>
      </div>
    </div>
  )
}

export function ResumeAtsPage() {
  const { id } = useParams<{ id: string }>()
  const [ats, setAts] = useState<AtsScore | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!id) return
    getAtsScore(Number(id))
      .then(setAts)
      .catch(() => setError('Could not load the ATS report for this resume.'))
      .finally(() => setLoading(false))
  }, [id])

  if (loading) return <div className="page-loading">Loading...</div>
  if (error || !ats) return <div className="page"><p>{error ?? 'Report not found.'}</p></div>

  const failCount = ats.checks.filter((c) => c.status === 'FAIL').length
  const warnCount = ats.checks.filter((c) => c.status === 'WARN').length
  const passCount = ats.checks.filter((c) => c.status === 'PASS').length

  return (
    <div className="page">
      <Link to="/resume" className="link-button">&larr; Back to resume</Link>

      <div className="report-tabs" style={{ marginTop: 14 }}>
        <Link to={`/resume/quality/${ats.resumeId}`}>Writing Quality</Link>
        <span className="active">ATS Score</span>
      </div>

      <div className="fit-header">
        <div className={`fit-score quality-score--${grade(ats.score)}`}>
          <div className="fit-score__label">ATS COMPATIBILITY</div>
          <div className="fit-score__value">{ats.score}</div>
          <div className="fit-score__recommendation">{GRADE_LABEL[grade(ats.score)]}</div>
        </div>
        <div className="fit-summary">
          <h1>ATS compatibility report</h1>
          <p className="muted">
            How reliably an Applicant Tracking System is likely to parse this resume - contact info,
            standard sections, dates, and clean text extraction. This is a different lens from Writing
            Quality: a resume can read beautifully to a human and still trip up automated parsing, or
            vice versa.
          </p>
          <div className="stat-grid">
            <div className="stat-card"><div className="stat-card__value">{passCount}</div><div className="stat-card__label">Passing</div></div>
            <div className="stat-card"><div className="stat-card__value">{warnCount}</div><div className="stat-card__label">Warnings</div></div>
            <div className="stat-card"><div className="stat-card__value">{failCount}</div><div className="stat-card__label">Failing</div></div>
          </div>
        </div>
      </div>

      <section>
        <h3>Parseability checks</h3>
        <div className="ats-check-grid">
          {ats.checks.map((check) => (
            <CheckCard check={check} key={check.label} />
          ))}
        </div>
      </section>
    </div>
  )
}
