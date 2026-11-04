<script setup lang="ts">
defineProps<{ title: string }>()
</script>

<template>
  <div class="auth-page">
    <el-card class="auth-card" shadow="always">
      <div class="brand">培训课程管理系统</div>
      <h2 class="title">{{ title }}</h2>
      <slot />
      <div v-if="$slots.footer" class="footer">
        <slot name="footer" />
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.auth-page {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100%;
  padding: 16px;
  box-sizing: border-box;
}

.auth-card {
  width: 100%;
  max-width: 400px;
}

.brand {
  color: var(--el-color-primary);
  font-weight: 600;
}

.title {
  margin: 8px 0 24px;
  font-size: 22px;
}

.footer {
  margin-top: 8px;
  text-align: center;
  color: var(--el-text-color-secondary);
  font-size: 14px;
}

.footer :deep(a) {
  color: var(--el-color-primary);
  text-decoration: none;
}

.footer :deep(a:hover) {
  text-decoration: underline;
}
</style>
