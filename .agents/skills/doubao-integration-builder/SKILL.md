---
name: doubao-integration-builder
description: Build integrations for Doubao and AI agents through Node.js CLI, local stdio MCP, or remote Streamable HTTP MCP. Use when exposing existing systems as agent-callable capabilities.
---

# 豆包 Integration Builder

Implement the integration in the target repository. Do not stop at architecture notes unless the user asks for design only.

## Scope

Build an agent-facing integration around an existing system or local tool.
Select Node.js CLI, local stdio MCP, or remote Streamable HTTP deliberately,
secure the selected boundary, and make the interface reliable for both people
and models.

Do not:

- force MCP when a CLI already satisfies the caller, deployment, and contract requirements;
- force a CLI when protocol-level discovery, typed schemas, cancellation, or remote multi-user access is required;
- replace the product's existing login or authorization model;
- build a new identity provider when a maintained OAuth authorization server can be used;
- hand-write OAuth authorization or token endpoints for production;
- infer that production safeguards may be skipped from a branch name, repository name, localhost URL, sample data, or workshop-like structure;
- pass an upstream or downstream API token through as the MCP access token;
- add role systems, admin concepts, or broad scopes not already required by the product;
- expose every internal API as a tool;
- build CLI commands by concatenating model-controlled shell strings;
- use MCP OAuth for local stdio transport;
- expose a local stdio server as a network listener.

## Default Recommendation

Choose the first matching row and stop reconsidering unless repository evidence
invalidates it:

| Situation | Recommended architecture |
| --- | --- |
| A maintained CLI already exposes the required operations | Extend its command and machine-output contract |
| A local process interface is sufficient and human use or shell composition matters | Node.js CLI over existing services or APIs |
| One user, local capabilities, and protocol discovery or typed schemas matter | Official MCP SDK with stdio; no MCP OAuth |
| Remote or shared service with an existing OAuth/OIDC authorization server | Official MCP SDK with Streamable HTTP plus a resource server that trusts the existing issuer |
| Remote service with only cookie or social login | Add a maintained authorization server or identity gateway, federate the existing login, and keep `/mcp` as a resource server |
| Existing product API is separately protected | Use token exchange, on-behalf-of, a service credential, or an encrypted per-user credential; never pass through the MCP token |

For a CLI, preserve the repository's existing runtime, package manager, command
framework, and API clients when they are suitable. For a new Node.js CLI, use
[nodejs-cli-integration.md](references/nodejs-cli-integration.md).

For MCP on Spring Boot, use Spring Security OAuth2 Resource Server for bearer
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

1. Read [nodejs-cli-integration.md](references/nodejs-cli-integration.md) only when the selected integration is a Node.js CLI.
2. Read [transport-selection.md](references/transport-selection.md) before choosing stdio or HTTP after MCP is selected.
3. Read [doubao-integration.md](references/doubao-integration.md) before selecting an MCP SDK, OAuth registration mode, or public URL.
4. Read [protocol-contract.md](references/protocol-contract.md) only for Streamable HTTP with OAuth.
5. Read [architecture-and-implementation.md](references/architecture-and-implementation.md) before editing MCP code.
6. Read [tool-design-for-reliable-model-use.md](references/tool-design-for-reliable-model-use.md) before defining MCP tools.
7. Read [verification.md](references/verification.md) before writing MCP tests or declaring completion.
8. Read [spring-ai-streamable-http.md](references/spring-ai-streamable-http.md) only when Spring AI is the selected MCP transport adapter.
9. Read [java-mcp-sdk-streamable-http.md](references/java-mcp-sdk-streamable-http.md) when the official MCP Java SDK and a Servlet or Spring Boot host are selected.
10. Read [spring-authorization-server-mcp.md](references/spring-authorization-server-mcp.md) when Spring Authorization Server is selected.
11. Read [feishu-identity-bridge.md](references/feishu-identity-bridge.md) when the existing identity source is Feishu/Lark or the user asks to reuse Feishu login.
12. Read [tls-and-public-url.md](references/tls-and-public-url.md) for Streamable HTTP when certificate trust, browser login, reverse proxies, or public URL deployment are involved.

Do not load all references at once.

## Workflow

### 1. Establish Facts

Inspect the repository before proposing changes. Record:

- target agent or runtime and whether it can launch local commands, launch an
  MCP child process, or connect only to remote MCP;
- language, framework, package manager, and test commands;
- existing CLI entry points, command framework, package scripts, output
  conventions, exit codes, and distribution method;
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

Stop discovery when the integration mode, runtime or framework, authentication
boundary, first capability, and focused test command are known. For MCP also
record the protocol version and internal principal. Do not inspect git history
or reopen the same files unless the current tree conflicts with the task or a
concrete failure requires it.

### 1.5 Reuse a Proven Recipe

Before researching framework APIs from scratch:

