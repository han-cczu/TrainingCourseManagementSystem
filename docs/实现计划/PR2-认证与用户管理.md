# PR2：认证与用户管理

| 项 | 内容 |
|---|---|
| 分支 | `feat/auth` |
| 依赖 | PR1（骨架） |
| 对应设计 | 技术设计 §4（认证与权限）、§7.2（接口）、§7.6（错误码）、§10（测试） |
| 预计工作量 | 1 天 |

## 1. 目标

- 员工可以自助注册并登录；管理员可以建号、停用/启用账号、重置密码
- 首次启动自动创建管理员
- 所有业务接口都需要登录，管理员接口按角色控制
- 建立后续 PR 共用的测试基础设施和 CI

**不做**：refresh token、找回密码、修改角色、登录限流（已在 nginx 实现）。

## 2. 后端

### 2.1 依赖（`pom.xml`）
- `spring-boot-starter-security`
- `spring-boot-starter-security-oauth2-resource-server`（JWT 签发与校验，用 Nimbus 实现）
- 测试：`spring-boot-starter-security-test`

### 2.2 配置（`application.yml`）
```yaml
app:
  jwt:
    secret: ${JWT_SECRET:dev-only-secret-change-me-0123456789abcdef}   # 仅本地开发默认值
    ttl: 8h
  admin:
    username: ${ADMIN_USERNAME:admin}
    password: ${ADMIN_PASSWORD:Admin12345}                            # 仅本地开发默认值
```
- 用 `@ConfigurationProperties` 绑定成 `JwtProperties`、`AdminProperties`，并加校验：密钥至少 32 字节。
- **prod profile 下**：如果密钥或管理员密码仍是上面的开发默认值，**启动直接失败**，防止误用默认值上线。

### 2.3 新增与修改的文件

| 包 | 文件 | 说明 |
|---|---|---|
| `user` | `User`、`Role`（ADMIN/EMPLOYEE）、`UserMapper` | 实体映射 `sys_user`，审计字段自动填充 |
| `user` | `UserService` | 创建用户（校验用户名唯一，`DuplicateKeyException` 转成 `USERNAME_TAKEN`）、停用/启用、重置密码、分页查询 |
| `user` | `UserAdminController` | `/api/users` 下的 4 个管理员接口（§7.2） |
| `user` | `AdminInitializer`（`ApplicationRunner`） | 库里没有 ADMIN 时，用配置创建管理员；多副本同时创建撞唯一键时，忽略异常 |
| `user/dto` | `UserVO`、`CreateUserRequest`、`UpdateEnabledRequest`、`ResetPasswordRequest` | `UserVO` 不包含密码哈希 |
| `auth` | `AuthController` | `register` / `login` / `me` / `password` |
| `auth` | `AuthService` | 注册（角色固定为 EMPLOYEE）、登录（停用账号同样返回 `BAD_CREDENTIALS`）、修改密码（校验旧密码） |
| `auth` | `JwtService` | 签发 token：`sub` = 用户 ID，claims 包含 `username`、`role`，有效期取配置，时间从 `Clock` 取 |
| `auth` | `CurrentUser`（record：id、username、role） | 控制器通过 `@AuthenticationPrincipal CurrentUser` 获取当前用户 |
| `auth/dto` | `RegisterRequest`、`LoginRequest`、`LoginResponse`、`ChangePasswordRequest` | 校验规则见 §4.3 |
| `config` | `SecurityConfig` | 见 2.4 |
| `config` | `JwtConfig` | `NimbusJwtEncoder` / `NimbusJwtDecoder`（HS256）。decoder 的 `JwtTimestampValidator` 使用注入的 `Clock` |
| `common` | `GlobalExceptionHandler` | 补充：`AccessDeniedException` 返回 403 `FORBIDDEN`，`AuthenticationException` 返回 401 `UNAUTHORIZED`。这两个必须放在兜底的 `Exception` 处理之前 |

### 2.4 SecurityConfig 要点
- 无状态：`SessionCreationPolicy.STATELESS`，关闭 CSRF（不使用 Cookie）、formLogin 和 httpBasic
- 放行：`POST /api/auth/register`、`POST /api/auth/login`、`/actuator/health/**`、`/v3/api-docs/**`、`/swagger-ui/**`；其余全部需要认证
- `oauth2ResourceServer().jwt()` 配置自定义 `JwtAuthenticationConverter`：
  - 根据 `sub` 查 `sys_user`。用户不存在或已停用时，抛 `InvalidBearerTokenException` 返回 401，这样停用账号**立即生效**（§4.4）
  - 角色以数据库为准，生成 `ROLE_ADMIN` / `ROLE_EMPLOYEE` 权限；principal 是 `CurrentUser`
- `authenticationEntryPoint` 和 `accessDeniedHandler` 输出统一的 `ErrorResponse` JSON（401 / 403）
- `@EnableMethodSecurity`，管理员接口加 `@PreAuthorize("hasRole('ADMIN')")`
- 密码编码器：`BCryptPasswordEncoder`

