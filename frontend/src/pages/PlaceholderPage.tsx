export function PlaceholderPage({ title, phase }: { title: string; phase: string }) {
  return (
    <div className="page">
      <h1>{title}</h1>
      <p className="muted">Coming in {phase}.</p>
    </div>
  )
}
