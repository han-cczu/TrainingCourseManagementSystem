<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowDown } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'

interface NavItem {
  path: string
  label: string
  adminOnly?: boolean
}

// 后续 PR 中补充：统计（PR5）
const NAV_ITEMS: NavItem[] = [
  { path: '/courses', label: '课程' },
  { path: '/my/enrollments', label: '我的报名' },
  { path: '/admin/users', label: '用户管理', adminOnly: true },
]

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()

const navItems = computed(() => NAV_ITEMS.filter((item) => !item.adminOnly || auth.isAdmin))
const activePath = computed(() => NAV_ITEMS.find((item) => route.path.startsWith(item.path))?.path ?? '')

function onCommand(command: string) {
  if (command === 'password') {
    void router.push({ name: 'change-password' })
  } else if (command === 'logout') {
    auth.clear()
    void router.replace({ name: 'login' })
  }
}
</script>

<template>
  <el-container class="layout">
    <el-header class="layout-header">
      <div class="brand">培训课程管理系统</div>
      <el-menu mode="horizontal" :default-active="activePath" :ellipsis="false" router class="nav">
        <el-menu-item v-for="item in navItems" :key="item.path" :index="item.path">
          {{ item.label }}
        </el-menu-item>
      </el-menu>
      <el-dropdown v-if="auth.user" trigger="click" @command="onCommand">
        <span class="user">
          {{ auth.user.realName }}
          <el-tag v-if="auth.isAdmin" size="small" type="warning" disable-transitions>管理员</el-tag>
          <el-icon><ArrowDown /></el-icon>
        </span>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="password">修改密码</el-dropdown-item>
            <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </el-header>
    <el-main>
      <router-view />
    </el-main>
  </el-container>
</template>

<style scoped>
.layout {
  min-height: 100%;
}

.layout-header {
  display: flex;
  align-items: center;
  gap: 24px;
  background: var(--el-bg-color);
  border-bottom: 1px solid var(--el-border-color-light);
}

.brand {
  font-size: 18px;
  font-weight: 600;
  white-space: nowrap;
}

.nav {
  flex: 1;
  min-width: 0;
  border-bottom: none;
}

.user {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  white-space: nowrap;
}

@media (max-width: 640px) {
  .brand {
    display: none;
  }
}
</style>
