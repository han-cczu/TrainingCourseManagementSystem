<script setup lang="ts">
import { computed } from 'vue'
import type { Course } from '@/types/api'

const props = defineProps<{ course: Pick<Course, 'capacity' | 'enrolledCount'> }>()

const percentage = computed(() => {
  const { capacity, enrolledCount } = props.course
  return capacity ? Math.min(100, Math.round((enrolledCount / capacity) * 100)) : 0
})
const full = computed(() => props.course.capacity !== null && props.course.enrolledCount >= props.course.capacity)
</script>

<template>
  <div class="capacity">
    <template v-if="course.capacity === null">
      <span class="text">已报名 {{ course.enrolledCount }} 人 · 不限人数</span>
    </template>
    <template v-else>
      <el-progress
        :percentage="percentage"
        :status="full ? 'exception' : undefined"
        :show-text="false"
        :stroke-width="6"
        class="bar"
      />
      <span class="text" :class="{ full }">
        {{ course.enrolledCount }} / {{ course.capacity }}{{ full ? ' 已满' : '' }}
      </span>
    </template>
  </div>
</template>

<style scoped>
.capacity {
  display: flex;
  align-items: center;
  gap: 8px;
}

.bar {
  flex: 1;
  min-width: 32px;
}

.text {
  font-size: 13px;
  font-variant-numeric: tabular-nums;
  color: var(--el-text-color-regular);
}

.text.full {
  color: var(--el-color-danger);
}
</style>
