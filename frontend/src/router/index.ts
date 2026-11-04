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
    path: '/',
    component: AppLayout,
    meta: { requiresAuth: true },
    children: [
      { path: '', redirect: { name: 'courses' } },
      {
        path: 'courses',
        name: 'courses',
        component: () => import('@/views/CourseListView.vue'),
        meta: { title: '课程列表' },
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
