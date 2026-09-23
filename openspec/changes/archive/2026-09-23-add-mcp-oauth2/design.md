# Design

## Context

参见 proposal.md - Why。当前 `/mcp` 由 `MappedInterceptor` 复用
`SessionInterceptor`，同时接受 Cookie 与会话 bearer；classpath 上没有
Spring Security。已冻结的既有事实：

- Spring Boot 3.4.5、Spring AI BOM 1.1.0（Spring AI 1.1.x）、
  Stateless Streamable HTTP（端点 `/mcp`）、H2 内存库。
- 飞书登录完整：`app_user` 以 UUID 为主键，opaque 会话存于 `user_session`
  （存 SHA-256 哈希），`AuthService.getSessionUser(token)` 可校验会话。

## Goals / Non-Goals

**Goals:**

- 在不改动现有 Web/CLI 认证行为的前提下，新增标准 MCP OAuth 2.1 全链路。
- 授权主体复用应用会话对应的内部用户；令牌与飞书身份严格隔离。
- 自动化客户端 fixture 可一次跑通注册→授权→令牌→`get_current_user`。

**Non-Goals:**

- 不重新实现飞书登录、用户体系或会话存储。
- 不设置显式同意页、不接入真实产品客户端、不做 HTTPS/Caddy 全栈 E2E
  （属最终集成环节）。
- 不引入 PAR、隐式授权、密码授权或客户端凭据流程。

## Decisions

### 1. 授权能力由 Spring Authorization Server + 社区 mcp-security 提供

新增 Spring Authorization Server Boot Starter，并引入 Spring AI 社区
mcp-security 的 Spring AI 1.1.x 匹配线（0.0.6）：其授权服务器模块提供
免初始访问令牌的 DCR 与 RFC 8707 支持，资源服务器模块提供受保护资源
元数据与 `resource_metadata` 质询。

理由：DCR、授权码、PKCE、令牌生命周期、JWK 均由成熟框架持有，应用只写
身份桥接与资源绑定，代码最少且与 Spring AI 1.1.x 匹配。

备选：纯 Spring Authorization Server 并自写窄 DCR 控制器与 PRM 端点
（无社区依赖，但代码量大）——作为社区库与 Boot 3.4.5 出现编译/装配
不兼容时的回退方案，触发条件为编译或 context load 失败。

### 2. 三条 SecurityFilterChain 按序装配

```
Order 1  授权服务器链   SAS 端点 (/register /oauth2/authorize /oauth2/token
                         JWK 元数据) + MCP 授权服务器 configurer
Order 2  MCP 资源链      仅 /mcp；JWT 资源服务器，校验 iss/aud/exp/alg/scope，
                         401 带 resource_metadata 质询
Order 3  默认链          /api/** 在 Security 层放行，仍由 SessionInterceptor
                         鉴权；其余既有行为不变
```

`/mcp` 移除 `mcpSessionInterceptor` bean，认证收敛为仅 Bearer。

### 3. 身份桥接过滤器

新增一个在 `SecurityContextHolderFilter` 之后、授权端点消费认证之前执行的
过滤器：读取 `ticket_session` Cookie → 经 `AuthService.getSessionUser`
校验 → 构造 name 为 `app_user.id` 的 `Authentication` 放入
`SecurityContext`。过滤器仅注册进授权服务器链，关闭其 Servlet 容器全局
注册，避免 `OncePerRequestFilter` 被标记已过滤而跳过链内执行。

未认证的授权请求由入口点重定向到现有 `/api/auth/feishu`。

### 4. 授权事务恢复

使用 Spring Security 有界 RequestCache 保存原始授权请求（仅服务端，不把
任意返回 URL 放入浏览器参数）。`AuthController` 飞书 Web 回调在建立会话
后优先重定向到缓存的原始授权 URL；无缓存时保持现有回 `/` 行为。这是对
现有登录代码的唯一改动。

### 5. MCP 资源令牌定制

通过令牌定制器，在授权码流程中按请求的 RFC 8707 `resource` 上下文设置：

```
iss = 配置的签发方
aud = 规范 MCP 资源 URI（精确字符串）
sub = app_user.id
scope = tickets:read
```

资源链以同一签发方、受众与 scope 独立校验，不把 MCP 受众加到其他令牌。

### 6. get_current_user 依据令牌主体取数

工具不再读 request attribute，而以认证后的令牌主体（`sub`）经用户查询
返回 AppUser，返回结构（id/name/avatarUrl）不变。新增按 ID 查询用户的
repository 方法。

## Risks / Trade-offs

- [社区库为 0.0.x incubator] → 以编译与 context load 作为第一道门；
  不兼容即按决策 1 回退纯 SAS + 窄适配器，不阻塞任务。
- [Cookie 与 Bearer 同时携带导致身份混淆]（历史教训）→ `/mcp` 仅接受
  Bearer，桥接过滤器只存在于授权服务器链，两条链认证来源隔离。
- [过滤器被全局注册导致链内跳过] → 关闭桥接过滤器的全局 Servlet 注册。
- [重启即换签名密钥导致令牌无法验证] → 测试环境使用固定密钥；生产密钥
  策略与稳定可轮换密钥源在后续发布设计中明确。
- [复用会话 Cookie 有违完全无状态] → 仅授权端点在浏览器交互期间使用会话
  桥接；`/mcp` 本身保持无状态 Bearer 认证。

## Migration Plan

1. 先以编译 + context load 锁定依赖与装配，再按任务切片推进。
2. `/mcp` 切换为 Bearer 属破坏性变更：无需要迁移的存量 MCP 客户端，
   标准客户端通过发现与 DCR 自动接入；Web/CLI 不经 `/mcp`，不受影响。
3. 回滚：还原依赖与安全配置、恢复 `mcpSessionInterceptor` bean 即可回到
   任务一状态；现有业务代码与数据无变更。
