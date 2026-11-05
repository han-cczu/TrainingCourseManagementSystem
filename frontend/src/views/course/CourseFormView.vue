<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import dayjs from 'dayjs'
import { ElMessage, type FormInstance, type FormItemRule, type FormRules } from 'element-plus'
import PageHeading from '@/components/PageHeading.vue'
import { coursesApi } from '@/api/courses'
import type { Course, CourseRequest } from '@/types/api'
import { API_DATETIME_FORMAT } from '@/utils/format'

const route = useRoute()
const router = useRouter()

const courseId = computed(() => (route.params.id ? Number(route.params.id) : null))
const isEdit = computed(() => courseId.value !== null)

const original = ref<Course | null>(null)
/** 已开课：时间和容量不能再改（技术设计 §5.3） */
const started = computed(() => original.value !== null && original.value.phase !== 'UPCOMING')

const formRef = ref<FormInstance>()
const loading = ref(false)
const saving = ref(false)
const form = reactive({
  title: '',
  instructor: '',
  location: '',
  description: '',
  startTime: '',
  endTime: '',
  unlimited: false,
  capacity: 30 as number | null | undefined,
})

const futureStart: FormItemRule = {
  trigger: 'change',
  validator: (_rule, value: string, callback) => {
    // 已开课时时间不可改，不再校验"必须在未来"
    if (!started.value && value && !dayjs(value).isAfter(dayjs())) {
      callback(new Error('开始时间必须晚于当前时间'))
    } else {
      callback()
    }
  },
}
const endAfterStart: FormItemRule = {
  trigger: 'change',
  validator: (_rule, value: string, callback) => {
    if (value && form.startTime && !dayjs(value).isAfter(dayjs(form.startTime))) {
      callback(new Error('结束时间必须晚于开始时间'))
    } else {
      callback()
    }
  },
}
const rules: FormRules = {
  title: [
    { required: true, message: '请输入课程名称', trigger: 'blur' },
    { max: 100, message: '最多 100 个字符', trigger: 'blur' },
  ],
  instructor: [
    { required: true, message: '请输入讲师', trigger: 'blur' },
    { max: 32, message: '最多 32 个字符', trigger: 'blur' },
  ],
  location: [{ max: 100, message: '最多 100 个字符', trigger: 'blur' }],
  description: [{ max: 2000, message: '最多 2000 个字符', trigger: 'blur' }],
  startTime: [{ required: true, message: '请选择开始时间', trigger: 'change' }, futureStart],
  endTime: [{ required: true, message: '请选择结束时间', trigger: 'change' }, endAfterStart],
  capacity: [{
    trigger: ['blur', 'change'],
    validator: (_rule, value: number | null | undefined, callback) => {
      if (form.unlimited) return callback()
      if (value == null) return callback(new Error('请输入课程容量'))
      if (!Number.isInteger(value) || value < minCapacity.value || value > 10000) {
        return callback(new Error(`容量需为 ${minCapacity.value}–10000 的整数`))
      }
      callback()
    },
  }],
}

/** 容量下限：编辑时不能小于已报名人数 */
const minCapacity = computed(() => Math.max(1, original.value?.enrolledCount ?? 0))

const disablePastDate = (date: Date) => dayjs(date).isBefore(dayjs(), 'day')

