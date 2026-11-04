<template>
  <main class="missing">
    <el-card class="ui-panel" shadow="never">
      <div class="code" aria-hidden="true">404</div>
      <h1>页面不存在</h1>
      <p>请返回首页，继续浏览培训课程。</p>
      <el-button type="primary" @click="$router.replace('/')">返回首页</el-button>
    </el-card>
  </main>
</template>

<style scoped>
.missing {
  display: grid;
  min-height: 100dvh;
  place-items: center;
  padding: 16px;
  text-align: center;
}

.missing .ui-panel {
  width: min(100%, 450px);
  --el-card-padding: 32px;
}

.code {
  color: var(--ui-accent);
  font-size: 64px;
  font-weight: 750;
  line-height: 1;
}

h1 {
  margin: 12px 0 0;
  font-size: 24px;
}

p {
  margin: 8px 0 20px;
  color: var(--ui-muted);
}
</style>
