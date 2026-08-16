import { type FormEvent, useState } from 'react'
import type { JobCreatePayload } from '../../api/job'

export function AddJobForm({ onCreate, onCancel }: {
  onCreate: (payload: JobCreatePayload) => Promise<void>
  onCancel: () => void
}) {
  const [title, setTitle] = useState('')
  const [company, setCompany] = useState('')
  const [rawDescription, setRawDescription] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setSubmitting(true)
    try {
      await onCreate({ title, company: company || undefined, rawDescription })
    } catch {
      setError('Could not save this job. Please try again.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <form className="add-job-form" onSubmit={handleSubmit}>
      <h2>Add a job</h2>
      {error && <div className="form-error" role="alert">{error}</div>}
      <div className="form-grid">
        <input placeholder="Job title" value={title} onChange={(e) => setTitle(e.target.value)} required />
        <input placeholder="Company (optional)" value={company} onChange={(e) => setCompany(e.target.value)} />
      </div>
      <textarea
        className="jd-textarea"
        placeholder="Paste the full job description here..."
        value={rawDescription}
        onChange={(e) => setRawDescription(e.target.value)}
        required
        minLength={20}
      />
      <div className="editor-actions">
        <button type="button" className="secondary" onClick={onCancel} disabled={submitting}>Cancel</button>
        <button type="submit" disabled={submitting}>{submitting ? 'Saving...' : 'Save & analyse'}</button>
      </div>
    </form>
  )
}
