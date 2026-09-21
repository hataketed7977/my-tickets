---
name: doubao-mcp-builder
description: Build MCP integrations for Doubao using local stdio or remote Streamable HTTP with OAuth 2.1. Use for Doubao MCP, private-system tools exposed to Doubao, or Doubao-compatible remote MCP services.
---

# 豆包 MCP Builder

Implement the integration in the target repository. Do not stop at architecture notes unless the user asks for design only.

## Scope

Build an MCP server around an existing system or local tool, select stdio or Streamable HTTP deliberately, secure the selected boundary, and make its tools easy for both strong and less capable models to call.

Do not:

- replace the product's existing login or authorization model;
- build a new identity provider when a maintained OAuth authorization server can be used;
- hand-write OAuth authorization or token endpoints for production;
- pass an upstream or downstream API token through as the MCP access token;
- add role systems, admin concepts, or broad scopes not already required by the product;
- expose every internal API as a tool;
- use MCP OAuth for local stdio transport;
- expose a local stdio server as a network listener.

## Default Recommendation

Choose the first matching row and stop reconsidering unless repository evidence
invalidates it:

| Situation | Recommended architecture |
| --- | --- |
| One user, local files or local CLI, Doubao launches the process | Official MCP SDK with stdio; no MCP OAuth |
| Remote or shared service with an existing OAuth/OIDC authorization server | Official MCP SDK with Streamable HTTP plus a resource server that trusts the existing issuer |
| Remote service with only cookie or social login | Add a maintained authorization server or identity gateway, federate the existing login, and keep `/mcp` as a resource server |
| Existing product API is separately protected | Use token exchange, on-behalf-of, a service credential, or an encrypted per-user credential; never pass through the MCP token |

For Spring Boot, default to Spring AI for MCP transport, Spring Security OAuth2
Resource Server for bearer validation, and the existing authorization server
or Spring Authorization Server for OAuth. A custom authorization facade is
allowed only for an explicitly disposable prototype; label it non-production
and include the hardening gaps in the completion report.

## Required References

Read only the files needed for the current phase:

1. Read [transport-selection.md](references/transport-selection.md) before choosing stdio or HTTP.
2. Read [protocol-contract.md](references/protocol-contract.md) only for Streamable HTTP with OAuth.
3. Read [architecture-and-implementation.md](references/architecture-and-implementation.md) before editing code.
4. Read [tool-design-for-reliable-model-use.md](references/tool-design-for-reliable-model-use.md) before defining tools.
5. Read [doubao-integration.md](references/doubao-integration.md) for the target Doubao surface.
6. Read [verification.md](references/verification.md) before writing tests or declaring completion.
7. Read [spring-ai-streamable-http-oauth.md](references/spring-ai-streamable-http-oauth.md) when the target is Spring Boot/Spring AI or when a proven OAuth-to-tool vertical slice is useful.

Do not load all references at once.

## Workflow

### 1. Establish Facts

Inspect the repository before proposing changes. Record:

- language, framework, package manager, and test commands;
- existing HTTP API, authentication middleware, authorization checks, and user identity type;
- existing OAuth/OIDC provider, issuer, token format, scopes, and secret handling;
- public MCP URL, deployment topology, reverse proxy, and allowed origins;
- whether the MCP process runs on the user's machine or a remote server;
- command, arguments, working directory, runtime, and environment needs for stdio;
- target Doubao product and its supported credential path;
- operations users actually need.

Ask only for facts that cannot be discovered and that block a secure implementation. Never request secrets in chat or commit them.

### 1.5 Reuse a Proven Recipe

Before researching framework APIs from scratch:

1. identify the repository framework and installed versions;
2. load the matching reference recipe when one exists;
3. select one row from Default Recommendation;
4. freeze an SDK, framework, MCP revision, issuer, resource, registration mode, and target-client compatibility matrix;
5. compile a minimal dependency and transport spike;
6. write one focused end-to-end test covering authentication, initialization, tool listing, and one tool call.

Timebox manual HTTP probing until the focused test passes. Use a managed foreground or tool-owned server process for smoke tests; do not repeatedly launch detached processes and infer protocol failures from stale or terminated servers.

Do not branch into multiple speculative implementations. When an API is
uncertain, inspect the installed dependency or its official example once,
record the result in the compatibility matrix, and continue with that version.

### 2. Select the Transport

Use [transport-selection.md](references/transport-selection.md):

