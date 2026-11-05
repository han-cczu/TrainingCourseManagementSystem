// PR4 自测清单的 UI 自动化（docs/实现计划/PR4-报名与容量限制.md §5）
// 用法：node pr4-enrollment.mjs   （前提：docker compose 已启动）
import {
  ADMIN, BASE, SHOTS, SUFFIX, apiToken, createCourseViaApi, createRunner, daysFromNow, login, message,
  registerEmployee, sql, waitPath,
} from './lib.mjs'

const TITLE = `E2E-报名-${SUFFIX}`

const { newPage, step, finish } = await createRunner('PR4 UI 自测结果')
const admin = await newPage('admin')
const pageA = await newPage('A')
const pageB = await newPage('B')
const pageC = await newPage('C')

let courseId
const users = {}

/** 在课程列表中搜到本次的课程卡片（等列表接口返回后再取） */
async function card(page) {
  await page.goto(`${BASE}/courses`)
  const search = page.getByPlaceholder('搜索课程名称或讲师')
  await search.fill(TITLE)
  await Promise.all([
    page.waitForResponse((r) => new URL(r.url()).pathname === '/api/courses' && r.url().includes('keyword')),
    search.press('Enter'),
  ])
  const c = page.locator('.course-card', { hasText: TITLE })
  await c.waitFor()
  return c
}

const button = (scope, name) => scope.getByRole('button', { name, exact: true })

async function confirmDialog(page, buttonName) {
  await page.getByRole('dialog').getByRole('button', { name: buttonName }).click()
}

await step('0. 准备：管理员建一门容量为 2 的课程，注册员工 A、B、C 并登录', async () => {
  const adminToken = await apiToken(admin, ADMIN)
  courseId = await createCourseViaApi(admin, adminToken, {
    title: TITLE, start: daysFromNow(3, 9), end: daysFromNow(3, 12), capacity: 2,
  })
  for (const [key, page] of [['A', pageA], ['B', pageB], ['C', pageC]]) {
    users[key] = await registerEmployee(page, `enroll_${key.toLowerCase()}_${SUFFIX}`)
    await login(page, users[key].username, users[key].password)
  }
  await login(admin, ADMIN.username, ADMIN.password)
})

await step('1. A、B 报名成功，名额显示 2 / 2', async () => {
  for (const page of [pageA, pageB]) {
    const c = await card(page)
    await button(c, '报名').click()
    await message(page, '报名成功')
    await button(c, '取消报名').waitFor()
    await c.locator('.el-tag', { hasText: '已报名' }).waitFor()
  }
  await (await card(pageB)).getByText('2 / 2 已满').waitFor()
  await pageB.screenshot({ path: `${SHOTS}/pr4-01-enrolled.png` })
})

await step('2. C 看到「名额已满」，按钮不可点', async () => {
  const c = await card(pageC)
  const full = button(c, '名额已满')
  await full.waitFor()
  if (!(await full.isDisabled())) throw new Error('「名额已满」按钮应禁用')
  await pageC.screenshot({ path: `${SHOTS}/pr4-02-full.png` })
})

await step('3. A 取消报名后，C 可以报名', async () => {
  const cA = await card(pageA)
  await button(cA, '取消报名').click()
  await confirmDialog(pageA, '确定取消')
  await message(pageA, '已取消报名')
  await button(cA, '报名').waitFor() // 有空位了，A 也能再报

  const cC = await card(pageC)
  await button(cC, '报名').click()
  await message(pageC, '报名成功')
  await cC.getByText('2 / 2 已满').waitFor()
})

