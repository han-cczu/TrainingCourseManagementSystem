<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowDown, Menu, Setting, SwitchButton } from '@element-plus/icons-vue'
import AppBrand from '@/components/AppBrand.vue'
import AppNavigation from '@/layouts/AppNavigation.vue'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const drawerOpen = ref(false)
const initial = computed(() => Array.from(auth.user?.realName.trim() || '?')[0])

watch(() => route.path, () => {
  drawerOpen.value = false
})

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
  <div class="layout">
    <aside class="sidebar">
      <router-link :to="{ name: 'courses' }" class="brand-link"><AppBrand /></router-link>
      <AppNavigation />
    </aside>

    <div class="workspace">
      <header class="topbar">
        <div class="mobile-brand">
          <button class="menu-toggle" type="button" aria-label="打开主导航" :aria-expanded="drawerOpen" @click="drawerOpen = true">
            <el-icon><Menu /></el-icon>
          </button>
          <router-link :to="{ name: 'courses' }" class="brand-link"><AppBrand /></router-link>
        </div>
        <div class="breadcrumb" aria-hidden="true">
          <span>培训工作台</span>
          <span class="separator">/</span>
          <span>{{ route.meta.title }}</span>
        </div>
        <el-dropdown v-if="auth.user" trigger="click" @command="onCommand">
          <button class="user" type="button" :aria-label="`${auth.user.realName}的账号菜单`">
            <span class="avatar" aria-hidden="true">{{ initial }}</span>
            <span class="user-info">
              <strong>{{ auth.user.realName }}</strong>
              <small>{{ auth.isAdmin ? '管理员' : '员工' }}</small>
            </span>
            <el-icon aria-hidden="true"><ArrowDown /></el-icon>
          </button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="password" :icon="Setting">修改密码</el-dropdown-item>
              <el-dropdown-item command="logout" :icon="SwitchButton" divided>退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </header>

      <main class="content">
        <router-view v-slot="{ Component, route: page }">
          <Transition name="page">
            <component :is="Component" :key="page.path" />
          </Transition>
        </router-view>
      </main>
    </div>

    <el-drawer v-model="drawerOpen" direction="ltr" size="min(288px, 88vw)" title="主导航" append-to-body>
      <AppNavigation @navigate="drawerOpen = false" />
    </el-drawer>
  </div>
</template>

<style scoped>
.layout {
  display: grid;
  grid-template-columns: 224px minmax(0, 1fr);
  min-height: 100dvh;
}

.sidebar {
  position: sticky;
  top: 0;
  height: 100dvh;
  overflow-y: auto;
  padding: 22px 16px;
  border-right: 1px solid var(--ui-border);
  background: var(--ui-surface);
}

.brand-link {
  display: inline-flex;
  width: fit-content;
  text-decoration: none;
  border-radius: 8px;
}

.sidebar .brand-link {
  margin: 0 0 34px 9px;
}

.workspace {
  min-width: 0;
}

.topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-height: 68px;
  padding-inline: clamp(16px, 3vw, 36px);
  border-bottom: 1px solid var(--ui-border);
  background: var(--ui-surface);
}

.breadcrumb {
  display: flex;
  gap: 10px;
  color: var(--ui-muted);
  font-size: 13px;
}

.breadcrumb span:last-child {
  color: var(--ui-ink);
  font-weight: 600;
}

.separator {
  color: #a09ab0;
}

.content {
  padding: clamp(18px, 3vw, 34px) clamp(12px, 3vw, 36px) 40px;
}

.user,
.menu-toggle {
  display: inline-flex;
  align-items: center;
  gap: 9px;
  min-height: 42px;
  padding: 4px 8px;
  border: 0;
  border-radius: 10px;
  background: transparent;
  color: var(--ui-ink);
  font: inherit;
  cursor: pointer;
  transition: background var(--ui-ease);
}

.user:hover,
.menu-toggle:hover {
  background: var(--el-fill-color-light);
}

.avatar {
  display: grid;
  flex: none;
  width: 34px;
  height: 34px;
  place-items: center;
  border-radius: 50%;
  background: var(--ui-accent-soft);
  color: var(--ui-accent);
  font-weight: 700;
}

.user-info {
  display: grid;
  max-width: 130px;
  text-align: left;
  line-height: 1.3;
}

.user-info strong {
  overflow: hidden;
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-info small {
  color: var(--ui-muted);
  font-size: 11px;
}

.mobile-brand {
  display: none;
}

@media (max-width: 900px) {
  .layout {
    grid-template-columns: minmax(0, 1fr);
  }

  .sidebar,
  .breadcrumb {
    display: none;
  }

  .mobile-brand {
    display: flex;
    align-items: center;
    gap: 6px;
  }

  .menu-toggle {
    justify-content: center;
    width: 42px;
    font-size: 21px;
  }
}

@media (max-width: 520px) {
  .topbar {
    min-height: 60px;
    padding-inline: 8px;
  }

  .user-info {
    display: none;
  }
}
</style>
