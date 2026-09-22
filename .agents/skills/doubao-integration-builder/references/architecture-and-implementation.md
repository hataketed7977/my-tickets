# Architecture and Implementation Playbook

Use this after selecting a transport in `transport-selection.md`. For HTTP, also read `protocol-contract.md`. Preserve the target system's language, framework, business services, and authorization model unless a hard protocol requirement prevents it.

## Evidence Pass

Inspect before editing:

```bash
rg --files -g 'package.json' -g 'pom.xml' -g 'build.gradle*' \
  -g 'requirements*.txt' -g 'pyproject.toml' -g 'go.mod'
rg -n "oauth|oidc|jwt|issuer|jwks|SecurityFilterChain|Authorization|Bearer" .
rg -n "controller|router|handler|service|repository|permission|authorize" .
rg -n "mcp|modelcontextprotocol|streamable|stdio" .
```

Find the narrowest existing service methods that already enforce product rules. MCP handlers should call those methods with a verified user context.

## Architecture Decision Tree

### A. Local stdio

Use a local executable when Doubao Work can launch the process and the tools need local files, applications, or CLI access.

```text
Doubao Work -> stdio child process -> local service or remote product API
```

Keep credentials out of command arguments. Use inherited environment variables, an OS keychain, or a local credential helper. If the process calls a remote API, authenticate that downstream call separately.

### B. HTTP with an existing authorization server

Use it directly.

```text
Doubao/MCP client -> Authorization Server
Doubao/MCP client -> MCP endpoint -> existing service layer -> data/downstream API
```

Required work:

- register or enable an MCP client;
- add MCP resource and scopes;
- expose protected-resource metadata;
- validate tokens at the MCP endpoint;
- map token subject to the existing application user.

### C. HTTP with an identity provider that cannot issue suitable tokens

Put a maintained OAuth authorization server or identity gateway in front of it. Federate login to the existing IdP. Do not turn the MCP module into a custom identity platform.

### D. HTTP when the existing system only has cookie sessions

Keep browser sessions for the web app. Add a maintained authorization server
or identity gateway as the OAuth boundary for `/mcp`; federate or bridge the
existing authenticated user into it. Exchange user authentication at the
authorization server, not inside a tool call. Do not implement authorization
and token endpoints in the MCP transport module. Never send the web session
cookie to the MCP client.

### E. Either transport calls a separately protected downstream API

Use separate downstream credentials:

- token exchange or on-behalf-of flow when supported;
- server-held service credential for service-level operations;
- encrypted per-user downstream token store when delegation is required.

Never pass the incoming MCP token through unchanged.

## In-Process Module or Sidecar

Prefer an in-process MCP module when:

- an official or mature SDK exists for the repository's language;
- business services are callable without HTTP;
- deployment and scaling characteristics match the application.

Prefer a local CLI adapter when:

- Doubao Work launches the process through stdio;
- tools need access to local files or applications;
- local installation and updates are operationally acceptable.

Prefer an HTTP sidecar or separate service when:

- the host framework lacks a reliable Streamable HTTP SDK;
- the host release cycle must be isolated from MCP changes;
- the MCP boundary needs independent scaling or internet exposure;
- strict network separation is required.

If using a sidecar, call the product through an authenticated internal API. Do not duplicate repositories or business rules.

## Component Boundaries

Keep these components distinct:

```text
stdio or HTTP transport
  -> local principal or bearer authentication
  -> scope and product authorization
  -> MCP protocol adapter
  -> tool input validation
  -> application service
  -> response mapper
```

Add small interfaces where needed:

```text
AuthenticatedPrincipal
  subject
  tenant_id
  scopes
  token_id

ToolContext
  principal
  request_id
  idempotency_key
  cancellation
```

Do not expose raw token claims to tool implementations.

## Recommended File Shape

Adapt names to the repository:

```text
mcp/
  core
  transport/stdio
  transport/http
  security
  metadata
  tools
  schemas
  errors
  tests
```

Keep OAuth browser endpoints in the authorization-server module, not under the MCP tool package. Do not load HTTP metadata or OAuth components in a stdio-only build.

## Implementation Sequence

### HTTP Slice 1: Metadata and challenge

Add:

- protected-resource metadata endpoint;
- `401` Bearer challenge with `resource_metadata` and minimal scope;
- configuration for canonical resource URI and issuer;
- tests asserting exact values.

### HTTP Slice 2: Token validation

Configure:

- issuer allowlist;
- JWKS or introspection;
- audience validator;
- timestamp validator;
- algorithm allowlist;
- scope extraction;
- authentication failure entry point.

