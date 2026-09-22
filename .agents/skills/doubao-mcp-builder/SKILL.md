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
- infer that production safeguards may be skipped from a branch name, repository name, localhost URL, sample data, or workshop-like structure;
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

For Spring Boot, use Spring Security OAuth2 Resource Server for bearer
validation. Use Spring AI for MCP transport only when its resolved MCP SDK
supports the revision and state model required by the target Doubao surface;
otherwise use the official MCP Java SDK directly. Use the existing
authorization server or Spring Authorization Server for OAuth. A custom
authorization facade is allowed only for an explicitly disposable prototype;
label it non-production and include the hardening gaps in the completion
report.

When Spring Authorization Server is selected, keep client registration,
authorization grants, PKCE, token issuance, refresh, revocation, and JWK
handling in the framework. Limit custom code to reviewed compatibility
adapters such as the target client's registration profile, RFC 8707 resource
binding, and the existing-login identity bridge.

## Required References

Read only the files needed for the current phase:

1. Read [transport-selection.md](references/transport-selection.md) before choosing stdio or HTTP.
2. Read [doubao-integration.md](references/doubao-integration.md) before selecting an SDK, OAuth registration mode, or public URL.
3. Read [protocol-contract.md](references/protocol-contract.md) only for Streamable HTTP with OAuth.
4. Read [architecture-and-implementation.md](references/architecture-and-implementation.md) before editing code.
5. Read [tool-design-for-reliable-model-use.md](references/tool-design-for-reliable-model-use.md) before defining tools.
6. Read [verification.md](references/verification.md) before writing tests or declaring completion.
7. Read [spring-ai-streamable-http-oauth.md](references/spring-ai-streamable-http-oauth.md) only when the target is Spring Boot or Spring AI.
8. Read [feishu-identity-bridge.md](references/feishu-identity-bridge.md) when the existing identity source is Feishu/Lark or the user asks to reuse Feishu login.
9. Read [tls-and-public-url.md](references/tls-and-public-url.md) for Streamable HTTP when certificate trust, browser login, reverse proxies, or public URL deployment are involved.

Do not load all references at once.

## Workflow

### 1. Establish Facts

Inspect the repository before proposing changes. Record:

- language, framework, package manager, and test commands;
- existing HTTP API, authentication middleware, authorization checks, and user identity type;
- existing OAuth/OIDC provider, issuer, token format, scopes, and secret handling;
- public MCP URL, deployment topology, reverse proxy, and allowed origins;
- TLS termination point, certificate authority type, private-key owner, renewal
  owner, and trust distribution for every supported client class;
- whether the MCP process runs on the user's machine or a remote server;
- command, arguments, working directory, runtime, and environment needs for stdio;
- target Doubao product and its supported credential path;
- operations users actually need.

Ask only for facts that cannot be discovered and that block a secure implementation. Never request secrets in chat or commit them.

Stop discovery when the transport, framework and versions, authentication
boundary, internal principal, first tool, and test command are known. Do not
inspect git history or reopen the same files unless the current tree conflicts
with the task or a concrete failure requires it.

### 1.5 Reuse a Proven Recipe

Before researching framework APIs from scratch:

1. identify the repository framework and installed versions;
2. load the matching reference recipe when one exists;
3. verify the exact target client's transport, TLS, registration, credential, and MCP revision requirements;
4. select one row from Default Recommendation;
5. freeze an SDK, framework, MCP revision, issuer, resource, registration mode, and target-client compatibility matrix;
6. prove from the resolved dependency graph and installed API that the selected
   transport supports every required matrix row;
7. compile a minimal dependency and transport spike;
8. write the first focused test for the current gate only.

Timebox manual HTTP probing until the focused test passes. Use a managed foreground or tool-owned server process for smoke tests; do not repeatedly launch detached processes and infer protocol failures from stale or terminated servers.

