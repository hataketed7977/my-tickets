# Tasks

## 1. 后端 Schema 与 Repository

- [x] 1.1 修改 `schema.sql`：`user_session` 表新增 `oauth_state VARCHAR(128)` 列 + 部分唯一索引（`WHERE oauth_state IS NOT NULL`），验证 `data.sql` 种子数据不受影响
- [x] 1.2 `AuthRepository` 新增 `createSession(tokenHash, userId, expiresAt, oauthState)` 重载（原方法委托到新方法，传 `null`），验证编译通过
- [x] 1.3 `AuthRepository` 新增 `findSessionByOAuthState(state)` 方法，返回 session 的 tokenHash + user；新增 `clearOAuthState(tokenHash)` 方法，验证编译通过

## 2. 后端 Service 与认证拦截器

- [x] 2.1 `AuthService` 新增 `loginWithFeishu(code, oauthState)` 重载，创建 session 时写入 `oauth_state`；原 `loginWithFeishu(code)` 委托新方法传 `null`，验证编译通过
- [x] 2.2 `AuthService` 新增 `getCliSessionUser(state)`：通过 `findSessionByOAuthState` 查到 session 对应的 user（仅返回 user，不返回 token，因为明文 token 不入库）；验证编译通过
- [x] 2.3 `AuthService` 新增 `issueTokenForUser(userId)`：为指定用户创建一个新 session（`oauth_state` 为 NULL），返回明文 token；`completeCliLogin(state)` 组合逻辑：查 user → 建新 session 取 token → 删原 state 行 → 返回 token+user，验证编译通过
- [x] 2.4 `SessionInterceptor` 增加 Bearer 头解析：Cookie 优先，回退到 `Authorization: Bearer <token>`，验证编译通过
- [x] 2.5 编写测试：用 test session 以 Bearer 头访问 `/api/auth/me` 返回 200，以无效 Bearer 访问返回 401，验证测试通过（RED→GREEN）

## 3. 后端 Controller 与 WebConfig

- [x] 3.1 `AuthController` 新增 `POST /api/auth/cli/session`：生成 state，返回 `{ sessionId, authorizeUrl }`，验证编译通过
- [x] 3.2 `AuthController` 新增 `GET /api/auth/cli/session/{id}`：通过 oauth_state 查 session，命中返回 `{ status: "ready", token, user }` 并清空 oauth_state；未命中返回 `{ status: "pending" }` 或 404，验证编译通过
- [x] 3.3 改造 `AuthController` 的 `GET /api/auth/feishu/callback`：先查 oauth_state 是否对应 CLI session，命中走 CLI 流程（换 token、写 oauth_state、返回 HTML 成功页、不设 Cookie），否则走原有 Web 流程，验证编译通过
- [x] 3.4 `WebConfig` 放行 `/api/auth/cli/session` 和 `/api/auth/cli/session/**`，验证编译通过
- [x] 3.5 编写测试：`POST /api/auth/cli/session` 返回 sessionId + 合法 authorizeUrl；`GET` 初始返回 pending；模拟回调后 GET 返回 ready + token + user；再次 GET 返回 404，验证测试通过（RED→GREEN）
- [x] 3.6 回归测试：现有 Web 回调流程测试仍通过（`exposesOnlyFeishuAuthentication` 等），验证 `npm test` 通过

## 4. CLI 模块脚手架

- [x] 4.1 创建 `apps/cli/package.json`（name: `@ticket-center/my-tickets-cli`，bin: `my-tickets`，type: module，依赖 commander + open，engines node>=22.5），验证 `npm install` 成功
- [x] 4.2 创建 `apps/cli/tsconfig.json`（ESNext module，Node 22 target，strict），验证 `tsc --noEmit` 无错误
- [x] 4.3 创建 `apps/cli/src/index.ts` 入口：commander 程序定义，注册 login/status/logout 子命令，全局 `--api-base-url` 选项，验证 `node --import tsx src/index.ts --help` 输出帮助

## 5. CLI 核心库

- [x] 5.1 `src/lib/config.ts`：`~/.my-tickets/config.json` 的读写（apiBaseUrl / token / user），`logout` 只删 token+user，验证单元测试覆盖读写与删除
- [x] 5.2 `src/lib/api.ts`：fetch 封装，自动带 `Authorization: Bearer` 头，统一错误处理，验证编译通过
- [x] 5.3 `src/lib/oauth.ts`：`createSession()` 调 `POST /api/auth/cli/session`，`pollSession(id)` 轮询直到 ready/timeout（间隔 1.5s，超时 120s），`openBrowser(url)` 调 `open`，验证编译通过

## 6. CLI 命令实现

- [x] 6.1 `src/commands/login.ts`：创建 session → 打开浏览器 → 轮询 → 写 config → 打印成功/超时，验证 `my-tickets login --help` 正常
- [x] 6.2 `src/commands/status.ts`：读 config，无 token 打印"未登录"退出 1；有 token 调 `/api/auth/me` 打印用户名；401 打印"登录已过期"退出 1，验证编译通过
- [x] 6.3 `src/commands/logout.ts`：删 config 中 token+user，保留 apiBaseUrl，打印成功，验证编译通过
- [x] 6.4 API Base URL 解析优先级：`--api-base-url` > `MY_TICKETS_API_URL` env > config > 默认值，验证单元测试覆盖四种来源优先级

## 7. CLI 测试

- [x] 7.1 `config.ts` 单元测试：读写、logout 保留 apiBaseUrl，验证测试通过
- [x] 7.2 `oauth.ts` 轮询逻辑测试：mock API，pending→ready 路径、超时路径，验证测试通过
- [x] 7.3 `login.ts` 集成测试（mock API）：成功登录写入 config，超时不写 config，验证测试通过
- [x] 7.4 `status.ts` 测试：未登录、已登录、token 过期三种分支，验证测试通过

## 8. 集成验证

- [x] 8.1 后端全量测试通过：`npm test`（即 `./services/api/gradlew -p services/api test`），验证所有测试绿
- [x] 8.2 CLI 类型检查 + lint 通过：`npm --prefix apps/cli run typecheck && npm --prefix apps/cli run lint`，验证无错误
- [x] 8.3 CLI 构建通过：`npm --prefix apps/cli run build`，验证产物生成
- [x] 8.4 端到端验证：启动后端，运行 `my-tickets login` 完成飞书登录，`my-tickets status` 显示用户，`my-tickets logout` 清除凭证，验证全流程闭环
