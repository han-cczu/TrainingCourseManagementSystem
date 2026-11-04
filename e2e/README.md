# UI 自测脚本

用 Playwright 驱动无头浏览器，按 `docs/实现计划/` 中各 PR 的「自测清单」在真实页面上操作，并输出逐项结果和截图（保存在 `e2e/shots/`）。

```powershell
# 前提：全栈已启动（前端 http://localhost:8000）
docker compose -f deploy/docker-compose.yml up -d --build

cd e2e
npm install
npm run setup        # 首次运行：下载 Chromium
npm run pr2          # PR2：认证与用户管理
npm run pr3          # PR3：课程管理
npm run pr4          # PR4：报名、取消、完成与容量限制
npm run pr5          # PR5：基础统计
npm run regression  # 回归：搜索响应乱序、容量空值及显式切换不限人数
npm run runner-check # 运行器校验：正常页面/console.error/pageerror 的退出码
npm run all          # 全部（PR2 含登录限流，放在最后执行）
```

公共工具在 `lib.mjs`：创建浏览器和页面、逐项记录结果、失败时自动截图、登录、通过接口注册员工、执行 SQL（用于构造「已开课」这类状态）。脚本启动时会先等后端就绪。

`npm run all` 依次执行新增回归（2 项）、运行器检查（3 项）、PR3/PR4/PR5/PR2（原有 25 项），总计 27 项 UI 自测和 3 项运行器检查。浏览器 `console.error`（除资源加载提示）及未捕获的 `pageerror` 会使脚本返回非零退出码。问题与修复详情见 [本地测试复查](../docs/本地测试复查-2026-11-04.md)。完整运行只需执行一次 all；PR2 的登录限流用例仍在最后。

- 可以用环境变量 `BASE_URL` 指定其他地址。
- 每次运行都会用带时间戳的新用户名，可以重复执行。
- 登录限流用例放在最后执行。触发限流后，同一 IP 大约需要等 1 分钟才能再次登录，所以连续重跑时请间隔 1 分钟。
