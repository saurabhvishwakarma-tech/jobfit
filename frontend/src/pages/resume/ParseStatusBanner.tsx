import type { ParseStatus } from '../../api/resume'

export function ParseStatusBanner({ status, error }: { status: ParseStatus; error: string | null }) {
  if (status === 'READY') return null

  if (status === 'FAILED') {
    return (
      <div className="status-banner status-banner--error">
        <strong>We couldn't parse this resume.</strong>
        <p>{error ?? 'An unexpected error occurred.'}</p>
      </div>
    )
  }

  return (
    <div className="status-banner status-banner--pending">
      <span className="spinner" aria-hidden="true" />
      {status === 'PENDING' ? 'Queued for parsing...' : 'Parsing your resume...'}
    </div>
  )
}
