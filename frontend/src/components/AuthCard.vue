<script setup lang="ts">
import { Calendar, CircleCheck, Reading, Tickets } from '@element-plus/icons-vue'
import AppBrand from '@/components/AppBrand.vue'

defineProps<{ title: string; description: string }>()
</script>

<template>
  <main class="auth-page">
    <aside class="auth-intro">
      <AppBrand />
      <div class="intro-content">
        <div class="eyebrow"><el-icon><Reading /></el-icon> 员工培训工作台</div>
        <p class="intro-title">从发现课程<br />到完成学习</p>
        <p class="intro-description">查看培训安排、报名课程，并随时了解自己的学习进度。</p>
        <ul class="intro-features">
          <li><el-icon><Calendar /></el-icon> 浏览课程安排</li>
          <li><el-icon><Tickets /></el-icon> 管理培训报名</li>
          <li><el-icon><CircleCheck /></el-icon> 记录学习进度</li>
        </ul>
      </div>
    </aside>

    <section class="auth-content">
      <div class="auth-form">
        <div class="mobile-brand"><AppBrand /></div>
        <h1>{{ title }}</h1>
        <p class="description">{{ description }}</p>
        <slot />
        <div v-if="$slots.footer" class="footer">
          <slot name="footer" />
        </div>
      </div>
    </section>
  </main>
</template>

<style scoped>
.auth-page {
  display: grid;
  grid-template-columns: minmax(340px, 0.9fr) minmax(0, 1.1fr);
  gap: 20px;
  min-height: 100dvh;
  padding: 20px;
  background: var(--ui-surface);
}

.auth-intro {
  display: flex;
  position: relative;
  isolation: isolate;
  flex-direction: column;
  overflow: hidden;
  padding: clamp(24px, 4vw, 52px);
  border: 1px solid #e8e1ff;
  border-radius: 24px;
  background:
    radial-gradient(ellipse at 90% 15%, #faf8ff 0, transparent 55%),
    linear-gradient(150deg, #f6f2ff, #eae2fc);
}

.auth-intro::after {
  position: absolute;
  z-index: -1;
  right: -240px;
  bottom: -390px;
  width: 650px;
  height: 650px;
  border: 1px solid #d0c0fb;
  border-radius: 50%;
  box-shadow: 0 0 0 34px #e3d8fe, 0 0 0 35px #d0c0fb, 0 0 0 70px #e7deff, 0 0 0 71px #d0c0fb;
  content: '';
}

.intro-content {
  width: min(100%, 460px);
  margin: auto;
  padding-block: 40px;
}

.eyebrow {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 6px 11px;
  border: 1px solid #ded2ff;
  border-radius: 20px;
  background: #faf8ff;
  color: var(--ui-accent);
  font-size: 13px;
  font-weight: 650;
}

.intro-title {
  margin: 20px 0 12px;
  color: #2c214b;
  font-size: clamp(33px, 4.2vw, 58px);
  font-weight: 750;
  line-height: 1.2;
  letter-spacing: 0.02em;
}

.intro-description {
  max-width: 340px;
  margin: 0;
  color: #53476c;
  font-size: 15px;
  line-height: 1.8;
}

.intro-features {
  display: grid;
  gap: 10px;
  margin: 24px 0 0;
  padding: 0;
  list-style: none;
}

.intro-features li {
  display: flex;
  align-items: center;
  gap: 10px;
  width: fit-content;
  color: #483960;
  font-size: 14px;
}

.intro-features .el-icon {
  padding: 7px;
  border-radius: 8px;
  background: #fff9;
  color: var(--ui-accent);
  font-size: 16px;
  box-sizing: content-box;
}

.auth-content {
  display: grid;
  place-items: center;
  padding: 32px 22px;
}

.auth-form {
  width: min(100%, 400px);
  animation: page-enter 230ms ease-out both;
}

.mobile-brand {
  display: none;
}

h1 {
  margin: 0;
  font-size: 32px;
  line-height: 1.25;
}

.description {
  margin: 8px 0 24px;
  color: var(--ui-muted);
  font-size: 14px;
}

.footer {
  margin-top: 20px;
  color: var(--ui-muted);
  font-size: 14px;
  text-align: center;
}

.footer :deep(a) {
  color: var(--ui-accent);
  font-weight: 650;
  text-underline-offset: 3px;
}

@media (max-width: 760px) {
  .auth-page {
    grid-template-columns: minmax(0, 1fr);
    padding: 0;
  }

  .auth-intro {
    display: none;
  }

  .mobile-brand {
    display: block;
    margin-bottom: 28px;
  }
}
</style>
