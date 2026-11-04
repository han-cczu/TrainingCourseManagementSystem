import dayjs from 'dayjs'
import type { CoursePhase, EnrollmentStatus, Role } from '@/types/api'

/** 后端返回北京时间的 ISO 本地时间，直接格式化，不做时区换算（技术设计 §5.4） */
export function formatDateTime(value: string | null | undefined): string {
  return value ? dayjs(value).format('YYYY-MM-DD HH:mm') : '-'
}

/** 同一天：2026-11-05 09:00 ~ 12:00；跨天：两端都显示日期 */
export function formatTimeRange(start: string, end: string): string {
  const s = dayjs(start)
  const e = dayjs(end)
  return s.isSame(e, 'day')
    ? `${s.format('YYYY-MM-DD HH:mm')} ~ ${e.format('HH:mm')}`
    : `${s.format('YYYY-MM-DD HH:mm')} ~ ${e.format('YYYY-MM-DD HH:mm')}`
}

/** 0~1 的比率显示为百分比（保留 1 位）；null（分母为 0）显示为 "-" */
export function formatPercent(rate: number | null | undefined): string {
  return rate === null || rate === undefined ? '-' : `${(rate * 100).toFixed(1)}%`
}

/** 0~1 的比率转为 el-progress 需要的 0~100 */
export function toPercentage(rate: number | null | undefined): number {
  return rate === null || rate === undefined ? 0 : Math.min(100, Math.round(rate * 1000) / 10)
}

/** 提交给后端的时间格式（ISO 本地时间） */
export const API_DATETIME_FORMAT = 'YYYY-MM-DD[T]HH:mm:ss'

export const ROLE_LABEL: Record<Role, string> = {
  ADMIN: '管理员',
  EMPLOYEE: '员工',
}

export const PHASE_LABEL: Record<CoursePhase, string> = {
  UPCOMING: '报名中',
  ONGOING: '进行中',
  ENDED: '已结束',
}

export const PHASE_TAG_TYPE: Record<CoursePhase, 'success' | 'warning' | 'info'> = {
  UPCOMING: 'success',
  ONGOING: 'warning',
  ENDED: 'info',
}

export const ENROLLMENT_STATUS_LABEL: Record<EnrollmentStatus, string> = {
  ENROLLED: '已报名',
  CANCELLED: '已取消',
  COMPLETED: '已完成',
}

export const ENROLLMENT_STATUS_TAG_TYPE: Record<EnrollmentStatus, 'primary' | 'info' | 'success'> = {
  ENROLLED: 'primary',
  CANCELLED: 'info',
  COMPLETED: 'success',
}
