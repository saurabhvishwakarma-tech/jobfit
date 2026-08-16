import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { getMatchAnalysis, type EvidenceItem, type MatchAnalysisDetail } from '../../api/matching'
import { createApplication, listApplications } from '../../api/application'
import { isAxiosError } from 'axios'

const RECOMMENDATION_LABEL: Record<string, string> = {
  STRONG_MATCH: 'Strong Match',
  REASONABLE_MATCH: 'Reasonable Match',
  STRETCH_APPLICATION: 'Stretch Application',
  POOR_MATCH: 'Poor Match',
}

const RECOMMENDATION_VERDICT: Record<string, string> = {
  STRONG_MATCH: 'Apply',
  REASONABLE_MATCH: 'Apply',
  STRETCH_APPLICATION: 'Consider applying',
  POOR_MATCH: 'Probably skip this one',
}

const REQUIREMENT_TYPE_LABEL: Record<string, string> = {
  REQUIRED_SKILL: 'Required skill',
  PREFERRED_SKILL: 'Preferred skill',
  RESPONSIBILITY: 'Responsibility',
  EDUCATION: 'Education',
  DOMAIN: 'Domain knowledge',
  SOFT_SKILL: 'Soft skill',
  EXPERIENCE_YEARS: 'Experience',
}

function EvidenceRow({ item }: { item: EvidenceItem }) {
  const icon = item.strength === 'STRONG' ? '✓' : item.strength === 'PARTIAL' ? '△' : '✗'
  return (
    <div className={`evidence-row evidence-row--${item.strength.toLowerCase()}`}>
      <div className="evidence-row__header">
        <span className="evidence-icon">{icon}</span>
        <span className="evidence-requirement">{item.requirementText}</span>
        <span className="evidence-type-tag">{REQUIREMENT_TYPE_LABEL[item.requirementType] ?? item.requirementType}</span>
      </div>
      <div className="evidence-row__body">
        <span className={`match-type-badge match-type-badge--${item.matchType.toLowerCase()}`}>
          {item.matchType === 'EXPLICIT' ? 'Explicit' : item.matchType === 'INFERRED' ? 'Inferred' : 'Absent'}
        </span>
        <span className="evidence-text">
          {item.resumeRefText ? `"${item.resumeRefText}"` : item.explanationText}
        </span>
      </div>
    </div>
  )
}

export function JobAnalysisPage() {
  const { id } = useParams<{ id: string }>()
  const [analysis, setAnalysis] = useState<MatchAnalysisDetail | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [tracking, setTracking] = useState(false)
  const [trackError, setTrackError] = useState<string | null>(null)
  const [appliedApplicationId, setAppliedApplicationId] = useState<number | null>(null)

  useEffect(() => {
    if (!id) return
    getMatchAnalysis(Number(id))
      .then(async (detail) => {
        setAnalysis(detail)
        // Check whether this job is already tracked (e.g. the user applied
        // earlier, or from the job detail page) so we can show the clean
        // "already applied" state right away instead of the action button.
        try {
          const applications = await listApplications()
          const existing = applications.find((a) => a.jobId === detail.jobId)
          if (existing) setAppliedApplicationId(existing.id)
        } catch {
          // Non-critical - just means we won't pre-detect an existing application.
        }
      })
      .catch(() => setError('Could not load this analysis.'))
      .finally(() => setLoading(false))
  }, [id])

  async function handleTrack() {
    if (!analysis) return
    setTracking(true)
    setTrackError(null)
    try {
      const application = await createApplication({
        jobId: analysis.jobId,
        resumeId: analysis.resumeId,
        matchAnalysisId: analysis.id,
      })
      setAppliedApplicationId(application.id)
    } catch (err) {
      if (isAxiosError(err) && err.response?.status === 409) {
        // Already tracked elsewhere - that's the same end state the user
        // wants, so treat it as success rather than an error.
        const applications = await listApplications().catch(() => [])
        const existing = applications.find((a) => a.jobId === analysis.jobId)
        if (existing) {
          setAppliedApplicationId(existing.id)
        } else {
          setTrackError('You already have this job tracked in your applications.')
        }
      } else {
        setTrackError('Could not track this application. Please try again.')
      }
    } finally {
      setTracking(false)
    }
  }

  if (loading) return <div className="page-loading">Loading...</div>
  if (error || !analysis) return <div className="page"><p>{error ?? 'Analysis not found.'}</p></div>

  const strong = analysis.evidence.filter((e) => e.strength === 'STRONG')
  const partial = analysis.evidence.filter((e) => e.strength === 'PARTIAL')
  const missing = analysis.evidence.filter((e) => e.strength === 'MISSING')

  return (
    <div className="page analysis-page">
      <Link className="link-button" to={`/jobs/${analysis.jobId}`}>&larr; Back to job</Link>

      <div className="fit-header">
        <div className={`fit-score fit-score--${analysis.recommendation.toLowerCase()}`}>
          <div className="fit-score__label">JOB FIT</div>
          <div className="fit-score__value">{analysis.overallScore}%</div>
          <div className="fit-score__recommendation">{RECOMMENDATION_LABEL[analysis.recommendation]}</div>
        </div>
        <div className="fit-summary">
          <h1>{analysis.jobTitle}</h1>
          <p className="muted">{analysis.company ?? 'Unknown company'}</p>
          <div className="verdict-banner">
            <strong>{RECOMMENDATION_VERDICT[analysis.recommendation]}</strong>
            <p>{analysis.recommendationReason}</p>
          </div>

          {appliedApplicationId === null ? (
            <div style={{ marginTop: 14 }}>
              <button onClick={handleTrack} disabled={tracking}>
                {tracking ? 'Marking as applied...' : 'Mark as applied'}
              </button>
              {trackError && <div className="form-error" role="alert" style={{ marginTop: 8 }}>{trackError}</div>}
            </div>
          ) : (
            <div className="applied-banner">
              <span className="applied-banner__check" aria-hidden="true">✓</span>
              <span>
                You've applied to this role. <Link to={`/applications/${appliedApplicationId}`}>View in Applications</Link>
              </span>
            </div>
          )}
        </div>
      </div>

      <section>
        <h3>Score breakdown</h3>
        <div className="score-bars">
          {analysis.components.map((c) => (
            <div className="score-bar" key={c.category}>
              <div className="score-bar__label">
                <span>{c.category}</span>
                <span>{c.earnedPoints}/{c.maxPoints}</span>
              </div>
              <div className="score-bar__track">
                <div
                  className="score-bar__fill"
                  style={{ width: `${Math.min(100, (c.earnedPoints / c.maxPoints) * 100)}%` }}
                />
              </div>
              <p className="score-bar__explanation">{c.explanation}</p>
            </div>
          ))}
        </div>
      </section>

      {strong.length > 0 && (
        <section>
          <h3>Strong matches</h3>
          {strong.map((e) => <EvidenceRow key={e.requirementId + e.requirementText} item={e} />)}
        </section>
      )}

      {partial.length > 0 && (
        <section>
          <h3>Partial matches</h3>
          {partial.map((e) => <EvidenceRow key={e.requirementId + e.requirementText} item={e} />)}
        </section>
      )}

      {missing.length > 0 && (
        <section>
          <h3>Missing</h3>
          {missing.map((e) => <EvidenceRow key={e.requirementId + e.requirementText} item={e} />)}
        </section>
      )}

      <p className="muted analysis-footer">
        <Link to={`/jobs/${analysis.jobId}`}>Back to job details</Link>
      </p>
    </div>
  )
}
