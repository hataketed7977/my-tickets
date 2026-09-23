# Tasks

## 1. 授权服务器骨架与元数据

- [x] 1.1 RED - 在 `services/api/src/test/java/com/bytedance/tickets/AuthorizationServerMetadataTest.java` 新增 `metadataExposesOAuthEndpoints`，断言授权服务器元数据包含与签发方一致的 `issuer`、授权端点、令牌端点、JWK 端点、`S256` 代码挑战方法与注册端点；新增 `protectedResourceMetadataIsPublic`，断言 GET `/.well-known/oauth-protected-resource/mcp` 返回精确 `resource`、授权服务器签发方与 `tickets:read` scope
- [x] 1.2 RED - 运行 `./services/api/gradlew -p services/api test --tests 'com.bytedance.tickets.AuthorizationServerMetadataTest'`，确认因依赖与授权服务器配置缺失而失败（类无法编译或无元数据端点）
- [x] 1.3 GREEN - 更新 `services/api/build.gradle` 加入 Spring Authorization Server Boot Starter 与 Spring AI 社区 mcp-security 的 Spring AI 1.1.x 匹配版本（0.0.6）；新增授权服务器安全配置与签发方、规范 MCP 资源 URI 配置项（`services/api/src/main/resources/application.yml`），提供 JWK 源与最小授权服务器过滤链
- [x] 1.4 GREEN - 重跑 `./services/api/gradlew -p services/api test --tests 'com.bytedance.tickets.AuthorizationServerMetadataTest'`，确认通过

## 2. 动态客户端注册与资源标识校验

- [x] 2.1 RED - 在 `services/api/src/test/java/com/bytedance/tickets/DynamicClientRegistrationTest.java` 新增 `registersClientWithValidMetadata`（201 且公共客户端无密钥）、`rejectsInvalidRedirectUri`、`rejectsUnsupportedGrantAndScope`，以及 `anonymousAuthorizeRedirectsToLogin`（未认证授权请求被重定向到登录入口）和 `tokenExchangeRejectsWrongResource`（`resource` 与规范资源不一致时拒绝）
- [x] 2.2 RED - 运行 `./services/api/gradlew -p services/api test --tests 'com.bytedance.tickets.DynamicClientRegistrationTest'`，确认因注册端点与资源校验缺失而失败
- [x] 2.3 GREEN - 在授权服务器配置上启用 MCP 授权服务器 configurer（免初始访问令牌的 DCR、RFC 8707 资源标识），对注册元数据做精确重定向 URI、授权类型、scope 与认证方式校验；启用授权请求与令牌请求的 resource 校验
- [x] 2.4 GREEN - 重跑 `./services/api/gradlew -p services/api test --tests 'com.bytedance.tickets.DynamicClientRegistrationTest'`，确认通过

## 3. 飞书登录身份桥接

- [x] 3.1 RED - 在 `services/api/src/test/java/com/bytedance/tickets/IdentityBridgeTest.java` 新增 `validSessionBecomesAuthorizePrincipal`（携带有效应用会话 Cookie 时授权端点以对应内部用户为主体并签发带一致 `state` 的授权码）、`invalidSessionIsNotAuthenticated`（过期/缺失会话转入登录）、`loginResumesOriginalAuthorization`（未认证授权请求经飞书登录后恢复同一授权事务）
- [x] 3.2 RED - 运行 `./services/api/gradlew -p services/api test --tests 'com.bytedance.tickets.IdentityBridgeTest'`，确认因会话桥接与授权事务恢复缺失而失败
- [x] 3.3 GREEN - 新增会话桥接过滤器（读取 `ticket_session` Cookie，经 `AuthService.getSessionUser` 校验后构造 name 为 `app_user.id` 的 `Authentication`），仅注册进授权服务器链并关闭其全局 Servlet 注册，置于 `SecurityContextHolderFilter` 之后；在 `services/api/src/main/java/com/bytedance/tickets/controller/AuthController.java` 飞书 Web 回调中优先按有界 RequestCache 重定向原始授权 URL，无缓存时保持回 `/`
- [x] 3.4 GREEN - 重跑 `./services/api/gradlew -p services/api test --tests 'com.bytedance.tickets.IdentityBridgeTest'`，确认通过

## 4. MCP 资源链与 get_current_user 全链路

- [x] 4.1 RED - 在 `services/api/src/test/java/com/bytedance/tickets/McpOAuthIntegrationTest.java` 新增 `standardClientCompletesOAuthFlowAndCallsGetCurrentUser`（注册→授权→PKCE 令牌交换，断言 JWT 的 iss/aud/sub/scope，再用令牌调用 `get_current_user` 返回主体用户）以及负向用例 `anonymousMcpRequestIsRejected`、`wrongAudienceTokenIsRejected`、`feishuTokenIsRejected`、`authorizationCodeCannotBeReplayed`
- [x] 4.2 RED - 运行 `./services/api/gradlew -p services/api test --tests 'com.bytedance.tickets.McpOAuthIntegrationTest'`，确认因 `/mcp` 仍走会话拦截器且无 JWT 资源链而失败
- [x] 4.3 GREEN - 新增 `/mcp` 的 JWT 资源服务器过滤链（校验 iss/aud/有效期/算法/scope，401 返回 `WWW-Authenticate: Bearer resource_metadata=...`）；在 `services/api/src/main/java/com/bytedance/tickets/mcp/McpServerConfig.java` 移除 `mcpSessionInterceptor` bean，并让 `get_current_user` 以令牌主体经新增的 `services/api/src/main/java/com/bytedance/tickets/repository/AuthRepository.java` 按 ID 查询返回 AppUser；增加默认 Security 链对 `/api/**` 放行以保留 SessionInterceptor 鉴权
- [x] 4.4 GREEN - 重跑 `./services/api/gradlew -p services/api test --tests 'com.bytedance.tickets.McpOAuthIntegrationTest'`，确认通过

## 5. 回归与变更校验

- [x] 5.1 NO-TEST - 运行 `./services/api/gradlew -p services/api test` 执行后端全部测试（含 ArchitectureTest、TicketApiTest 及新增 OAuth 测试），确认分层与命名规则及既有功能无回归；这是集成性校验，不作为单切片诊断手段
- [x] 5.2 NO-TEST - 运行 `openspec validate add-mcp-oauth2 --strict` 确认 change 产物合法；该命令只校验规格文档，应用行为测试不适用