await step('4. B 取消后，A 在「我的报名」里重新报名，复用原记录（只有 1 行）', async () => {
  const cB = await card(pageB)
  await button(cB, '取消报名').click()
  await confirmDialog(pageB, '确定取消')
  await message(pageB, '已取消报名')

  await pageA.goto(`${BASE}/my/enrollments`)
  const row = pageA.locator('.el-table__row', { hasText: TITLE })
  await row.locator('.el-tag', { hasText: '已取消' }).waitFor()
  await row.getByRole('button', { name: '重新报名' }).click()
  await message(pageA, '报名成功')
  await row.locator('.el-tag', { hasText: '已报名' }).waitFor()

  const rows = sql(`SELECT COUNT(*) FROM enrollment e JOIN sys_user u ON u.id = e.user_id
                    WHERE e.course_id = ${courseId} AND u.username = '${users.A.username}'`)
  if (rows !== '1') throw new Error(`A 应只有 1 条报名记录，实际 ${rows}`)
  const counts = sql(`SELECT CONCAT(enrolled_count, '/', capacity) FROM course WHERE id = ${courseId}`)
  if (counts !== '2/2') throw new Error(`已报名/容量应为 2/2，实际 ${counts}`)
})

await step('5. 开课后：报名/取消按钮消失；A 本人标记完成，管理员给 C 标记完成，确认方式正确', async () => {
  sql(`UPDATE course SET start_time = NOW() - INTERVAL 10 MINUTE, end_time = NOW() + INTERVAL 2 HOUR WHERE id = ${courseId}`)

  await pageA.goto(`${BASE}/courses/${courseId}`)
  await pageA.locator('.el-tag', { hasText: '进行中' }).first().waitFor()
  if ((await button(pageA, '取消报名').count()) !== 0) throw new Error('开课后不应出现「取消报名」')
  await button(pageA, '标记完成').click()
  await confirmDialog(pageA, '确认完成')
  await message(pageA, '已标记为完成')
  await pageA.locator('.enrollment-status').getByText('已完成', { exact: true }).waitFor()

  await pageB.goto(`${BASE}/courses/${courseId}`)
  await pageB.getByText('报名已截止').waitFor()

  await admin.goto(`${BASE}/courses/${courseId}`)
  const rowC = admin.locator('.el-table__row', { hasText: users.C.username })
  await rowC.getByRole('button', { name: '标记完成' }).click()
  await confirmDialog(admin, '确认完成')
  await message(admin, '标记为完成')
  await rowC.getByText('管理员确认').waitFor()
  await admin.locator('.el-table__row', { hasText: users.A.username }).getByText('本人确认').waitFor()
  await admin.locator('.el-table__row', { hasText: users.B.username }).locator('.el-tag', { hasText: '已取消' }).waitFor()
  await admin.getByRole('dialog').waitFor({ state: 'hidden' })
  await admin.screenshot({ path: `${SHOTS}/pr4-05-admin-enrollments.png`, fullPage: true })
})

await step('6. 「我的报名」统计与列表正确（A：已完成 1；B：已取消 1）', async () => {
  const statValue = async (page, title) =>
    (await page.locator('.stat', { hasText: title }).locator('.el-statistic__number').innerText()).trim()

  await pageA.goto(`${BASE}/my/enrollments`)
  await waitPath(pageA, '/my/enrollments')
  await pageA.locator('.el-table__row', { hasText: TITLE }).locator('.el-tag', { hasText: '已完成' }).waitFor()
  if ((await statValue(pageA, '已完成')) !== '1') throw new Error('A 的「已完成」应为 1')
  await pageA.screenshot({ path: `${SHOTS}/pr4-06-my-enrollments.png` })

  await pageB.goto(`${BASE}/my/enrollments`)
  await pageB.locator('.el-table__row', { hasText: TITLE }).waitFor()
  if ((await statValue(pageB, '已取消')) !== '1') throw new Error('B 的「已取消」应为 1')
  if ((await statValue(pageB, '已报名')) !== '0') throw new Error('B 的「已报名」应为 0')
  await pageB.getByRole('tab', { name: '已完成' }).click()
  await pageB.getByText('还没有报名记录').waitFor()
})

await finish()
