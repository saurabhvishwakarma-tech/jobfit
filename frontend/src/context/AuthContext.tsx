import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import {
  type UserResponse,
  login as apiLogin,
  register as apiRegister,
  refresh as apiRefresh,
  logout as apiLogout,
} from '../api/auth'
import { setAccessToken, setUnauthorizedHandler } from '../api/client'

const REFRESH_TOKEN_KEY = 'jobfit.refreshToken'

interface AuthContextValue {
  user: UserResponse | null
  isLoading: boolean
  isAuthenticated: boolean
  login: (email: string, password: string) => Promise<void>
  register: (email: string, password: string, fullName: string) => Promise<void>
  logout: () => Promise<void>
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserResponse | null>(null)
  const [isLoading, setIsLoading] = useState(true)

  const applyAuthResponse = useCallback((res: { accessToken: string; refreshToken: string; user: UserResponse }) => {
    setAccessToken(res.accessToken)
    localStorage.setItem(REFRESH_TOKEN_KEY, res.refreshToken)
    setUser(res.user)
  }, [])

  const clearAuth = useCallback(() => {
    setAccessToken(null)
    localStorage.removeItem(REFRESH_TOKEN_KEY)
    setUser(null)
  }, [])

  // On first load, try to silently restore a session from the stored
  // refresh token so a page reload doesn't force a re-login.
  useEffect(() => {
    const storedRefreshToken = localStorage.getItem(REFRESH_TOKEN_KEY)
    if (!storedRefreshToken) {
      setIsLoading(false)
      return
    }
    apiRefresh(storedRefreshToken)
      .then(applyAuthResponse)
      .catch(clearAuth)
      .finally(() => setIsLoading(false))
  }, [applyAuthResponse, clearAuth])

  // Wire the axios 401 interceptor to attempt a silent refresh.
  useEffect(() => {
    setUnauthorizedHandler(async () => {
      const storedRefreshToken = localStorage.getItem(REFRESH_TOKEN_KEY)
      if (!storedRefreshToken) return null
      try {
        const res = await apiRefresh(storedRefreshToken)
        applyAuthResponse(res)
        return res.accessToken
      } catch {
        clearAuth()
        return null
      }
    })
  }, [applyAuthResponse, clearAuth])

  const login = useCallback(
    async (email: string, password: string) => {
      const res = await apiLogin(email, password)
      applyAuthResponse(res)
    },
    [applyAuthResponse],
  )

  const register = useCallback(
    async (email: string, password: string, fullName: string) => {
      const res = await apiRegister(email, password, fullName)
      applyAuthResponse(res)
    },
    [applyAuthResponse],
  )

  const logout = useCallback(async () => {
    try {
      await apiLogout()
    } finally {
      clearAuth()
    }
  }, [clearAuth])

  const value = useMemo<AuthContextValue>(
    () => ({ user, isLoading, isAuthenticated: user !== null, login, register, logout }),
    [user, isLoading, login, register, logout],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used within an AuthProvider')
  return ctx
}