Fail closed when configuration is absent. Load secrets from environment or the deployment secret manager.

### HTTP Slice 3: MCP transport

Use the official SDK's Streamable HTTP implementation. Put authentication middleware before protocol dispatch. Configure:

- request body limit;
- allowed origins;
- timeouts and cancellation;
- request IDs;
- proxy buffering behavior;
- supported protocol versions.

### stdio Slice 1: Process contract

Add:

- an executable entry point using the official SDK's stdio transport;
- startup validation for runtime, working directory, and required environment names;
- JSON-RPC-only `stdout`;
- diagnostics-only `stderr`;
- graceful EOF shutdown and cancellation;
- a subprocess test that starts and stops the server.

### Shared Slice: First read tool

Choose a narrow, common, read-only operation. Call an existing service method. Return both:

- concise text for user display;
- structured content matching an output schema.

Write the end-to-end test before adding more tools.

### Shared Slice: Mutations

For each mutation:

- require a write scope;
- require a stable target ID;
- require expected version or idempotency key when practical;
- expose a preview or `dry_run` for high-impact actions;
- require confirmation through the target client's supported mechanism;
- audit subject, tool, target, result, and request ID without arguments that contain secrets.

## TypeScript Guidance

For new TypeScript work, prefer the official v2 packages that implement MCP `2026-07-28`:

```text
@modelcontextprotocol/server
@modelcontextprotocol/express
zod v4 or another Standard Schema implementation
```

Use the server package's stdio transport for local mode and the matching framework adapter for HTTP mode. Use Node.js 20 or newer for the v2 SDK. Keep the framework adapter thin:

```typescript
type Principal = Readonly<{
  subject: string;
  tenantId: string;
  scopes: ReadonlySet<string>;
}>;

function requireScope(principal: Principal, scope: string): void {
  if (!principal.scopes.has(scope)) {
    throw new ToolAuthorizationError("insufficient_scope", scope);
  }
}
```

Do not copy v1 examples using the monolithic `@modelcontextprotocol/sdk` package into a v2 implementation. Check the installed SDK's examples for exact transport APIs before coding.

## Spring Boot Routing

For Spring AI transport, read `spring-ai-streamable-http.md`. For direct
official Java SDK transport in a Servlet or Spring Boot host, read
`java-mcp-sdk-streamable-http.md`. When Spring Authorization Server is
selected, also read `spring-authorization-server-mcp.md`. Do not copy partial
Spring snippets from this generic architecture reference or hand-write
JSON-RPC and OAuth protocol handling.

## Scope and Product Permission Mapping

Scopes describe delegated capability, not every product object:

```text
tickets:read
tickets:write
categories:read
categories:write
```

Enforce both layers:

```text
token has tickets:write
AND
existing product service allows this subject to modify ticket T
```

Do not encode database row IDs, tenant IDs, or user roles into scope names.

## Configuration Contract

Use transport-specific environment-backed configuration.

HTTP:

```text
MCP_PUBLIC_URL=https://mcp.example.com/mcp
MCP_AUTH_ISSUER=https://auth.example.com
MCP_JWKS_URI=
MCP_ALLOWED_ORIGINS=https://client.example.com
MCP_PROTOCOL_COMPATIBILITY=latest
```

stdio:

```text
MCP_TRANSPORT=stdio
PRODUCT_API_URL=https://api.example.com
PRODUCT_API_TOKEN=<injected secret>
MCP_ALLOWED_PATHS=/Users/example/Documents
```

Add only variables the selected token-validation mode needs. Validate URLs and required values at startup. Never log client secrets, bearer tokens, authorization codes, or refresh tokens.

## Observability

Emit structured records:

```json
{
  "request_id": "req_...",
  "subject_hash": "...",
  "client_id": "...",
  "tool": "get_ticket",
  "scope_decision": "allow",
  "result": "success",
  "duration_ms": 42
}
```

Record authorization failures without token contents. Track:

- stdio process starts, exits, restarts, and malformed-message counts;
- `401` and `403` counts;
- tool latency and error rate;
- rate-limit decisions;
- authorization-server and JWKS failures;
- mutation outcomes;
- protocol version distribution.

## Migration Strategy

Ship progressively:

1. selected transport entry point in a non-production environment;
2. one read-only tool;
3. Doubao Work connection and model evaluation;
4. the second transport only when it has a real deployment use case;
5. remaining read tools;
6. low-risk writes behind explicit allowlists;
7. high-risk writes only after confirmation and audit are verified.

Keep the existing web/API behavior unchanged unless the implementation contract explicitly includes it.
