import type { ReactNode } from 'react'
import { NavLink, useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'

const navItems = [
  { to: '/dashboard', label: 'Dashboard', icon: '▦' },
  { to: '/resume', label: 'My Resume', icon: '▤' },
  { to: '/jobs', label: 'Jobs', icon: '⌗' },
  { to: '/compare', label: 'Compare', icon: '⇆' },
  { to: '/applications', label: 'Applications', icon: '✓' },
]

export function Layout({ children }: { children: ReactNode }) {
  const { user, logout } = useAuth()
  const navigate = useNavigate()

  async function handleLogout() {
    await logout()
    navigate('/login')
  }

  return (
    <div className="app-shell">
      <header className="app-header">
        <div className="brand">JobFit</div>
        <nav>
          {navItems.map((item) => (
            <NavLink key={item.to} to={item.to} className={({ isActive }) => (isActive ? 'active' : '')}>
              <span aria-hidden="true" style={{ marginRight: 6, opacity: 0.85 }}>{item.icon}</span>
              {item.label}
            </NavLink>
          ))}
        </nav>
        <div className="user-menu">
          <span>{user?.fullName}</span>
          <button onClick={handleLogout}>Log out</button>
        </div>
      </header>
      <main className="app-content">{children}</main>
    </div>
  )
}
