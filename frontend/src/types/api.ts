// 与后端 DTO 一一对应（技术设计 §7）

export type Role = 'ADMIN' | 'EMPLOYEE'

export interface UserInfo {
  id: number
  username: string
  realName: string
  department: string | null
  role: Role
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
