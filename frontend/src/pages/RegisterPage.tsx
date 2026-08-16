import { type FormEvent, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import type { ApiError } from '../api/auth'
import { isAxiosError } from 'axios'

export function RegisterPage() {
  const { register } = useAuth()
  const navigate = useNavigate()
  const [fullName, setFullName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [submitting, setSubmitting] = useState(false)

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setFieldErrors({})
    setSubmitting(true)
    try {
      await register(email, password, fullName)
      navigate('/dashboard')
    } catch (err) {
      if (isAxiosError<ApiError>(err) && err.response) {
        setError(err.response.data.message)
        const fe: Record<string, string> = {}
        for (const violation of err.response.data.fieldErrors ?? []) {
          fe[violation.field] = violation.message
        }
        setFieldErrors(fe)
      } else {
        setError('Something went wrong. Please try again.')
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="auth-page">
      <form className="auth-card" onSubmit={handleSubmit}>
        <h1>Create your JobFit account</h1>
        {error && <div className="form-error" role="alert">{error}</div>}
        <label htmlFor="fullName">Full name</label>
        <input id="fullName" value={fullName} onChange={(e) => setFullName(e.target.value)} required />
        {fieldErrors.fullName && <span className="field-error">{fieldErrors.fullName}</span>}

        <label htmlFor="email">Email</label>
        <input
          id="email"
          type="email"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          required
          autoComplete="email"
        />
        {fieldErrors.email && <span className="field-error">{fieldErrors.email}</span>}

        <label htmlFor="password">Password</label>
        <input
          id="password"
          type="password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          required
          autoComplete="new-password"
          minLength={10}
        />
        {fieldErrors.password && <span className="field-error">{fieldErrors.password}</span>}

        <button type="submit" disabled={submitting}>
          {submitting ? 'Creating account...' : 'Create account'}
        </button>
        <p className="auth-switch">
          Already have an account? <Link to="/login">Log in</Link>
        </p>
      </form>
    </div>
  )
}
