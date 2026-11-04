# PR3：课程管理

| 项 | 内容 |
|---|---|
| 分支 | `feat/course` |
| 依赖 | PR2（需要登录和角色） |
| 对应设计 | 技术设计 §5.1（course 表）、§5.3（时间规则）、§5.4（时区）、§7.3（接口） |
| 预计工作量 | 0.5 天 |

## 1. 目标

- 管理员创建、编辑课程；所有登录用户可以浏览课程列表和详情
- 实现时间规则：只能创建未来的课程；已开课的课程不能改时间和容量
- 课程阶段（UPCOMING / ONGOING / ENDED）实时计算
- 列表返回当前用户在每门课的报名状态 `myStatus`（这个 PR 里恒为 null，报名功能在 PR4 实现，但 SQL 先写好）

**不做**：删除课程、课程分类、封面图。

## 2. 后端

### 2.1 新增文件

| 包 | 文件 | 说明 |
|---|---|---|
| `course` | `Course`、`CourseMapper` | `CourseMapper` 额外提供 `selectByIdForUpdate(id)`（`@Select ... FOR UPDATE`，PR4 的报名也会用到） |
| `course` | `CoursePhase` | 枚举，提供 `static of(start, end, now)` |
| `course` | `CourseService` | 创建、编辑、分页查询、详情 |
| `course` | `CourseController` | §7.3 的 4 个接口；创建和编辑需要 ADMIN |
| `course/dto` | `CourseRequest`（创建与编辑共用）、`CourseVO` | 见 2.2；查询参数直接用 `@RequestParam` |
| `resources/mapper` | `CourseMapper.xml` | 分页查询：`LEFT JOIN enrollment e ON e.course_id = c.id AND e.user_id = #{userId}` 带出 `myStatus` |

### 2.2 字段与校验
| 字段 | 规则 |
|---|---|
| `title` | 必填，1–100 字 |
| `description` | 选填，最多 2000 字 |
| `instructor` | 必填，1–32 字 |
| `location` | 选填，最多 100 字 |
| `startTime` / `endTime` | 必填，`endTime > startTime`；创建时 `startTime > now` |
| `capacity` | 选填，为空表示不限；填写时 1–10000 |

`CourseVO` 包含：上面这些字段，加上 `id`、`enrolledCount`、`remaining`（不限人数时为 null）、`phase`、`myStatus`、`createdAt`。

### 2.3 编辑规则（§5.3）
1. 先 `selectByIdForUpdate` 锁住课程行，防止和报名同时修改 `enrolled_count`
2. 课程已开课（`now >= startTime`）时，如果 `startTime`、`endTime`、`capacity` 有任何一项**与原值不同**，返回 409 `COURSE_STARTED`；只改标题、描述等字段则允许
3. 新容量不为空且小于 `enrolled_count` 时，返回 400 `CAPACITY_BELOW_ENROLLED`
4. 开课前编辑时，新的 `startTime` 也必须晚于现在

### 2.4 列表查询
- 参数：`keyword`（模糊匹配标题或讲师）、`phase`（UPCOMING / ONGOING / ENDED，转换成相对于 `now` 的时间条件，`now` 由 Service 从 Clock 取出后传给 SQL）、`page`、`size`
- 排序：未开始的课程排在前面并按开始时间升序，其余按开始时间降序。实现上用 `ORDER BY (start_time > #{now}) DESC, ...`，或者先按 phase 筛选、默认按 `start_time DESC`。实现时选一种，并写进技术设计
- `keyword` 用 `CONCAT('%', #{keyword}, '%')` 绑定参数，**不拼接字符串**

## 3. 测试

| 测试类 | 覆盖内容 |
|---|---|
| `CoursePhaseTest` | 开课前 1 秒、开课当时、结课当时、结课后 1 秒的阶段边界 |
| `CourseControllerIT` | 管理员创建返回 201；员工创建返回 403；未登录返回 401；参数校验（结束早于开始、开始在过去、容量为 0）返回 400；详情不存在返回 404 |
| `CourseEditIT` | 开课前可以改所有字段；开课后（用 MutableClock 往后推）改时间或容量返回 409，改标题成功；容量改到小于已报名人数返回 400（直接在库里把 `enrolled_count` 设成 3 来模拟） |
| `CourseQueryIT` | 关键字搜索、三种 phase 筛选、分页的 total 正确；size > 100 时被限制 |

## 4. 前端

| 文件 | 内容 |
|---|---|
| `src/api/courses.ts`，类型补充 | 接口封装 |
| `views/course/CourseListView.vue` | 搜索框、阶段筛选（全部/报名中/进行中/已结束）、分页；每门课显示阶段标签、时间、讲师、名额进度（`已报/容量`，不限人数时显示「不限」）；管理员显示「新建课程」和「编辑」按钮。报名按钮在 PR4 加 |
| `views/course/CourseFormView.vue` | 新建和编辑共用一个表单；用日期时间选择器；已开课时时间和容量字段禁用并给出提示；提交后回到详情页 |
| `views/course/CourseDetailView.vue` | 课程信息；预留「报名人员」区域（PR4 实现） |
| `utils/format.ts` | 用 dayjs 格式化时间，提供阶段文案和标签颜色 |
| `router/index.ts` | 新增 `/courses/new`、`/courses/:id`、`/courses/:id/edit` 路由 |

## 5. 自测清单

> 已自动化：`cd e2e; npm run pr3`（Playwright），见 `e2e/README.md`。
1. 管理员新建一门 3 天后开课的课程，容量 2；再新建一门不限人数的课程
2. 员工登录后能看到这两门课，但没有「新建」和「编辑」按钮；直接访问 `/courses/new` 会被跳走
3. 搜索关键字、切换阶段筛选，确认结果正确
4. 新建课程时把开始时间设在过去，确认提示错误
5. 用 MySQL 客户端把某门课的 `start_time` 改到过去，确认编辑页的时间和容量字段变为禁用，但修改标题可以保存

## 6. 完成标准
- [x] 测试全部通过、CI 通过
- [x] 自测全部通过，截图贴在 PR 描述里
- [x] 列表排序规则已写回技术设计 §7.3
