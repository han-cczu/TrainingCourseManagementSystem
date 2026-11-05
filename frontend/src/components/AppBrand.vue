<script setup lang="ts">
import { Reading } from '@element-plus/icons-vue'
</script>

<template>
  <div class="brand">
    <span class="mark" aria-hidden="true"><el-icon><Reading /></el-icon></span>
    <span>培训课程管理</span>
  </div>
</template>

<style scoped>
.brand {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  color: var(--ui-ink);
  font-size: 16px;
  font-weight: 700;
  white-space: nowrap;
}

.mark {
  display: grid;
  width: 36px;
  height: 36px;
  place-items: center;
  border-radius: 11px;
  color: white;
  font-size: 21px;
  background: linear-gradient(145deg, #7b60e5, var(--ui-accent));
}
</style>
