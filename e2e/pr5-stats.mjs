// PR5 自测清单的 UI 自动化（docs/实现计划/PR5-统计.md §5）
// 本地库里有之前的数据，因此先取统计基线，再造数据，验证页面数字 = 基线 + 本次增量
import {
  ADMIN, BASE, SHOTS, SUFFIX, apiToken, createCourseViaApi, createRunner, daysFromNow, login, registerEmployee,
  sql, waitPath,
} from './lib.mjs'

const TITLE = `E2E-统计-${SUFFIX}`

const { newPage, step, finish } = await createRunner('PR5 UI 自测结果')
const admin = await newPage('admin')
const emp = await newPage('emp')

let adminToken
let baseline
const employees = []

const api = async (page, method, path, token) => {
  const resp = await page.request.fetch(`${BASE}/api${path}`, { method, headers: { Authorization: token } })
  if (resp.status() >= 400) throw new Error(`${method} ${path} -> HTTP ${resp.status()} ${await resp.text()}`)
  return resp.status() === 204 ? null : resp.json()
}

const cardValue = async (page, key) => (await page.locator(`.stat-card[data-stat="${key}"] .value`).innerText()).trim()

await step('0. 记录统计基线；建一门容量为 4 的课：3 人报名、1 人报名后取消、开课后 1 人完成', async () => {
  adminToken = await apiToken(admin, ADMIN)
  baseline = await api(admin, 'GET', '/stats/overview', adminToken)

  const courseId = await createCourseViaApi(admin, adminToken, {
    title: TITLE, start: daysFromNow(3, 9), end: daysFromNow(3, 12), capacity: 4,
  })
  for (let i = 1; i <= 4; i++) {
    const account = await registerEmployee(emp, `stats_e${i}_${SUFFIX}`)
    employees.push({ ...account, token: await apiToken(emp, account) })
  }
  for (const e of employees) await api(emp, 'POST', `/courses/${courseId}/enrollments`, e.token)
  await api(emp, 'DELETE', `/courses/${courseId}/enrollments/me`, employees[3].token)

  sql(`UPDATE course SET start_time = NOW() - INTERVAL 10 MINUTE, end_time = NOW() + INTERVAL 2 HOUR WHERE id = ${courseId}`)
  await api(emp, 'POST', `/courses/${courseId}/enrollments/me/complete`, employees[0].token)
})

await step('1. 概览卡片 = 基线 + 本次增量（课程 +1、有效报名 +3、完成 +1、参与员工 +3、用户 +4）', async () => {
  await login(admin, ADMIN.username, ADMIN.password)
  await admin.getByRole('navigation', { name: '主导航' }).getByRole('link', { name: '统计', exact: true }).click()
  await waitPath(admin, '/stats')
  await admin.locator('.stat-card').first().waitFor()

  const expect = {
    courseCount: baseline.courseCount + 1,
    activeEnrollments: baseline.activeEnrollments + 3,
    completedCount: baseline.completedCount + 1,
    participantCount: baseline.participantCount + 3,
  }
  for (const [key, value] of Object.entries(expect)) {
    const actual = await cardValue(admin, key)
    if (actual !== String(value)) throw new Error(`${key}：期望 ${value}，页面显示 ${actual}`)
  }
  const users = await admin.locator('.stat-card[data-stat="participantCount"] .hint').innerText()
  if (!users.includes(`共 ${baseline.userCount + 4} 名用户`)) throw new Error(`用户总数不对：${users}`)

  const rate = (expect.completedCount / expect.activeEnrollments * 100).toFixed(1) + '%'
  const shownRate = await cardValue(admin, 'completionRate')
  if (shownRate !== rate) throw new Error(`整体完成率：期望 ${rate}，页面显示 ${shownRate}`)
  await admin.screenshot({ path: `${SHOTS}/pr5-01-stats.png`, fullPage: true })
})

await step('2. 课程统计表中本课程：报名 3 / 4、占用率 75.0%、完成 1、完成率 33.3%', async () => {
  await admin.locator('.el-select').click()
  await admin.getByRole('option', { name: '前 50 门' }).click()
  const row = admin.locator('.el-table__row', { hasText: TITLE })
  await row.waitFor()
  await row.locator('.enrolled-cell', { hasText: '3 / 4' }).waitFor()
  await row.getByText('75.0%').waitFor()
  await row.locator('.completion-cell', { hasText: '33.3%' }).waitFor()
})

await step('3. 切换「按完成率」后，完成率列从高到低排列（无人报名的 "-" 在最后）', async () => {
  await Promise.all([
    admin.waitForResponse((r) => r.url().includes('/api/stats/courses') && r.url().includes('COMPLETION_RATE')),
    admin.locator('.el-radio-button', { hasText: '按完成率' }).click(),
  ])
  await admin.waitForFunction(() =>
    [...document.querySelectorAll('.el-table .el-loading-mask')].every((el) => el.offsetParent === null))
  const texts = await admin.locator('.completion-cell .rate-text').allInnerTexts()
  const values = texts.map((t) => (t.trim() === '-' ? -1 : parseFloat(t)))
  for (let i = 1; i < values.length; i++) {
    if (values[i] > values[i - 1]) throw new Error(`第 ${i + 1} 行完成率 ${texts[i]} 高于上一行 ${texts[i - 1]}`)
  }
  await admin.screenshot({ path: `${SHOTS}/pr5-03-sort-by-rate.png`, fullPage: true })
})

await step('4. 员工看不到「统计」，直接访问 /stats 被跳走，调用接口返回 403', async () => {
  await login(emp, employees[0].username, employees[0].password)
  if ((await emp.getByRole('navigation', { name: '主导航' }).getByRole('link', { name: '统计', exact: true }).count()) !== 0) {
    throw new Error('员工不应看到「统计」')
  }
  await emp.goto(`${BASE}/stats`)
  await waitPath(emp, '/courses')
  const resp = await emp.request.get(`${BASE}/api/stats/overview`, { headers: { Authorization: employees[0].token } })
  if (resp.status() !== 403) throw new Error(`期望 403，实际 ${resp.status()}`)
})

await finish()
