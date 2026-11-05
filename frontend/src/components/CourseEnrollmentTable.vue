<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { enrollmentsApi } from '@/api/enrollments'
import type { Course, CourseEnrollment, EnrollmentStatus } from '@/types/api'
import { ENROLLMENT_STATUS_LABEL, ENROLLMENT_STATUS_TAG_TYPE, formatDateTime } from '@/utils/format'

/** 课程报名人员（仅管理员）：按状态筛选，开课后可逐条标记完成。 */
const props = defineProps<{ course: Course }>()
const emit = defineEmits<{ changed: [] }>()

const query = reactive({ status: '' as EnrollmentStatus | '', page: 1, size: 10 })
const records = ref<CourseEnrollment[]>([])
const total = ref(0)
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const result = await enrollmentsApi.listByCourse(props.course.id, {
      status: query.status || undefined,
      page: query.page,
      size: query.size,
    })
    records.value = result.records
    total.value = result.total
  } catch {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
}

function filter() {
  query.page = 1
  void load()
}

function confirmedBy(row: CourseEnrollment): string {
  if (row.selfConfirmed === null) return '-'
  return row.selfConfirmed ? '本人确认' : `管理员确认（${row.completedByName ?? '-'}）`
}

async function complete(row: CourseEnrollment) {
  try {
    await ElMessageBox.confirm(`确认「${row.realName}」已完成本课程的学习？`, '标记完成', {
      type: 'warning',
      confirmButtonText: '确认完成',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  try {
    await enrollmentsApi.completeByAdmin(row.id)
    ElMessage.success(`已将「${row.realName}」标记为完成`)
  } catch {
    // 拦截器已提示
  }
  await load()
  emit('changed')
}

onMounted(load)
defineExpose({ reload: load })
</script>

<template>
  <div class="enrollments">
    <div class="toolbar">
      <h2 class="panel-title">报名人员</h2>
      <el-radio-group v-model="query.status" size="small" aria-label="报名状态" @change="filter">
        <el-radio-button value="">全部</el-radio-button>
        <el-radio-button value="ENROLLED">已报名</el-radio-button>
        <el-radio-button value="COMPLETED">已完成</el-radio-button>
        <el-radio-button value="CANCELLED">已取消</el-radio-button>
      </el-radio-group>
    </div>

    <el-table v-loading="loading" :data="records" row-key="id" empty-text="暂无报名" class="ui-table">
      <el-table-column prop="realName" label="姓名" min-width="100" />
      <el-table-column prop="username" label="用户名" min-width="110" />
      <el-table-column label="部门" min-width="90">
        <template #default="{ row }">{{ row.department || '-' }}</template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="ENROLLMENT_STATUS_TAG_TYPE[row.status as EnrollmentStatus]" size="small" disable-transitions>
            {{ ENROLLMENT_STATUS_LABEL[row.status as EnrollmentStatus] }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="报名时间" width="150">
        <template #default="{ row }">{{ formatDateTime(row.enrolledAt) }}</template>
      </el-table-column>
      <el-table-column label="完成时间" width="150">
        <template #default="{ row }">{{ formatDateTime(row.completedAt) }}</template>
      </el-table-column>
      <el-table-column label="确认方式" min-width="150">
        <template #default="{ row }">{{ confirmedBy(row as CourseEnrollment) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="100" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="row.status === 'ENROLLED' && course.phase !== 'UPCOMING'"
            link
            type="success"
            @click="complete(row as CourseEnrollment)"
          >
            标记完成
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-if="total > query.size"
      v-model:current-page="query.page"
      :page-size="query.size"
      :total="total"
      :pager-count="5"
      background
      layout="total, prev, pager, next"
      class="ui-pagination"
      @current-change="load"
    />
  </div>
</template>

<style scoped>
.toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}
</style>
