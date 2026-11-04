<script setup lang="ts">
import { DataAnalysis, Reading, Tickets, User } from '@element-plus/icons-vue'
import { useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const emit = defineEmits<{ navigate: [] }>()
const route = useRoute()
const auth = useAuthStore()

const groups = [
  {
    label: '学习',
    items: [
      { path: '/courses', label: '课程列表', icon: Reading },
      { path: '/my/enrollments', label: '我的报名', icon: Tickets },
    ],
  },
  {
    label: '管理',
    adminOnly: true,
    items: [
      { path: '/stats', label: '统计', icon: DataAnalysis },
      { path: '/admin/users', label: '用户管理', icon: User },
    ],
  },
]
</script>

<template>
  <nav class="navigation" aria-label="主导航">
    <template v-for="group in groups" :key="group.label">
      <div v-if="!group.adminOnly || auth.isAdmin" class="nav-group">
        <p>{{ group.label }}</p>
        <router-link
          v-for="item in group.items"
          :key="item.path"
          :to="item.path"
          class="nav-link"
          :class="{ 'nav-link-active': route.path === item.path || route.path.startsWith(item.path + '/') }"
          @click="emit('navigate')"
        >
          <el-icon aria-hidden="true"><component :is="item.icon" /></el-icon>
          {{ item.label }}
        </router-link>
      </div>
    </template>
  </nav>
</template>

<style scoped>
.navigation {
  display: grid;
  gap: 26px;
}

.nav-group {
  display: grid;
  gap: 5px;
}

.nav-group p {
  margin: 0 0 4px;
  padding-inline: 12px;
  color: var(--ui-muted);
  font-size: 12px;
  font-weight: 650;
}

.nav-link {
  display: flex;
  align-items: center;
  gap: 11px;
  min-height: 44px;
  padding-inline: 12px;
  border-radius: 10px;
  color: var(--el-text-color-regular);
  font-size: 14px;
  font-weight: 550;
  text-decoration: none;
  transition: background var(--ui-ease), color var(--ui-ease);
}

.nav-link .el-icon {
  font-size: 18px;
}

.nav-link:hover {
  background: var(--el-fill-color-light);
}

.nav-link-active,
.nav-link-active:hover {
  background: var(--ui-accent-soft);
  color: var(--ui-accent);
  font-weight: 650;
}
</style>
