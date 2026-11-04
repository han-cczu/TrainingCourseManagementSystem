// UI 自测公共工具：浏览器与页面、逐项记录结果、失败自动截图、常用操作
import { chromium } from 'playwright'
import { execFileSync } from 'node:child_process'
import { mkdirSync } from 'node:fs'
import { fileURLToPath } from 'node:url'

export const BASE = process.env.BASE_URL ?? 'http://localhost:8000'
export const SHOTS = 'shots'
export const ADMIN = { username: 'admin', password: 'Admin12345' }
/** 每次运行唯一的后缀，保证可重复执行 */
export const SUFFIX = Date.now().toString().slice(-6)

const COMPOSE_FILE = fileURLToPath(new URL('../deploy/docker-compose.yml', import.meta.url))

/** 等后端就绪：未登录访问 /api/auth/me 返回 401 说明 nginx → backend 链路已通 */
async function waitForBackend(timeoutMs = 90_000) {
  const deadline = Date.now() + timeoutMs
  while (Date.now() < deadline) {
    try {
      if ((await fetch(`${BASE}/api/auth/me`)).status === 401) return
    } catch {
      // 前端或后端尚未启动
    }
    await new Promise((r) => setTimeout(r, 1000))
  }
  throw new Error(`后端在 ${timeoutMs / 1000}s 内未就绪：${BASE}`)
}

export async function createRunner(title) {
  mkdirSync(SHOTS, { recursive: true })
  await waitForBackend()
  const browser = await chromium.launch()
  const pages = []
  const results = []
  const consoleErrors = []

  async function newPage(label) {
    const ctx = await browser.newContext({ viewport: { width: 1280, height: 800 }, locale: 'zh-CN' })
    const page = await ctx.newPage()
    page.setDefaultTimeout(10_000)
    // 预期内的 4xx 请求浏览器会记为 "Failed to load resource"，不算前端代码错误
    page.on('console', (m) => {
      if (m.type() === 'error' && !m.text().startsWith('Failed to load resource')) {
        consoleErrors.push(`[${label}] ${m.text()}`)
      }
    })
    page.on('pageerror', (error) => consoleErrors.push(`[${label}] ${error.message}`))
    pages.push([label, page])
    return page
  }

  async function step(name, fn) {
    try {
      await fn()
      results.push(['PASS', name, ''])
    } catch (e) {
      const firstLine = String(e.message).split('\n')[0]
      results.push(['FAIL', name, firstLine])
      const tag = name.split(/[ .]/)[0]
      for (const [label, p] of pages) {
        if (p.isClosed()) continue
        await p.screenshot({ path: `${SHOTS}/FAIL-${tag}-${label}.png` }).catch(() => {})
        console.log(`  [${label}] url=${p.url()}`)
      }
      console.log('  ' + String(e.message).split('\n').slice(0, 6).join('\n  '))
    }
  }

  async function finish() {
    await browser.close()
    console.log(`\n==== ${title} ====`)
    for (const [status, name, detail] of results) console.log(`${status}  ${name}${detail ? '  -> ' + detail : ''}`)
    console.log(`\n浏览器控制台错误：${consoleErrors.length === 0 ? '无' : ''}`)
    for (const e of consoleErrors) console.log('  ' + e)
    process.exit(results.some(([s]) => s === 'FAIL') || consoleErrors.length > 0 ? 1 : 0)
  }

  return { newPage, step, finish }
}

/** 等待 Element Plus 的 ElMessage 提示 */
export const message = (page, text) => page.locator('.el-message', { hasText: text }).first().waitFor()

/** 按 pathname 精确等待（'**\/x' 通配会误匹配 /login?redirect=/x） */
export const waitPath = (page, path) =>
  page.waitForURL((u) => (path instanceof RegExp ? path.test(u.pathname) : u.pathname === path))

export async function login(page, username, password) {
  if (!page.url().includes('/login')) await page.goto(`${BASE}/login`)
  await page.getByPlaceholder('用户名').fill(username)
  await page.getByPlaceholder('密码').fill(password)
  await page.getByRole('button', { name: '登录' }).click()
  await page.waitForURL((u) => u.pathname !== '/login')
}

/** 通过接口注册员工（比走页面快），返回用户名 */
export async function registerEmployee(page, username, password = 'Passw0rd1') {
  const resp = await page.request.post(`${BASE}/api/auth/register`, {
    data: { username, password, realName: `员工-${username}`, department: '研发部' },
  })
  if (resp.status() !== 201) throw new Error(`注册 ${username} 失败：HTTP ${resp.status()}`)
  return { username, password }
}

/** 通过接口登录，返回 Authorization 头的值 */
export async function apiToken(page, { username, password }) {
  const resp = await page.request.post(`${BASE}/api/auth/login`, { data: { username, password } })
  if (!resp.ok()) throw new Error(`登录 ${username} 失败：HTTP ${resp.status()}`)
  return `Bearer ${(await resp.json()).token}`
}

/** 管理员通过接口建课（页面建课已在 PR3 自测覆盖），返回课程 ID */
export async function createCourseViaApi(page, adminToken, { title, start, end, capacity }) {
  const iso = (d) => `${fmt(d).replace(' ', 'T')}:00`
  const resp = await page.request.post(`${BASE}/api/courses`, {
    headers: { Authorization: adminToken },
    data: { title, instructor: '王老师', location: '3 楼大会议室', startTime: iso(start), endTime: iso(end), capacity },
  })
  if (resp.status() !== 201) throw new Error(`建课失败：HTTP ${resp.status()} ${await resp.text()}`)
  return (await resp.json()).id
}

/** 在本地 compose 的 MySQL 中执行 SQL（用于构造"已开课"等状态） */
export function sql(statement) {
  // 必须指定 utf8mb4：客户端默认字符集下，含中文的 WHERE 条件会匹配不到任何行
  // SQL 通过 stdin 传入，避免 Windows 命令行参数的编码和引号转义问题；出错时 execFileSync 会抛异常
  return execFileSync(
    'docker',
    ['compose', '-f', COMPOSE_FILE, 'exec', '-T', 'mysql',
      'sh', '-c', 'MYSQL_PWD="$MYSQL_PASSWORD" mysql -utcms --default-character-set=utf8mb4 -N tcms'],
    { input: statement, encoding: 'utf8' },
  ).trim()
}

/** 本地时间格式化为 YYYY-MM-DD HH:mm（与表单日期选择器的显示格式一致） */
export function fmt(date) {
  const p = (n) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${p(date.getMonth() + 1)}-${p(date.getDate())} ${p(date.getHours())}:${p(date.getMinutes())}`
}

export function daysFromNow(days, hour, minute = 0) {
  const d = new Date()
  d.setDate(d.getDate() + days)
  d.setHours(hour, minute, 0, 0)
  return d
}
