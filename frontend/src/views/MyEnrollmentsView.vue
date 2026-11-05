<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { CircleCheck, CircleClose, Tickets } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeading from '@/components/PageHeading.vue'
import { enrollmentsApi } from '@/api/enrollments'
import type { EnrollmentStatus, MyEnrollment, MyStats } from '@/types/api'
import {
  ENROLLMENT_STATUS_LABEL,
  ENROLLMENT_STATUS_TAG_TYPE,
  PHASE_LABEL,
  PHASE_TAG_TYPE,
  formatDateTime,
  formatTimeRange,
} from '@/utils/format'

const stats = ref<MyStats>({ enrolled: 0, completed: 0, cancelled: 0 })
const query = reactive({ status: 'ALL' as EnrollmentStatus | 'ALL', page: 1, size: 10 })
const records = ref<MyEnrollment[]>([])
const total = ref(0)
const loading = ref(false)
const busyId = ref<number | null>(null)

async function loadStats() {
  try {
    stats.value = await enrollmentsApi.myStats()
  } catch {
    // 拦截器已提示
  }
}

async function load() {
  loading.value = true
  try {
    const result = await enrollmentsApi.listMine({
      status: query.status === 'ALL' ? undefined : query.status,
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

function changeTab() {
  query.page = 1
  void load()
}

/** 操作结束后刷新列表和统计（失败通常是数据已过期，例如刚好被抢满） */
async function act(row: MyEnrollment, action: () => Promise<unknown>, successText: string) {
  busyId.value = row.id
  try {
    await action()
    ElMessage.success(successText)
  } catch {
    // 拦截器已提示
  } finally {
    busyId.value = null
    await Promise.all([load(), loadStats()])
  }
}

async function confirm(text: string, title: string, confirmButtonText: string) {
  try {
    await ElMessageBox.confirm(text, title, { type: 'warning', confirmButtonText, cancelButtonText: '再想想' })
    return true
  } catch {
    return false
  }
}

async function cancel(row: MyEnrollment) {
  if (await confirm(`确定取消报名「${row.title}」？`, '取消报名', '确定取消')) {
    await act(row, () => enrollmentsApi.cancel(row.courseId), '已取消报名')
  }
}

async function complete(row: MyEnrollment) {
  if (await confirm(`确认已完成「${row.title}」的学习？完成后不能撤销。`, '标记完成', '确认完成')) {
    await act(row, () => enrollmentsApi.completeSelf(row.courseId), '已标记为完成')
  }
}

const reEnroll = (row: MyEnrollment) => act(row, () => enrollmentsApi.enroll(row.courseId), '报名成功')

onMounted(() => Promise.all([load(), loadStats()]))
</script>

<template>
  <div class="page-card">
    <PageHeading title="我的报名" description="查看课程报名记录和学习进度。" />
    <div class="stats">
      <el-card shadow="never" class="ui-panel stat">
        <span class="metric-icon" aria-hidden="true"><Tickets /></span>
        <el-statistic title="已报名" :value="stats.enrolled" />
      </el-card>
      <el-card shadow="never" class="ui-panel stat">
        <span class="metric-icon is-success" aria-hidden="true"><CircleCheck /></span>
        <el-statistic title="已完成" :value="stats.completed" />
      </el-card>
      <el-card shadow="never" class="ui-panel stat">
        <span class="metric-icon is-neutral" aria-hidden="true"><CircleClose /></span>
        <el-statistic title="已取消" :value="stats.cancelled" />
      </el-card>
    </div>

    <el-card class="ui-panel" shadow="never">
      <el-tabs v-model="query.status" @tab-change="changeTab">
        <el-tab-pane label="全部" name="ALL" />
        <el-tab-pane label="已报名" name="ENROLLED" />
        <el-tab-pane label="已完成" name="COMPLETED" />
        <el-tab-pane label="已取消" name="CANCELLED" />
      </el-tabs>

      <el-table v-loading="loading" :data="records" row-key="id" class="ui-table">
        <template #empty>
          <el-empty description="还没有报名记录">
            <el-button type="primary" @click="$router.push({ name: 'courses' })">去看看课程</el-button>
          </el-empty>
        </template>
        <el-table-column label="课程" min-width="180">
          <template #default="{ row }">
            <router-link :to="{ name: 'course-detail', params: { id: row.courseId } }" class="course-link">
              {{ row.title }}
            </router-link>
            <div class="sub">{{ row.instructor }}<template v-if="row.location"> · {{ row.location }}</template></div>
          </template>
        </el-table-column>
        <el-table-column label="上课时间" min-width="200">
          <template #default="{ row }">{{ formatTimeRange(row.startTime, row.endTime) }}</template>
        </el-table-column>
        <el-table-column label="课程阶段" width="96">
          <template #default="{ row }">
            <el-tag :type="PHASE_TAG_TYPE[(row as MyEnrollment).phase]" size="small" disable-transitions>
              {{ PHASE_LABEL[(row as MyEnrollment).phase] }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="我的状态" width="96">
          <template #default="{ row }">
            <el-tag :type="ENROLLMENT_STATUS_TAG_TYPE[(row as MyEnrollment).status]" size="small" disable-transitions>
              {{ ENROLLMENT_STATUS_LABEL[(row as MyEnrollment).status] }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="报名 / 完成时间" width="150">
          <template #default="{ row }">
            <div>{{ formatDateTime(row.enrolledAt) }}</div>
            <div v-if="row.completedAt" class="sub">完成 {{ formatDateTime(row.completedAt) }}</div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="110" fixed="right">
          <template #default="{ row }">
            <template v-if="row.status === 'ENROLLED' && row.phase === 'UPCOMING'">
              <el-button link type="danger" :loading="busyId === row.id" @click="cancel(row as MyEnrollment)">
                取消报名
              </el-button>
            </template>
            <template v-else-if="row.status === 'ENROLLED'">
              <el-button link type="success" :loading="busyId === row.id" @click="complete(row as MyEnrollment)">
                标记完成
              </el-button>
            </template>
            <template v-else-if="row.status === 'CANCELLED' && row.phase === 'UPCOMING'">
              <el-button link type="primary" :loading="busyId === row.id" @click="reEnroll(row as MyEnrollment)">
                重新报名
              </el-button>
            </template>
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
    </el-card>
  </div>
</template>

<style scoped>
.stats {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
  margin-bottom: 16px;
}

.stat :deep(.el-statistic__number) {
  font-size: 26px;
  font-weight: 650;
  font-variant-numeric: tabular-nums;
}

.course-link {
  color: var(--el-text-color-primary);
  font-weight: 600;
  text-decoration: none;
}

.course-link:hover {
  color: var(--el-color-primary);
}

.sub {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

@media (max-width: 640px) {
  .stats {
    gap: 8px;
  }

  .stat {
    --el-card-padding: 10px;
  }
}
</style>
