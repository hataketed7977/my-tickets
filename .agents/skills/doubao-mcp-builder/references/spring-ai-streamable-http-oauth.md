# Spring AI Streamable HTTP with OAuth

Use this recipe for an existing Spring Boot application that needs a remote
MCP endpoint, reuses an existing browser login as the user identity source,
and exposes a first authenticated tool. Choose between Spring AI and the
official MCP Java SDK from target-client compatibility, not framework
familiarity.

This is a vertical-slice recipe, not a recommendation to build a general
authorization server. Prefer an existing or managed OAuth authorization
server whenever it can issue audience-bound MCP access tokens.

## Compatibility Evidence, Not Default

One validated repository slice used:

| Component | Version or mode |
| --- | --- |
| Java | 21 |
| Spring Boot | 3.4.5 |
| Spring AI | 1.1.0 |
| MCP Java SDK | 0.16.0, transitively |
| Transport | Streamable HTTP |
| MCP revision exercised | `2025-06-18` |
| Token mode | JWT bearer, HS256 for a disposable local slice |
| User identity | Existing external-login-backed web session |

This matrix is historical compatibility evidence, not a dependency
recommendation. Do not copy these versions into another repository. Inspect
the target project's dependency management, select a mutually compatible
Spring Boot and Spring AI release, inspect the resulting MCP SDK version, and
test the MCP revision required by the target Doubao surface.

## Recommended Production Architecture

Use this stack unless repository evidence requires a different one:

```text
Doubao or MCP client
  -> maintained OAuth authorization server
       -> existing OIDC or social login as upstream identity
       -> authorization code + PKCE S256
       -> MCP-audience access token
  -> Spring Security OAuth2 Resource Server
  -> compatible Spring AI or official MCP Java SDK transport
  -> small allowlisted tools
  -> existing application services and authorization
```

Select the authorization layer in this order:

1. Use the product's existing standards-compliant OAuth/OIDC authorization
   server if it can issue an MCP-specific audience and scopes.
2. Use a managed authorization server or gateway and federate the existing
   login.
3. If self-hosting is required, use Spring Authorization Server and bridge the
   existing authenticated user into Spring Security.
4. Use custom `/authorize` and `/token` code only for a disposable prototype
   or a narrowly reviewed compatibility facade.

The selected MCP SDK owns framing. Spring Security Resource Server owns bearer
authentication when its integration fits the selected transport. The
authorization server owns client registration, redirect URI validation, PKCE,
code lifecycle, token issuance, refresh, and revocation. Application services
continue to own object-level product authorization.

If the existing application login is represented only by a custom cookie and
MVC interceptor, add one authentication bridge that creates a trusted Spring
Security `Authentication` for the authorization server. Do not reimplement the
authorization-code protocol around that cookie.

## Non-Default Prototype Evidence

Skip this section unless the user explicitly requests a disposable prototype
or written acceptance criteria permit a custom authorization facade. Never
infer prototype status from a repository or branch name, localhost, seed data,
or workshop-like structure.

Require an explicit non-default mode such as
`app.mcp.auth-mode=prototype-facade`. Do not assign this value as a default.
Reject it at startup in environments whose deployment policy requires a
maintained authorization server, and report the active mode and hardening gaps
without logging secrets.

One validated prototype used this flow:

```text
MCP client
  -> GET /oauth/authorize + PKCE S256 + resource
  -> existing web session?
       no  -> upstream login -> callback -> resume /oauth/authorize
       yes -> short-lived single-use authorization code
  -> POST /oauth/token + code_verifier
  <- MCP-audience JWT access token
  -> POST /mcp + Bearer token
  -> Spring Security resource server
  -> Spring AI Streamable HTTP transport
  -> first_read_tool
  -> existing user service by verified JWT subject
```

This slice proves identity continuity and MCP interoperability, but its custom
authorization facade is not the production default. Use it to understand the
vertical flow and tests, then replace the facade with a maintained
authorization server unless every hardening requirement at the end of this
reference is implemented and reviewed.

Keep four boundaries distinct:

1. The external identity provider authenticates the person.
2. The OAuth authorization layer issues an MCP-specific token.
3. Spring Security authenticates `/mcp`.
4. Existing application services authorize and load business data.

Never give the MCP client the web session cookie or reuse an upstream identity
provider token as the MCP access token.

## Transport SDK Decision

Use Spring AI when its resolved MCP Java SDK supports every requirement in the
target-client matrix. Use the official MCP Java SDK directly when Spring AI
lags the required MCP revision or transport state model.

For the Doubao Work MCP OAuth 2.1 profile documented on 2026-09-22, verify all
of these before selecting Spring AI:

