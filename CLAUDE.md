# CLAUDE.md

培训课程管理系统（TCMS）：Vue 3 + TS 前端、Spring Boot 4.1 + MyBatis-Plus 后端、MySQL 8.4，Docker / K8s 交付。

**需求、设计决策和 API 以 `docs/技术设计.md` 为准**。动手前先读相关章节；实现与设计不一致时，同步更新文档。

**按 PR 实现**：每个 PR 的任务清单、测试、自测步骤和完成标准在 `docs/实现计划/PR*-*.md`（总览见 `docs/实现计划/README.md`）。开始一个 PR 前先读对应文档，完成后勾选任务并更新总览里的状态。

## 常用命令

```powershell
# 后端（JDK 21；测试需要 Docker 运行中，Testcontainers 起 MySQL）
cd backend; .\mvnw.cmd test
cd backend; .\mvnw.cmd spring-boot:run          # 需先 docker compose -f deploy/docker-compose.yml up -d mysql

# 前端（Node >= 22.12，pnpm 10）
cd frontend; pnpm install; pnpm build           # build = vue-tsc 类型检查 + vite 构建
cd frontend; pnpm dev                           # http://localhost:5173，/api 代理到 :8080

# 全栈本地运行
docker compose -f deploy/docker-compose.yml up -d --build   # 前端 http://localhost:8000
```

## 后端约定
- 按业务分包：`auth`、`user`、`course`、`enrollment`、`stats`，公共代码放 `common`、`config`。
- 单表 CRUD 用 MyBatis-Plus `BaseMapper` / `LambdaQueryWrapper`；连表、聚合 SQL 写在 `resources/mapper/*.xml`；禁止 `wrapper.last()` 拼 SQL；行锁用专门的 Mapper 方法（如 `selectByIdForUpdate`）。
- "当前时间"只从注入的 `Clock` 取，禁止 `LocalDateTime.now()` 无参调用。
- 业务错误抛 `BizException(ErrorCode.X)`；新增错误码同时更新 `ErrorCode` 和技术设计 §7.6。
- 数据库变更只能新增 Flyway 脚本 `V{n}__xxx.sql`，**不得修改已提交的迁移**；迁移只增不改（便于回滚）。
- 报名、取消、改容量必须先 `SELECT ... FOR UPDATE` 锁课程行（技术设计 §6）。
- 当前用户：控制器参数 `@AuthenticationPrincipal CurrentUser`；管理员接口用 `@PreAuthorize("hasRole('ADMIN')")`。

## 测试约定
- 集成测试继承 `support/AbstractIntegrationTest`（Testcontainers MySQL + MockMvc，上下文共享，每个测试前清库、重置时钟），类名以 `IT` 结尾；纯单元测试以 `Test` 结尾。
- 造数据用 `TestDataHelper`（`createEmployee/createAdmin/bearer/disable/insertCourse/insertEnrollment/count`）；控制时间用注入的 `MutableClock`（`setNow/advance`）。
- **时钟拨动超过 8 小时后，之前签发的 token 已过期**（JWT 校验也用这个时钟），要在拨动后重新 `data.bearer(user)`。
- 不要用 `@MockitoBean` 等改变上下文的注解，否则会创建新的 Spring 上下文和容器，测试变慢。
- UI 自测脚本在 `e2e/`（Playwright），需先 `docker compose up`。

## 前端约定
- 接口调用统一走 `src/api/http.ts`（自动带 token、统一错误提示）；类型定义放 `src/types/api.ts`，与后端 DTO 对应。
- 路由权限用 `meta.requiresAuth` / `meta.requiresAdmin`。

## Git / PR 流程
- 仓库：https://github.com/han-cczu/TrainingCourseManagementSystem（公开）。`main` 只通过 PR 合入。
- 每个功能一个分支（如 `feat/auth`），PR 按技术设计 §11.1 拆分，PR 描述附自测结果（测试输出、手测步骤）。
- 提交前：后端 `mvnw test` 通过，前端 `pnpm build` 通过。
- 文档、注释、提交信息用中文。

## 部署
生产部署（火山引擎单节点 K8s）的清单和脚本已就绪：`deploy/k8s/`、`deploy/deploy.ps1`、`docs/部署手册.md`。2026-11-04 已用方式 B（不使用镜像仓库）上线，再次发布直接运行 `.\deploy\deploy.ps1`。`secret.env` 不得提交，丢失后数据库密码将无法对上。
