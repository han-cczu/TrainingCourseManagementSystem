<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft } from '@element-plus/icons-vue'
import CapacityBar from '@/components/CapacityBar.vue'
import { coursesApi } from '@/api/courses'
import { useAuthStore } from '@/stores/auth'
import type { Course } from '@/types/api'
import { ENROLLMENT_STATUS_LABEL, PHASE_LABEL, PHASE_TAG_TYPE, formatDateTime, formatTimeRange } from '@/utils/format'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const course = ref<Course | null>(null)
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    course.value = await coursesApi.get(Number(route.params.id))
  } catch {
    void router.replace({ name: 'courses' })
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page-card">
    <el-button link :icon="ArrowLeft" class="back" @click="$router.push({ name: 'courses' })">返回课程列表</el-button>

    <el-card v-loading="loading">
      <template v-if="course">
        <div class="head">
          <h2 class="title">{{ course.title }}</h2>
          <el-tag :type="PHASE_TAG_TYPE[course.phase]" disable-transitions>{{ PHASE_LABEL[course.phase] }}</el-tag>
          <div class="spacer" />
          <el-button
            v-if="auth.isAdmin"
            type="primary"
            plain
            @click="$router.push({ name: 'course-edit', params: { id: course.id } })"
          >
            编辑课程
          </el-button>
        </div>

        <el-descriptions :column="2" border class="desc">
          <el-descriptions-item label="时间" :span="2">
            {{ formatTimeRange(course.startTime, course.endTime) }}
          </el-descriptions-item>
          <el-descriptions-item label="讲师">{{ course.instructor }}</el-descriptions-item>
          <el-descriptions-item label="地点">{{ course.location || '-' }}</el-descriptions-item>
          <el-descriptions-item label="名额" :span="2">
            <CapacityBar :course="course" class="capacity" />
          </el-descriptions-item>
          <el-descriptions-item label="我的状态">
            {{ course.myStatus ? ENROLLMENT_STATUS_LABEL[course.myStatus] : '未报名' }}
          </el-descriptions-item>
          <el-descriptions-item label="最后更新">{{ formatDateTime(course.updatedAt) }}</el-descriptions-item>
          <el-descriptions-item label="课程介绍" :span="2">
            <div class="description">{{ course.description || '暂无介绍' }}</div>
          </el-descriptions-item>
        </el-descriptions>
      </template>
    </el-card>
  </div>
</template>

<style scoped>
.back {
  margin-bottom: 12px;
}

.head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  margin-bottom: 20px;
}

.title {
  margin: 0;
  font-size: 20px;
}

.spacer {
  flex: 1;
}

.capacity {
  max-width: 360px;
}

.description {
  white-space: pre-wrap;
  line-height: 1.6;
}

@media (max-width: 640px) {
  .desc :deep(.el-descriptions__table) {
    table-layout: fixed;
  }
}
</style>
