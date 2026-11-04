import http from './http'
import type { CreateUserRequest, PageResult, UserInfo, UserQuery } from '@/types/api'

/** 管理员用户管理（仅 ADMIN 可调用） */
export const usersApi = {
  list: (query: UserQuery) =>
    http.get<PageResult<UserInfo>>('/users', { params: query }).then((r) => r.data),

  create: (body: CreateUserRequest) => http.post<UserInfo>('/users', body).then((r) => r.data),

  setEnabled: (id: number, enabled: boolean) =>
    http.patch<UserInfo>(`/users/${id}/enabled`, { enabled }).then((r) => r.data),

  resetPassword: (id: number, newPassword: string) =>
    http.put<void>(`/users/${id}/password`, { newPassword }).then(() => undefined),
}