1. identify the repository runtime, framework, package manager, and installed versions;
2. load the matching reference recipe when one exists;
3. verify the target runtime's process-launch and output needs for CLI, or its transport, TLS, registration, credential, and revision requirements for MCP;
4. select one row from Default Recommendation;
5. freeze the CLI command contract or the MCP compatibility matrix;
6. prove that the selected framework and dependencies satisfy that contract;
7. compile a minimal command or transport spike;
8. write the first focused test for the current gate only.

Timebox manual HTTP probing until the focused test passes. Use a managed foreground or tool-owned server process for smoke tests; do not repeatedly launch detached processes and infer protocol failures from stale or terminated servers.

The selected contract is a hard gate before dependency or integration code edits.
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

For HTTP MCP, copy all five gates from section 5 into the execution checklist.
The compatibility matrix belongs to Gate 1; do not create a separate discovery
task that increases the gate count. Never omit the target-client gate or replace
it with a full server-side test suite.

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
- A focused test gets one initial run and at most two hypothesis-driven reruns
  before a diagnostic reset. A gate gets at most eight build or test
  invocations without explicit user approval.
- At a diagnostic reset, stop editing, remove temporary probes, summarize the
  invariants, and compare the implementation with the selected official
  example or reference before running another test.
- If the same failure signature survives two attempts, stop retrying. Capture
  the exact request, response, and resolved dependency version; form a new
  evidence-based hypothesis before another run.
- After three rejected hypotheses or ten diagnostic actions without a new
  observable, stop mutating code and report the gate as blocked with evidence.
- If a gate remains red after its timebox, report the failing gate and evidence
  instead of continuing into later gates.

### 2. Select the Integration Boundary

Choose CLI when the target can launch a local process, a command contract is
sufficient, and human usability or shell composition is valuable. Use
[nodejs-cli-integration.md](references/nodejs-cli-integration.md).

Choose MCP when protocol-level discovery, typed schemas, notifications,
cancellation, or MCP-native client configuration is required. Then use
[transport-selection.md](references/transport-selection.md):

- choose stdio when the client launches a trusted local process for one user;
- choose Streamable HTTP when the server is remote, shared, centrally deployed, or multi-user;
- support CLI and MCP when people need commands and MCP clients need the same business operations.

When supporting multiple modes, share business adapters, validation, result
types, and errors. Keep each CLI or MCP entry point thin. Do not tunnel stdio
through HTTP or duplicate business logic.

### 3. Freeze the Contract

Write a short implementation contract before code:

- selected integration mode or multi-mode requirement;
- for CLI, executable name, subcommands, options, stdin, stdout, stderr, exit
  codes, JSON shape, working directory, environment names, authentication
  source, and packaging method;
- for MCP, selected transport or dual-transport requirement;
- HTTP canonical resource URI, issuer, registration mode, scopes, and audience when applicable;
- HTTPS public URL, TLS termination point, certificate source, trust
  distribution, and internal listener protocol when applicable;
- stdio executable command, arguments, working directory, environment names, and local trust boundary when applicable;
- MCP protocol versions to support when applicable;
- command or tool inventory with read/write risk;
- explicit non-goals.

For MCP, prefer the latest stable revision. Add older protocol behavior only
when the named client requires it.

### 4. Choose the Security Architecture

For CLI and stdio, treat process launch, filesystem permissions, environment
injection, and operating-system identity as the local boundary. The process may
still authenticate to a downstream API, but that is downstream authentication,
not MCP OAuth.

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

For a Node.js CLI, use these gates:

1. freeze the command, output, error, exit-code, configuration, and packaging contract;
2. run the fastest configured static check and prove `--help`, `--version`,
   and one read-only command with a mocked dependency;
3. add authentication or configuration only when required, then prove the
   command through a real subprocess;
4. package or install once and run one real authorized smoke test.

Do not create separate tasks for the package manifest, entry point, helper,
formatter, or each test file. Each gate must leave one complete command
behavior working.

For HTTP MCP, use these gates without adding code from a later gate before the
current gate is green:

1. compatibility matrix plus an executable transport spike that proves the
   required state model, endpoint, protocol revision, and response framing;
2. protected-resource metadata, anonymous `401`, bearer validation, MCP
   initialization, tool listing, and one tool call using a test-only token or
   disposable issuer;
3. authorization-server discovery, client registration, PKCE, resource
   binding, code exchange, and negative cases;
4. existing-login identity bridge and signed-out resume flow;
5. exact target-client smoke test.

Dependency resolution or compilation alone does not pass Gate 1. For a
stateless target, the spike must initialize and call one no-op tool without a
session identifier. Gate 2 must not add authorization-server, dynamic client
registration, consent, browser-login, or identity-bridge code.

