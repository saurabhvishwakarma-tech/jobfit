import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import {
  Bar,
  BarChart,
  Cell,
  Legend,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import { useAuth } from '../context/AuthContext'
import { getDashboard, type Dashboard } from '../api/dashboard'
import { listApplications, APPLICATION_STATUSES, type ApplicationStatus } from '../api/application'

const CHART_COLORS = {
  primary: '#6d6afe',
  muted: '#7c8aa3',
}

const STATUS_COLOR: Record<ApplicationStatus, string> = {
  SAVED: '#7c8aa3',
  APPLIED: '#38bdf8',
  ONLINE_ASSESSMENT: '#f5a742',
  INTERVIEW: '#6d6afe',
  OFFER: '#2fd992',
  REJECTED: '#f0555f',
  WITHDRAWN: '#4b5468',
}

const STATUS_LABEL: Record<ApplicationStatus, string> = {
  SAVED: 'Saved',
  APPLIED: 'Applied',
  ONLINE_ASSESSMENT: 'Assessment',
  INTERVIEW: 'Interview',
  OFFER: 'Offer',
  REJECTED: 'Rejected',
  WITHDRAWN: 'Withdrawn',
}

const TOOLTIP_STYLE = {
  background: '#131826',
  border: '1px solid #232a3b',
  borderRadius: 10,
  fontSize: 12.5,
  color: '#eef1f8',
  boxShadow: '0 8px 24px -8px rgba(0,0,0,0.5)',
}

export function DashboardPage() {
  const { user } = useAuth()
  const [dashboard, setDashboard] = useState<Dashboard | null>(null)
  const [statusCounts, setStatusCounts] = useState<Record<string, number>>({})
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    // allSettled (not all) on purpose: the applications call is a nice-to-have
    // for the pipeline chart, and shouldn't be able to take down the whole
    // dashboard if it fails while the core dashboard data loaded fine.
    Promise.allSettled([getDashboard(), listApplications()]).then(([dashResult, appsResult]) => {
      if (dashResult.status === 'fulfilled') setDashboard(dashResult.value)
      if (appsResult.status === 'fulfilled') {
        const counts: Record<string, number> = {}
        for (const app of appsResult.value) {
          counts[app.status] = (counts[app.status] ?? 0) + 1
        }
        setStatusCounts(counts)
      }
      setLoading(false)
    })
  }, [])

  if (loading) return <div className="page-loading">Loading...</div>

  return (
    <div className="page">
      <h1>Welcome back, {user?.fullName?.split(' ')[0]}</h1>

      {dashboard && dashboard.totalJobsAdded === 0 ? (
        <p className="muted">
          Add a job and upload your resume to start seeing fit scores, application stats, and skill
          trends here.
        </p>
      ) : (
        dashboard && <DashboardContent dashboard={dashboard} statusCounts={statusCounts} />
      )}
    </div>
  )
}

