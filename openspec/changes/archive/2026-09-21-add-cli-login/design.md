# Design

## Context

现有认证体系（见 proposal.md）：Spring Boot 后端通过飞书 OAuth 颁发 session token，
token 以 `ticket_session` HttpOnly Cookie 下发，`SessionInterceptor` 只从 Cookie 读取
token。`user_session` 表存储 token 的 SHA-256 哈希，关联 `app_user`，有效期 7 天。

CLI 无法接收 HttpOnly Cookie，也无法驱动浏览器重定向流程，因此需要：
1. 一种非 Cookie 的 token 投递方式
2. 一种非浏览器的 OAuth 回调处理方式

本设计复用现有飞书回调地址和 `user_session` 表，仅做最小增量。

## Goals / Non-Goals

**Goals:**
- CLI 能通过飞书 OAuth 完成登录并获取明文 session token
- 后端同时支持 Cookie 和 Bearer 两种认证方式，Web 行为零变化
- 不引入新的 session 存储表，复用 `user_session`
- 不修改飞书后台配置，复用已注册的回调地址

**Non-Goals:**
- 工单的增删改查 CLI 命令（下一个 change）
- token 自动刷新 / 多环境 profile / shell 补全 / 打包发布
- 服务端主动作废 CLI session（logout 仅删本地 token）
- 非飞书的认证方式

## Decisions

### 1. 回调走后端，CLI 轮询取 token（而非 CLI 起本地回调服务器）

**选择**：飞书 `redirect_uri` 继续指向后端 `http://localhost:55888/api/auth/feishu/callback`。
CLI 发起登录后轮询后端获取 token。

**理由**：复用已注册的飞书回调地址，无需在飞书后台新增 `http://localhost` 回调配置。
后端持有 App Secret，token 交换必须由后端完成，CLI 只需轮询结果。

**替代方案**：CLI 起本地 HTTP server 接收回调（RFC 8252）。被否，因为需要飞书后台
额外注册 localhost 回调，且 CLI 要管理临时 server 生命周期。

### 2. Session 状态复用 `user_session` 表，新增 `oauth_state` 列

**选择**：`user_session` 表加 nullable 列 `oauth_state VARCHAR(128)` + 部分唯一索引。
完整 token 投递机制：

1. 回调时 `loginWithFeishu(code, state)` 创建 session S1，把 `state` 写入 S1 的
   `oauth_state` 列。S1 的明文 token 不返回、不存储（仅 DB 中存哈希）。
2. CLI 轮询 `GET /api/auth/cli/session/{state}` 时，通过 `oauth_state` 找到 S1，
   拿到关联的 user。
3. 为该 user **新建一个 session S2**（`oauth_state` 为 NULL），返回 S2 的明文 token。
4. 删除 S1（仅用作 state→user 的临时关联）。

**理由**：明文 token 不入库（安全），也不存内存 Map（持久、无清理逻辑）。
`oauth_state` 列的唯一作用是把"哪个 state 对应哪个用户"落到 DB，供轮询反查。
`oauth_state` 为 NULL 时是普通 Web session，不影响现有逻辑。

**替代方案**：`ConcurrentHashMap<state, token>` 暂存明文 token。被否，多一套状态管理、
不持久、且明文 token 落在内存中。

### 3. `state` 直接作为 `sessionId`

**选择**：`POST /api/auth/cli/session` 返回的 `sessionId` 就是生成的 OAuth `state`
（`UUID-UUID`），回调和轮询都用它作为 key。

**理由**：省去 `state → sessionId` 的额外映射表。`state` 本身不可猜测，作为轮询凭证
足够安全。

### 4. Bearer 认证与 Cookie 并存，Cookie 优先

**选择**：`SessionInterceptor` 先读 Cookie，没有再读 `Authorization: Bearer` 头。

**理由**：Cookie 优先保证 Web 行为不变；Bearer 为 CLI 而设。两者指向同一个
`AuthService.getSessionUser(token)` 方法，token 格式和校验逻辑完全一致。

### 5. CLI 配置文件位置 `~/.my-tickets/config.json`

**选择**：用户主目录下的隐藏目录，存 `apiBaseUrl`、`token`、`user`。

**理由**：CLI 工具惯例（`~/.gitconfig`、`~/.npmrc`）。token 明文存储是 CLI 常规做法，
因为 CLI 进程本身就是用户自己的。

### 6. CLI 技术栈：Node.js + TypeScript + commander + open

**选择**：`apps/cli` 新建独立 npm package，用 `commander` 解析命令，`open` 打开浏览器，
HTTP 用 Node 22 内置 `fetch`（不引 axios）。

**理由**：与 `apps/web` 平级，TypeScript 生态一致；`fetch` 内建于 Node 22，减少依赖。

## Risks / Trade-offs

- **[轮询延迟]** CLI 需轮询直到用户登录完成 → 设置合理轮询间隔（1.5s）和超时（120s），
  登录成功后后端立即返回 ready。
- **[oauth_state 竞态]** 理论上同一 state 可能被多次轮询 → 返回 token 后立即清空
  `oauth_state`，后续轮询返回 404。
- **[明文 token 落盘]** `~/.my-tickets/config.json` 存明文 token → 这是 CLI 工具的
  标准权衡，文件权限依赖操作系统；logout 可主动删除。
- **[Web 回调回归风险]** 改造 `/feishu/callback` 可能影响 Web 流程 → 回调先查
  `oauth_state`，命中才走 CLI 分支，否则完全走原有逻辑；现有测试覆盖 Web 流程。

## Migration Plan

1. 后端 schema 变更：`user_session` 加 `oauth_state` 列（nullable，无数据迁移，
   现有行该列为 NULL）。
2. 后端代码变更：`AuthRepository`、`AuthService`、`SessionInterceptor`、
   `AuthController`、`WebConfig`。
3. 新建 `apps/cli` 模块。
4. 无线上数据迁移风险（H2 内存库，schema.sql 每次启动重建）。
5. 回滚：还原 schema 和后端代码即可，CLI 模块可独立删除。

## Open Questions

无。所有设计决策已在探索阶段与用户确认。