### 2.5 业务规则核对
- [x] 用户名 3–32 位，只能是字母、数字、下划线；密码 8–64 位，至少包含字母和数字（注册、建号、改密码、重置密码都要校验）
- [x] 注册请求里传了 `role` 字段时**忽略**（不接受）
- [x] 登录失败一律返回 `BAD_CREDENTIALS`（用户不存在、密码错误、账号停用都一样）
- [x] 管理员停用自己返回 409 `CANNOT_DISABLE_SELF`
- [x] 账号停用后，已签发的 token 在下一次请求时返回 401

## 3. 测试

### 3.1 测试基础设施（后续 PR 复用）
- `AbstractIntegrationTest`：`@SpringBootTest` + `@AutoConfigureMockMvc` + `@Import(TestcontainersConfig.class, TestClockConfig.class)`
- `TestClockConfig`：提供 `@Primary MutableClock`（可以 `setNow(...)`、`advance(Duration)`），每个测试前重置为固定时间
- `TestDataHelper`：`createUser(role)`、`tokenOf(user)`、`truncateAll()`（按外键顺序清空 enrollment → course → sys_user）
- 处理 Mockito 自附加警告：在 surefire 中配置 `-javaagent` 加载 mockito-core

### 3.2 测试用例
| 测试类 | 覆盖内容 |
|---|---|
| `AuthControllerIT` | 注册 → 登录 → 获取 me 的完整流程；重复用户名返回 409；密码错误、用户不存在、账号停用都返回 401 `BAD_CREDENTIALS`；各项参数校验返回 400；注册时传 role 被忽略；修改密码（旧密码错误返回 400 或 401） |
| `SecurityIT` | 不带 token 返回 401；token 被篡改返回 401；token 过期返回 401（用 MutableClock 往后推 9 小时）；员工调用 `/api/users` 返回 403；停用账号后原 token 返回 401 |
| `UserAdminIT` | 建员工、建管理员；停用/启用；停用自己返回 409；重置密码后可以用新密码登录；分页和关键字搜索 |
| `AdminInitializerIT` | 空库启动后存在 admin；再次运行不会重复创建 |

## 4. 前端

| 文件 | 内容 |
|---|---|
| `src/api/auth.ts`、`src/api/users.ts` | 接口封装 |
| `src/types/api.ts` | 补充 `LoginResponse`、`CreateUserRequest` 等类型 |
| `src/stores/auth.ts` | 新增 `login()`、`logout()`、`refreshMe()`。应用启动时如果已有 token，先调用 `/auth/me` 校验 |
| `views/LoginView.vue` | 登录表单（前端校验规则和后端一致）；登录成功后跳回 `redirect` 参数指定的页面；附注册入口 |
| `views/RegisterView.vue` | 注册表单（确认密码），注册成功后自动登录 |
| `views/ChangePasswordView.vue` | `/account/password` |
| `views/admin/UserManageView.vue` | `/admin/users`：表格、搜索、分页、新建对话框（可选角色）、启用开关、重置密码对话框；自己那一行的启用开关禁用 |
| `layouts/AppLayout.vue` | 用户下拉菜单加「修改密码」；管理员显示「用户管理」导航 |
| `router/index.ts` | 新增上述路由，设置 `requiresAuth` / `requiresAdmin` / `guestOnly` |

## 5. CI 与仓库配置
- `.github/workflows/ci.yml`：在 `pull_request` 和推送到 `main` 时触发
  - backend：`actions/setup-java`（temurin 21，Maven 缓存）→ `./mvnw -B test`（GitHub 的 ubuntu runner 自带 Docker，Testcontainers 可以直接用）
  - frontend：`pnpm/action-setup` + `actions/setup-node`（Node 24，pnpm 缓存）→ `pnpm install --frozen-lockfile` → `pnpm build`
- `.github/pull_request_template.md`：包含变更内容、对应设计章节、测试结果、自测截图、AI 使用情况这几个栏目

## 6. 自测清单（docker compose 启动后）

> 已自动化：`cd e2e; npm run pr2`（Playwright，逐项输出结果并截图），见 `e2e/README.md`。
1. 用开发默认的管理员账号 `admin` / `Admin12345` 登录，确认导航栏显示「用户管理」
2. 打开一个隐身窗口注册员工 `zhangsan`，确认自动登录、导航栏没有「用户管理」，直接访问 `/admin/users` 会被跳走
3. 管理员停用 `zhangsan`，确认 `zhangsan` 的窗口刷新后被踢回登录页
4. 管理员重置 `zhangsan` 的密码并启用账号，确认可以用新密码登录
5. 连续输错密码 25 次以上，确认出现「操作过于频繁」的提示（nginx 限流）
6. 修改密码后退出，确认旧密码无法登录、新密码可以登录

## 7. 完成标准
- [x] 第 2、4、5 节的任务全部完成，3.2 的测试全部通过
- [x] 第 6 节自测全部通过，结果截图贴在 PR 描述里
- [x] CI 通过
- [x] 技术设计 §4、§7.2 与实现一致
