// 与后端 DTO 一一对应（技术设计 §7）

export type Role = 'ADMIN' | 'EMPLOYEE'

/** 后端 UserVO */
export interface UserInfo {
  id: number
  username: string
  realName: string
  department: string | null
  role: Role
  enabled: boolean
  createdAt: string
}

/** 统一错误响应体 */
export interface ErrorResponse {
  code: string
  message: string
  details?: Record<string, unknown>
}

/** 分页响应，page 从 1 开始 */
export interface PageResult<T> {
  records: T[]
  total: number
  page: number
  size: number
}

export interface PageQuery {
  page?: number
  size?: number
}

// ---------- 认证 ----------

export interface LoginRequest {
  username: string
  password: string
}

export interface LoginResponse {
  token: string
  expiresAt: string
  user: UserInfo
}

export interface RegisterRequest {
  username: string
  password: string
  realName: string
  department?: string
}

export interface ChangePasswordRequest {
  oldPassword: string
  newPassword: string
}

// ---------- 用户管理 ----------

export interface CreateUserRequest extends RegisterRequest {
  role: Role
}

export interface UserQuery extends PageQuery {
  keyword?: string
}
