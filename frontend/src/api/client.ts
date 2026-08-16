import axios, { type InternalAxiosRequestConfig } from 'axios'

export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

/**
 * Holds the current access token in memory only (never localStorage) to
 * limit exposure to XSS-based token theft. It's set by AuthContext after
 * login/refresh and cleared on logout.
 */
let accessToken: string | null = null
export function setAccessToken(token: string | null) {
  accessToken = token
}

export const apiClient = axios.create({ baseURL: API_BASE_URL })

apiClient.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  if (accessToken) {
    config.headers.set('Authorization', `Bearer ${accessToken}`)
  }
  return config
})

/**
 * Callback injected by AuthContext: given a 401, try to refresh the access
 * token using the stored refresh token. Returns the new access token, or
 * null if refresh also failed (caller should redirect to login).
 */
let onUnauthorized: (() => Promise<string | null>) | null = null
export function setUnauthorizedHandler(handler: () => Promise<string | null>) {
  onUnauthorized = handler
}

apiClient.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config
    if (error.response?.status === 401 && onUnauthorized && !originalRequest._retry) {
      originalRequest._retry = true
      const newToken = await onUnauthorized()
      if (newToken) {
        originalRequest.headers.Authorization = `Bearer ${newToken}`
        return apiClient(originalRequest)
      }
    }
    return Promise.reject(error)
  },
)
