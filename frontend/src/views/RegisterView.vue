<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import AuthCard from '@/components/AuthCard.vue'
import { authApi } from '@/api/auth'
import { useAuthStore } from '@/stores/auth'
import {
  confirmPasswordRules,
  departmentRules,
  passwordRules,
  realNameRules,
  usernameRules,
} from '@/utils/validation'

const router = useRouter()
const auth = useAuthStore()

const formRef = ref<FormInstance>()
const loading = ref(false)
const form = reactive({ username: '', realName: '', department: '', password: '', confirmPassword: '' })
const rules: FormRules = {
  username: usernameRules,
  realName: realNameRules,
  department: departmentRules,
  password: passwordRules,
  confirmPassword: confirmPasswordRules(() => form.password),
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    const { username, realName, department, password } = form
    await authApi.register({ username, realName, password, department: department || undefined })
    // 注册成功后直接登录
    const result = await authApi.login({ username, password })
    auth.setSession(result.token, result.user)
    ElMessage.success('注册成功')
    await router.replace({ name: 'courses' })
  } catch {
    // 错误提示（如用户名已被占用）由 http 拦截器统一弹出
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <AuthCard title="注册账号" description="创建员工账号后即可浏览和报名课程。">
    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-position="top"
      @submit.prevent="submit"
    >
      <el-form-item label="用户名" prop="username">
        <el-input v-model.trim="form.username" placeholder="3-32 位字母、数字或下划线" autocomplete="username" />
      </el-form-item>
      <el-form-item label="姓名" prop="realName">
        <el-input v-model.trim="form.realName" placeholder="真实姓名" />
      </el-form-item>
      <el-form-item label="部门" prop="department">
        <el-input v-model.trim="form.department" placeholder="选填" />
      </el-form-item>
      <el-form-item label="密码" prop="password">
        <el-input
          v-model="form.password"
          type="password"
          placeholder="8-64 位，包含字母和数字"
          autocomplete="new-password"
          show-password
        />
      </el-form-item>
      <el-form-item label="确认密码" prop="confirmPassword">
        <el-input v-model="form.confirmPassword" type="password" autocomplete="new-password" show-password />
      </el-form-item>
      <el-button type="primary" native-type="submit" :loading="loading" class="full">注册</el-button>
    </el-form>
    <template #footer>
      已有账号？<router-link :to="{ name: 'login' }">去登录</router-link>
    </template>
  </AuthCard>
</template>

<style scoped>
.full {
  width: 100%;
}
</style>
