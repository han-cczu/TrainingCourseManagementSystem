import http from './http'
import type { CourseStats, CourseStatsSort, StatsOverview } from '@/types/api'

/** 基础统计（仅管理员） */
export const statsApi = {
  overview: () => http.get<StatsOverview>('/stats/overview').then((r) => r.data),

  courses: (sort: CourseStatsSort, limit: number) =>
    http.get<CourseStats[]>('/stats/courses', { params: { sort, limit } }).then((r) => r.data),
}
