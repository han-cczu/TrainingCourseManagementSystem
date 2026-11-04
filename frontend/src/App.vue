<script setup lang="ts">
import { onMounted } from 'vue'
import { authApi } from '@/api/auth'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()

// 刷新页面时用 /auth/me 校验本地 token，并同步最新的用户信息（如角色、姓名变化）
onMounted(() => {
  if (auth.isLoggedIn) {
    authApi
      .me()
      .then(auth.setUser)
      .catch(() => {
        // token 失效时 http 拦截器会清理登录态并跳转登录页
      })
  }
})
</script>

<template>
  <router-view />
</template>
