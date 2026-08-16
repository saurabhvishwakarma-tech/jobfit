import { Navigate, Route, Routes } from 'react-router-dom'
import { AuthProvider, useAuth } from './context/AuthContext'
import { ProtectedRoute } from './components/ProtectedRoute'
import { Layout } from './components/Layout'
import { LoginPage } from './pages/LoginPage'
import { RegisterPage } from './pages/RegisterPage'
import { DashboardPage } from './pages/DashboardPage'
import { ResumePage } from './pages/resume/ResumePage'
import { ResumeQualityPage } from './pages/resume/ResumeQualityPage'
import { ResumeAtsPage } from './pages/resume/ResumeAtsPage'
import { JobsPage } from './pages/job/JobsPage'
import { JobDetailPage } from './pages/job/JobDetailPage'
import { JobComparisonPage } from './pages/job/JobComparisonPage'
import { JobAnalysisPage } from './pages/analysis/JobAnalysisPage'
import { ApplicationsPage } from './pages/application/ApplicationsPage'
import { ApplicationDetailPage } from './pages/application/ApplicationDetailPage'

function AuthRedirect() {
  const { isAuthenticated, isLoading } = useAuth()
  if (isLoading) return <div className="page-loading">Loading...</div>
  return <Navigate to={isAuthenticated ? '/dashboard' : '/login'} replace />
}

export default function App() {
  return (
    <AuthProvider>
      <Routes>
        <Route path="/" element={<AuthRedirect />} />
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />
        <Route
          path="/dashboard"
          element={
            <ProtectedRoute>
              <Layout>
                <DashboardPage />
              </Layout>
            </ProtectedRoute>
          }
        />
        <Route
          path="/resume"
          element={
            <ProtectedRoute>
              <Layout>
                <ResumePage />
              </Layout>
            </ProtectedRoute>
          }
        />
        <Route
          path="/resume/quality/:id"
          element={
            <ProtectedRoute>
              <Layout>
                <ResumeQualityPage />
              </Layout>
            </ProtectedRoute>
          }
        />
        <Route
          path="/resume/ats/:id"
          element={
            <ProtectedRoute>
              <Layout>
                <ResumeAtsPage />
              </Layout>
            </ProtectedRoute>
          }
        />
        <Route
          path="/jobs"
          element={
            <ProtectedRoute>
              <Layout>
                <JobsPage />
              </Layout>
            </ProtectedRoute>
          }
        />
        <Route
          path="/jobs/:id"
          element={
            <ProtectedRoute>
              <Layout>
                <JobDetailPage />
              </Layout>
            </ProtectedRoute>
          }
        />
        <Route
          path="/compare"
          element={
            <ProtectedRoute>
              <Layout>
                <JobComparisonPage />
              </Layout>
            </ProtectedRoute>
          }
        />
        <Route
          path="/match-analyses/:id"
          element={
            <ProtectedRoute>
              <Layout>
                <JobAnalysisPage />
              </Layout>
            </ProtectedRoute>
          }
        />
        <Route
          path="/applications"
          element={
            <ProtectedRoute>
              <Layout>
                <ApplicationsPage />
              </Layout>
            </ProtectedRoute>
          }
        />
        <Route
          path="/applications/:id"
          element={
            <ProtectedRoute>
              <Layout>
                <ApplicationDetailPage />
              </Layout>
            </ProtectedRoute>
          }
        />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </AuthProvider>
  )
}
