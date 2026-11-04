import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import type { UserInfo } from '@/types/api'

const STORAGE_KEY = 'tcms.auth'

interface PersistedSession {
  token: string
  user: UserInfo
}

// localStorage 在隐私模式等情况下可能不可用，读写都要兜底
function loadSession(): PersistedSession | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    return raw ? (JSON.parse(raw) as PersistedSession) : null
  } catch {
    return null
  }
}

function saveSession(session: PersistedSession | null) {
  try {
    if (session) localStorage.setItem(STORAGE_KEY, JSON.stringify(session))
    else localStorage.removeItem(STORAGE_KEY)
  } catch {
    /* 忽略：只影响刷新后是否保持登录 */
  }
}

export const useAuthStore = defineStore('auth', () => {
  const saved = loadSession()
  const token = ref<string | null>(saved?.token ?? null)
  const user = ref<UserInfo | null>(saved?.user ?? null)

  const isLoggedIn = computed(() => token.value !== null)
  const isAdmin = computed(() => user.value?.role === 'ADMIN')

  function setSession(newToken: string, newUser: UserInfo) {
    token.value = newToken
    user.value = newUser
    saveSession({ token: newToken, user: newUser })
  }

  function setUser(newUser: UserInfo) {
    user.value = newUser
    if (token.value) saveSession({ token: token.value, user: newUser })
  }

  function clear() {
    token.value = null
    user.value = null
    saveSession(null)
  }

  return { token, user, isLoggedIn, isAdmin, setSession, setUser, clear }
})
