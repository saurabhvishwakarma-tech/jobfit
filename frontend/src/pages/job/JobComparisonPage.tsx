import { useEffect, useMemo, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { compareJobs, type ComparedJob, type JobComparison } from '../../api/comparison'
import { isAxiosError } from 'axios'

export function JobComparisonPage() {
  const [searchParams] = useSearchParams()
  const jobIdsParam = searchParams.get('jobIds')
  const jobIds = useMemo(() => parseJobIds(jobIdsParam), [jobIdsParam])

  const [comparison, setComparison] = useState<JobComparison | null>(null)
  const [loading, setLoading] = useState(jobIds.length >= 2)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (jobIds.length < 2) return
    setLoading(true)
    setError(null)
    compareJobs(jobIds)
      .then(setComparison)
      .catch((err) => {
        const message = isAxiosError(err) && err.response?.data?.message
          ? err.response.data.message
          : 'Could not compare these jobs. Please try again.'
        setError(message)
      })
      .finally(() => setLoading(false))
  }, [jobIds])

  if (jobIds.length < 2) {
    return (
      <div className="page">
        <h1>Compare jobs</h1>
        <p className="muted">
          Select at least two jobs from the <Link to="/jobs">Jobs page</Link> to compare them side by side.
        </p>
      </div>
    )
  }

  if (loading) return <div className="page-loading">Loading...</div>

  return (
    <div className="page">
      <h1>Compare jobs</h1>
      {error && <div className="form-error" role="alert">{error}</div>}

      {comparison && (
        <>
          <div className="compare-header">
            {comparison.jobs.map((job) => (
              <div className="compare-header__job" key={job.jobId}>
                <Link to={`/jobs/${job.jobId}`} className="job-card__title">{job.title}</Link>
                <div className="muted">{job.company ?? 'Unknown company'}</div>
                {job.analysed ? (
                  <div className={`mini-score mini-score--${scoreClass(job.overallScore ?? 0)}`}>
                    {job.overallScore}%
                  </div>
                ) : (
                  <div className="muted compare-not-analysed">Not analysed yet</div>
                )}
              </div>
            ))}
          </div>

          <section className="dashboard-section">
            <h3>Score breakdown</h3>
            <CategoryScoreTable jobs={comparison.jobs} />
          </section>

          <section className="dashboard-section">
            <h3>Skill overlap</h3>
            {comparison.skillComparison.length === 0 ? (
              <p className="muted">None of these jobs have identifiable skill requirements to compare.</p>
            ) : (
              <table className="compare-table">
                <thead>
                  <tr>
                    <th>Skill</th>
                    {comparison.jobs.map((job) => <th key={job.jobId}>{job.title}</th>)}
                    <th>You</th>
                  </tr>
                </thead>
                <tbody>
                  {comparison.skillComparison.map((row) => (
                    <tr key={row.skillName}>
                      <td>{row.skillName}</td>
                      {row.requirementPerJob.map((req, i) => (
                        <td key={comparison.jobs[i].jobId}>
                          {req ? <span className={`requirement-badge requirement-badge--${req.toLowerCase()}`}>{req}</span> : '—'}
                        </td>
                      ))}
                      <td>
                        <span className={`resume-status resume-status--${row.resumeStatus.toLowerCase()}`}>
                          {resumeStatusLabel(row.resumeStatus)}
                        </span>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </section>
        </>
      )}
    </div>
  )
}

function CategoryScoreTable({ jobs }: { jobs: ComparedJob[] }) {
  const categoryNames: string[] = []
  jobs.forEach((job) => job.categoryScores.forEach((c) => {
    if (!categoryNames.includes(c.category)) categoryNames.push(c.category)
  }))

  if (categoryNames.length === 0) {
    return <p className="muted">None of these jobs have been analysed yet.</p>
  }

  return (
    <table className="compare-table">
      <thead>
        <tr>
          <th>Category</th>
          {jobs.map((job) => <th key={job.jobId}>{job.title}</th>)}
        </tr>
      </thead>
      <tbody>
        {categoryNames.map((category) => (
          <tr key={category}>
            <td>{category}</td>
            {jobs.map((job) => {
              const component = job.categoryScores.find((c) => c.category === category)
              return (
                <td key={job.jobId}>
                  {component ? `${component.earnedPoints}/${component.maxPoints}` : '—'}
                </td>
              )
            })}
          </tr>
        ))}
      </tbody>
    </table>
  )
}

function parseJobIds(raw: string | null): number[] {
  if (!raw) return []
  return raw.split(',').map((s) => Number(s.trim())).filter((n) => Number.isInteger(n) && n > 0)
}

function resumeStatusLabel(status: string): string {
  if (status === 'EXPLICIT') return 'You have this'
  if (status === 'INFERRED') return 'Possibly (inferred)'
  return 'Missing'
}

function scoreClass(score: number): string {
  if (score >= 80) return 'strong'
  if (score >= 60) return 'reasonable'
  if (score >= 40) return 'stretch'
  return 'poor'
}
