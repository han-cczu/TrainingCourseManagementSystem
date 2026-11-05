<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { FormInstance, FormRules } from 'element-plus'
import AuthCard from '@/components/AuthCard.vue'
import { authApi } from '@/api/auth'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const formRef = ref<FormInstance>()
const loading = ref(false)
const form = reactive({ username: '', password: '' })
// 登录只校验非空，不提示用户名/密码规则
const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

/** 只允许站内相对路径，防止 ?redirect= 被用来跳转到外部网站 */
function redirectTarget(): string {
  const redirect = route.query.redirect
  return typeof redirect === 'string' && redirect.startsWith('/') && !redirect.startsWith('//')
    ? redirect
    : '/courses'
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    const result = await authApi.login({ ...form })
    auth.setSession(result.token, result.user)
    await router.replace(redirectTarget())
  } catch {
    // 错误提示（用户名或密码错误、操作过于频繁）由 http 拦截器统一弹出
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <AuthCard title="登录" description="使用你的账号进入培训工作台。">
    <el-form ref="formRef" :model="form" :rules="rules" size="large" label-position="top" @submit.prevent="submit">
      <el-form-item label="用户名" prop="username">
        <el-input v-model.trim="form.username" placeholder="用户名" autocomplete="username" />
      </el-form-item>
      <el-form-item label="密码" prop="password">
        <el-input
          v-model="form.password"
          type="password"
          placeholder="密码"
          autocomplete="current-password"
          show-password
        />
      </el-form-item>
      <el-button type="primary" native-type="submit" :loading="loading" class="full">登录</el-button>
    </el-form>
    <template #footer>
      还没有账号？<router-link :to="{ name: 'register' }">立即注册</router-link>
    </template>
  </AuthCard>
</template>

<style scoped>
.full {
  width: 100%;
}
</style>
