<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ArrowRight, Calendar, Location, Plus, Reading, Search, User } from '@element-plus/icons-vue'
import CapacityBar from '@/components/CapacityBar.vue'
import EnrollAction from '@/components/EnrollAction.vue'
import PageHeading from '@/components/PageHeading.vue'
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

const keywordInput = ref('')
const query = reactive({ keyword: '', phase: '' as CoursePhase | '', page: 1, size: 12 })
const records = ref<Course[]>([])
const total = ref(0)
const loading = ref(true)
const failed = ref(false)
const hasFilters = computed(() => Boolean(query.keyword || query.phase))
let loadVersion = 0

async function load() {
  const version = ++loadVersion
  loading.value = true
  failed.value = false
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
    if (version === loadVersion) failed.value = true
  } finally {
    if (version === loadVersion) loading.value = false
  }
}

function search() {
  query.keyword = keywordInput.value
  query.page = 1
  void load()
}

function resetFilters() {
  keywordInput.value = ''
  query.phase = ''
  search()
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
    <section class="catalog-banner">
      <PageHeading title="课程列表" description="浏览培训安排，查找适合你的课程并报名。">
        <template v-if="auth.isAdmin" #actions>
          <el-button type="primary" :icon="Plus" @click="$router.push({ name: 'course-new' })">新建课程</el-button>
        </template>
      </PageHeading>
    </section>

    <el-card class="ui-panel filters" shadow="never">
      <div class="toolbar">
        <form class="search" role="search" @submit.prevent="search">
          <el-input
            v-model.trim="keywordInput"
            placeholder="搜索课程名称或讲师"
            aria-label="搜索课程名称或讲师"
            :prefix-icon="Search"
            clearable
            @clear="search"
          />
          <el-button native-type="submit">搜索</el-button>
        </form>
        <el-radio-group v-model="query.phase" aria-label="课程阶段" @change="search">
          <el-radio-button value="">全部</el-radio-button>
          <el-radio-button value="UPCOMING">报名中</el-radio-button>
          <el-radio-button value="ONGOING">进行中</el-radio-button>
          <el-radio-button value="ENDED">已结束</el-radio-button>
        </el-radio-group>
      </div>
      <div v-if="hasFilters" class="applied-filters">
        <span>当前筛选</span>
        <el-tag v-if="query.keyword" class="keyword-tag" disable-transitions>关键词 {{ query.keyword }}</el-tag>
        <el-tag v-if="query.phase" disable-transitions>{{ PHASE_LABEL[query.phase] }}</el-tag>
        <el-button link type="primary" @click="resetFilters">清除筛选</el-button>
      </div>
    </el-card>

    <div class="results-heading">
      <h2 class="panel-title">{{ hasFilters ? '搜索结果' : '全部课程' }}</h2>
      <span v-if="!loading && !failed" role="status">共 {{ total }} 门课程</span>
    </div>

    <div v-loading="loading && records.length > 0" class="grid-wrap" :aria-busy="loading">
      <div v-if="loading && records.length === 0" class="grid" aria-hidden="true">
        <el-card v-for="index in 3" :key="index" class="ui-panel skeleton-card" shadow="never">
          <el-skeleton :rows="4" animated />
        </el-card>
      </div>
      <el-empty v-else-if="failed" class="result-state" description="课程加载失败">
        <el-button @click="load">重新加载</el-button>
      </el-empty>
      <el-empty v-else-if="records.length === 0" class="result-state" description="暂无课程">
        <el-button v-if="hasFilters" @click="resetFilters">查看全部课程</el-button>
      </el-empty>
      <div v-else class="grid">
        <el-card
          v-for="course in records"
          :key="course.id"
          :class="['ui-panel', 'course-card', `phase-${course.phase.toLowerCase()}`]"
          shadow="never"
        >
          <div class="card-head">
            <span class="course-icon" aria-hidden="true"><el-icon><Reading /></el-icon></span>
            <el-tag :type="PHASE_TAG_TYPE[course.phase]" size="small" effect="light" disable-transitions>
              {{ PHASE_LABEL[course.phase] }}
            </el-tag>
          </div>
          <h3 class="card-title">
            <router-link :to="{ name: 'course-detail', params: { id: course.id } }" class="title">
              {{ course.title }}
            </router-link>
          </h3>
          <div class="meta">
            <div><el-icon aria-hidden="true"><Calendar /></el-icon><span>{{ formatTimeRange(course.startTime, course.endTime) }}</span></div>
            <div><el-icon aria-hidden="true"><User /></el-icon><span>讲师 · {{ course.instructor }}</span></div>
            <div><el-icon aria-hidden="true"><Location /></el-icon><span>{{ course.location || '地点待定' }}</span></div>
          </div>
          <div class="course-capacity">
            <div class="capacity-label">报名名额</div>
            <CapacityBar :course="course" />
          </div>
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
            <el-button link type="primary" :icon="ArrowRight" @click="$router.push({ name: 'course-detail', params: { id: course.id } })">
              详情
            </el-button>
            <EnrollAction :course="course" size="small" class="action" @changed="refreshCourse(course.id)" />
          </div>
        </el-card>
      </div>
    </div>

    <el-pagination
      v-if="!failed && total > 0"
      v-model:current-page="query.page"
      v-model:page-size="query.size"
      :total="total"
      :page-sizes="[12, 24, 48]"
      :pager-count="5"
      background
      layout="sizes, prev, pager, next"
      class="ui-pagination"
      @current-change="load"
      @size-change="search"
    />
  </div>
</template>

<style scoped>
.catalog-banner {
  position: relative;
  isolation: isolate;
  overflow: hidden;
  padding: clamp(20px, 3.5vw, 36px);
  border: 1px solid #e5dcfd;
  border-radius: var(--ui-radius);
  background: linear-gradient(112deg, #f5f1ff, #eae2ff);
}

.catalog-banner::after {
  position: absolute;
  z-index: -1;
  top: -142px;
  right: -36px;
  width: 310px;
  height: 310px;
  border: 1px solid #d7c8fc;
  border-radius: 50%;
  box-shadow: 0 0 0 22px #eee7ff, 0 0 0 23px #d7c8fc, 0 0 0 45px #ece4ff, 0 0 0 46px #d7c8fc;
  content: '';
}

.catalog-banner :deep(.page-heading) {
  margin: 0;
}

.filters {
  margin-block: 20px;
}

.filters :deep(.el-card__body) {
  padding-block: 14px;
}

.toolbar,
.applied-filters,
.results-heading {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
}

.toolbar,
.results-heading {
  justify-content: space-between;
}

.search {
  display: flex;
  gap: 8px;
  width: min(100%, 340px);
}

.applied-filters {
  gap: 8px;
  padding-top: 12px;
  margin-top: 12px;
  border-top: 1px solid var(--ui-border);
  color: var(--ui-muted);
  font-size: 13px;
}

.keyword-tag {
  height: auto;
  max-width: 100%;
  min-height: 24px;
  white-space: normal;
  overflow-wrap: anywhere;
}

.results-heading {
  margin-bottom: 12px;
  color: var(--ui-muted);
  font-size: 13px;
}

.results-heading h2 {
  color: var(--ui-ink);
}

.grid-wrap {
  min-height: 200px;
}

.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(min(100%, 280px), 1fr));
  gap: 16px;
}

