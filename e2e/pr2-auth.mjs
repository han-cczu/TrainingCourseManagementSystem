// PR2 自测清单的 UI 自动化（docs/实现计划/PR2-认证与用户管理.md §6）
// 用法：node pr2-selftest.mjs   （前提：docker compose 已启动，前端 http://localhost:8000）
import { chromium } from 'playwright'
import { mkdirSync } from 'node:fs'

const BASE = process.env.BASE_URL ?? 'http://localhost:8000'
const SHOTS = 'shots'
mkdirSync(SHOTS, { recursive: true })

const suffix = Date.now().toString().slice(-6)
const EMP = `zhangsan_${suffix}`
const CREATED = `lisi_${suffix}`
const EMP_PW = 'Passw0rd1'
const RESET_PW = 'Reset1234x'
const CHANGED_PW = 'Changed123'

const results = []
const consoleErrors = []

async function step(name, fn) {
  try {
    await fn()
    results.push(['PASS', name, ''])
  } catch (e) {
    results.push(['FAIL', name, String(e.message).split('\n')[0]])
    const tag = name.split('.')[0]
    for (const [label, p] of [['admin', admin], ['emp', emp]]) {
      await p.screenshot({ path: `${SHOTS}/FAIL-${tag}-${label}.png` }).catch(() => {})
      console.log(`  [${label}] url=${p.url()}`)
    }
    console.log('  ' + String(e.message).split('\n').slice(0, 6).join('\n  '))
  }
}

const browser = await chromium.launch()
const newPage = async () => {
  const ctx = await browser.newContext({ viewport: { width: 1280, height: 800 }, locale: 'zh-CN' })
  const page = await ctx.newPage()
  page.setDefaultTimeout(10_000)
  // 预期内的 4xx 请求（如错误密码）浏览器会记为 "Failed to load resource"，不算前端代码错误
  page.on('console', (m) => {
    if (m.type() === 'error' && !m.text().startsWith('Failed to load resource')) {
      consoleErrors.push(`[${m.location().url}] ${m.text()}`)
    }
  })
  page.on('pageerror', (error) => consoleErrors.push(error.message))
  return page
}
const admin = await newPage()
const emp = await newPage()

const message = (page, text) => page.locator('.el-message', { hasText: text }).first().waitFor()
const menu = (page, name) => page.getByRole('navigation', { name: '主导航' }).getByRole('link', { name, exact: true })

async function login(page, username, password) {
  if (!page.url().includes('/login')) await page.goto(`${BASE}/login`)
  await page.getByPlaceholder('用户名').fill(username)
  await page.getByPlaceholder('密码').fill(password)
  await page.getByRole('button', { name: '登录' }).click()
}

async function userRow(page, username) {
  await page.getByPlaceholder('搜索用户名或姓名').fill(username)
  await page.getByRole('button', { name: '搜索' }).click()
  const row = page.locator('.el-table__row', { hasText: username })
  await row.waitFor()
  return row
}

// 1. 管理员登录：未登录访问管理页 → 跳登录页并带 redirect → 登录后回到管理页
await step('1. 管理员登录（带 redirect 回跳），导航显示「用户管理」', async () => {
  await admin.goto(`${BASE}/admin/users`)
  await admin.waitForURL(/\/login\?redirect=/)
  await login(admin, 'admin', 'Admin12345')
  await admin.waitForURL((u) => u.pathname === '/admin/users')
  await menu(admin, '用户管理').waitFor()
  await admin.locator('.el-table__row').first().waitFor() // 等列表数据加载完再截图
  await admin.screenshot({ path: `${SHOTS}/01-admin-user-manage.png` })
})

// 2. 员工自助注册 → 自动登录 → 无管理入口，直接访问管理页被跳走
await step('2. 员工注册后自动登录；无「用户管理」；访问 /admin/users 被跳走', async () => {
  await emp.goto(`${BASE}/register`)
  await emp.getByPlaceholder('3-32 位字母、数字或下划线').fill(EMP)
  await emp.getByPlaceholder('真实姓名').fill('张三')
  await emp.getByPlaceholder('选填').fill('研发部')
  const pwInputs = emp.locator('input[autocomplete="new-password"]')
  await pwInputs.nth(0).fill(EMP_PW)
  await pwInputs.nth(1).fill(EMP_PW)
  await emp.screenshot({ path: `${SHOTS}/02-register.png` })
  await emp.getByRole('button', { name: '注册' }).click()
  await emp.waitForURL((u) => u.pathname === '/courses')
  await message(emp, '注册成功')
  if ((await menu(emp, '用户管理').count()) !== 0) throw new Error('员工不应看到「用户管理」')
  await emp.goto(`${BASE}/admin/users`)
  await emp.waitForURL((u) => u.pathname === '/courses')
})

