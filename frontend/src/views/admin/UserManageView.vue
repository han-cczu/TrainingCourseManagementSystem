<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { Plus, Search } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import PageHeading from '@/components/PageHeading.vue'
import { usersApi } from '@/api/users'
import { useAuthStore } from '@/stores/auth'
import type { Role, UserInfo } from '@/types/api'
import { ROLE_LABEL, formatDateTime } from '@/utils/format'
import {
  confirmPasswordRules,
  departmentRules,
  passwordRules,
  realNameRules,
  usernameRules,
} from '@/utils/validation'

const auth = useAuthStore()

// ---------------- 列表 ----------------
const query = reactive({ keyword: '', page: 1, size: 10 })
const records = ref<UserInfo[]>([])
const total = ref(0)
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const result = await usersApi.list({ ...query, keyword: query.keyword || undefined })
    records.value = result.records
    total.value = result.total
  } catch {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
}

function search() {
  query.page = 1
  void load()
}

onMounted(load)

/** el-switch 的 before-change：停用前二次确认；接口成功才切换开关 */
async function beforeToggle(user: UserInfo): Promise<boolean> {
  const next = !user.enabled
  if (!next) {
    try {
      await ElMessageBox.confirm(`确定停用「${user.realName}」？停用后该用户会被立即登出且无法登录。`, '停用账号', {
        type: 'warning',
        confirmButtonText: '停用',
        cancelButtonText: '取消',
      })
    } catch {
      return false
    }
  }
  try {
    await usersApi.setEnabled(user.id, next)
    ElMessage.success(next ? '已启用' : '已停用')
    return true
  } catch {
    return false
  }
}

// ---------------- 新建用户 ----------------
const createVisible = ref(false)
const createLoading = ref(false)
const createFormRef = ref<FormInstance>()
const createForm = reactive({
  username: '',
  realName: '',
  department: '',
  role: 'EMPLOYEE' as Role,
  password: '',
})
const createRules: FormRules = {
  username: usernameRules,
  realName: realNameRules,
  department: departmentRules,
  password: passwordRules,
}

function openCreate() {
  createVisible.value = true
  createFormRef.value?.resetFields()
}

async function submitCreate() {
  const valid = await createFormRef.value?.validate().catch(() => false)
  if (!valid) return
  createLoading.value = true
  try {
    await usersApi.create({ ...createForm, department: createForm.department || undefined })
    ElMessage.success('用户已创建')
    createVisible.value = false
    search()
  } catch {
    // 拦截器已提示（如用户名已被占用）
  } finally {
    createLoading.value = false
  }
}

// ---------------- 重置密码 ----------------
const resetVisible = ref(false)
const resetLoading = ref(false)
const resetTarget = ref<UserInfo | null>(null)
const resetFormRef = ref<FormInstance>()
const resetForm = reactive({ newPassword: '', confirmPassword: '' })
const resetRules: FormRules = {
  newPassword: passwordRules,
  confirmPassword: confirmPasswordRules(() => resetForm.newPassword),
}

function openReset(user: UserInfo) {
  resetTarget.value = user
  resetVisible.value = true
  resetFormRef.value?.resetFields()
}

async function submitReset() {
  const valid = await resetFormRef.value?.validate().catch(() => false)
  if (!valid || !resetTarget.value) return
  resetLoading.value = true
  try {
    await usersApi.resetPassword(resetTarget.value.id, resetForm.newPassword)
    ElMessage.success(`已重置「${resetTarget.value.realName}」的密码`)
    resetVisible.value = false
  } catch {
    // 拦截器已提示
  } finally {
    resetLoading.value = false
  }
}
</script>

<template>
  <div class="page-card">
    <PageHeading title="用户管理" description="管理员工账号、账号状态和密码。">
      <template #actions>
        <el-button type="primary" :icon="Plus" @click="openCreate">新建用户</el-button>
      </template>
    </PageHeading>

    <el-card class="ui-panel" shadow="never">
      <form class="toolbar" role="search" @submit.prevent="search">
        <el-input
          v-model.trim="query.keyword"
          placeholder="搜索用户名或姓名"
          aria-label="搜索用户名或姓名"
          :prefix-icon="Search"
          clearable
          class="search"
          @clear="search"
        />
        <el-button native-type="submit">搜索</el-button>
      </form>

      <el-table v-loading="loading" :data="records" row-key="id" empty-text="暂无用户" class="ui-table">
        <el-table-column prop="username" label="用户名" min-width="120" />
        <el-table-column prop="realName" label="姓名" min-width="100" />
        <el-table-column label="部门" min-width="100">
          <template #default="{ row }">{{ row.department || '-' }}</template>
        </el-table-column>
        <el-table-column label="角色" width="90">
          <template #default="{ row }">
            <el-tag :type="row.role === 'ADMIN' ? 'warning' : 'info'" size="small" disable-transitions>{{ ROLE_LABEL[row.role as Role] }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-switch
              v-model="row.enabled"
              :disabled="row.id === auth.user?.id"
              :before-change="() => beforeToggle(row as UserInfo)"
            />
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="150">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openReset(row as UserInfo)">重置密码</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="query.page"
        v-model:page-size="query.size"
        :total="total"
        :page-sizes="[10, 20, 50]"
        :pager-count="5"
        background
        layout="total, sizes, prev, pager, next"
        class="ui-pagination"
        @current-change="load"
        @size-change="search"
      />
    </el-card>

    <el-dialog v-model="createVisible" title="新建用户" width="min(480px, 92vw)">
      <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-position="top">
        <el-form-item label="用户名" prop="username">
          <el-input v-model.trim="createForm.username" placeholder="3-32 位字母、数字或下划线" />
        </el-form-item>
        <el-form-item label="姓名" prop="realName">
          <el-input v-model.trim="createForm.realName" />
        </el-form-item>
        <el-form-item label="部门" prop="department">
          <el-input v-model.trim="createForm.department" placeholder="选填" />
        </el-form-item>
        <el-form-item label="角色" prop="role">
          <el-radio-group v-model="createForm.role">
            <el-radio value="EMPLOYEE">员工</el-radio>
            <el-radio value="ADMIN">管理员</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="初始密码" prop="password">
          <el-input v-model="createForm.password" type="password" placeholder="8-64 位，包含字母和数字" show-password />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="createLoading" @click="submitCreate">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="resetVisible" :title="`重置密码：${resetTarget?.realName ?? ''}`" width="min(440px, 92vw)">
      <el-form ref="resetFormRef" :model="resetForm" :rules="resetRules" label-position="top">
        <el-form-item label="新密码" prop="newPassword">
          <el-input v-model="resetForm.newPassword" type="password" placeholder="8-64 位，包含字母和数字" show-password />
        </el-form-item>
        <el-form-item label="确认新密码" prop="confirmPassword">
          <el-input v-model="resetForm.confirmPassword" type="password" show-password />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="resetVisible = false">取消</el-button>
        <el-button type="primary" :loading="resetLoading" @click="submitReset">重置</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
}

.search {
  max-width: 280px;
}
</style>
