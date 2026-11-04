<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { Search } from '@element-plus/icons-vue'
import CapacityBar from '@/components/CapacityBar.vue'
import EnrollAction from '@/components/EnrollAction.vue'
import { coursesApi } from '@/api/courses'
import { useAuthStore } from '@/stores/auth'
import type { Course, CoursePhase } from '@/types/api'
import {
  ENROLLMENT_STATUS_LABEL,
  ENROLLMENT_STATUS_TAG_TYPE,
  PHASE_LABEL,
  PHASE_TAG_TYPE,
  formatTimeRange,
} from '@/utils/format'

const auth = useAuthStore()

const query = reactive({ keyword: '', phase: '' as CoursePhase | '', page: 1, size: 12 })
const records = ref<Course[]>([])
const total = ref(0)
const loading = ref(false)
let loadVersion = 0

async function load() {
  const version = ++loadVersion
  loading.value = true
  try {
    const result = await coursesApi.list({
      keyword: query.keyword || undefined,
      phase: query.phase || undefined,
      page: query.page,
      size: query.size,
    })
    if (version !== loadVersion) return
    records.value = result.records
    total.value = result.total
  } catch {
    // 拦截器已提示
  } finally {
    if (version === loadVersion) loading.value = false
  }
}

function search() {
  query.page = 1
  void load()
}

/** 报名/取消后只刷新这一张卡片，避免整页重载导致卡片位置跳动 */
async function refreshCourse(id: number) {
  try {
    const fresh = await coursesApi.get(id)
    const index = records.value.findIndex((c) => c.id === id)
    if (index >= 0) records.value[index] = fresh
  } catch {
    void load()
  }
}

onMounted(load)
</script>

<template>
  <div class="page-card">
    <div class="toolbar">
      <el-input
        v-model.trim="query.keyword"
        placeholder="搜索课程名称或讲师"
        :prefix-icon="Search"
        clearable
        class="search"
        @keyup.enter="search"
        @clear="search"
      />
      <el-radio-group v-model="query.phase" @change="search">
        <el-radio-button value="">全部</el-radio-button>
        <el-radio-button value="UPCOMING">报名中</el-radio-button>
        <el-radio-button value="ONGOING">进行中</el-radio-button>
        <el-radio-button value="ENDED">已结束</el-radio-button>
      </el-radio-group>
      <div class="spacer" />
      <el-button v-if="auth.isAdmin" type="primary" @click="$router.push({ name: 'course-new' })">
        新建课程
      </el-button>
    </div>

    <div v-loading="loading" class="grid-wrap">
      <el-empty v-if="!loading && records.length === 0" description="暂无课程" />
      <div v-else class="grid">
        <el-card v-for="course in records" :key="course.id" shadow="hover" class="course-card">
          <div class="card-head">
            <router-link :to="{ name: 'course-detail', params: { id: course.id } }" class="title">
              {{ course.title }}
            </router-link>
            <el-tag :type="PHASE_TAG_TYPE[course.phase]" size="small" effect="light" disable-transitions>
              {{ PHASE_LABEL[course.phase] }}
            </el-tag>
          </div>
          <dl class="meta">
            <dt>时间</dt>
            <dd>{{ formatTimeRange(course.startTime, course.endTime) }}</dd>
            <dt>讲师</dt>
            <dd>{{ course.instructor }}</dd>
            <dt>地点</dt>
            <dd>{{ course.location || '-' }}</dd>
          </dl>
          <CapacityBar :course="course" />
          <div class="card-foot">
            <el-tag
              v-if="course.myStatus"
              size="small"
              :type="ENROLLMENT_STATUS_TAG_TYPE[course.myStatus]"
              effect="plain"
              disable-transitions
            >
              {{ ENROLLMENT_STATUS_LABEL[course.myStatus] }}
            </el-tag>
            <div class="spacer" />
            <el-button
              v-if="auth.isAdmin"
              link
              type="primary"
              @click="$router.push({ name: 'course-edit', params: { id: course.id } })"
            >
              编辑
            </el-button>
            <el-button link type="primary" @click="$router.push({ name: 'course-detail', params: { id: course.id } })">
              详情
            </el-button>
            <EnrollAction :course="course" size="small" class="action" @changed="refreshCourse(course.id)" />
          </div>
        </el-card>
      </div>
    </div>

    <el-pagination
      v-if="total > 0"
      v-model:current-page="query.page"
      v-model:page-size="query.size"
      :total="total"
      :page-sizes="[12, 24, 48]"
      layout="total, sizes, prev, pager, next"
      class="pagination"
      @current-change="load"
      @size-change="search"
    />
  </div>
</template>

<style scoped>
.toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}

.search {
  width: 260px;
}

.spacer {
  flex: 1;
}

.grid-wrap {
  min-height: 200px;
}

.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 16px;
}

.course-card :deep(.el-card__body) {
  display: flex;
  flex-direction: column;
  gap: 12px;
  height: 100%;
  box-sizing: border-box;
}

.card-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
}

.title {
  font-size: 16px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  text-decoration: none;
  line-height: 1.4;
}

.title:hover {
  color: var(--el-color-primary);
}

.meta {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 4px 12px;
  margin: 0;
  font-size: 13px;
}

.meta dt {
  color: var(--el-text-color-secondary);
}

.meta dd {
  margin: 0;
  color: var(--el-text-color-regular);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-foot {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-top: auto;
}

.card-foot .action {
  margin-left: 8px;
}

.pagination {
  margin-top: 16px;
  justify-content: flex-end;
}

@media (max-width: 640px) {
  .search {
    width: 100%;
  }
}
</style>
