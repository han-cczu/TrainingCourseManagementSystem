// 回归：列表响应乱序、有限容量被清空。
import assert from 'node:assert/strict'
import {
  ADMIN, BASE, SUFFIX, createCourseViaApi, createRunner, daysFromNow,
} from './lib.mjs'

const { newPage, step, finish } = await createRunner('补充回归测试')
const setup = await newPage('setup')
const loginResponse = await setup.request.post(`${BASE}/api/auth/login`, { data: ADMIN })
assert.equal(loginResponse.status(), 200, '管理员登录失败；刚执行过限流测试时请稍后重试')
const session = await loginResponse.json()
const authorization = `Bearer ${session.token}`

async function authenticatedPage(label) {
  const page = await newPage(label)
  await page.addInitScript(({ token, user }) => {
    localStorage.setItem('tcms.auth', JSON.stringify({ token, user }))
  }, session)
  return page
}

await step('1. 搜索响应乱序时，列表仍对应最后输入的关键字', async () => {
  const page = await authenticatedPage('search-race')
  let releaseSlow
  const slowGate = new Promise((resolve) => { releaseSlow = resolve })
  // 只替换课程列表 GET 的响应，固定制造“旧请求晚于新请求返回”；不依赖机器网速。
  await page.route('**/api/courses?*', async (route) => {
    const keyword = new URL(route.request().url()).searchParams.get('keyword')
    if (keyword === 'slow') {
      await slowGate
    }
    const records = keyword ? [{
      id: keyword === 'slow' ? 900001 : 900002,
      title: keyword === 'slow' ? '旧搜索结果' : '最新搜索结果',
      instructor: '测试讲师', location: null, description: null,
      startTime: '2099-01-01T09:00:00', endTime: '2099-01-01T11:00:00',
      phase: 'UPCOMING', capacity: 10, enrolledCount: 0, remaining: 10, myStatus: null,
    }] : []
    await route.fulfill({ json: { records, total: records.length, page: 1, size: 12 } })
  })
  try {
    await page.goto(`${BASE}/courses`)
    await page.getByText('暂无课程').waitFor()
    const search = page.getByPlaceholder('搜索课程名称或讲师')
    const slowSeen = page.waitForRequest((request) =>
      new URL(request.url()).searchParams.get('keyword') === 'slow')
    await search.fill('slow')
    await search.press('Enter')
    await slowSeen
    await search.fill('fast')
    await search.press('Enter')
    await page.getByText('最新搜索结果', { exact: true }).waitFor()
    const oldResponse = page.waitForResponse((response) =>
      new URL(response.url()).searchParams.get('keyword') === 'slow')
    releaseSlow()
    await (await oldResponse).finished()
    await page.waitForLoadState('networkidle')
    const titles = await page.locator('.course-card .title').allTextContents()
    assert.deepEqual(titles.map((title) => title.trim()), ['最新搜索结果'], '旧请求覆盖了最新搜索结果')
  } finally {
    releaseSlow()
  }
})

await step('2. 未选择「不限人数」时，清空容量应提示校验错误且不提交', async () => {
  const page = await authenticatedPage('empty-capacity')
  // 只编辑本测试创建的课程；不触碰现有课程或演示数据。
  const courseId = await createCourseViaApi(setup, authorization, {
    title: `E2E-容量空值-${SUFFIX}`, start: daysFromNow(3, 9), end: daysFromNow(3, 11), capacity: 5,
  })
  await page.goto(`${BASE}/courses/${courseId}/edit`)
  const capacityItem = page.locator('.el-form-item', { hasText: '名额' })
  const capacity = capacityItem.locator('.el-input-number input')
  await page.waitForFunction(() => document.querySelector('.el-input-number input')?.value === '5')
  assert.equal(await capacityItem.locator('input[type="checkbox"]').isChecked(), false)
  await capacity.fill('')
  await capacity.press('Tab')
  const outcome = Promise.race([
    page.waitForResponse((response) => new URL(response.url()).pathname === `/api/courses/${courseId}`
      && response.request().method() === 'PUT').then(async (response) => ({
      submitted: true, status: response.status(), capacity: response.request().postDataJSON().capacity,
    })),
    page.locator('.el-form-item__error').first().waitFor().then(() => ({ submitted: false })),
  ])
  await page.getByRole('button', { name: '保存', exact: true }).click()
  const result = await outcome
  assert.equal(result.submitted, false, `容量为空仍然提交：${JSON.stringify(result)}`)
  const stored = await setup.request.get(`${BASE}/api/courses/${courseId}`, { headers: { Authorization: authorization } })
  assert.equal((await stored.json()).capacity, 5)

  // 错误修正后仍可正常保存：显式打开不限人数才允许提交 null。
  await capacityItem.locator('.el-switch').click()
  const saved = page.waitForResponse((response) => new URL(response.url()).pathname === `/api/courses/${courseId}`
    && response.request().method() === 'PUT')
  await page.getByRole('button', { name: '保存', exact: true }).click()
  const response = await saved
  assert.equal(response.status(), 200)
  assert.equal((await response.json()).capacity, null)
})

await finish()
