# PR4：报名、取消、完成与容量限制

| 项 | 内容 |
|---|---|
| 分支 | `feat/enrollment` |
| 依赖 | PR3 |
| 对应设计 | 技术设计 §5.2（状态机）、§5.3（时间规则）、§6（并发控制）、§7.4（接口） |
| 预计工作量 | 1 天（本项目的核心） |

## 1. 目标

覆盖需求里的这几项：用户报名课程、用户取消报名、查看课程已报名人员、标记学习完成（本人或管理员）、查看我的报名，以及**扩展任务：课程容量限制**。

## 2. 后端

### 2.1 新增文件

| 包 | 文件 | 说明 |
|---|---|---|
| `enrollment` | `Enrollment`、`EnrollmentStatus` | 实体自带状态转换方法：`newEnrolled(courseId, userId, now)`、`reEnroll(now)`、`cancel(now)`、`complete(now, byUserId)`；状态不允许时抛 `INVALID_STATUS_TRANSITION` |
| `enrollment` | `EnrollmentMapper` + `EnrollmentMapper.xml` | `selectByCourseAndUser`；`selectCourseEnrollments`（连表 sys_user，带出姓名、部门，以及「本人确认 / 管理员确认」）；`selectMyEnrollments`（连表 course）；`countMyStats` |
| `enrollment` | `EnrollmentService` | 见 2.2 |
| `enrollment` | `EnrollmentController` | 报名、取消、本人标记完成、报名人员列表、管理员标记完成 |
| `enrollment` | `MyEnrollmentController` | `GET /api/me/enrollments`、`GET /api/me/stats` |
| `course` | `CourseMapper.incrementEnrolledCount(id, delta)` | `UPDATE course SET enrolled_count = enrolled_count + #{delta}, updated_at = #{now} WHERE id = #{id}` |

### 2.2 EnrollmentService：所有写操作的固定步骤
**统一加锁顺序：先锁课程行，再操作报名行**，避免死锁。

| 方法 | 步骤 |
|---|---|
| `enroll(courseId, user)` | ① `selectByIdForUpdate`，不存在返回 404 ② `now >= startTime` 返回 `COURSE_STARTED` ③ 查本人的报名记录，状态为 ENROLLED 或 COMPLETED 返回 `ALREADY_ENROLLED` ④ 有容量限制且 `enrolled_count >= capacity` 返回 `COURSE_FULL` ⑤ 没有记录就 insert，状态为 CANCELLED 就 `reEnroll` 后 update ⑥ 已报名人数 +1 |
| `cancel(courseId, user)` | ① 锁课程 ② 已开课返回 `COURSE_STARTED` ③ 没有记录返回 `ENROLLMENT_NOT_FOUND`，状态不是 ENROLLED 返回 `INVALID_STATUS_TRANSITION` ④ `cancel` ⑤ 已报名人数 −1 |
| `completeSelf(courseId, user)` | ① 锁课程 ② `now < startTime` 返回 `COURSE_NOT_STARTED` ③ 记录必须是 ENROLLED ④ `complete(now, user.id)`，已报名人数不变 |
| `completeByAdmin(enrollmentId, admin)` | ① 先查报名记录得到 courseId，不存在返回 404 ② 锁课程 ③ 锁住后**重新读取**报名记录（避免读到加锁前的旧状态）④ 时间、状态校验同上 ⑤ `complete(now, admin.id)` |

- 每个方法都加 `@Transactional`；插入时撞了唯一键（`DuplicateKeyException`）转成 `ALREADY_ENROLLED`
- 只有报名本人能操作 `/me` 相关接口；管理员给别人标记完成只能走 `/api/enrollments/{id}/complete`

## 3. 测试（重点）

| 测试类 | 覆盖内容 |
|---|---|
| `EnrollmentTest`（单元测试） | 实体的状态机：所有允许和不允许的状态转换 |
| `EnrollmentFlowIT` | 报名 → 取消 → 再报名，确认复用同一行（id 不变）、计数正确；重复报名返回 409；已完成后取消返回 409；用 MutableClock 推到开课后：报名和取消返回 `COURSE_STARTED`，开课前标记完成返回 `COURSE_NOT_STARTED`；本人完成和管理员完成都记录正确的 `completed_by`；员工调用管理员完成接口返回 403 |
| `CapacityIT` | 容量 2：两人报名成功，第三人返回 `COURSE_FULL`；有一人取消后，第三人可以报名；完成的人仍然占名额；不限人数的课程不受限制 |
| `ConcurrencyIT` | **20 个不同用户同时报名一门容量为 5 的课程，恰好 5 个成功、15 个返回 `COURSE_FULL`，`enrolled_count = 5`，有效报名记录也是 5 条**；同一用户 10 个线程同时报名，只有 1 个成功，计数为 1。用 `CountDownLatch` 让所有线程同时开始，直接调用 Service（不经过 MockMvc） |
| `MyEnrollmentIT` | 我的报名按状态筛选、分页；统计数字正确；看不到别人的报名 |
| `CourseEnrollmentListIT` | 管理员能看到报名人员和确认方式；员工返回 403 |

## 4. 前端

| 文件 | 内容 |
|---|---|
| `src/api/enrollments.ts`，类型补充 | 接口封装 |
| `components/EnrollAction.vue` | 根据课程阶段和 `myStatus` 决定显示哪个按钮：报名 / 取消报名 / 已满（禁用）/ 标记完成 / 已完成（标签）/ 已结束。取消前弹确认框；遇到 `COURSE_FULL` 等错误后刷新数据 |
| `CourseListView.vue`、`CourseDetailView.vue` | 接入 `EnrollAction`；详情页给管理员加「报名人员」表格：可按状态筛选，显示确认方式，每行有「标记完成」按钮 |
| `views/MyEnrollmentsView.vue` | `/my/enrollments`：顶部显示已报名、已完成、已取消三张统计卡片；按状态分标签页的表格；每行可以取消或标记完成 |
| `AppLayout.vue` | 导航新增「我的报名」 |

## 5. 自测清单

> 已自动化：`cd e2e; npm run pr4`（Playwright，3 个员工各用独立浏览器上下文）。开课这一步用 SQL 把开始时间改到过去，不用真的等 2 分钟。
1. 管理员建一门容量为 2、2 分钟后开课的课程
2. 员工 A、B 报名成功，员工 C 看到「已满」；A 取消后，C 可以报名
3. A 取消后再报名：报名人员表里 A 只有一条记录
4. 等到开课：报名和取消按钮消失，出现「标记完成」；B 自己标记完成，管理员给 C 标记完成；报名人员表显示「本人确认」和「管理员确认」
5. 「我的报名」页面里的统计和列表正确
6. 并发：执行 `ConcurrencyIT`，把输出贴在 PR 描述里

## 6. 完成标准
- [x] 测试全部通过（并发测试连续运行 3 次都通过）、CI 通过
- [x] 自测全部通过，截图贴在 PR 描述里
- [x] 技术设计 §5.2、§6、§7.4 与实现一致
