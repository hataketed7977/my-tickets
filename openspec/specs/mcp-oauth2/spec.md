# mcp-oauth2 Specification

## Purpose

为远程 Stateless Streamable HTTP MCP 服务提供标准 OAuth 2.1 授权能力：
标准 MCP 客户端可完成发现、动态注册、用户授权与令牌交换，并以现有飞书
登录用户的身份调用 `get_current_user`。

## Requirements

### Requirement: 受保护资源元数据

系统 SHALL 通过受保护资源元数据（RFC 9728）公布规范 MCP 资源及其授权
服务器。元数据 MUST 无需凭证即可获取，`resource` MUST 精确标识本 MCP
服务，`authorization_servers` MUST 至少包含一个绝对 HTTPS 签发方，且
MUST 声明所支持的 scope。

#### Scenario: 未携带令牌访问 MCP 端点获得发现指引

- **WHEN** 客户端在未携带 `Authorization` 的情况下请求 `/mcp`
- **THEN** 系统返回 `401`，且 `WWW-Authenticate` 为 `Bearer` 质询，
  其中 `resource_metadata` 指向受保护资源元数据 URL

#### Scenario: 读取受保护资源元数据

- **WHEN** 客户端 GET 受保护资源元数据 URL
- **THEN** 系统返回 `200` 与 JSON，包含精确的 `resource`、至少一个
  授权服务器签发方，以及 `tickets:read` scope

### Requirement: 授权服务器元数据

系统 SHALL 公布授权服务器元数据（RFC 8414），其中 MUST 包含授权端点、
令牌端点、`code` 响应类型、`authorization_code` 授权类型、`S256` PKCE
方法，以及所支持的客户端注册方式。元数据中的 `issuer` MUST 与签发方
完全一致。

#### Scenario: 读取授权服务器元数据

- **WHEN** 客户端按标准发现路径请求授权服务器元数据
- **THEN** 系统返回 `200` 与 JSON，包含授权端点、令牌端点、JWK 端点、
  `S256` 代码挑战方法与注册端点，且 `issuer` 与签发方一致

### Requirement: 动态客户端注册

系统 SHALL 支持无需初始访问令牌的动态客户端注册。注册请求 MUST 使用
精确匹配校验重定向 URI，MUST 拒绝不支持的授权类型、scope 与客户端认证
方式，并仅返回标准注册元数据。

#### Scenario: 使用合法元数据注册成功

- **WHEN** 客户端提交包含精确重定向 URI、授权码类型与支持 scope 的注册请求
- **THEN** 系统返回 `201` 与标准客户端注册元数据，包含可用于后续授权的
  `client_id`，且公共客户端不获得客户端密钥

#### Scenario: 非法注册元数据被拒绝

- **WHEN** 客户端提交非法重定向 URI、不支持的授权类型或 scope
- **THEN** 系统返回错误响应且不创建客户端

### Requirement: 授权码流程与 PKCE

系统 SHALL 使用授权码流程并强制 PKCE `S256`，使用一次性随机 `state`、
短期一次性授权码与精确重定向 URI 匹配。系统 MUST NOT 支持隐式授权或
密码授权。

#### Scenario: 已认证用户获得授权码

- **WHEN** 已认证用户携带合法 `client_id`、精确重定向 URI、`state` 与
  `S256` 代码挑战请求授权端点
- **THEN** 系统重定向至该重定向 URI，附带与请求一致的 `state` 和授权码，
  无需额外同意步骤

#### Scenario: 缺少或错误的 PKCE 参数被拒绝

- **WHEN** 授权请求缺少代码挑战，或令牌交换使用错误的 `code_verifier`
- **THEN** 系统拒绝该请求或令牌交换，且不签发令牌

#### Scenario: 重定向 URI 不匹配被拒绝

- **WHEN** 授权请求的重定向 URI 与客户端注册值不精确一致
- **THEN** 系统拒绝该授权请求

### Requirement: 现有飞书登录身份桥接

系统 SHALL 以现有飞书登录建立的应用会话作为授权主体，禁止重新实现登录
或用户体系，且 MUST NOT 将应用会话 Cookie 或飞书令牌作为 MCP 凭证下发。
未认证的授权请求 MUST 进入现有飞书登录流程，并在登录完成后恢复原始
授权事务。

#### Scenario: 未登录授权请求在登录后恢复

- **WHEN** 未认证用户发起授权请求并完成飞书登录
- **THEN** 系统恢复原始授权事务，并基于同一浏览器绑定的授权请求签发授权码

#### Scenario: 有效应用会话成为授权主体

- **WHEN** 携带有效应用会话 Cookie 的用户请求授权端点
- **THEN** 授权端点以该会话对应的内部用户作为认证主体

#### Scenario: 无效或过期会话不构成认证

- **WHEN** 授权请求携带缺失、过期或已注销的应用会话
- **THEN** 系统不认证该请求并转入登录流程

### Requirement: MCP 资源访问令牌

系统 SHALL 为当前 MCP Resource 签发 JWT Access Token。令牌 MUST 满足：
`iss` 为授权服务器签发方，`aud` 包含规范 MCP 资源，`sub` 为稳定内部
用户 ID，`scope` 仅含已授予的 MCP scope。授权请求与令牌请求 MUST 携带
并按 RFC 8707 校验 `resource`。

#### Scenario: PKCE 令牌交换得到 MCP 令牌

- **WHEN** 客户端使用合法授权码、匹配的 `code_verifier` 与精确 `resource`
  请求令牌端点
- **THEN** 系统签发 JWT，其 `iss`、`aud`、`sub`、`scope` 分别为签发方、
  规范 MCP 资源、内部用户 ID 与 `tickets:read`

#### Scenario: 资源标识不匹配被拒绝

- **WHEN** 授权或令牌请求的 `resource` 与规范 MCP 资源不一致
- **THEN** 系统拒绝该请求，且不签发令牌

#### Scenario: 授权码不可重放

- **WHEN** 客户端使用同一授权码再次请求令牌
- **THEN** 系统拒绝该令牌请求

### Requirement: MCP 端点 Bearer 认证

系统 SHALL 在 MCP 协议分派前校验 Bearer 令牌的签名、签发方、受众、
有效期、算法与所需 scope。`/mcp` MUST NOT 接受应用会话 Cookie。

#### Scenario: 使用 MCP 令牌访问受保护工具

- **WHEN** 客户端携带合法 MCP Access Token 调用 `get_current_user`
- **THEN** 系统返回令牌主体对应用户的 ID、姓名与头像

#### Scenario: 匿名请求被拒绝

- **WHEN** 客户端不携带令牌请求 `/mcp`
- **THEN** 系统返回 `401` 与 Bearer 质询，不返回登录页面

#### Scenario: 非 MCP 令牌被拒绝

- **WHEN** 客户端携带为其他资源签发的令牌或飞书令牌请求 `/mcp`
- **THEN** 系统返回 `401`，不进行协议分派
