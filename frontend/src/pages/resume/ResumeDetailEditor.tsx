import { useState } from 'react'
import type {
  Certification,
  ContactInfo,
  Education,
  Experience,
  ProjectItem,
  ResumeDetail,
  ResumeUpdatePayload,
} from '../../api/resume'

function emptyExperience(): Experience {
  return { id: null, jobTitle: '', company: '', location: null, startDate: null, endDate: null, current: false, highlights: [] }
}
function emptyEducation(): Education {
  return { id: null, institution: '', degree: null, fieldOfStudy: null, startDate: null, endDate: null }
}
function emptyCertification(): Certification {
  return { id: null, name: '', issuer: null, issuedDate: null }
}
function emptyProject(): ProjectItem {
  return { id: null, name: '', description: null, technologies: null }
}

export function ResumeDetailEditor({
  resume,
  onSave,
}: {
  resume: ResumeDetail
  onSave: (payload: ResumeUpdatePayload) => Promise<void>
}) {
  const [editing, setEditing] = useState(false)
  const [contactInfo, setContactInfo] = useState<ContactInfo>(resume.contactInfo)
  const [experiences, setExperiences] = useState<Experience[]>(resume.experiences)
  const [education, setEducation] = useState<Education[]>(resume.education)
  const [certifications, setCertifications] = useState<Certification[]>(resume.certifications)
  const [projects, setProjects] = useState<ProjectItem[]>(resume.projects)
  const [saving, setSaving] = useState(false)

  function resetToSaved() {
    setContactInfo(resume.contactInfo)
    setExperiences(resume.experiences)
    setEducation(resume.education)
    setCertifications(resume.certifications)
    setProjects(resume.projects)
  }

  async function handleSave() {
    setSaving(true)
    try {
      await onSave({ contactInfo, experiences, education, certifications, projects })
      setEditing(false)
    } finally {
      setSaving(false)
    }
  }

  if (!editing) {
    return (
      <div className="resume-view">
        <div className="resume-view__header">
          <h2>{contactInfo.fullName ?? 'Your resume'}</h2>
          <button onClick={() => setEditing(true)}>Review & edit</button>
        </div>

        <section>
          <h3>Contact</h3>
          <p className="muted">
            {[contactInfo.email, contactInfo.phone, contactInfo.location].filter(Boolean).join(' · ') || 'No contact details detected.'}
          </p>
        </section>

        <section>
          <h3>Experience</h3>
          {experiences.length === 0 && <p className="muted">No experience detected.</p>}
          {experiences.map((exp, i) => (
            <div className="entry" key={i}>
              <div className="entry__title">{exp.jobTitle} — {exp.company}</div>
              <div className="entry__meta">
                {exp.startDate ?? '?'} – {exp.current ? 'Present' : (exp.endDate ?? '?')}
              </div>
              <ul>
                {exp.highlights.map((h, hi) => <li key={hi}>{h.text}</li>)}
              </ul>
            </div>
          ))}
        </section>

        <section>
          <h3>Education</h3>
          {education.length === 0 && <p className="muted">No education detected.</p>}
          {education.map((edu, i) => (
            <div className="entry" key={i}>
              <div className="entry__title">{edu.institution}</div>
              <div className="entry__meta">{[edu.degree, edu.fieldOfStudy].filter(Boolean).join(', ')}</div>
            </div>
          ))}
        </section>

        <section>
          <h3>Skills</h3>
          {resume.skills.length === 0 && <p className="muted">No skills detected yet.</p>}
          <div className="skill-tags">
            {resume.skills.map((s) => (
              <span key={s.skillId} className={`skill-tag skill-tag--${s.source.toLowerCase()}`}>
                {s.name}
                <span className="skill-tag__source">{s.source === 'EXPLICIT' ? 'explicit' : 'inferred'}</span>
              </span>
            ))}
          </div>
        </section>

        {(certifications.length > 0 || projects.length > 0) && (
          <section>
            <h3>Certifications & Projects</h3>
            {certifications.map((c, i) => <div className="entry" key={`c${i}`}>{c.name}{c.issuer ? ` — ${c.issuer}` : ''}</div>)}
            {projects.map((p, i) => <div className="entry" key={`p${i}`}>{p.name}</div>)}
          </section>
        )}
      </div>
    )
  }

  return (
    <div className="resume-editor">
      <div className="resume-view__header">
        <h2>Review & correct your resume</h2>
        <div className="editor-actions">
          <button className="secondary" onClick={() => { resetToSaved(); setEditing(false) }} disabled={saving}>
            Cancel
          </button>
          <button onClick={handleSave} disabled={saving}>{saving ? 'Saving...' : 'Save changes'}</button>
        </div>
      </div>

      <section>
        <h3>Contact</h3>
        <div className="form-grid">
          <input placeholder="Full name" value={contactInfo.fullName ?? ''}
                 onChange={(e) => setContactInfo({ ...contactInfo, fullName: e.target.value })} />
          <input placeholder="Email" value={contactInfo.email ?? ''}
                 onChange={(e) => setContactInfo({ ...contactInfo, email: e.target.value })} />
          <input placeholder="Phone" value={contactInfo.phone ?? ''}
                 onChange={(e) => setContactInfo({ ...contactInfo, phone: e.target.value })} />
          <input placeholder="Location" value={contactInfo.location ?? ''}
                 onChange={(e) => setContactInfo({ ...contactInfo, location: e.target.value })} />
        </div>
      </section>

      <section>
        <div className="section-header">
          <h3>Experience</h3>
          <button className="link-button" onClick={() => setExperiences([...experiences, emptyExperience()])}>+ Add role</button>
        </div>
        {experiences.map((exp, i) => (
          <div className="entry-editor" key={i}>
            <div className="form-grid">
              <input placeholder="Job title" value={exp.jobTitle}
                     onChange={(e) => setExperiences(experiences.map((x, xi) => xi === i ? { ...x, jobTitle: e.target.value } : x))} />
              <input placeholder="Company" value={exp.company}
                     onChange={(e) => setExperiences(experiences.map((x, xi) => xi === i ? { ...x, company: e.target.value } : x))} />
            </div>
            <textarea
              placeholder="Highlights, one per line"
              value={exp.highlights.map((h) => h.text).join('\n')}
              onChange={(e) => setExperiences(experiences.map((x, xi) => xi === i
                ? { ...x, highlights: e.target.value.split('\n').filter((l) => l.trim() !== '').map((text) => ({ text })) }
                : x))}
            />
            <button className="link-button link-button--danger"
                    onClick={() => setExperiences(experiences.filter((_, xi) => xi !== i))}>
              Remove
            </button>
          </div>
        ))}
      </section>

      <section>
        <div className="section-header">
          <h3>Education</h3>
          <button className="link-button" onClick={() => setEducation([...education, emptyEducation()])}>+ Add</button>
        </div>
        {education.map((edu, i) => (
          <div className="entry-editor" key={i}>
            <div className="form-grid">
              <input placeholder="Institution" value={edu.institution}
                     onChange={(e) => setEducation(education.map((x, xi) => xi === i ? { ...x, institution: e.target.value } : x))} />
              <input placeholder="Degree" value={edu.degree ?? ''}
                     onChange={(e) => setEducation(education.map((x, xi) => xi === i ? { ...x, degree: e.target.value } : x))} />
              <input placeholder="Field of study" value={edu.fieldOfStudy ?? ''}
                     onChange={(e) => setEducation(education.map((x, xi) => xi === i ? { ...x, fieldOfStudy: e.target.value } : x))} />
            </div>
            <button className="link-button link-button--danger"
                    onClick={() => setEducation(education.filter((_, xi) => xi !== i))}>
              Remove
            </button>
          </div>
        ))}
      </section>

      <section>
        <div className="section-header">
          <h3>Certifications</h3>
          <button className="link-button" onClick={() => setCertifications([...certifications, emptyCertification()])}>+ Add</button>
        </div>
        {certifications.map((c, i) => (
          <div className="entry-editor" key={i}>
            <div className="form-grid">
              <input placeholder="Name" value={c.name}
                     onChange={(e) => setCertifications(certifications.map((x, xi) => xi === i ? { ...x, name: e.target.value } : x))} />
              <input placeholder="Issuer" value={c.issuer ?? ''}
                     onChange={(e) => setCertifications(certifications.map((x, xi) => xi === i ? { ...x, issuer: e.target.value } : x))} />
            </div>
            <button className="link-button link-button--danger"
                    onClick={() => setCertifications(certifications.filter((_, xi) => xi !== i))}>
              Remove
            </button>
          </div>
        ))}
      </section>

      <section>
        <div className="section-header">
          <h3>Projects</h3>
          <button className="link-button" onClick={() => setProjects([...projects, emptyProject()])}>+ Add</button>
        </div>
        {projects.map((p, i) => (
          <div className="entry-editor" key={i}>
            <input placeholder="Project name" value={p.name}
                   onChange={(e) => setProjects(projects.map((x, xi) => xi === i ? { ...x, name: e.target.value } : x))} />
            <textarea placeholder="Description" value={p.description ?? ''}
                      onChange={(e) => setProjects(projects.map((x, xi) => xi === i ? { ...x, description: e.target.value } : x))} />
            <button className="link-button link-button--danger"
                    onClick={() => setProjects(projects.filter((_, xi) => xi !== i))}>
              Remove
            </button>
          </div>
        ))}
      </section>
    </div>
  )
}
