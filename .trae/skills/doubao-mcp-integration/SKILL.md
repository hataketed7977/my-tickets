---
name: doubao-mcp-integration
description: Build MCP integrations for Doubao Work over local stdio or remote Streamable HTTP with OAuth 2.1. Use when exposing private systems or local tools to Doubao.
---

# Doubao MCP Integration

Implement the integration in the target repository. Do not stop at architecture notes unless the user asks for design only.

## Scope

Build an MCP server around an existing system or local tool, select stdio or Streamable HTTP deliberately, secure the selected boundary, and make its tools easy for both strong and less capable models to call.

Do not:

- replace the product's existing login or authorization model;
- build a new identity provider when a maintained OAuth authorization server can be used;
- pass an upstream or downstream API token through as the MCP access token;
- add role systems, admin concepts, or broad scopes not already required by the product;
- expose every internal API as a tool;
- use MCP OAuth for local stdio transport;
- expose a local stdio server as a network listener.

## Required References

Read only the files needed for the current phase:

1. Read [transport-selection.md](references/transport-selection.md) before choosing stdio or HTTP.
2. Read [protocol-contract.md](references/protocol-contract.md) only for Streamable HTTP with OAuth.
3. Read [architecture-and-implementation.md](references/architecture-and-implementation.md) before editing code.
4. Read [tool-design-for-reliable-model-use.md](references/tool-design-for-reliable-model-use.md) before defining tools.
5. Read [doubao-integration.md](references/doubao-integration.md) for the target Doubao surface.
6. Read [verification.md](references/verification.md) before writing tests or declaring completion.

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
3. A thin authorization facade only when the existing identity system cannot issue OAuth tokens.

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

## Completion Report

Report:

- transport and architecture selected, including why;
- HTTP resource URI, issuer, scopes, and registration mode when applicable;
- stdio command contract and credential source when applicable;
- supported MCP revisions;
- tools added and their risk class;
- files changed;
- exact tests and probe commands run with results;
- Doubao configuration still requiring console or credential setup;
- deferred compatibility risks.

Do not report completion from discovery alone. HTTP requires a protected tool call and a negative authorization case; stdio requires a real subprocess tool call, protocol-only `stdout`, clean shutdown, and a denied local or product permission case.
