---
name: doubao-integration-builder
description: Build integrations for Doubao and AI agents through Node.js CLI, local stdio MCP, or remote Streamable HTTP MCP. Use when exposing existing systems as agent-callable capabilities.
---

# Doubao Integration Builder

Implement the requested integration in the target repository. Optimize for the
shortest path to trustworthy evidence. Do not stop at architecture notes unless
the user asks for design only.

## Priorities

Apply these priorities in order:

1. Preserve explicit user requirements and existing repository decisions.
2. Reuse a matching implementation recipe instead of researching alternatives.
3. Produce the smallest complete behavior in the requested scope.
4. Verify it with the lightest deterministic evidence that proves that scope.
5. Escalate to broader tests, live processes, external clients, or data
   inspection only when the task requires them or concrete failure evidence
   points there.

Do not:

- reconsider a working or pinned stack without incompatibility evidence;
- search for the latest dependency or protocol revision when the repository
  already fixes one and the user did not request an upgrade;
- force MCP when a CLI satisfies the caller and contract requirements;
- force CLI when protocol discovery, typed schemas, cancellation, or remote
  multi-user access is required;
- use MCP OAuth for local stdio transport;
- expose a local stdio server as a network listener;
- replace the product's existing login or authorization model;
- hand-write production OAuth authorization or token endpoints when a
  maintained authorization server is available;
- pass an upstream or downstream API token through as the MCP access token;
- expose every internal API as a command or tool;
- build CLI commands by concatenating model-controlled shell strings;
- infer that production safeguards may be skipped from repository names,
  localhost URLs, sample data, or workshop-like structure.

## Freeze Existing Decisions

Inspect dependency manifests, nearby integration code, configuration, and
focused tests first.

If the repository state that existed before the current task already pins an
SDK, transport, security framework, JSON stack, protocol revision, or
deployment boundary, treat that choice as frozen unless:

- the user explicitly requests migration or upgrade; or
- a focused compile or behavior test proves that it cannot satisfy the
  requested contract.

Do not treat dependencies or architecture introduced during the current
attempt as pre-existing decisions. Use the task-start diff or committed
baseline to distinguish them without exploring unrelated history.

Do not compare alternatives, build a compatibility matrix, inspect unrelated
cached releases, or browse for newer versions on the fast path. For one unknown
API, inspect the selected dependency, its installed source, or one matching
official example and continue with that version.

Treat sample applications as API and protocol evidence, not architecture
authority. When a sample hand-writes a capability owned by the selected
framework, reuse only its verified request shapes, response shapes, API calls,
and test vectors. Do not copy its custom protocol implementation.

Use the repository's wrapper, lockfiles, dependency management, and setup
scripts. In controlled training or CI environments, tool installation and
dependency prefetch are environment prerequisites rather than implementation
work. Do not change frameworks or versions merely because a download, file
permission, or local tool setup is unavailable; report that environment
precondition separately.

When no relevant decision exists, choose the first matching architecture:

| Situation | Default |
| --- | --- |
| Maintained CLI already exposes the operation | Extend its command and machine-output contract |
| Local process and human shell use matter | Node.js CLI over existing services or APIs |
| One local user needs MCP discovery or schemas | Official MCP SDK with stdio and no MCP OAuth |
| Remote/shared service has an OAuth/OIDC issuer | Streamable HTTP MCP resource server trusting that issuer |
| Remote service has only cookie or social login | Maintained authorization server federated to the existing login |
| Product API is separately protected | Token exchange, on-behalf-of, service credential, or encrypted per-user credential |

Freeze the selected route after the first focused compile or contract test.
Reopen selection only when new evidence invalidates it.

## Reference Router

After the route is frozen, load only references needed for the behavior being
implemented. Do not load selection references or alternative-framework recipes
after a matching stack is known.

- Node.js CLI implementation:
  [nodejs-cli-integration.md](references/nodejs-cli-integration.md)
