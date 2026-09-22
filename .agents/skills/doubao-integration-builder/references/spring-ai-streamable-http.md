# Spring AI Streamable HTTP Transport

Use this reference only when Spring AI is the selected MCP transport adapter.
Use `spring-authorization-server-mcp.md` separately when Spring Authorization
Server is selected. Use `java-mcp-sdk-streamable-http.md` instead when the
official MCP Java SDK is integrated directly.

## Compatibility Gate

Do not choose Spring AI from framework familiarity. Resolve its transitive MCP
Java SDK and prove that it supports every target-client requirement:

```text
Spring Boot and Spring AI compatibility
MCP Java SDK version
required MCP revision
stateful or stateless Streamable HTTP
required request headers and response framing
Servlet or WebFlux host
tool registration API
request-context propagation
```

Dependency resolution and compilation are insufficient. Build an executable
transport spike that initializes the server and calls one no-op tool using the
required state model.

If any requirement cannot be proven from the selected release's installed API,
official documentation, and spike, use the official MCP Java SDK directly.
Do not emulate a newer protocol with an older adapter.

## Dependencies and Configuration

Use the repository's dependency-management convention:

```groovy
implementation platform("org.springframework.ai:spring-ai-bom:$springAiVersion")
implementation "org.springframework.ai:spring-ai-starter-mcp-server-webmvc"
```

Property names vary by release. Verify configuration metadata from the
selected version before editing. A typical shape is:

```yaml
spring:
  ai:
    mcp:
      server:
        protocol: STREAMABLE
        name: service-mcp
        version: 1.0.0
        type: SYNC
        capabilities:
          resource: false
          prompt: false
          completion: false
        streamable-http:
          mcp-endpoint: /mcp
```

Do not copy this shape when the installed release uses different property
names. Do not add multiple Spring AI or MCP SDK versions to make examples
compile.

## Security Boundary

Spring AI owns MCP framing and dispatch. Spring Security Resource Server owns
bearer authentication. Application services own product authorization.

Keep the `/mcp` security chain stateless and separate from the application's
browser session chain. Validate issuer, audience, expiry, `nbf`, algorithm, and
scope before protocol dispatch.

Return a complete Bearer challenge with `resource_metadata` for missing or
invalid credentials.

Do not use a Spring AI transport security example as authorization-server
configuration. Read `spring-authorization-server-mcp.md` for self-hosted OAuth.

## Tool Registration

Use the selected release's documented local tool registration API. Prefer one
explicit provider bean over mixing annotation scanning and manual registration.

Set every MCP tool name explicitly in snake_case. Do not rely on the Java
method name.

Keep tool methods thin:

```text
verified principal
  -> required MCP scope
  -> existing product authorization
  -> application service
  -> typed result
```

Do not accept caller-supplied user or tenant identity when it must come from
the authenticated principal.

Resolve tool annotation and provider imports from the selected release's
installed API or official documentation once. Do not scan every cached Spring
AI release.

## Request Context

Verify how the selected release propagates request metadata and
authentication to tool handlers. Do not assume a thread-local
`SecurityContext` survives asynchronous or reactive dispatch.

When context propagation is required:

- capture only normalized principal data;
- avoid copying bearer tokens or mutable request objects;
- test concurrent calls for principal isolation;
- reject missing context before calling application services.

If the framework adapter cannot carry the required principal safely, use the
official MCP Java SDK transport or another supported adapter.

## Focused Transport Test

Before adding an authorization server, prove:

1. anonymous `/mcp` returns `401` and the required challenge;
2. a test-only valid token passes issuer, audience, and scope validation;
3. `initialize` negotiates the required revision;
4. the response uses the documented JSON or SSE framing;
5. stateful mode propagates its session identifier, or stateless mode does not
   require one;
6. `notifications/initialized` follows notification semantics;
7. `tools/list` includes the explicit tool name;
8. one tool call returns the expected authorized result;
9. wrong issuer, audience, and scope are rejected.

Use the real HTTP boundary selected by the starter. Do not use a direct
`tools/list` call as a transport health check.

## Execution Discipline

Use one dependency graph command and one targeted API inspection for an
uncertain class or property. Then implement against the selected release.

Do not:

- inspect multiple cached versions;
- alternate between annotation and provider registration without evidence;
- add OAuth, DCR, browser login, or consent before the protected transport
  test passes;
- run the full module suite after every transport edit;
- loosen status and framing assertions merely to make a test pass.

Run:

1. executable transport spike;
2. focused protected transport test;
3. full module suite once after later gates pass.

## Common Failure Modes

| Symptom | Likely cause | Action |
| --- | --- | --- |
| Tool provider class is missing | API copied from another release | Inspect the selected release once |
| Tool is not listed | Annotation scanning and provider registration mismatch | Use one documented registration path |
| MCP POST rejects `Accept` | Required JSON and SSE media types were not advertised | Match the selected transport contract |
| `tools/list` fails before dispatch | Stateful session was not initialized | Initialize and propagate the session ID |
| JSON assertion fails | Response is SSE | Parse the SSE `data:` field |
| Principal disappears in tool handler | Context did not cross dispatch boundary | Use the supported request-context mechanism |
| Existing APIs return `403` | Security chain captured unrelated routes | Narrow the MCP matcher |
| IDE reports missing classes but compile passes | Dependency index is stale | Trust the clean build, then refresh the IDE |

## Completion Evidence

Record:

- Spring Boot, Spring AI, and transitive MCP SDK versions;
- target MCP revision and state model;
- exact transport configuration;
- tool registration path;
- authentication context propagation;
- focused transport test command and result;
- full module test result;
- target-client result or explicit unverified status.

## Primary Sources

- Spring AI MCP reference:
  https://docs.spring.io/spring-ai/reference/api/mcp/mcp-overview.html
- MCP Java SDK:
  https://github.com/modelcontextprotocol/java-sdk