Within Gate 3, test in this order: metadata, registration, anonymous
authorization redirect, authenticated code issuance, token exchange, then one
MCP call with the issued token. Do not rerun the complete flow to diagnose an
earlier layer.

If the exact target client is unavailable, leave Gate 5 pending and report the
server-side integration as complete but client compatibility as unverified.

#### Independent HTTP delivery profiles

When the prompt explicitly selects one profile, implement only that profile.
Use the Skill and its references for implementation detail; do not require the
prompt to repeat the full checklist.

**HTTPS MCP with existing authentication**

- deliver a complete HTTPS Streamable HTTP MCP service;
- reuse an existing non-browser bearer or service authentication mechanism;
- complete transport, authentication, one read-only tool, negative
  authorization, and target-runtime verification;
- do not add an OAuth authorization server, DCR, consent, browser login, PKCE,
  or refresh-token behavior.

This profile is independently complete. Do not describe it as a foundation for
another task or assume an OAuth phase will follow.

**HTTPS MCP with OAuth 2.1**

- deliver the complete HTTPS MCP transport and OAuth integration from the
  repository state found at the start;
- complete all five HTTP MCP gates, including DCR and the existing-login bridge
  when required;
- do not assume the existing-authentication profile was implemented first;
- reuse compatible existing code when present, but verify every required gate.

This profile is independently complete. Do not automatically execute or depend
on the other profile.

#### stdio delivery gates

For stdio:

1. executable entry point;
2. clean newline-delimited JSON-RPC on `stdout`;
3. logs only on `stderr`;
4. environment and working-directory validation;
5. graceful EOF shutdown and cancellation;
6. one tool calling an existing business service;
7. subprocess integration tests.

Then add remaining read capabilities, followed by writes with confirmation and
audit controls. Reuse existing domain services instead of duplicating business
logic in CLI commands or MCP handlers.

### 6. Design for Reliable Model Use

For CLI, apply [nodejs-cli-integration.md](references/nodejs-cli-integration.md).
For MCP, apply every hard rule in
[tool-design-for-reliable-model-use.md](references/tool-design-for-reliable-model-use.md).
Across both modes:

- expose a small allowlisted command or tool set;
- use flat, strict schemas and constrained values;
- separate lookup from mutation;
- return stable identifiers and a compact structured result;
- make errors machine-actionable;
- document side effects and risk accurately.

### 7. Integrate the Target Client

For CLI, verify the named agent or runtime can launch the executable with the
required arguments, environment, working directory, and timeout, and can parse
the documented stdout and exit codes. Never claim a Doubao surface can execute
an arbitrary CLI unless current product evidence proves it.

For MCP, follow [doubao-integration.md](references/doubao-integration.md). For
HTTP, keep server configuration separate from per-user credentials and match
the configured MCP URL exactly. For stdio, provide a reproducible command and
inject secrets through the client environment instead of command arguments.

Never claim interactive OAuth discovery works in a specific Doubao surface unless current official documentation or a live test proves it. Use its documented Vault or credential flow when required.

### 8. Verify

For CLI, follow [nodejs-cli-integration.md](references/nodejs-cli-integration.md)
and run focused unit, command, and subprocess tests before one final package
and real-environment smoke test.

For MCP, follow [verification.md](references/verification.md). Run the
transport-specific focused tests. Use the bundled HTTP probe only against a
server the user owns or is authorized to test:

```bash
python3 scripts/verify_remote_mcp.py \
  --acknowledge-authorized-target \
  https://mcp.example.com/mcp
```

For write capabilities, test denial, confirmation, idempotency, and audit
behavior. For MCP, test tool selection with at least one weaker target model
when available.

For HTTP MCP, run verification in this order to keep feedback fast:

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

- selected integration mode and why;
- transport and architecture selected, including why;
- CLI executable, command contract, package or install method, configuration
  precedence, authentication source, and supported machine-output mode when applicable;
- whether the authorization layer is existing, managed, framework-provided, or prototype-only;
- HTTP resource URI, issuer, scopes, and registration mode when applicable;
- HTTPS public URL, TLS termination point, certificate authority type, trust
  distribution, and renewal owner when applicable;
- stdio command contract and credential source when applicable;
- supported MCP revisions;
- tools added and their risk class;
- files changed;
- exact tests and probe commands run with results;
- target runtime configuration still requiring console or credential setup;
- deferred compatibility risks.

Do not report completion from discovery alone. CLI requires a packaged or
repository-local subprocess call with stable stdout, stderr, exit codes, and a
negative case. HTTP requires a protected tool call and a negative authorization
case; stdio requires a real subprocess tool call, protocol-only `stdout`, clean
shutdown, and a denied local or product permission case.
Do not call a custom OAuth facade production-ready merely because login and one
tool call succeed.
If the exact target client was not exercised, report "server-side integration
complete; target-client compatibility unverified" instead of declaring the
integration complete.
