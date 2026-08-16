import { apiClient } from './client'

export interface UserResponse {
  id: number
  email: string
  fullName: string
  createdAt: string
}

export interface AuthResponse {
  accessToken: string
  accessTokenExpiresInSeconds: number
  refreshToken: string
  user: UserResponse
}

export interface ApiError {
  timestamp: string
  status: number
  error: string
  message: string
  path: string
  fieldErrors: { field: string; message: string }[]
}

export function register(email: string, password: string, fullName: string) {
  return apiClient
    .post<AuthResponse>('/api/auth/register', { email, password, fullName })
    .then((r) => r.data)
}

export function login(email: string, password: string) {
  return apiClient.post<AuthResponse>('/api/auth/login', { email, password }).then((r) => r.data)
}

export function refresh(refreshToken: string) {
  return apiClient.post<AuthResponse>('/api/auth/refresh', { refreshToken }).then((r) => r.data)
}

export function logout() {
  return apiClient.post('/api/auth/logout')
}

export function fetchCurrentUser() {
  return apiClient.get<UserResponse>('/api/users/me').then((r) => r.data)
}
