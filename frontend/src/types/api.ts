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

// ---------- 报名 ----------

/** 报名/完成操作的返回值（后端 EnrollmentVO） */
export interface Enrollment {
  id: number
  courseId: number
  userId: number
  status: EnrollmentStatus
  enrolledAt: string
  cancelledAt: string | null
  completedAt: string | null
  completedBy: number | null
}

/** 课程报名人员列表的一行（管理员） */
export interface CourseEnrollment {
  id: number
  userId: number
  username: string
  realName: string
  department: string | null
  status: EnrollmentStatus
  enrolledAt: string
  cancelledAt: string | null
  completedAt: string | null
  completedBy: number | null
  completedByName: string | null
  /** 本人确认完成；未完成为 null */
  selfConfirmed: boolean | null
}

/** 我的报名列表的一行 */
export interface MyEnrollment {
  id: number
  status: EnrollmentStatus
  enrolledAt: string
  cancelledAt: string | null
  completedAt: string | null
  completedBy: number | null
  courseId: number
  title: string
  instructor: string
  location: string | null
  startTime: string
  endTime: string
  phase: CoursePhase
}

export interface MyStats {
  enrolled: number
  completed: number
  cancelled: number
}

export interface EnrollmentQuery extends PageQuery {
  status?: EnrollmentStatus
}

// ---------- 统计（管理员） ----------

/** 全局概览；比率为 0~1，分母为 0 时为 null */
export interface StatsOverview {
  courseCount: number
  upcomingCount: number
  ongoingCount: number
  endedCount: number
  /** 有效报名人次 = 已报名 + 已完成 */
  activeEnrollments: number
  completedCount: number
  completionRate: number | null
  participantCount: number
  userCount: number
}

export type CourseStatsSort = 'ENROLLED' | 'COMPLETION_RATE'

export interface CourseStats {
  courseId: number
  title: string
  instructor: string
  startTime: string
  endTime: string
  phase: CoursePhase
  capacity: number | null
  enrolledCount: number
  completedCount: number
  /** 完成数 / 已报名人数；无人报名为 null */
  completionRate: number | null
  /** 已报名人数 / 容量；不限人数为 null */
  fillRate: number | null
}

// ---------- 用户管理 ----------

export interface CreateUserRequest extends RegisterRequest {
  role: Role
}

export interface UserQuery extends PageQuery {
  keyword?: string
}