function DashboardContent({
  dashboard,
  statusCounts,
}: {
  dashboard: Dashboard
  statusCounts: Record<string, number>
}) {
  const skillData = dashboard.mostRequestedSkills.slice(0, 6).map((s) => ({ name: s.skillName, count: s.count }))
  const pipelineData = APPLICATION_STATUSES
    .map((status) => ({ status, label: STATUS_LABEL[status], value: statusCounts[status] ?? 0 }))
    .filter((d) => d.value > 0)
  const hasCharts = skillData.length > 0 || pipelineData.length > 0

  return (
    <>
      <div className="stat-grid">
        <StatCard label="Jobs added" value={dashboard.totalJobsAdded} />
        <StatCard label="Jobs analysed" value={dashboard.jobsAnalysed} />
        <StatCard label="Applications tracked" value={dashboard.applicationsTracked} />
        <StatCard label="Interviews" value={dashboard.interviews} />
        <StatCard label="Offers" value={dashboard.offers} />
        <StatCard
          label="Average fit score"
          value={dashboard.averageFitScore === null ? '—' : `${dashboard.averageFitScore}%`}
        />
      </div>

      {hasCharts && (
        <div className="chart-row">
          {skillData.length > 0 && (
            <div className="chart-card">
              <h3>Most in-demand skills</h3>
              <ResponsiveContainer width="100%" height={Math.max(160, skillData.length * 34)}>
                <BarChart data={skillData} layout="vertical" margin={{ left: 8, right: 16, top: 4, bottom: 4 }}>
                  <XAxis type="number" allowDecimals={false} stroke={CHART_COLORS.muted} fontSize={11} tickLine={false} axisLine={false} />
                  <YAxis
                    type="category"
                    dataKey="name"
                    width={100}
                    stroke={CHART_COLORS.muted}
                    fontSize={12}
                    tickLine={false}
                    axisLine={false}
                  />
                  <Tooltip cursor={{ fill: 'rgba(109,106,254,0.08)' }} contentStyle={TOOLTIP_STYLE} labelStyle={{ color: '#eef1f8' }} />
                  <Bar dataKey="count" fill={CHART_COLORS.primary} radius={[0, 6, 6, 0]} maxBarSize={16} />
                </BarChart>
              </ResponsiveContainer>
            </div>
          )}

          {pipelineData.length > 0 && (
            <div className="chart-card">
              <h3>Application pipeline</h3>
              <ResponsiveContainer width="100%" height={200}>
                <PieChart>
                  <Pie
                    data={pipelineData}
                    dataKey="value"
                    nameKey="label"
                    innerRadius={52}
                    outerRadius={76}
                    paddingAngle={3}
                    strokeWidth={0}
                  >
                    {pipelineData.map((entry) => (
                      <Cell key={entry.status} fill={STATUS_COLOR[entry.status]} />
                    ))}
                  </Pie>
                  <Tooltip contentStyle={TOOLTIP_STYLE} />
                  <Legend
                    layout="vertical"
                    verticalAlign="middle"
                    align="right"
                    iconType="circle"
                    iconSize={8}
                    formatter={(value) => <span style={{ color: '#b7c0d4', fontSize: 12.5 }}>{value}</span>}
                  />
                </PieChart>
              </ResponsiveContainer>
            </div>
          )}
        </div>
      )}

      <div className="dashboard-columns">
        <section className="dashboard-section">
          <h3>Most requested skills</h3>
          {dashboard.mostRequestedSkills.length === 0 ? (
            <p className="muted">Analyse some jobs to see which skills come up most often.</p>
          ) : (
            <ul className="freq-list">
              {dashboard.mostRequestedSkills.map((s) => (
                <li key={s.skillName}>
                  <span>{s.skillName}</span>
                  <span className="freq-count">{s.count}</span>
                </li>
              ))}
            </ul>
          )}
        </section>

        <section className="dashboard-section">
          <h3>Common skill gaps</h3>
          {dashboard.commonSkillGaps.length === 0 ? (
            <p className="muted">No recurring gaps found yet - keep analysing jobs to surface patterns.</p>
          ) : (
            <ul className="freq-list freq-list--gap">
              {dashboard.commonSkillGaps.map((s) => (
                <li key={s.skillName}>
                  <span>{s.skillName}</span>
                  <span className="freq-count freq-count--gap">{s.count}</span>
                </li>
              ))}
            </ul>
          )}
        </section>
      </div>

      <section className="dashboard-section">
        <h3>Your strongest skills</h3>
        {dashboard.strongestSkills.length === 0 ? (
          <p className="muted">Upload a resume to see the skills we found explicitly stated in it.</p>
        ) : (
          <div className="skill-tags">
            {dashboard.strongestSkills.map((name) => (
              <span className="skill-tag" key={name}>{name}</span>
            ))}
          </div>
        )}
      </section>

      <section className="dashboard-section">
        <h3>Best-fit roles</h3>
        {dashboard.bestFitRoles.length === 0 ? (
          <p className="muted">Analyse a job's fit to see your best matches ranked here.</p>
        ) : (
          <div className="job-list">
            {dashboard.bestFitRoles.map((role) => (
              <Link to={`/jobs/${role.jobId}`} className="job-card" key={role.jobId}>
                <div>
                  <div className="job-card__title">{role.title ?? 'Untitled job'}</div>
                  <div className="muted">{role.company ?? 'Unknown company'}</div>
                </div>
                <div className={`mini-score mini-score--${scoreClass(role.score)}`}>{role.score}%</div>
              </Link>
            ))}
          </div>
        )}
      </section>
    </>
  )
}

function StatCard({ label, value }: { label: string; value: number | string }) {
  return (
    <div className="stat-card">
      <div className="stat-card__value">{value}</div>
      <div className="stat-card__label">{label}</div>
    </div>
  )
}

function scoreClass(score: number): string {
  if (score >= 80) return 'strong'
  if (score >= 60) return 'reasonable'
  if (score >= 40) return 'stretch'
  return 'poor'
}
