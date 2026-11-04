import dayjs from 'dayjs'
import type { Role } from '@/types/api'

/** 后端返回北京时间的 ISO 本地时间，直接格式化，不做时区换算（技术设计 §5.4） */
export function formatDateTime(value: string | null | undefined): string {
  return value ? dayjs(value).format('YYYY-MM-DD HH:mm') : '-'
}

export const ROLE_LABEL: Record<Role, string> = {
  ADMIN: '管理员',
  EMPLOYEE: '员工',
}
