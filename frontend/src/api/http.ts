import axios, { type AxiosError } from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'
import { useAuthStore } from '@/stores/auth'
import type { ErrorResponse } from '@/types/api'

/** 所有接口共用的 axios 实例：自动带 token、统一处理错误（技术设计 §8.2） */
const http = axios.create({
  baseURL: '/api',
  timeout: 15_000,
})

http.interceptors.request.use((config) => {
  const auth = useAuthStore()
  if (auth.token) {
    config.headers.Authorization = `Bearer ${auth.token}`
  }
  return config
})

http.interceptors.response.use(
  (response) => response,
  (error: AxiosError<ErrorResponse>) => {
    const status = error.response?.status
    const body = error.response?.data

    // token 失效/账号被停用：清理登录态并回到登录页（登录接口自身的 401 = 密码错误，不跳转）
    if (status === 401 && body?.code !== 'BAD_CREDENTIALS') {
      useAuthStore().clear()
      const current = router.currentRoute.value
      if (current.name !== 'login') {
        void router.replace({ name: 'login', query: { redirect: current.fullPath } })
      }
      ElMessage.warning('登录已过期，请重新登录')
      return Promise.reject(error)
    }

    const message =
      body?.message ?? (error.code === 'ECONNABORTED' ? '请求超时，请稍后重试' : '网络异常，请稍后重试')
    ElMessage.error(message)
    return Promise.reject(error)
  },
)

/** 取出后端返回的业务错误码，便于调用方做分支（例如 COURSE_FULL 时刷新列表） */
export function errorCodeOf(error: unknown): string | undefined {
  return axios.isAxiosError<ErrorResponse>(error) ? error.response?.data?.code : undefined
}

export default http
