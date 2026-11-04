# PR5：基础统计

| 项 | 内容 |
|---|---|
| 分支 | `feat/stats` |
| 依赖 | PR4 |
| 对应设计 | 技术设计 §7.5 |
| 预计工作量 | 0.5 天 |

## 1. 目标
给管理员提供全局概览和按课程的统计。个人统计已在 PR4 的 `/api/me/stats` 中实现。

## 2. 后端

| 文件 | 说明 |
|---|---|
| `stats/StatsMapper` + `StatsMapper.xml` | `selectOverview(now)`、`selectCourseStats(now, sort, limit)` |
| `stats/StatsService`、`StatsController` | `GET /api/stats/overview`、`GET /api/stats/courses`，都需要 ADMIN |
| `stats/dto` | `OverviewVO`、`CourseStatsVO`、`CourseStatsSort` 枚举（`ENROLLED`、`COMPLETION_RATE`） |

**口径**（写进技术设计 §7.5）：
- 有效报名人次 = ENROLLED + COMPLETED；完成率 = COMPLETED ÷ 有效报名人次（分母为 0 时返回 null）
- 名额占用率 = 已报名人数 ÷ 容量（不限人数的课程返回 null）
- 参与员工数 = 至少有一条有效报名的不同用户数
- 课程各阶段的数量按传入的 `now` 计算（不在 SQL 里调用 `NOW()`，保证测试可以控制时间）

**安全**：排序字段只能是枚举值，在 XML 里用 `<choose>` 映射成固定的列名，**不接受外部传入的列名**；`limit` 限制在 1–50 之间。

## 3. 测试
| 测试类 | 覆盖内容 |
|---|---|
| `StatsIT` | 准备 3 门课程、5 个用户、各种报名状态的数据，逐项核对概览数字；课程统计的排序和 limit；不限人数的课程占用率为 null；没有报名时完成率为 null；员工返回 403 |

## 4. 前端
| 文件 | 内容 |
|---|---|
| `src/api/stats.ts`，类型补充 | 接口封装 |
| `views/admin/StatsView.vue` | `/stats`：顶部一行指标卡片（课程数、各阶段数量、报名人次、完成率、参与员工数）；下方课程统计表，可以切换排序，完成率和占用率用进度条显示 |
| `AppLayout.vue` | 管理员导航新增「统计」 |

图表是可选项（本次**未做**，统计页用表格加进度条展示）：如果时间允许，可以用 ECharts 画一张热门课程报名数的柱状图；时间不够就只用表格（技术设计 §11.2 中允许砍掉）。

## 5. 自测清单

> 已自动化：`cd e2e; npm run pr5`。本地库里有历史数据，脚本先取统计基线，再验证「页面数字 = 基线 + 本次增量」。
1. 用 PR4 自测留下的数据打开统计页，对照报名人员表手工核对数字
2. 切换排序，确认表格顺序正确
3. 员工直接访问 `/stats` 会被跳走，调用接口返回 403

## 6. 完成标准
- [x] 测试全部通过、CI 通过；自测截图贴在 PR 描述里
- [x] 统计口径已写进技术设计 §7.5