- choose stdio when Doubao launches a trusted local process for one local user;
- choose Streamable HTTP when the MCP server is remote, shared, centrally deployed, or multi-user;
- support both when the same tool set needs local development and remote production access.

When supporting both, share tools, schemas, business adapters, and errors. Keep two thin transport entry points. Do not tunnel stdio through HTTP or implement separate business logic.

### 3. Freeze the Contract

Write a short implementation contract before code:

- selected transport or dual-transport requirement;
- HTTP canonical resource URI, issuer, registration mode, scopes, and audience when applicable;
- stdio executable command, arguments, working directory, environment names, and local trust boundary when applicable;
- protocol versions to support;
- tool inventory with read/write risk;
- explicit non-goals.

Prefer the latest stable MCP revision. Add older protocol behavior only when the named client requires it.

### 4. Choose the Security Architecture

For stdio, treat process launch, filesystem permissions, environment injection, and operating-system identity as the MCP transport boundary. The local process may still authenticate to a downstream API, but that is downstream authentication, not MCP OAuth.

For HTTP, use this order:

1. Existing standards-compliant authorization server plus MCP resource server.
2. Managed authorization server or gateway plus MCP resource server.
3. A maintained framework authorization server federated to the existing identity system.
4. A custom authorization facade only for an explicitly disposable prototype or separately reviewed compatibility exception.

Keep the MCP transport adapter, token validation, business service, and downstream credential handling separate.

Stop and report a blocker if the proposed design requires accepting a token issued to another resource, wildcard redirect URIs, plaintext production HTTP, or secrets in source control.

### 5. Implement in Vertical Slices

Implement one read-only tool end to end first. For HTTP:

1. protected resource metadata;
2. unauthenticated `401` challenge;
3. authorization-server discovery compatibility;
4. bearer validation with issuer, audience, expiry, and scope checks;
5. Streamable HTTP MCP endpoint;
6. one tool calling an existing business service;
7. integration tests.

For stdio:

1. executable entry point;
2. clean newline-delimited JSON-RPC on `stdout`;
3. logs only on `stderr`;
4. environment and working-directory validation;
5. graceful EOF shutdown and cancellation;
6. one tool calling an existing business service;
7. subprocess integration tests.

Then add remaining read tools, followed by write tools with confirmation and audit controls. Reuse existing domain services instead of duplicating business logic in MCP handlers.

### 6. Design for Reliable Model Use

Apply every hard rule in [tool-design-for-reliable-model-use.md](references/tool-design-for-reliable-model-use.md). In particular:

- expose a small allowlisted tool set;
- use flat, strict schemas and constrained values;
- separate lookup from mutation;
- return stable identifiers and a compact structured result;
- make errors machine-actionable;
- annotate risk accurately.

### 7. Integrate the Target Client

For Doubao, follow [doubao-integration.md](references/doubao-integration.md). For HTTP, keep server configuration separate from per-user credentials and match the configured MCP URL exactly. For stdio, provide a reproducible command and inject secrets through the client environment instead of command arguments.

Never claim interactive OAuth discovery works in a specific Doubao surface unless current official documentation or a live test proves it. Use its documented Vault or credential flow when required.

### 8. Verify

Follow [verification.md](references/verification.md). Run the transport-specific focused tests. Use the bundled HTTP probe only against a server the user owns or is authorized to test:

```bash
python3 scripts/verify_remote_mcp.py \
  --acknowledge-authorized-target \
  https://mcp.example.com/mcp
```

For write tools, test denial, confirmation, idempotency, and audit behavior. Test tool selection with at least one weaker target model when available.

Run verification in this order to keep feedback fast:

1. compile the changed module;
2. run the focused OAuth/MCP vertical-slice test;
3. run the module test suite;
4. start one managed server process;
5. run metadata and negative-auth probes;
6. run the real target-client smoke test.

## Completion Report

Report:

- transport and architecture selected, including why;
- whether the authorization layer is existing, managed, framework-provided, or prototype-only;
- HTTP resource URI, issuer, scopes, and registration mode when applicable;
- stdio command contract and credential source when applicable;
- supported MCP revisions;
- tools added and their risk class;
- files changed;
- exact tests and probe commands run with results;
- Doubao configuration still requiring console or credential setup;
- deferred compatibility risks.

Do not report completion from discovery alone. HTTP requires a protected tool call and a negative authorization case; stdio requires a real subprocess tool call, protocol-only `stdout`, clean shutdown, and a denied local or product permission case.
Do not call a custom OAuth facade production-ready merely because login and one
tool call succeed.
