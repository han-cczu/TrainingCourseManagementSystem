<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { enrollmentsApi } from '@/api/enrollments'
import type { Course } from '@/types/api'

/**
 * 当前用户在某门课上的操作按钮（技术设计 §5.2、§5.3）：
 * 开课前：报名 / 取消报名 / 名额已满；开课后：标记完成 / 报名已截止；已完成不显示按钮（由状态标签展示）。
 * 操作结束（无论成败）都会触发 changed，由父组件刷新数据——失败多因数据已过期（如刚被抢满）。
 */
const props = withDefaults(defineProps<{ course: Course; size?: 'small' | 'default' }>(), { size: 'default' })
const emit = defineEmits<{ changed: [] }>()

const busy = ref(false)

type Mode = 'enroll' | 'cancel' | 'full' | 'complete' | 'closed' | 'none'

const mode = computed<Mode>(() => {
  const { phase, myStatus, capacity, remaining } = props.course
  if (myStatus === 'COMPLETED') return 'none'
  if (phase === 'UPCOMING') {
    if (myStatus === 'ENROLLED') return 'cancel'
    return capacity !== null && (remaining ?? 0) <= 0 ? 'full' : 'enroll'
  }
  return myStatus === 'ENROLLED' ? 'complete' : 'closed'
})

async function run(action: () => Promise<unknown>, successText: string) {
  busy.value = true
  try {
    await action()
    ElMessage.success(successText)
  } catch {
    // 错误提示（名额已满、课程已开始等）由 http 拦截器统一弹出
  } finally {
    busy.value = false
    emit('changed')
  }
}

async function confirm(text: string, title: string, confirmButtonText: string): Promise<boolean> {
  try {
    await ElMessageBox.confirm(text, title, { type: 'warning', confirmButtonText, cancelButtonText: '再想想' })
    return true
  } catch {
    return false
  }
}

const enroll = () => run(() => enrollmentsApi.enroll(props.course.id), '报名成功')

async function cancel() {
  if (await confirm(`确定取消报名「${props.course.title}」？`, '取消报名', '确定取消')) {
    await run(() => enrollmentsApi.cancel(props.course.id), '已取消报名')
  }
}

async function complete() {
  if (await confirm(`确认已完成「${props.course.title}」的学习？完成后不能撤销。`, '标记完成', '确认完成')) {
    await run(() => enrollmentsApi.completeSelf(props.course.id), '已标记为完成')
  }
}
</script>

<template>
  <el-button v-if="mode === 'enroll'" type="primary" :size="size" :loading="busy" @click="enroll">报名</el-button>
  <el-button v-else-if="mode === 'cancel'" type="danger" plain :size="size" :loading="busy" @click="cancel">
    取消报名
  </el-button>
  <el-button v-else-if="mode === 'full'" :size="size" disabled>名额已满</el-button>
  <el-button v-else-if="mode === 'complete'" type="success" :size="size" :loading="busy" @click="complete">
    标记完成
  </el-button>
  <span v-else-if="mode === 'closed'" class="closed">报名已截止</span>
</template>

<style scoped>
.closed {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
</style>
