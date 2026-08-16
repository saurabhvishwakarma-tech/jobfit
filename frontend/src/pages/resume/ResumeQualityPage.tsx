import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { getResumeQuality, type QualityIssue, type ResumeQuality } from '../../api/resumeQuality'

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

function IssueRow({ issue }: { issue: QualityIssue }) {
  return (
    <div className={`quality-issue quality-issue--${issue.severity.toLowerCase()}`}>
      <span className={`severity-badge severity-badge--${issue.severity.toLowerCase()}`}>{issue.severity}</span>
      <span className="quality-issue__message">{issue.message}</span>
    </div>
  )
}

export function ResumeQualityPage() {
  const { id } = useParams<{ id: string }>()
  const [quality, setQuality] = useState<ResumeQuality | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!id) return
    getResumeQuality(Number(id))
      .then(setQuality)
      .catch(() => setError('Could not load the quality report for this resume.'))
      .finally(() => setLoading(false))
  }, [id])

  if (loading) return <div className="page-loading">Loading...</div>
  if (error || !quality) return <div className="page"><p>{error ?? 'Report not found.'}</p></div>

  const categories = Array.from(new Set(quality.issues.map((i) => i.category)))

  return (
    <div className="page">
      <Link to="/resume" className="link-button">&larr; Back to resume</Link>

      <div className="report-tabs" style={{ marginTop: 14 }}>
        <span className="active">Writing Quality</span>
        <Link to={`/resume/ats/${quality.resumeId}`}>ATS Score</Link>
      </div>

      <div className="fit-header">
        <div className={`fit-score quality-score--${grade(quality.score)}`}>
          <div className="fit-score__label">RESUME QUALITY</div>
          <div className="fit-score__value">{quality.score}</div>
          <div className="fit-score__recommendation">{GRADE_LABEL[grade(quality.score)]}</div>
        </div>
        <div className="fit-summary">
          <h1>Resume quality report</h1>
          <p className="muted">
            A deterministic writing check - no AI, no guesswork. Every issue below points at something
            specific you can fix.
          </p>
          <div className="stat-grid">
            <div className="stat-card"><div className="stat-card__value">{quality.highCount}</div><div className="stat-card__label">High priority</div></div>
            <div className="stat-card"><div className="stat-card__value">{quality.mediumCount}</div><div className="stat-card__label">Medium priority</div></div>
            <div className="stat-card"><div className="stat-card__value">{quality.lowCount}</div><div className="stat-card__label">Polish</div></div>
          </div>
        </div>
      </div>

      {quality.issues.length === 0 ? (
        <p className="muted">No issues found - this resume reads cleanly. Nice work.</p>
      ) : (
        categories.map((category) => (
          <section key={category} className="dashboard-section">
            <h3>{category}</h3>
            {quality.issues.filter((i) => i.category === category).map((issue, idx) => (
              <IssueRow issue={issue} key={`${category}-${idx}`} />
            ))}
          </section>
        ))
      )}
    </div>
  )
}