- HTTPS MCP and authorization URLs, including local testing;
- Dynamic Client Registration through an advertised `registration_endpoint`;
- the current stateless Streamable HTTP transport;
- the negotiated protocol revision and required headers.

The validated Doubao Java demo used the official SDK directly with
`HttpServletStatelessServerTransport`. Do not force an older stateful Spring AI
adapter to emulate the current client protocol.

## Recommended Dependencies and Configuration

Spring Security resource server:

```groovy
implementation 'org.springframework.boot:spring-boot-starter-security'
implementation 'org.springframework.boot:spring-boot-starter-oauth2-resource-server'
```

Choose one MCP transport dependency.

Spring AI, only when compatible:

```groovy
implementation platform("org.springframework.ai:spring-ai-bom:$springAiVersion")
implementation 'org.springframework.ai:spring-ai-starter-mcp-server-webmvc'
```

Official MCP Java SDK, when direct integration is required:

```groovy
implementation "io.modelcontextprotocol.sdk:mcp:$mcpSdkVersion"
```

Set `springAiVersion` through the repository's existing version catalog,
dependency-management block, or build property. Resolve it from the target
Spring Boot compatibility matrix; do not default new projects to the
historical version above.

Do not add Nimbus JOSE or another JWT library directly when the selected Spring
Security starter already supplies the required implementation. Inspect the
resolved dependency graph first and add a direct dependency only when
application code intentionally owns that API.

When this deployment must host the authorization server, add the framework
starter instead of hand-writing OAuth endpoints:

```groovy
implementation 'org.springframework.boot:spring-boot-starter-oauth2-authorization-server'
```

Let the repository's Spring Boot dependency management select a compatible
Spring Authorization Server version. Pin and test the resulting versions in
CI rather than mixing arbitrary examples from other releases.

Illustrative Spring AI resource-server configuration follows. Skip the
`spring.ai.mcp` block when using the official Java SDK directly. Property names
vary across Spring AI releases; verify them against the selected version's
configuration metadata before editing:

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: ${MCP_AUTH_ISSUER}
  ai:
    mcp:
      server:
        protocol: STREAMABLE
        name: my-service-mcp
        version: 1.0.0
        type: SYNC
        capabilities:
          resource: false
          prompt: false
          completion: false
        streamable-http:
          mcp-endpoint: /mcp

