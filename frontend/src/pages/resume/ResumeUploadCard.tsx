import { type ChangeEvent, useRef, useState } from 'react'

export function ResumeUploadCard({ onUpload }: { onUpload: (file: File) => Promise<void> }) {
  const inputRef = useRef<HTMLInputElement>(null)
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  async function handleFileChange(e: ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0]
    if (!file) return
    if (file.type !== 'application/pdf') {
      setError('Please upload a PDF file.')
      return
    }
    setError(null)
    setSubmitting(true)
    try {
      await onUpload(file)
    } catch {
      setError('Upload failed. Please try again.')
    } finally {
      setSubmitting(false)
      if (inputRef.current) inputRef.current.value = ''
    }
  }

  return (
    <div className="upload-card">
      <h2>Upload your resume</h2>
      <p className="muted">
        PDF only, up to 10MB. We'll extract your experience, education, and skills automatically -
        you'll get a chance to review and correct anything before it's used anywhere else.
      </p>
      {error && <div className="form-error" role="alert">{error}</div>}
      <label className="file-drop">
        <input
          ref={inputRef}
          type="file"
          accept="application/pdf"
          onChange={handleFileChange}
          disabled={submitting}
        />
        {submitting ? 'Uploading...' : 'Choose a PDF file'}
      </label>
    </div>
  )
}
