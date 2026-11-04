import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import AppLayout from '@/layouts/AppLayout.vue'

declare module 'vue-router' {
  interface RouteMeta {
    title?: string
    /** 需要登录 */
    requiresAuth?: boolean
    /** 需要管理员 */
    requiresAdmin?: boolean
    /** 已登录用户访问时直接跳走（登录/注册页） */
    guestOnly?: boolean
  }
}

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/LoginView.vue'),
    meta: { title: '登录', guestOnly: true },
  },
  {
    path: '/register',
    name: 'register',
    component: () => import('@/views/RegisterView.vue'),
    meta: { title: '注册', guestOnly: true },
  },
  {
    path: '/',
    component: AppLayout,
    meta: { requiresAuth: true },
    children: [
      { path: '', redirect: { name: 'courses' } },
      {
        path: 'courses',
        name: 'courses',
        component: () => import('@/views/course/CourseListView.vue'),
        meta: { title: '课程列表' },
      },
      {
        path: 'courses/new',
        name: 'course-new',
        component: () => import('@/views/course/CourseFormView.vue'),
        meta: { title: '新建课程', requiresAdmin: true },
      },
      {
        path: 'courses/:id(\\d+)',
        name: 'course-detail',
        component: () => import('@/views/course/CourseDetailView.vue'),
        meta: { title: '课程详情' },
      },
      {
        path: 'courses/:id(\\d+)/edit',
        name: 'course-edit',
        component: () => import('@/views/course/CourseFormView.vue'),
        meta: { title: '编辑课程', requiresAdmin: true },
      },
      {
        path: 'my/enrollments',
        name: 'my-enrollments',
        component: () => import('@/views/MyEnrollmentsView.vue'),
        meta: { title: '我的报名' },
      },
      {
        path: 'account/password',
        name: 'change-password',
        component: () => import('@/views/account/ChangePasswordView.vue'),
        meta: { title: '修改密码' },
      },
      {
        path: 'stats',
        name: 'stats',
        component: () => import('@/views/admin/StatsView.vue'),
        meta: { title: '统计', requiresAdmin: true },
      },
      {
        path: 'admin/users',
        name: 'admin-users',
        component: () => import('@/views/admin/UserManageView.vue'),
        meta: { title: '用户管理', requiresAdmin: true },
      },
    ],
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'not-found',
    component: () => import('@/views/NotFoundView.vue'),
    meta: { title: '页面不存在' },
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior(to, from, savedPosition) {
    if (savedPosition) return savedPosition
    if (to.path !== from.path) return { top: 0 }
  },
})

router.beforeEach((to) => {
  const auth = useAuthStore()
  if (to.matched.some((r) => r.meta.requiresAuth) && !auth.isLoggedIn) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (to.matched.some((r) => r.meta.requiresAdmin) && !auth.isAdmin) {
    return { name: 'courses' }
  }
  if (to.meta.guestOnly && auth.isLoggedIn) {
    return { name: 'courses' }
  }
  return true
})

router.afterEach((to) => {
  document.title = to.meta.title ? `${to.meta.title} · 培训课程管理系统` : '培训课程管理系统'
})

export default router
