# 实现计划总览

《[技术设计](../技术设计.md)》回答"做什么、为什么"；本目录按 PR 回答"怎么做、做到什么程度算完成"。每份文档对应一个分支和一个 Pull Request。

## PR 列表

每个 PR 都是**后端 + 对应前端页面**的完整功能切片，合并后即可在页面上端到端使用。

| PR | 分支 | 内容 | 依赖 | 状态 |
|---|---|---|---|---|
| PR1 | `main`（初始提交） | 项目骨架、设计文档、Docker / K8s 清单 | — | ✅ 已完成 `d974308` |
| [PR2](PR2-认证与用户管理.md) | `feat/auth` | 注册、登录、JWT、用户管理、CI | PR1 | ✅ 已合并 #2 |
| [PR3](PR3-课程管理.md) | `feat/course` | 课程增改查、课程列表、时间规则 | PR2 | ✅ 已合并 #3 |
| [PR4](PR4-报名与容量限制.md) | `feat/enrollment` | 报名/取消/完成、我的报名、报名人员、容量限制与并发 | PR3 | 🔄 评审中 |
| [PR5](PR5-统计.md) | `feat/stats` | 全局统计、课程统计 | PR4 | 待开始 |
| [PR6](PR6-交付收尾.md) | `chore/delivery` | 自测说明、AI Coding 使用说明、前端按需引入、README 收尾 | PR5 | 待开始 |

生产部署（技术设计 §9、[部署手册](../部署手册.md)）的清单和脚本已在 PR1 中完成，**上线暂缓**，所有功能在本地完成后再执行。

## 通用约定

### 分支与提交
- 从最新的 `main` 拉分支：`git switch main && git pull && git switch -c feat/xxx`
- 提交信息格式：`类型: 中文描述`，类型用 `feat` / `fix` / `test` / `docs` / `chore` / `refactor`
- 一个 PR 内可以有多个提交，按"后端 → 测试 → 前端 → 文档"的顺序提交，便于评审
- 合并方式：Squash merge，合并后删除分支

### 每个 PR 的完成标准（Definition of Done）
1. 本 PR 文档中的任务清单全部勾选
2. `cd backend; .\mvnw.cmd test` 全部通过
3. `cd frontend; pnpm build` 通过（含 vue-tsc 类型检查）
4. `docker compose -f deploy/docker-compose.yml up -d --build` 后，按本 PR 文档的"自测清单"在页面上手测通过
5. 实现与《技术设计》不一致的地方，已同步更新设计文档
6. PR 描述按 `.github/pull_request_template.md` 填写（PR2 中创建该模板），附测试输出和自测结果
7. GitHub Actions CI 通过（PR2 之后）

### 测试基础设施（PR2 中建立，后续复用）
| 组件 | 作用 |
|---|---|
| `AbstractIntegrationTest` | `@SpringBootTest` + MockMvc + Testcontainers MySQL；同一个 Spring 上下文和容器在所有测试类之间复用 |
| `MutableClock`（测试用 `@Primary Clock`） | 测试中自由设置"当前时间"，验证开课前后的时间规则 |
| `TestDataHelper` | 快速创建用户、课程、报名，获取 JWT；每个测试前清空业务表 |

### 在新窗口中开始一个 PR
在新的 Claude Code 窗口中说："按 `docs/实现计划/PR3-课程管理.md` 实现 PR3"。`CLAUDE.md` 会被自动读取，其中包含项目约定和常用命令。
