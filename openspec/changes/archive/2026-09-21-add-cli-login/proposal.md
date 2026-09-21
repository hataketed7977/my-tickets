# Proposal

## Why

系统目前只有 Web 端（浏览器）能通过飞书 OAuth 登录，认证依赖 HttpOnly Cookie。
非浏览器客户端（如 CLI）无法接收 Cookie，也无法通过重定向流程拿到 session token，
导致工单数据只能在 Web 页面操作。需要新增一套 CLI 登录能力，让命令行工具也能
完成身份认证并调用受保护的 API。

## What Changes

- 新增 CLI 工具 `my-tickets`（位于 `apps/cli`，Node.js + TypeScript），提供
  `login`、`status`、`logout` 三个命令。
- 后端 `SessionInterceptor` 增加对 `Authorization: Bearer <token>` 头的支持，
  与现有 Cookie 认证并存（Cookie 优先）。
- 后端新增 CLI 登录会话端点：`POST /api/auth/cli/session` 创建会话并返回授权 URL，
  `GET /api/auth/cli/session/{id}` 供 CLI 轮询获取 token。
- 改造 `GET /api/auth/feishu/callback`：复用现有飞书回调地址，当回调的 `state`
  对应一个 CLI 会话时，走 CLI 流程（把 token 写入 session 行供轮询取回），
  否则走原有 Web 流程（设 Cookie + 跳转）。
- `user_session` 表新增 `oauth_state` nullable 列，用于把 CLI 会话与 session 行
  关联，复用现有 session 存储，不引入额外的内存状态。
- CLI 登录态保存在用户本机 `~/.my-tickets/config.json`（apiBaseUrl + token + user）。

## Capabilities

### New Capabilities

- `cli-auth`: CLI 工具通过飞书 OAuth 完成登录，获取并持久化 session token，
  后续命令以 Bearer 头方式调用受保护 API。

### Modified Capabilities

（无现有 capability，后端认证行为的增强归入 `cli-auth` 的 spec delta 描述。）

## Impact

- 后端代码：`services/api`（controller、service、repository、security、config、schema）。
- 新增 CLI 模块：`apps/cli`。
- 数据库 schema：`user_session` 表加 `oauth_state` 列（nullable，向后兼容）。
- API：新增 2 个端点，改造 1 个回调端点的内部逻辑（Web 行为不变）。
- 依赖：CLI 新增 `commander`、`open`；后端无新依赖。
- 飞书后台：无需修改，复用已注册的回调地址。