The compatibility matrix is a hard gate before dependency or MCP code edits.
Never choose a dependency version because it appears in a historical example.
If the current framework adapter cannot be proven compatible, select an
official MCP SDK that satisfies the matrix. If no supported implementation can
be proven, stop and report the compatibility blocker instead of adapting an
older transport speculatively.

Do not branch into multiple speculative implementations. When an API is
uncertain, inspect the installed dependency or its official example once,
record the result in the compatibility matrix, and continue with that version.
Inspect only the selected version; do not scan every cached release or compare
unselected APIs after the matrix is frozen.

Keep the implementation plan to 3-5 behavior-complete gates. Each gate must
end in an observable test. Do not create one task per file, class, dependency,
or configuration edit. Rewrite the plan before coding if it is organized by
components or contains more than five implementation gates.

### 1.6 Bound Execution

Inspect repository start scripts before running them. Treat commands that own
a server, file watcher, event loop, foreground supervisor, or unbounded loop
as long-running processes, not commands expected to complete.

- Do not start the full application stack during discovery or compilation.
- Start only the service required by the current gate after its compile-time
  and isolated checks pass.
- Give startup and readiness checks explicit deadlines. Once readiness passes,
  continue verification without waiting for the server command to exit.
- Keep the process in a managed tool session and stop it when the gate ends.
- Do not run overlapping build or test commands for the same module.
- For a failing gate, record the failure signature, suspected layer, current
  evidence, and one discriminating check before editing code.
- Use one hypothesis, one bounded probe or change, and one focused rerun.
  Confirm that a test fixture or client did not construct an invalid request
  before changing production protocol or security code.
- If the same failure signature survives two attempts, stop retrying. Capture
  the exact request, response, and resolved dependency version; form a new
  evidence-based hypothesis before another run.
- After three rejected hypotheses or ten diagnostic actions without a new
  observable, stop mutating code and report the gate as blocked with evidence.
- If a gate remains red after its timebox, report the failing gate and evidence
  instead of continuing into later gates.

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
- HTTPS public URL, TLS termination point, certificate source, trust
  distribution, and internal listener protocol when applicable;
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

Require explicit user instruction or written acceptance criteria before
selecting option 4. Never infer prototype status from repository metadata.

Stop and report a blocker if the proposed design requires accepting a token issued to another resource, wildcard redirect URIs, plaintext production HTTP, or secrets in source control.

For remote HTTP, also stop if normal clients require disabled certificate or
hostname validation, or if advertised HTTPS URLs do not match the actual TLS
listener and certificate identity.

### 5. Implement in Vertical Slices

Implement one read-only tool first. For HTTP, use these gates without adding
code from a later gate before the current gate is green:

1. compatibility matrix and dependency/transport compile;
2. protected-resource metadata, anonymous `401`, bearer validation, MCP
   initialization, tool listing, and one tool call using a test-only token or
   disposable issuer;
3. authorization-server discovery, client registration, PKCE, resource
   binding, code exchange, and negative cases;
4. existing-login identity bridge and signed-out resume flow;
5. exact target-client smoke test.

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
2. run the protected MCP transport and tool test with a test-only token or
   disposable issuer;
3. run focused authorization-server tests;
4. run focused existing-login and resume-flow tests;
5. finish the planned negative cases;
6. run the module test suite once;
7. start one managed server process;
8. run metadata and negative-auth probes;
9. for browser-login bridges, verify that Web and API use the same scheme and
   that credentialed CORS and cookie attributes match the effective runtime
   configuration;
10. run the TLS gate in
   [tls-and-public-url.md](references/tls-and-public-url.md) without trust
   bypasses;
11. run the real target-client smoke test.

## Completion Report

Report:

- transport and architecture selected, including why;
- whether the authorization layer is existing, managed, framework-provided, or prototype-only;
- HTTP resource URI, issuer, scopes, and registration mode when applicable;
- HTTPS public URL, TLS termination point, certificate authority type, trust
  distribution, and renewal owner when applicable;
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
If the exact target client was not exercised, report "server-side integration
complete; target-client compatibility unverified" instead of declaring the
integration complete.