- MCP transport choice, only when the repository and request do not already
  determine stdio or HTTP:
  [transport-selection.md](references/transport-selection.md)
- Spring AI transport, only when the repository already uses it or it is the
  selected adapter:
  [spring-ai-streamable-http.md](references/spring-ai-streamable-http.md)
- Official MCP Java SDK with Servlet or Spring Boot, only when the repository
  already uses it or Spring AI cannot satisfy the required contract:
  [java-mcp-sdk-streamable-http.md](references/java-mcp-sdk-streamable-http.md)
- OAuth protocol contract, only when OAuth is in scope:
  [protocol-contract.md](references/protocol-contract.md)
- Spring Authorization Server, only when it is selected:
  [spring-authorization-server-mcp.md](references/spring-authorization-server-mcp.md)
- Feishu/Lark identity reuse:
  [feishu-identity-bridge.md](references/feishu-identity-bridge.md)
- TLS and public URLs, only when the task changes or validates ingress,
  certificates, browser origins, or advertised URLs:
  [tls-and-public-url.md](references/tls-and-public-url.md)
- Tool design, only when adding or changing tools:
  [tool-design-for-reliable-model-use.md](references/tool-design-for-reliable-model-use.md)
- Doubao product behavior, only when the task includes Doubao configuration,
  compatibility, or live validation:
  [doubao-integration.md](references/doubao-integration.md)
- Cross-language architecture, only when no matching implementation recipe
  exists or the task changes component boundaries:
  [architecture-and-implementation.md](references/architecture-and-implementation.md)
- Broader release verification, only for final integration or release review:
  [verification.md](references/verification.md)

## Workflow

### 1. Inspect Lightly

Discover only facts that determine implementation:

- requested integration mode and target runtime;
- existing language, framework, package manager, dependencies, and tests;
- current authentication boundary and principal type;
- selected transport, endpoint, protocol revision, and state model for MCP;
- first command or tool behavior;
- focused compile or test command;
- TLS/public URL facts only when runtime ingress is in scope.

Stop discovery when these facts are known. Do not inspect git history, every
module, dependency caches, live databases, or the full deployment unless a
specific failure requires it.

For ephemeral or in-memory databases, use repository/service fixtures in tests.
Do not query tables manually as routine verification. Inspect database state
only when a failing persistence test identifies it as the unresolved layer.

### 2. Freeze a Short Contract

Record:

- selected mode and existing stack;
- command or MCP endpoint;
- authentication and authorization boundary;
- one requested capability;
- explicit non-goals;
- the lowest evidence level that proves completion.

For CLI, also freeze stdin, stdout, stderr, exit codes, JSON shape,
configuration precedence, and packaging.

For MCP, also freeze transport, state model, protocol revision, tool name and
schema, and resource/issuer/scope only when OAuth is in scope.

Do not turn this contract into a separate research deliverable.

### 3. Implement One Complete Behavior

Keep entry points thin and reuse existing application services. Share business
adapters, validation, result types, and errors across CLI and MCP modes.

For CLI:

1. implement command parsing and stable machine output;
2. call one existing business operation;
3. prove success and one safe failure through a real subprocess;
4. add authentication or packaging only when required by the request.

For stdio MCP:

1. create the executable entry point;
2. keep JSON-RPC only on `stdout` and logs on `stderr`;
3. validate environment and working directory;
4. expose one tool through an existing business service;
5. prove the tool call and clean shutdown through a subprocess test.

For Streamable HTTP MCP, select only capabilities required by the request:

1. transport and tool contract;
2. bearer/resource protection;
3. OAuth discovery, registration, PKCE, token, and resource binding;
4. existing-login identity bridge;
5. target-client integration.

These are ordered capability stages, not mandatory steps for every task. Do not
implement or verify a later stage merely because it exists. In particular:

- existing-authentication work does not imply adding OAuth;
- OAuth server work does not imply exercising a real identity provider;
- server implementation does not imply configuring a target client;
- target-client end-to-end validation belongs only to an explicit integration
  or final release scope.

