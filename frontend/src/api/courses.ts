import http from './http'
import type { Course, CourseQuery, CourseRequest, PageResult } from '@/types/api'

export const coursesApi = {
  list: (query: CourseQuery) =>
    http.get<PageResult<Course>>('/courses', { params: query }).then((r) => r.data),

  get: (id: number) => http.get<Course>(`/courses/${id}`).then((r) => r.data),

  /** 仅管理员 */
  create: (body: CourseRequest) => http.post<Course>('/courses', body).then((r) => r.data),

  /** 仅管理员 */
  update: (id: number, body: CourseRequest) => http.put<Course>(`/courses/${id}`, body).then((r) => r.data),
}