await step('2b. 注册表单校验：弱密码、两次密码不一致给出提示', async () => {
  const p = await newPage()
  await p.goto(`${BASE}/register`)
  const pwInputs = p.locator('input[autocomplete="new-password"]')
  await pwInputs.nth(0).fill('onlyletters')
  await pwInputs.nth(1).fill('different1')
  await p.getByRole('button', { name: '注册' }).click()
  await p.getByText('密码需为 8-64 位，且同时包含字母和数字').waitFor()
  await p.getByText('两次输入的密码不一致').waitFor()
  await p.screenshot({ path: `${SHOTS}/02b-register-validation.png` })
  await p.context().close()
})

// 管理员在页面上新建用户
await step('2c. 管理员通过对话框新建用户', async () => {
  await admin.getByRole('button', { name: '新建用户' }).click()
  const dialog = admin.getByRole('dialog', { name: '新建用户' })
  await dialog.getByPlaceholder('3-32 位字母、数字或下划线').fill(CREATED)
  await dialog.locator('.el-form-item', { hasText: '姓名' }).locator('input').fill('李四')
  await dialog.getByPlaceholder('8-64 位，包含字母和数字').fill(EMP_PW)
  await dialog.getByRole('button', { name: '创建' }).click()
  await message(admin, '用户已创建')
  await userRow(admin, CREATED)
})

// 3. 管理员停用员工 → 员工刷新后被踢回登录页
await step('3. 管理员停用员工，员工刷新后被踢回登录页', async () => {
  const row = await userRow(admin, EMP)
  await row.locator('.el-switch').click()
  await admin.getByRole('dialog').getByRole('button', { name: '停用' }).click()
  await message(admin, '已停用')
  await admin.screenshot({ path: `${SHOTS}/03-admin-disabled-user.png` })
  await emp.reload()
  await emp.waitForURL(/\/login/)
  await message(emp, '登录已过期')
})

// 4. 管理员重置密码并启用 → 员工用新密码登录
await step('4. 管理员重置密码并启用，员工可用新密码登录', async () => {
  const row = await userRow(admin, EMP)
  await row.getByRole('button', { name: '重置密码' }).click()
  const dialog = admin.getByRole('dialog', { name: /重置密码/ })
  const inputs = dialog.locator('input[type="password"]')
  await inputs.nth(0).fill(RESET_PW)
  await inputs.nth(1).fill(RESET_PW)
  await admin.screenshot({ path: `${SHOTS}/04-reset-password-dialog.png` })
  await dialog.getByRole('button', { name: '重置' }).click()
  await message(admin, '已重置')
  await row.locator('.el-switch').click()
  await message(admin, '已启用')

  await login(emp, EMP, RESET_PW)
  await emp.waitForURL((u) => u.pathname === '/courses')
})

// 6. 修改密码 → 退出 → 旧密码失败、新密码成功
await step('6. 修改密码后旧密码无法登录、新密码可以登录', async () => {
  await emp.goto(`${BASE}/account/password`)
  const inputs = emp.locator('form input[type="password"]')
  await inputs.nth(0).fill(RESET_PW)
  await inputs.nth(1).fill(CHANGED_PW)
  await inputs.nth(2).fill(CHANGED_PW)
  await emp.getByRole('button', { name: '保存' }).click()
  await message(emp, '密码已修改')
  await emp.screenshot({ path: `${SHOTS}/06-change-password.png` })

  await emp.locator('.user').click()
  await emp.getByText('退出登录').click()
  await emp.waitForURL((u) => u.pathname === '/login')

  await login(emp, EMP, RESET_PW)
  await message(emp, '用户名或密码错误')
  await emp.screenshot({ path: `${SHOTS}/06b-login-failed.png` })
  await login(emp, EMP, CHANGED_PW)
  await emp.waitForURL((u) => u.pathname === '/courses')
})

// 5. 登录限流（放最后：会影响同一 IP 接下来一分钟的登录）
await step('5. 连续错误登录触发 nginx 限流提示', async () => {
  const p = await newPage()
  await p.goto(`${BASE}/login`)
  await p.getByPlaceholder('用户名').fill(EMP)
  await p.getByPlaceholder('密码').fill('WrongPass1')
  let limited = false
  for (let i = 0; i < 30 && !limited; i++) {
    const [resp] = await Promise.all([
      p.waitForResponse((r) => r.url().endsWith('/api/auth/login')),
      p.getByRole('button', { name: '登录' }).click(),
    ])
    limited = resp.status() === 429
  }
  if (!limited) throw new Error('30 次内未触发 429')
  await message(p, '操作过于频繁')
  await p.screenshot({ path: `${SHOTS}/05-rate-limited.png` })
  await p.context().close()
})

await browser.close()

console.log('\n==== PR2 UI 自测结果 ====')
for (const [status, name, detail] of results) console.log(`${status}  ${name}${detail ? '  -> ' + detail : ''}`)
console.log(`\n浏览器控制台错误：${consoleErrors.length === 0 ? '无' : ''}`)
for (const e of consoleErrors) console.log('  ' + e)
process.exit(results.some(([s]) => s === 'FAIL') || consoleErrors.length > 0 ? 1 : 0)