For Spring Boot HTTP MCP, preserve the adapter selected before the task. When
starting without an adapter, prefer the Spring AI MCP Boot starter and
annotations if its version is compatible with the repository and it supports
the required transport, state model, protocol revision, and request context.
Use the official MCP Java SDK directly only when the Spring adapter lacks a
required capability or low-level Servlet control is explicitly needed.

Spring AI extends and depends on the MCP Java SDK; they are complementary, not
competing application frameworks. For an existing non-OAuth Session Bearer,
reuse the repository's current middleware, interceptor, or a narrowly scoped
Servlet filter; do not introduce a new security architecture solely to protect
one endpoint. For standards-based OAuth bearer validation, use Spring Security
OAuth2 Resource Server.

When Spring Authorization Server is selected, leave client registration,
grants, PKCE, token lifecycle, revocation, and JWK handling in the framework.
Keep custom code limited to required compatibility adapters, resource binding,
and the existing-login bridge.

### 4. Design Reliable Interfaces

Across CLI and MCP:

- expose a small allowlisted command or tool set;
- use flat, strict schemas and constrained values;
- separate lookup from mutation;
- return stable identifiers and compact structured results;
- make errors machine-actionable without leaking secrets;
- document side effects and risk accurately;
- keep product authorization in application services.

For writes, require the product's confirmation, idempotency, and audit controls.
Do not invent roles, broad scopes, or admin concepts.

### 5. Use the Lightest Sufficient Evidence

Verification levels:

1. **Compile/static** — proves dependency and type compatibility.
2. **Focused automated test** — proves the changed behavior and its nearest
   negative case. This is the default implementation loop.
3. **Affected module suite/build** — proves local regression safety. Run once
   after focused tests pass.
4. **Managed runtime probe** — use only when the task changes runtime routing,
   TLS, process startup, public metadata, browser-origin behavior, or another
   boundary that automated tests cannot prove.
5. **Real target-client end to end** — use only when explicitly requested,
   when target-client compatibility is the deliverable, or during final release
   validation.

Stop at the lowest level that proves the requested scope. Do not start the full
application stack for ordinary code, transport, authorization, or identity
tests when an embedded or subprocess test covers the boundary.

Do not repeat an already green lower level unless later edits affect it. Run
one end-to-end flow after all required lower levels are green; never use the
full flow as the diagnostic loop for a lower-layer failure.

For security-sensitive behavior, focused evidence must include a denial or
negative case. For TLS, never use disabled certificate or hostname validation
as completion evidence.

### 6. Diagnose from Evidence

Treat server owners, file watchers, foreground supervisors, and event loops as
long-running processes. Keep them in managed sessions and stop them after the
required runtime evidence is captured.

On failure:

1. record the exact failure signature and current layer;
2. check request or fixture validity before changing production code;
3. form one discriminating hypothesis;
4. make one bounded probe or change;
5. rerun only the focused failing check.

If the same signature survives without new evidence, stop editing and compare
the selected implementation with its matching recipe or official example. Do
not branch into alternative frameworks, broad searches, repeated full suites,
database exploration, or live-client retries.

## Completion Report

Report only facts relevant to the requested scope:

- selected mode, stack, transport, and security boundary;
- command or tool contract;
- files changed;
- exact focused tests and affected suite/build results;
- runtime or target-client evidence only when performed;
- deferred compatibility or deployment work outside scope.

Do not claim behavior beyond the evidence level reached. If target-client
compatibility was in scope but not exercised, report it as unverified. If it
was outside scope, do not treat its absence as incomplete work.

CLI completion requires a real subprocess success and safe failure. stdio MCP
completion requires a real subprocess tool call, protocol-only `stdout`, and
clean shutdown. HTTP MCP completion requires a focused real-HTTP or equivalent
embedded-server test for the requested capability and its nearest negative
case. Production readiness additionally requires the relevant release and
target-client gates.
