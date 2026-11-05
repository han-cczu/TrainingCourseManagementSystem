<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import PageHeading from '@/components/PageHeading.vue'
import { authApi } from '@/api/auth'
import { confirmPasswordRules, passwordRules } from '@/utils/validation'

const formRef = ref<FormInstance>()
const loading = ref(false)
const form = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })
const rules: FormRules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: passwordRules,
  confirmPassword: confirmPasswordRules(() => form.newPassword),
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    await authApi.changePassword({ oldPassword: form.oldPassword, newPassword: form.newPassword })
    ElMessage.success('密码已修改，下次登录请使用新密码')
    formRef.value?.resetFields()
  } catch {
    // 原密码错误等提示由 http 拦截器统一弹出
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="page-card narrow">
    <PageHeading title="修改密码" description="验证当前密码后，设置用于下次登录的新密码。" />
    <el-card class="ui-panel" shadow="never">
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="submit">
        <el-form-item label="原密码" prop="oldPassword">
          <el-input v-model="form.oldPassword" type="password" autocomplete="current-password" show-password />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input
            v-model="form.newPassword"
            type="password"
            placeholder="8-64 位，包含字母和数字"
            autocomplete="new-password"
            show-password
          />
        </el-form-item>
        <el-form-item label="确认新密码" prop="confirmPassword">
          <el-input v-model="form.confirmPassword" type="password" autocomplete="new-password" show-password />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" native-type="submit" :loading="loading">保存</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<style scoped>
.narrow {
  max-width: 520px;
}
</style>
