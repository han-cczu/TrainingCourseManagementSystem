import http from './http'
import type {
  ChangePasswordRequest,
  LoginRequest,
  LoginResponse,
  RegisterRequest,
  UserInfo,
} from '@/types/api'

export const authApi = {
  register: (body: RegisterRequest) => http.post<UserInfo>('/auth/register', body).then((r) => r.data),

  login: (body: LoginRequest) => http.post<LoginResponse>('/auth/login', body).then((r) => r.data),

  me: () => http.get<UserInfo>('/auth/me').then((r) => r.data),

  changePassword: (body: ChangePasswordRequest) =>
    http.put<void>('/auth/password', body).then(() => undefined),
}
