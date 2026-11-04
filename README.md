# 培训课程管理系统（TCMS）

公司内部培训课程系统：员工可以浏览课程、报名、取消报名、标记学习完成并查看自己的培训情况；管理员可以维护课程、查看报名人员、做统计和管理用户。

> 当前进度：**项目骨架**已完成（后端工程、数据库迁移、统一异常、前端工程、Docker 镜像与 compose）。业务功能按 [技术设计 §11.1](docs/技术设计.md#111-pr-拆分) 的 PR 拆分逐步交付。

## 技术栈

| 层 | 技术 |
|---|---|
| 前端 | Vue 3.5 · TypeScript 6 · Vite 8 · Vue Router · Pinia · Element Plus · Axios |
| 后端 | Java 21 · Spring Boot 4.1 · MyBatis-Plus 3.5 · Spring Security (JWT) · Flyway |
| 数据库 | MySQL 8.4 |
| 交付 | Docker · docker-compose · Kubernetes |

## 目录结构

```
backend/     Spring Boot 后端（Maven Wrapper、Dockerfile）
frontend/    Vue 3 前端（Dockerfile、nginx 配置）
deploy/      docker-compose.yml、k8s/ 清单
docs/        技术设计、自测说明、AI Coding 使用说明
```

## 快速开始（Docker Compose，推荐）

只需要 Docker Desktop：

```bash
docker compose -f deploy/docker-compose.yml up -d --build
```

| 服务 | 地址 |
|---|---|
| 前端 | http://localhost:8000 |
| 后端健康检查 | http://localhost:8080/actuator/health |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| MySQL | localhost:3306（库 `tcms`，用户 `tcms` / `tcms123456`，仅本地开发默认值） |

停止：`docker compose -f deploy/docker-compose.yml down`（加 `-v` 会同时删除数据库数据）。

## 本地开发

**1. 启动 MySQL**
```bash
docker compose -f deploy/docker-compose.yml up -d mysql
```

**2. 启动后端**（需要 JDK 21，Maven 由 Wrapper 自动下载）
```bash
cd backend
./mvnw spring-boot:run          # Windows: mvnw.cmd spring-boot:run
./mvnw test                     # 集成测试使用 Testcontainers，需要 Docker 运行中
```
数据库连接可通过环境变量覆盖：`DB_HOST`、`DB_PORT`、`DB_NAME`、`DB_USERNAME`、`DB_PASSWORD`。

**3. 启动前端**（需要 Node ≥ 22.12、pnpm 10）
```bash
cd frontend
pnpm install
pnpm dev                        # http://localhost:5173，/api 自动代理到 localhost:8080
pnpm build                      # 类型检查 + 生产构建
```

## 部署到服务器（Kubernetes）

生产环境是火山引擎云服务器上的单节点 K8s，访问地址 http://101.96.237.10:30080 。

```powershell
.\deploy\deploy.ps1 -Registry <镜像仓库地址>   # 或不带参数：不使用镜像仓库，直接导入
```

一次性准备（SSH 免密、安全组、镜像仓库）、回滚、备份与恢复见 [部署手册](docs/部署手册.md)。K8s 清单在 `deploy/k8s/`（kustomize：`base/` + `overlays/prod/`）。

## 文档

- [技术设计](docs/技术设计.md)：需求范围、数据模型、业务规则、并发控制、API、部署、测试策略
- [部署手册](docs/部署手册.md)：生产环境的发布、回滚、备份与恢复
- 自测说明：待补充
- AI Coding 使用说明：待补充
