import { createApp } from 'vue'
import { createPinia } from 'pinia'
// Element Plus 按需引入（vite.config.ts）：模板中用到的组件自动引入组件和样式。
// 以下是插件覆盖不到的部分：基础 CSS 变量，以及在代码中调用的 ElMessage / ElMessageBox 的样式
import 'element-plus/theme-chalk/base.css'
import 'element-plus/es/components/message/style/css'
import 'element-plus/es/components/message-box/style/css'
import './styles/main.css'

import App from './App.vue'
import router from './router'

const app = createApp(App)
// pinia 必须先于 router 安装：路由守卫里会用到 auth store
app.use(createPinia())
app.use(router)
app.mount('#app')
