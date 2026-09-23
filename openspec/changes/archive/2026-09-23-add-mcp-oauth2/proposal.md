# Proposal

## Why

当前 `/mcp` 端点复用 `SessionInterceptor`，同时接受 Cookie 和会话 bearer，
无法被标准 MCP 客户端接入。MCP 规范要求远程 Streamable HTTP 服务通过
OAuth 2.1 保护：客户端需能发现授权服务器、动态注册、在用户授权后换取
Access Token。引入标准授权能力后，任意兼容 MCP 授权规范的客户端都能安全
接入，并以已登录用户身份调用工具。

## What Changes

- 新增 OAuth 2.1 授权服务器端点：动态客户端注册（无需初始访问令牌）、
  授权端点、令牌端点、JWK Set 与授权服务器元数据。
- 授权主体复用现有飞书登录身份：以应用会话桥接 Spring Security 认证，
  令牌主体（`sub`）为内部 `app_user.id`，不重新实现登录或用户体系。
- 为当前 MCP Resource 签发 JWT Access Token：`iss` 为授权服务器签发方，
  `aud` 为规范 MCP 资源 URI，`scope` 仅含 `tickets:read`；授权与令牌请求
  均按 RFC 8707 携带并校验 `resource`。
- 已认证用户访问授权端点即签发授权码，不设置显式同意页。
- `/mcp` 的认证边界收敛为仅接受 OAuth Access Token（Bearer），**BREAKING**：
  不再接受应用会话 Cookie 作为 MCP 凭证。
- 未登录的授权请求跳转现有飞书登录，登录完成后通过服务端 RequestCache
  恢复原始授权事务；飞书回调仅此一处行为扩展，其余登录链路不变。
- `get_current_user` 改为依据令牌主体返回当前用户，不改变返回结构。
- `/api/**`、Web 与 CLI 的现有认证行为保持不变。

## Capabilities

### New Capabilities

- `mcp-oauth2`: 远程 Streamable HTTP MCP 服务的标准 OAuth 2.1 授权能力，
  覆盖受保护资源与授权服务器元数据、动态客户端注册、授权码 + PKCE 流程、
  现有飞书登录身份桥接、MCP 资源令牌签发，以及使用 Access Token 调用
  `get_current_user`。

### Modified Capabilities

（无：现有 `cli-auth`、`cli-ticket-query` 的需求不发生变化。）

## Impact

- 代码：`services/api` 新增授权服务器与资源服务器安全配置、身份桥接过滤器、
  受保护资源元数据端点；`McpServerConfig` 移除 `/mcp` 的会话拦截器并调整
  `get_current_user` 的取数方式；`AuthController` 飞书回调增加授权事务恢复。
- 依赖：新增 Spring Authorization Server Boot Starter 及 Spring AI 社区
  mcp-security（Spring AI 1.1.x 匹配版本）。
- 配置：新增签发方、规范 MCP 资源 URI、注册模式与 scope 相关配置项。
- API/客户端：`/mcp` 不再接受 Cookie，调用方必须携带 OAuth Access Token。