.result-state {
  border: 1px dashed var(--ui-border);
  border-radius: var(--ui-radius);
  background: var(--ui-surface);
}

.skeleton-card {
  min-height: 268px;
}

@media (max-width: 600px) {
  .skeleton-card:not(:first-child) {
    display: none;
  }
}

.el-card.course-card {
  transition: border-color var(--ui-ease), box-shadow var(--ui-ease), transform var(--ui-ease);
}

@media (hover: hover) and (pointer: fine) and (prefers-reduced-motion: no-preference) {
  .el-card.course-card:hover {
    transform: translateY(-2px);
    border-color: #d4c7fb;
    box-shadow: 0 8px 22px rgb(58 38 111 / 11%);
  }
}

.course-card :deep(.el-card__body) {
  display: flex;
  flex-direction: column;
  gap: 12px;
  height: 100%;
}

.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.course-icon {
  display: grid;
  width: 42px;
  height: 42px;
  place-items: center;
  border-radius: 12px;
  background: var(--ui-accent-soft);
  color: var(--ui-accent);
  font-size: 21px;
}

.phase-ongoing .course-icon {
  background: #fff2da;
  color: #905d15;
}

.phase-ended .course-icon {
  background: #eef0f7;
  color: #565d78;
}

.card-title {
  margin: 0;
  font-size: 18px;
  line-height: 1.4;
  overflow-wrap: anywhere;
}

.title {
  color: var(--ui-ink);
  text-decoration: none;
}

.title:hover {
  color: var(--ui-accent);
  text-decoration: underline;
  text-underline-offset: 3px;
}

.meta {
  display: grid;
  gap: 6px;
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.meta > div {
  display: flex;
  align-items: baseline;
  gap: 8px;
  overflow-wrap: anywhere;
}

.meta .el-icon {
  flex: none;
  top: 2px;
  color: var(--ui-muted);
  font-size: 15px;
}

.course-capacity {
  margin-top: auto;
  padding: 8px 10px;
  border-radius: 9px;
  background: var(--el-fill-color-lighter);
}

.capacity-label {
  margin-bottom: 4px;
  color: var(--ui-muted);
  font-size: 12px;
}

.card-foot {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.card-foot :deep(.el-button + .el-button) {
  margin-left: 0;
}

.spacer {
  flex: 1;
}
</style>
