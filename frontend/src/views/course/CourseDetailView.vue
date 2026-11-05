<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, Calendar, EditPen, Location, User } from '@element-plus/icons-vue'
import CapacityBar from '@/components/CapacityBar.vue'
import CourseEnrollmentTable from '@/components/CourseEnrollmentTable.vue'
import EnrollAction from '@/components/EnrollAction.vue'
import PageHeading from '@/components/PageHeading.vue'
import { coursesApi } from '@/api/courses'
import { useAuthStore } from '@/stores/auth'
import type { Course } from '@/types/api'
import {
  ENROLLMENT_STATUS_LABEL,
  ENROLLMENT_STATUS_TAG_TYPE,
  PHASE_LABEL,
  PHASE_TAG_TYPE,
  formatDateTime,
  formatTimeRange,
} from '@/utils/format'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const course = ref<Course | null>(null)
const loading = ref(true)
const enrollmentTable = ref<InstanceType<typeof CourseEnrollmentTable> | null>(null)

async function load() {
  loading.value = course.value === null
  try {
    course.value = await coursesApi.get(Number(route.params.id))
  } catch {
    void router.replace({ name: 'courses' })
  } finally {
    loading.value = false
  }
}

/** 自己报名/取消后：刷新课程信息，管理员还要刷新报名人员表 */
async function onEnrollChanged() {
  await load()
  await enrollmentTable.value?.reload()
}

onMounted(load)
</script>

<template>
  <div class="page-card">
    <el-button link :icon="ArrowLeft" class="back" @click="$router.push({ name: 'courses' })">返回课程列表</el-button>

    <el-card v-if="loading" class="ui-panel" shadow="never" aria-busy="true">
      <el-skeleton :rows="5" animated />
    </el-card>

    <template v-if="course">
      <PageHeading :title="course.title">
        <template #actions>
          <el-tag :type="PHASE_TAG_TYPE[course.phase]" disable-transitions>{{ PHASE_LABEL[course.phase] }}</el-tag>
          <el-button
            v-if="auth.isAdmin"
            :icon="EditPen"
            @click="$router.push({ name: 'course-edit', params: { id: course.id } })"
          >
            编辑课程
          </el-button>
        </template>
      </PageHeading>

      <div class="detail-grid">
        <el-card class="ui-panel registration" shadow="never">
          <template #header><h2 class="panel-title">报名信息</h2></template>
          <div class="registration-capacity">
            <span class="label">报名名额</span>
            <CapacityBar :course="course" />
          </div>
          <div class="enrollment-status">
            <span class="label">我的状态</span>
            <el-tag v-if="course.myStatus" :type="ENROLLMENT_STATUS_TAG_TYPE[course.myStatus]" size="small" disable-transitions>
              {{ ENROLLMENT_STATUS_LABEL[course.myStatus] }}
            </el-tag>
            <span v-else>未报名</span>
          </div>
          <div class="enrollment-action">
            <EnrollAction :course="course" @changed="onEnrollChanged" />
          </div>
        </el-card>

        <el-card class="ui-panel overview" shadow="never">
          <template #header><h2 class="panel-title">课程信息</h2></template>
          <dl class="facts">
            <div class="time">
              <dt><el-icon aria-hidden="true"><Calendar /></el-icon> 上课时间</dt>
              <dd>{{ formatTimeRange(course.startTime, course.endTime) }}</dd>
            </div>
            <div>
              <dt><el-icon aria-hidden="true"><User /></el-icon> 讲师</dt>
              <dd>{{ course.instructor }}</dd>
            </div>
            <div>
              <dt><el-icon aria-hidden="true"><Location /></el-icon> 地点</dt>
              <dd>{{ course.location || '地点待定' }}</dd>
            </div>
          </dl>
          <h3 class="panel-title">课程介绍</h3>
          <p class="description">{{ course.description || '暂无介绍' }}</p>
          <p class="updated">最后更新 {{ formatDateTime(course.updatedAt) }}</p>
        </el-card>
      </div>

      <el-card v-if="auth.isAdmin" class="ui-panel enrollments" shadow="never">
        <CourseEnrollmentTable ref="enrollmentTable" :course="course" @changed="load" />
      </el-card>
    </template>
  </div>
</template>

<style scoped>
.back {
  margin-bottom: 16px;
}

.detail-grid {
  display: grid;
  grid-template-columns: minmax(0, 2fr) minmax(245px, 1fr);
  grid-template-areas: 'overview registration';
  gap: 16px;
  align-items: start;
}

.overview {
  grid-area: overview;
}

.registration {
  grid-area: registration;
}

.registration-capacity {
  display: grid;
  gap: 6px;
  margin-bottom: 16px;
}

.label,
.updated,
.facts dt {
  color: var(--ui-muted);
  font-size: 13px;
}

.enrollment-status {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  font-size: 14px;
}

.enrollment-action:not(:empty) {
  padding-top: 14px;
  margin-top: 14px;
  border-top: 1px solid var(--ui-border);
}

.enrollment-action :deep(.el-button) {
  width: 100%;
}

.facts {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin: 0 0 22px;
}

.facts > div {
  padding: 10px 14px;
  border-radius: 9px;
  background: var(--el-fill-color-lighter);
}

.facts .time {
  grid-column: 1 / -1;
}

.facts dt {
  display: flex;
  align-items: center;
  gap: 6px;
}

.facts dd {
  margin: 4px 0 0;
  font-size: 14px;
  overflow-wrap: anywhere;
}

.enrollments {
  margin-top: 16px;
}

.description {
  margin: 8px 0 16px;
  color: var(--el-text-color-regular);
  white-space: pre-wrap;
  line-height: 1.75;
  overflow-wrap: anywhere;
}

.updated {
  margin: 0;
}

@media (max-width: 720px) {
  .detail-grid {
    grid-template-columns: minmax(0, 1fr);
    grid-template-areas: 'registration' 'overview';
  }
}
</style>
