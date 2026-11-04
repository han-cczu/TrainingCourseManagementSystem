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

// ---------- 课程 ----------

/** 课程阶段：按当前时间计算（技术设计 §5.3） */
export type CoursePhase = 'UPCOMING' | 'ONGOING' | 'ENDED'

export type EnrollmentStatus = 'ENROLLED' | 'CANCELLED' | 'COMPLETED'

/** 后端 CourseVO */
export interface Course {
  id: number
  title: string
  description: string | null
  instructor: string
  location: string | null
  startTime: string
  endTime: string
  /** null 表示不限人数 */
  capacity: number | null
  enrolledCount: number
  /** 剩余名额；不限人数时为 null */
  remaining: number | null
  phase: CoursePhase
  /** 当前用户的报名状态；未报名为 null */
  myStatus: EnrollmentStatus | null
  createdAt: string
  updatedAt: string
}

export interface CourseRequest {
  title: string
  description?: string
  instructor: string
  location?: string
  startTime: string
  endTime: string
  capacity: number | null
}

export interface CourseQuery extends PageQuery {
  keyword?: string
  phase?: CoursePhase
}

// ---------- 用户管理 ----------

export interface CreateUserRequest extends RegisterRequest {
  role: Role
}

export interface UserQuery extends PageQuery {
  keyword?: string
}