app:
  mcp:
    resource: ${MCP_RESOURCE:https://mcp.example.com/mcp}
```

Fail startup when issuer, resource, key discovery, or required scopes are
missing. Add an explicit audience validator because issuer validation alone is
not sufficient.

Bind security settings through typed, validated configuration instead of
scattered `@Value` fields:

```java
@Validated
@ConfigurationProperties("app.mcp")
public record McpSecurityProperties(
        @NotNull URI issuer,
        @NotNull URI resource,
        @NotEmpty Set<String> requiredScopes,
        @NotNull AuthorizationMode authMode
) {
    public enum AuthorizationMode {
        EXTERNAL,
        FRAMEWORK,
        PROTOTYPE_FACADE
    }
}
```

Enable configuration-property scanning using the repository's existing
convention. Add mode-specific validation for issuer discovery, signing keys,
client registration, and HTTPS. A missing or malformed security value must
prevent startup rather than fail during the first authorization request.

For local development, use a disposable authorization-server profile with a
persistent test key or a containerized test issuer. Do not make a random
per-process HMAC signing key part of the production recipe; every restart
invalidates tokens and shared-secret validation prevents clean key
distribution and rotation.

## Spring Authorization Server Defaults

Add the Spring Boot authorization-server starter and compile before replacing
an existing implementation. Then let Spring Authorization Server own:

- authorization-code and refresh-token persistence;
- exact redirect URI and client authentication checks;
- PKCE validation and authorization-code replay handling;
- JWT encoding, JWK publication, token response formatting, and refresh-token
  rotation.

Do not retain parallel custom code paths for those responsibilities after the
framework flow passes its integration tests.

When self-hosting is necessary, pre-register the target client with exact
redirect URIs and require PKCE. The following example is for a public client
that cannot keep a secret; use the target client's documented authentication
method when it is confidential:

```java
@Bean
RegisteredClientRepository registeredClients(McpClientProperties properties) {
    RegisteredClient doubao = RegisteredClient
            .withId(UUID.randomUUID().toString())
            .clientId(properties.clientId())
            .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri(properties.redirectUri())
            .scope("openid")
            .scope(properties.readScope())
            .clientSettings(ClientSettings.builder()
                    .requireProofKey(true)
                    .requireAuthorizationConsent(true)
                    .build())
            .tokenSettings(TokenSettings.builder()
                    .accessTokenTimeToLive(Duration.ofMinutes(15))
                    .build())
            .build();
    return new InMemoryRegisteredClientRepository(doubao);
}
```

Use a JDBC-backed repository in production. Customize access tokens so their
audience is the exact canonical MCP resource:

```java
@Bean
OAuth2TokenCustomizer<JwtEncodingContext> mcpAudience(
        McpClientProperties properties,
        @Value("${app.mcp.resource}") String resource
) {
    return context -> {
        boolean isMcpAccessToken =
                OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())
                && properties.allowedClientIds().contains(
                        context.getRegisteredClient().getClientId()
                );
        if (isMcpAccessToken) {
            context.getClaims().audience(List.of(resource));
        }
    };
}
```

This example assumes every configured client ID is dedicated to the canonical
MCP resource. Do not add the MCP audience to every access token emitted by a
shared authorization server. When one client can request multiple resources,
use the authorization server's validated resource-indicator context instead
of selecting the audience from client ID alone.

Also configure a stable issuer, persistent signing keys with rotation, exact
client metadata, and the registration mode supported by the target Doubao
surface. Verify that the selected Spring Authorization Server version supports
the required RFC 8707 `resource` behavior. If it does not, use a maintained
gateway or a reviewed framework extension; do not silently omit resource
binding.

Two narrow compatibility adapters may be necessary:

1. If the target client performs unauthenticated RFC 7591 registration while
   the framework's built-in OIDC registration endpoint requires an initial
   access token, validate the request and save a framework
   `RegisteredClient`. Do not implement grants, codes, or tokens in this
   adapter.
2. If the selected framework release does not validate RFC 8707 `resource`,
   require the canonical resource at both authorization and token endpoints,
   preserve it with the authorization, and set the JWT audience through the
   framework token customizer.

Use the framework's JDBC repositories when the application already has a
database. Start from the exact schema bundled with the resolved framework
version, then adapt only SQL data types required by the selected database
dialect. Test a real code exchange after the adaptation; successful table
creation alone does not prove authorization state can be serialized and read.

When bridging an existing cookie login, map the validated application session
to a Spring Security `Authentication` before the authorization endpoint. Use
Spring Security's bounded saved-request mechanism to resume the original
authorization request after upstream login instead of maintaining a second
custom authorization transaction.

Serve the Web application and API with the same URL scheme. A secure
`SameSite=Lax` session cookie can be dropped from credentialed requests when
an HTTP Web origin calls an HTTPS API because browsers apply schemeful
same-site rules. Verify the effective environment after loading local env
files; stale values can override corrected defaults.

## Keep MCP Security Separate

Use an ordered filter chain for `/mcp`:

```java
@Bean
@Order(Ordered.HIGHEST_PRECEDENCE)
SecurityFilterChain mcpSecurity(HttpSecurity http) throws Exception {
    return http
            .securityMatcher("/mcp")
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(
                    SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
            .oauth2ResourceServer(oauth -> oauth.jwt(Customizer.withDefaults()))
            .build();
}
```

Do not add a catch-all `permitAll` chain as part of this recipe. Preserve and
test the application's existing security chains. If the application has no
Spring Security chain yet, add one from its actual access policy and default
to authenticated or denied routes rather than globally permitting requests.

Use a standard `JwtDecoder` with independent issuer, audience, timestamp, and
algorithm validation. For an external issuer prefer
`JwtDecoders.fromIssuerLocation`. For a co-located thin authorization facade,
prefer Spring Security `JwtEncoder` and `JwtDecoder` over custom JWT parsing.

Return a Bearer challenge with `resource_metadata` for missing or invalid MCP
credentials. A bare `401` is useful as a negative test but is not the complete
discovery contract.

## Prototype Facade State

Skip this section when using a maintained authorization server. If an
explicitly approved prototype facade is unavoidable, persist only the state
required for a short-lived exchange:

```text
code_hash
user_id
client_id
redirect_uri
resource
scope
code_challenge
code_challenge_method
expires_at
consumed_at
```

Requirements:

- store a hash of the authorization code, not the bearer value;
- require PKCE `S256`;
- bind code to client, exact redirect URI, resource, scope, and user;
- expire codes in minutes;
- consume the code atomically in the same transaction as validation;
- reject replay, mismatch, expiry, and missing verifier as `invalid_grant`.

When the existing login session is absent, save a bounded same-origin
authorization transaction and resume it after the upstream login callback.
Prefer a server-side transaction ID over storing a raw authorization URL in a
cookie.

## Register Tools Explicitly

Spring AI derives the tool name from the Java method unless `name` is set.
Always set the MCP identifier explicitly. This example uses
`get_current_user`; replace it with the smallest useful read operation already
present in the target product:

```java
@Component
final class IdentityTools {
    private final UserDirectory users;

    IdentityTools(UserDirectory users) {
        this.users = users;
    }

    @Tool(
            name = "get_current_user",
            description = "Return the authenticated user's id, name, and avatar URL"
    )
    UserResult getCurrentUser() {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new IllegalStateException("Unauthenticated request");
        }
        return UserResult.from(users.getById(jwt.getSubject()));
    }
}

