import http from './http'
import type {
  CourseEnrollment,
  Enrollment,
  EnrollmentQuery,
  MyEnrollment,
  MyStats,
  PageResult,
} from '@/types/api'

export const enrollmentsApi = {
  /** 当前用户报名 */
  enroll: (courseId: number) => http.post<Enrollment>(`/courses/${courseId}/enrollments`).then((r) => r.data),

  /** 当前用户取消报名 */
  cancel: (courseId: number) => http.delete<void>(`/courses/${courseId}/enrollments/me`).then(() => undefined),

  /** 当前用户标记学习完成 */
  completeSelf: (courseId: number) =>
    http.post<Enrollment>(`/courses/${courseId}/enrollments/me/complete`).then((r) => r.data),

  /** 课程报名人员（仅管理员） */
  listByCourse: (courseId: number, query: EnrollmentQuery) =>
    http
      .get<PageResult<CourseEnrollment>>(`/courses/${courseId}/enrollments`, { params: query })
      .then((r) => r.data),

  /** 管理员给某条报名标记完成 */
  completeByAdmin: (enrollmentId: number) =>
    http.post<Enrollment>(`/enrollments/${enrollmentId}/complete`).then((r) => r.data),

  listMine: (query: EnrollmentQuery) =>
    http.get<PageResult<MyEnrollment>>('/me/enrollments', { params: query }).then((r) => r.data),

  myStats: () => http.get<MyStats>('/me/stats').then((r) => r.data),
}
