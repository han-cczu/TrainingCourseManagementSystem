<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

interface NavItem {
  path: string
  label: string
  adminOnly?: boolean
}

// 后续 PR 中逐步补充：我的报名、统计、用户管理
const NAV_ITEMS: NavItem[] = [{ path: '/courses', label: '课程' }]

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()

const navItems = computed(() => NAV_ITEMS.filter((item) => !item.adminOnly || auth.isAdmin))
const activePath = computed(() => NAV_ITEMS.find((item) => route.path.startsWith(item.path))?.path ?? '')

function logout() {
  auth.clear()
  void router.replace({ name: 'login' })
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
      <el-dropdown v-if="auth.user" trigger="click">
        <span class="user">
          {{ auth.user.realName }}
          <el-tag v-if="auth.isAdmin" size="small" type="warning">管理员</el-tag>
        </span>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item @click="logout">退出登录</el-dropdown-item>
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
  border-bottom: none;
}

.user {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
}
</style>