onMounted(async () => {
  if (!isEdit.value) return
  loading.value = true
  try {
    const course = await coursesApi.get(courseId.value!)
    original.value = course
    Object.assign(form, {
      title: course.title,
      instructor: course.instructor,
      location: course.location ?? '',
      description: course.description ?? '',
      startTime: course.startTime,
      endTime: course.endTime,
      unlimited: course.capacity === null,
      capacity: course.capacity ?? Math.max(30, course.enrolledCount),
    })
  } catch {
    void router.replace({ name: 'courses' })
  } finally {
    loading.value = false
  }
})

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  const body: CourseRequest = {
    title: form.title,
    instructor: form.instructor,
    location: form.location || undefined,
    description: form.description || undefined,
    startTime: form.startTime,
    endTime: form.endTime,
    capacity: form.unlimited ? null : form.capacity!,
  }
  saving.value = true
  try {
    const saved = isEdit.value ? await coursesApi.update(courseId.value!, body) : await coursesApi.create(body)
    ElMessage.success(isEdit.value ? '课程已保存' : '课程已创建')
    await router.replace({ name: 'course-detail', params: { id: saved.id } })
  } catch {
    // 拦截器已提示（如 COURSE_STARTED、CAPACITY_BELOW_ENROLLED）
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="page-card narrow">
    <PageHeading
      :title="isEdit ? '编辑课程' : '新建课程'"
      description="填写课程信息、上课时间和报名名额。"
    />
    <el-card v-loading="loading" class="ui-panel" shadow="never">
      <el-alert
        v-if="started"
        type="warning"
        :closable="false"
        show-icon
        title="课程已开始，开始/结束时间和容量不能再修改"
        class="alert"
      />

      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" class="course-form" @submit.prevent="submit">
        <h2 class="panel-title form-wide">基本信息</h2>
        <el-form-item label="课程名称" prop="title" class="form-wide">
          <el-input v-model="form.title" maxlength="100" show-word-limit />
        </el-form-item>
        <el-form-item label="讲师" prop="instructor">
          <el-input v-model="form.instructor" maxlength="32" />
        </el-form-item>
        <el-form-item label="地点" prop="location">
          <el-input v-model="form.location" placeholder="会议室或线上会议链接，选填" maxlength="100" />
        </el-form-item>
        <el-form-item label="课程介绍" prop="description" class="form-wide">
          <el-input v-model="form.description" type="textarea" :rows="4" maxlength="2000" show-word-limit />
        </el-form-item>
        <h2 class="panel-title schedule-title form-wide">时间与名额</h2>
        <el-form-item label="开始时间" prop="startTime">
          <el-date-picker
            v-model="form.startTime"
            type="datetime"
            placeholder="选择开始时间"
            format="YYYY-MM-DD HH:mm"
            popper-class="course-date-popper"
            :value-format="API_DATETIME_FORMAT"
            :disabled="started"
            :disabled-date="disablePastDate"
            class="picker"
          />
        </el-form-item>
        <el-form-item label="结束时间" prop="endTime">
          <el-date-picker
            v-model="form.endTime"
            type="datetime"
            placeholder="选择结束时间"
            format="YYYY-MM-DD HH:mm"
            popper-class="course-date-popper"
            :value-format="API_DATETIME_FORMAT"
            :disabled="started"
            :disabled-date="disablePastDate"
            class="picker"
          />
        </el-form-item>
        <el-form-item label="名额" prop="capacity" class="form-wide">
          <div class="capacity">
            <el-switch
              v-model="form.unlimited"
              active-text="不限人数"
              :disabled="started"
              @change="formRef?.clearValidate('capacity')"
            />
            <el-input-number
              v-if="!form.unlimited"
              v-model="form.capacity"
              :min="minCapacity"
              :max="10000"
              :disabled="started"
            />
            <span v-if="original" class="hint">已报名 {{ original.enrolledCount }} 人</span>
          </div>
        </el-form-item>
        <div class="form-actions form-wide">
          <el-button type="primary" native-type="submit" :loading="saving">
            {{ isEdit ? '保存' : '创建' }}
          </el-button>
          <el-button @click="$router.back()">取消</el-button>
        </div>
      </el-form>
    </el-card>
  </div>
</template>

<style scoped>
.narrow {
  max-width: 780px;
}

.alert {
  margin-bottom: 16px;
}

.course-form {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  column-gap: 16px;
}

.form-wide {
  grid-column: 1 / -1;
}

.panel-title {
  margin-bottom: 12px;
}

.schedule-title,
.form-actions {
  padding-top: 16px;
  border-top: 1px solid var(--ui-border);
}

.form-actions {
  display: flex;
  justify-content: flex-end;
}

.course-form :deep(.picker) {
  width: 100%;
}

.capacity {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
}

.hint {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

@media (max-width: 560px) {
  .course-form {
    grid-template-columns: minmax(0, 1fr);
  }
}

@media (max-width: 350px) {
  :global(.course-date-popper .el-date-picker) {
    width: calc(100vw - 16px);
  }

  :global(.course-date-popper .el-date-picker .el-picker-panel__content) {
    width: auto;
    margin-inline: 10px;
  }
}

@media (max-width: 420px) {
  :global(.course-date-popper .el-date-picker__time-header .el-time-panel) {
    right: 5px;
    left: auto;
  }
}
</style>
