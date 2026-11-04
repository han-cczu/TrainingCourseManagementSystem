<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { CircleCheck, DataAnalysis, Reading, Refresh, Tickets, User } from '@element-plus/icons-vue'
import PageHeading from '@/components/PageHeading.vue'
import { statsApi } from '@/api/stats'
import type { CourseStats, CourseStatsSort, StatsOverview } from '@/types/api'
import { PHASE_LABEL, PHASE_TAG_TYPE, formatPercent, formatTimeRange, toPercentage } from '@/utils/format'

const overview = ref<StatsOverview | null>(null)
const courses = ref<CourseStats[]>([])
const query = reactive({ sort: 'ENROLLED' as CourseStatsSort, limit: 10 })
const loadingOverview = ref(false)
const loadingCourses = ref(false)

async function loadOverview() {
  loadingOverview.value = true
  try {
    overview.value = await statsApi.overview()
  } catch {
    // 拦截器已提示
  } finally {
    loadingOverview.value = false
  }
}

async function loadCourses() {
  loadingCourses.value = true
  try {
    courses.value = await statsApi.courses(query.sort, query.limit)
  } catch {
    // 拦截器已提示
  } finally {
    loadingCourses.value = false
  }
}

const refresh = () => Promise.all([loadOverview(), loadCourses()])

onMounted(refresh)
</script>

<template>
  <div class="page-card">
    <PageHeading title="统计" description="查看整体参与情况，以及各课程的报名和完成数据。">
      <template #actions>
        <el-button :icon="Refresh" :loading="loadingOverview || loadingCourses" @click="refresh">刷新</el-button>
      </template>
    </PageHeading>
    <div v-loading="loadingOverview" class="cards">
      <template v-if="overview">
        <el-card shadow="never" class="ui-panel stat-card" data-stat="courseCount">
          <span class="metric-icon" aria-hidden="true"><Reading /></span>
          <div class="label">课程总数</div>
          <div class="value">{{ overview.courseCount }}</div>
          <div class="hint">
            报名中 {{ overview.upcomingCount }} · 进行中 {{ overview.ongoingCount }} · 已结束 {{ overview.endedCount }}
          </div>
        </el-card>
        <el-card shadow="never" class="ui-panel stat-card" data-stat="activeEnrollments">
          <span class="metric-icon" aria-hidden="true"><Tickets /></span>
          <div class="label">有效报名人次</div>
          <div class="value">{{ overview.activeEnrollments }}</div>
          <div class="hint">已报名 + 已完成，不含已取消</div>
        </el-card>
        <el-card shadow="never" class="ui-panel stat-card" data-stat="completedCount">
          <span class="metric-icon is-success" aria-hidden="true"><CircleCheck /></span>
          <div class="label">完成人次</div>
          <div class="value">{{ overview.completedCount }}</div>
          <div class="hint">本人或管理员确认完成</div>
        </el-card>
        <el-card shadow="never" class="ui-panel stat-card" data-stat="completionRate">
          <span class="metric-icon is-success" aria-hidden="true"><DataAnalysis /></span>
          <div class="label">整体完成率</div>
          <div class="value">{{ formatPercent(overview.completionRate) }}</div>
          <div class="hint">完成人次 ÷ 有效报名人次</div>
        </el-card>
        <el-card shadow="never" class="ui-panel stat-card" data-stat="participantCount">
          <span class="metric-icon is-neutral" aria-hidden="true"><User /></span>
          <div class="label">参与员工</div>
          <div class="value">{{ overview.participantCount }}</div>
          <div class="hint">共 {{ overview.userCount }} 名用户</div>
        </el-card>
      </template>
    </div>

    <el-card class="ui-panel" shadow="never">
      <template #header>
        <div class="header">
          <h2 class="panel-title">课程统计</h2>
          <div class="controls">
            <el-radio-group v-model="query.sort" size="small" aria-label="课程统计排序" @change="loadCourses">
              <el-radio-button value="ENROLLED">按报名人数</el-radio-button>
              <el-radio-button value="COMPLETION_RATE">按完成率</el-radio-button>
            </el-radio-group>
            <el-select v-model="query.limit" size="small" class="limit" aria-label="显示课程数量" @change="loadCourses">
              <el-option :value="10" label="前 10 门" />
              <el-option :value="20" label="前 20 门" />
              <el-option :value="50" label="前 50 门" />
            </el-select>
          </div>
        </div>
      </template>

      <el-table v-loading="loadingCourses" :data="courses" row-key="courseId" empty-text="暂无课程" class="ui-table">
        <el-table-column type="index" label="#" width="50" />
        <el-table-column label="课程" min-width="200">
          <template #default="{ row }">
            <router-link :to="{ name: 'course-detail', params: { id: row.courseId } }" class="course-link">
              {{ row.title }}
            </router-link>
            <div class="sub">{{ row.instructor }} · {{ formatTimeRange(row.startTime, row.endTime) }}</div>
          </template>
        </el-table-column>
        <el-table-column label="阶段" width="90">
          <template #default="{ row }">
            <el-tag :type="PHASE_TAG_TYPE[(row as CourseStats).phase]" size="small" disable-transitions>
              {{ PHASE_LABEL[(row as CourseStats).phase] }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="报名 / 容量" width="110" class-name="enrolled-cell">
          <template #default="{ row }">{{ row.enrolledCount }} / {{ row.capacity ?? '不限' }}</template>
        </el-table-column>
        <el-table-column label="名额占用率" min-width="150">
          <template #default="{ row }">
            <div class="rate">
              <el-progress
                v-if="row.fillRate !== null"
                :percentage="toPercentage(row.fillRate)"
                :show-text="false"
                :status="row.fillRate >= 1 ? 'exception' : undefined"
                class="bar"
              />
              <span class="rate-text">{{ formatPercent(row.fillRate) }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="completedCount" label="完成数" width="80" />
        <el-table-column label="完成率" min-width="150" class-name="completion-cell">
          <template #default="{ row }">
            <div class="rate">
              <el-progress
                v-if="row.completionRate !== null"
                :percentage="toPercentage(row.completionRate)"
                :show-text="false"
                status="success"
                class="bar"
              />
              <span class="rate-text">{{ formatPercent(row.completionRate) }}</span>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<style scoped>
.cards {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 20px;
  min-height: 120px;
}

.label {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.value {
  margin: 4px 0;
  font-size: 28px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  font-variant-numeric: tabular-nums;
}

.hint {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.header {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.controls {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.limit {
  width: 100px;
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

.rate {
  display: flex;
  align-items: center;
  gap: 8px;
}

.bar {
  flex: 1;
  min-width: 60px;
}

.rate-text {
  width: 48px;
  text-align: right;
  font-variant-numeric: tabular-nums;
}

@media (max-width: 1120px) {
  .cards {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 540px) {
  .cards {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