@Configuration
class McpToolRegistration {
    @Bean
    ToolCallbackProvider mcpToolCallbacks(IdentityTools tools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(tools)
                .build();
    }
}
```

For the historical Spring AI 1.1.0 compatibility profile, the provider import
is:

```java
org.springframework.ai.tool.method.MethodToolCallbackProvider
```

Do not make a proxied `@Configuration` class `final`. Prefer a typed result
record over returning a JSON string when the installed SDK serializes the
record correctly.

Tool authorization must check both:

```text
JWT contains required MCP scope
AND
existing service allows jwt.subject to perform the product operation
```

Do not accept a user ID from tool arguments for identity.

## Fixed Implementation Playbook

Use these gates in order. Do not debug OAuth browser redirects and MCP
transport framing at the same time.

### Gate 1: Version and dependency compile

Record:

```text
Java and Spring Boot
Spring AI and transitive MCP SDK
MCP revision required by the target client
authorization-server product and version
canonical issuer and MCP resource URI
client registration mode
```

Add only the MCP transport and resource-server dependencies, then compile.
Resolve package or property differences by inspecting the installed artifacts
or the matching official example once. Do not try APIs from multiple versions.
Compile immediately at this point, before adding database tables, OAuth
controllers, tools, or broad configuration changes.

### Gate 2: Protected transport with a test token

Before integrating the browser OAuth flow:

1. expose protected-resource metadata;
2. assert anonymous `/mcp` returns the complete Bearer challenge;
3. configure issuer, audience, timestamp, algorithm, and scope validation;
4. mint a token only inside the test fixture or use a disposable test issuer;
5. complete MCP initialize, tool listing, and one read-only tool call.

This gate isolates Spring Security and MCP framing from authorization-server
redirects. Never add a production "test token" endpoint.

### Gate 3: Authorization server

Connect the maintained authorization server and prove:

1. discovery metadata;
2. registered client and exact redirect URI;
3. authorization code with PKCE S256;
4. exact `resource` binding at authorization and token exchange;
5. MCP audience and least-privilege scopes in the access token;
6. replay rejection and documented refresh behavior.

### Gate 4: Existing-login bridge

Reuse the existing identity provider only as upstream user authentication:

1. start authorization while signed out;
2. complete the existing login;
3. resume the same bounded authorization transaction;
4. verify the authorization server sees the authenticated application user;
5. call the MCP tool and verify JWT subject maps to that user.

### Gate 5: Target client

Only after Gates 1-4 pass, connect the exact Doubao surface. Verify its
registration and credential model, protocol revision, canonical URL matching,
tool allowlist, token refresh, and negative authorization behavior.

At each gate run the focused test first. Run the full module suite only after
the focused gate is green.

## Compatibility Example: Stateful `2025-06-18`

Use this section only when the frozen compatibility matrix selects the
historical Spring AI 1.1.0 / MCP SDK 0.16.0 profile or the target client
requires equivalent session behavior. For newer stateless revisions, follow
the installed SDK test client and do not force `Mcp-Session-Id`.

For the stateful compatibility profile, do not start with `tools/list`. The
minimum sequence is:

1. `initialize`;
2. read `Mcp-Session-Id` from the response;
3. send `notifications/initialized` with that session ID;
4. call `tools/list`;
5. call `tools/call`.

Every POST in this compatibility mode should send:

```http
Content-Type: application/json
Accept: application/json, text/event-stream
Authorization: Bearer <access-token>
Mcp-Session-Id: <session-id-after-initialize>
```

Example initialization payload:

```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "initialize",
  "params": {
    "protocolVersion": "2025-06-18",
    "capabilities": {},
    "clientInfo": {
      "name": "integration-test",
      "version": "1.0.0"
    }
  }
}
```

Spring AI may return `initialize` as JSON and later request responses as SSE:

```text
id:<session-id>
event:message
data:{"jsonrpc":"2.0","id":2,"result":{...}}
```

Parse the `data:` field as JSON. In MockMvc, decode response bytes explicitly
as UTF-8 before asserting non-ASCII tool output:

```java
String body = result.getResponse()
        .getContentAsString(StandardCharsets.UTF_8);

