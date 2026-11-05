// PR3 自测清单的 UI 自动化（docs/实现计划/PR3-课程管理.md §5）
// 用法：node pr3-course.mjs   （前提：docker compose 已启动）
import {
  ADMIN, BASE, SHOTS, SUFFIX, createRunner, daysFromNow, fmt, login, message, registerEmployee, sql, waitPath,
} from './lib.mjs'

const JAVA = `E2E-Java入门-${SUFFIX}`
const GIT = `E2E-Git分享-${SUFFIX}`

const { newPage, step, finish } = await createRunner('PR3 UI 自测结果')
const admin = await newPage('admin')
const emp = await newPage('emp')

const formItem = (page, label) => page.locator('.el-form-item', { hasText: label })

async function pickDateTime(page, label, date) {
  const input = formItem(page, label).locator('input')
  await input.click()
  await input.fill(fmt(date))
  await input.press('Enter')
  await page.keyboard.press('Escape')
}

async function fillCourse(page, { title, start, end, capacity }) {
  await formItem(page, '课程名称').locator('input').fill(title)
  await formItem(page, '讲师').locator('input').fill('王老师')
  await formItem(page, '地点').locator('input').fill('3 楼大会议室')
  await pickDateTime(page, '开始时间', start)
  await pickDateTime(page, '结束时间', end)
  if (capacity === null) {
    await formItem(page, '名额').locator('.el-switch').click()
  } else {
    await formItem(page, '名额').locator('.el-input-number input').fill(String(capacity))
  }
  await formItem(page, '课程介绍').locator('textarea').fill('面向新人的入门课程。\n第二行介绍。')
}

const cards = (page) => page.locator('.course-card')

/** 等待初始列表和本次操作的结果，避免数到加载骨架或旧列表。 */
async function reloadingList(page, action) {
  const settled = page.locator('.grid-wrap[aria-busy="false"]')
  await settled.waitFor()
  await Promise.all([
    page.waitForResponse((r) => new URL(r.url()).pathname === '/api/courses' && r.request().method() === 'GET'),
    action(),
  ])
  await settled.waitFor()
}

async function expectCardCount(page, expected) {
  const actual = await cards(page).count()
  if (actual !== expected) throw new Error(`期望 ${expected} 门课，实际 ${actual}`)
}

// 1. 管理员新建两门课：3 天后开课、容量 2；以及不限人数
await step('1. 管理员新建课程（容量 2、不限人数各一门），跳转到详情页', async () => {
  await login(admin, ADMIN.username, ADMIN.password)
  await admin.getByRole('button', { name: '新建课程' }).click()
  await waitPath(admin, '/courses/new')
  await fillCourse(admin, { title: JAVA, start: daysFromNow(3, 9), end: daysFromNow(3, 12), capacity: 2 })
  await admin.screenshot({ path: `${SHOTS}/pr3-01-course-form.png` })
  await admin.getByRole('button', { name: '创建' }).click()
  await message(admin, '课程已创建')
  await waitPath(admin, /^\/courses\/\d+$/)
  await admin.getByRole('heading', { name: JAVA }).waitFor()
  await admin.getByText('0 / 2').waitFor()
  await admin.screenshot({ path: `${SHOTS}/pr3-01b-course-detail.png` })

  await admin.goto(`${BASE}/courses/new`)
  await fillCourse(admin, { title: GIT, start: daysFromNow(5, 14), end: daysFromNow(5, 16), capacity: null })
  await admin.getByRole('button', { name: '创建' }).click()
  await message(admin, '课程已创建')
  await admin.getByText('不限人数').first().waitFor()
})

// 2. 员工能看到课程，但没有新建/编辑入口
await step('2. 员工能看到两门课，没有「新建」「编辑」；访问 /courses/new 被跳走', async () => {
  const account = await registerEmployee(emp, `course_emp_${SUFFIX}`)
  await login(emp, account.username, account.password)
  await emp.getByPlaceholder('搜索课程名称或讲师').fill(SUFFIX)
  await reloadingList(emp, () => emp.getByPlaceholder('搜索课程名称或讲师').press('Enter'))
  await expectCardCount(emp, 2)
  await cards(emp).filter({ hasText: JAVA }).waitFor()
  await cards(emp).filter({ hasText: GIT }).waitFor()
  if ((await emp.getByRole('button', { name: '新建课程' }).count()) !== 0) throw new Error('员工不应看到「新建课程」')
  if ((await emp.getByRole('button', { name: '编辑' }).count()) !== 0) throw new Error('员工不应看到「编辑」')
  await emp.screenshot({ path: `${SHOTS}/pr3-02-employee-list.png` })
  await emp.goto(`${BASE}/courses/new`)
  await waitPath(emp, '/courses')
})

// 3. 搜索与阶段筛选
await step('3. 关键字搜索 + 阶段筛选结果正确', async () => {
  await admin.goto(`${BASE}/courses`)
  const search = admin.getByPlaceholder('搜索课程名称或讲师')
  await search.fill(SUFFIX)
  await reloadingList(admin, () => search.press('Enter'))
  await expectCardCount(admin, 2)

  await reloadingList(admin, () => admin.locator('.el-radio-button', { hasText: '已结束' }).click())
  await expectCardCount(admin, 0)
  await admin.getByText('暂无课程').waitFor()
  await reloadingList(admin, () => admin.locator('.el-radio-button', { hasText: '报名中' }).click())
  await expectCardCount(admin, 2)
  await admin.screenshot({ path: `${SHOTS}/pr3-03-filter.png` })
})

// 4. 开始时间在过去：前端拦截，不会创建
await step('4. 开始时间早于当前时间时给出错误提示，不会创建', async () => {
  await admin.goto(`${BASE}/courses/new`)
  const past = new Date(Date.now() - 60 * 60 * 1000)
  await fillCourse(admin, { title: `E2E-过去-${SUFFIX}`, start: past, end: daysFromNow(1, 18), capacity: 5 })
  await admin.getByRole('button', { name: '创建' }).click()
  await admin.locator('.el-form-item__error').filter({ hasText: /开始时间必须晚于当前时间|请选择开始时间/ }).waitFor()
  await waitPath(admin, '/courses/new')
  await admin.screenshot({ path: `${SHOTS}/pr3-04-past-start.png` })
})

// 5. 已开课：时间和容量禁用，改标题可以保存
await step('5. 已开课的课程：时间和容量禁用，修改标题可以保存', async () => {
  sql(`UPDATE course SET start_time = NOW() - INTERVAL 1 HOUR, end_time = NOW() + INTERVAL 2 HOUR WHERE title = '${JAVA}'`)
  const id = sql(`SELECT id FROM course WHERE title = '${JAVA}'`)
  await admin.goto(`${BASE}/courses/${id}/edit`)
  await admin.getByText('课程已开始，开始/结束时间和容量不能再修改').waitFor()
  for (const label of ['开始时间', '结束时间']) {
    if (!(await formItem(admin, label).locator('input').isDisabled())) throw new Error(`${label} 应禁用`)
  }
  if (!(await formItem(admin, '名额').locator('.el-input-number input').isDisabled())) throw new Error('容量应禁用')
  await admin.screenshot({ path: `${SHOTS}/pr3-05-started-edit.png` })

  const renamed = `${JAVA}（第二期）`
  await formItem(admin, '课程名称').locator('input').fill(renamed)
  await admin.getByRole('button', { name: '保存' }).click()
  await message(admin, '课程已保存')
  await admin.getByRole('heading', { name: renamed }).waitFor()
  await admin.locator('.el-tag', { hasText: '进行中' }).first().waitFor()
})

await finish()
