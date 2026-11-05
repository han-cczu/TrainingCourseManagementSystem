<script setup lang="ts">
defineProps<{ title: string; description?: string }>()
</script>

<template>
  <header class="page-heading">
    <div class="heading-copy">
      <h1>{{ title }}</h1>
      <p v-if="description">{{ description }}</p>
    </div>
    <div v-if="$slots.actions" class="heading-actions">
      <slot name="actions" />
    </div>
  </header>
</template>

<style scoped>
.page-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px 20px;
  margin-bottom: 22px;
}

.heading-copy {
  min-width: 0;
}

h1 {
  margin: 0;
  color: var(--ui-ink);
  font-size: clamp(25px, 3vw, 34px);
  line-height: 1.25;
  overflow-wrap: anywhere;
}

p {
  margin: 7px 0 0;
  color: var(--ui-muted);
  font-size: 14px;
}

.heading-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.heading-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}
</style>