JsonNode parseSseData(String body, ObjectMapper mapper) throws Exception {
    for (String line : body.split("\n")) {
        if (line.startsWith("data:")) {
            return mapper.readTree(line.substring(5).trim());
        }
    }
    throw new AssertionError("Missing SSE data field");
}
```

## Focused Vertical-Slice Test

One integration test should prove this complete chain:

```text
seed application user and valid web session
-> authorize with state, resource, and PKCE S256
-> capture authorization code
-> exchange code with verifier
-> initialize MCP session
-> send initialized notification
-> list the configured first read tool
-> call the configured first read tool
-> assert the result is authorized for the verified JWT subject
```

Add negative cases before broad manual testing:

- anonymous `/mcp` is `401` with the expected challenge;
- wrong issuer, audience, expiry, algorithm, and scope are rejected;
- plain or missing PKCE is rejected;
- redirect URI and resource mismatches are rejected;
- authorization-code replay is rejected;
- a valid token for another API is rejected.

Run focused tests first:

```bash
./gradlew test --tests '*Mcp*IntegrationTest'
./gradlew test
```

Complete the planned focused authorization, resume-flow, and negative cases
before running the full module suite. Run the full suite once, then start a
real server for a smoke test.

## Repeated Failure Modes

| Symptom | Cause | Fast fix |
| --- | --- | --- |
| `MethodToolCallbackProvider` not found | Wrong package copied from another Spring AI version | Inspect the installed JAR; for 1.1.0 use `org.springframework.ai.tool.method` |
| Configuration class proxy error | Nested `@Configuration` declared `final` | Remove `final` or use `proxyBeanMethods = false` when valid |
| Existing APIs suddenly return `403` | One broad Spring Security chain captured all routes | Use ordered matchers and preserve existing auth |
| MCP POST returns `400` with invalid Accept headers | Client sent only JSON or only SSE | Send both `application/json, text/event-stream` |
| `tools/list` returns `400` before dispatch | MCP session was never initialized | Run initialize and initialized notification first |
| JSON assertion fails on tools response | Response is SSE, not plain JSON | Parse the SSE `data:` line |
| Chinese or other text is mojibake | Test decoded response with default charset | Decode response as UTF-8 explicitly |
| Tool appears in camelCase | `@Tool` defaulted to the Java method name | Set an explicit snake_case `name` |
| OAuth errors become generic `500` | Global exception advice wins | Give OAuth advice higher precedence and test RFC error JSON |
| Application fails to start after a YAML edit | A top-level key such as `spring` was duplicated | Merge into the existing mapping and run a context-load test immediately |
| Database rejects a generated transaction ID | Encoded identifier is longer than its column | Derive schema length from the encoded format and test the maximum value |
| Curl reports connection refused after a prior successful boot | Detached dev server was terminated or stale processes conflict | Use one managed process, wait for readiness, and stop it explicitly |
| IDE reports missing Spring classes but Gradle compiles | Dependency index is stale | Treat clean Gradle compile/test as source of truth, then refresh IDE |

When a request returns a large exception JSON body, extract the top-level
message first. For example, the SDK message
`Invalid Accept headers. Expected TEXT_EVENT_STREAM and APPLICATION_JSON`
points directly to the transport contract and should not trigger auth rewrites.

## Production Hardening Before Reuse

Do not promote a minimal custom authorization facade until all of these are
true:

- clients and exact redirect URIs are registered;
- `resource` is required and checked at authorization and token endpoints;
- unsupported scopes fail with `invalid_scope` instead of being discarded;
- each tool enforces its required scope;
- authorization codes are hashed and consumed atomically;
- state and authorization response issuer handling match the client contract;
- signing keys are persistent, rotated, and stored in a secret manager;
- JWT validation includes issuer, audience, expiry, `nbf`, and algorithm;
- `WWW-Authenticate` points to protected-resource metadata;
- Origin, CORS, rate limits, request limits, and proxy SSE settings are tested;
- refresh and revocation behavior is documented when tokens outlive a session;
- audit logs identify subject, client, tool, result, and request ID without
  recording tokens or authorization codes.

If these requirements exceed the product's intended scope, use a maintained
authorization server or identity gateway instead of extending the facade.

## Primary Sources

- Spring Authorization Server getting started:
  https://docs.spring.io/spring-authorization-server/reference/getting-started.html
- Spring Authorization Server configuration model:
  https://docs.spring.io/spring-authorization-server/reference/configuration-model.html
- Spring Security OAuth2 Resource Server:
  https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/index.html
