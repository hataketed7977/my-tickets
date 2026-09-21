# Transport Selection

Choose the transport before designing authentication or deployment. MCP tool semantics stay the same; only process lifecycle, framing, metadata carriage, cancellation, and security boundary change.

## Decision

Choose **stdio** when both core conditions are true:

- Doubao Work can launch a command on the user's machine;
- central multi-user availability is not required.

These are supporting signals, not additional mandatory conditions:

- the MCP server and required runtime can be installed locally;
- one local user or desktop session owns the process;
- the tool needs local files, local applications, CLI programs, or developer credentials;
- local installation and upgrade are operationally acceptable.

Choose **Streamable HTTP** when any are true:

- the MCP server runs in a data center or cloud;
- multiple users or clients share the service;
- the product needs centralized deployment, audit, policy, rate limiting, or scaling;
- the client cannot install or launch the server locally;
- access must work across devices.

Support **both** when local development or local-device access and centrally hosted production access are both real requirements. Share one tool catalog and business layer; add two thin entry points.

When the core conditions conflict, choose Streamable HTTP unless the user has
an explicit requirement for a separate local adapter. Do not treat "the source
application is a web app" or "development happens locally" as a transport
decision.

## Comparison

| Dimension | stdio | Streamable HTTP |
| --- | --- | --- |
| Location | Usually the user's machine | Remote or centrally hosted |
| Startup | Client launches a subprocess | Service is already running |
| Channel | `stdin` and `stdout` | HTTPS POST, JSON or request-scoped SSE response |
| Framing | One JSON-RPC message per line | HTTP request and response framing |
| Lifecycle | Owned by client process | Owned by service platform |
| MCP auth | No OAuth transport flow | OAuth 2.1 for protected remote servers |
| Credentials | Local environment, keychain, files, OS identity | Bearer access token plus server-side policy |
| Concurrency | Normally one client per process | Multiple clients and users |
| Scaling | One process per connection/session | Horizontal service scaling |
| Network exposure | None by default | Public or private network endpoint |
| Best fit | Local files, CLI, IDE, desktop automation | SaaS, enterprise APIs, shared systems |

## What "Local stdio" Means

The MCP client starts the server:

```text
Doubao Work
  -> launch command
  -> child MCP process
  -> write JSON-RPC to child stdin
  <- read JSON-RPC from child stdout
```

The server executable can be:

- a native binary;
- `node dist/index.js`;
- `python -m package`;
- `java -jar app-mcp.jar`;
- a package runner such as `npx` or `uvx`.

The process is local, but its tools may still call a remote business API. In that case:

```text
Doubao Work -> local stdio MCP -> HTTPS business API
```

The API token belongs to the local MCP process and downstream API boundary. It is not MCP transport OAuth.

## stdio Contract

Require:

- read one UTF-8 JSON-RPC message per line from `stdin`;
- write only valid JSON-RPC messages to `stdout`;
- write logs and diagnostics only to `stderr`;
- never print banners, progress text, or stack traces to `stdout`;
- exit promptly when `stdin` reaches EOF;
- handle cancellation notifications;
- return nonzero exit status for startup configuration failures;
- avoid interactive prompts after process launch.

Treat these as startup inputs:

```text
command
args[]
cwd
env variable names
runtime version
package or binary version
```

Do not put secrets directly in command arguments because process listings and logs may expose them. Prefer the client environment, OS keychain, or a least-privilege local credential helper.

## Streamable HTTP Contract

Require:

- one canonical HTTPS MCP endpoint;
- current Streamable HTTP framing;
- Origin validation;
- OAuth 2.1 protected-resource discovery and bearer validation;
- centralized rate limiting, audit, timeout, and body-size controls;
- explicit protocol-version compatibility;
- stateless request handling where supported by the target revision.

Read `protocol-contract.md` for the full HTTP OAuth contract.

## Shared Core for Dual Transport

Use this structure:

```text
mcp/
  core/
    tools
    schemas
    errors
    business adapters
  transport/
    stdio entry point
    http entry point
  security/
    local credential adapter
    HTTP bearer validator
```

Both transports must expose the same:

- tool names and descriptions;
- input and output schemas;
- pagination behavior;
- domain errors;
- product authorization rules;
- confirmation and idempotency semantics.

Transport-specific code may differ only for:

- startup and shutdown;
- framing and request metadata;
- authenticated-principal construction;
- cancellation wiring;
- transport observability.

Do not fork tool implementations by transport.

## Security Boundary

stdio is not automatically safe:

- any local process with access to the configured executable or credentials may be relevant to the threat model;
- tool arguments can request destructive local actions;
- environment variables can leak to child processes;
- working-directory changes can alter file access;
- dependency or package-runner supply-chain compromise executes locally.

Apply:

- pinned package or binary versions;
- checksums or signed releases where available;
- least-privilege filesystem and API credentials;
- explicit allowed directories and hosts;
- user confirmation for destructive operations;
- no shell interpolation of model-provided values.

HTTP adds:

- network attackers;
- token theft and replay;
- confused-deputy and audience confusion;
- discovery SSRF;
- DNS rebinding;
- cross-user and cross-tenant isolation.

Apply OAuth and HTTP controls from `protocol-contract.md`.

## Packaging Guidance

For stdio:

- prefer one reproducible install command or signed artifact;
- fail fast with a concise message on `stderr`;
- support `--version`;
- pin runtime and dependency versions;
- document environment variable names without example secrets.

For HTTP:

- publish a stable HTTPS URL;
- separate deployment configuration from user credentials;
- provide health and readiness checks outside the MCP endpoint;
- roll out protocol changes behind compatibility tests.

## Selection Output

Before implementation, record:

```text
Transport: stdio | streamable-http | both
Reason:
Execution location:
Process owner:
Credential source:
Target MCP revisions:
Doubao product and version:
```

Do not default to HTTP merely because the source system is a web application. A local stdio adapter can call its API. Do not default to stdio merely because development is local when production users need centralized access.

## Primary Sources

- MCP transport overview: https://modelcontextprotocol.io/specification/2026-07-28/basic/transports
- MCP stdio transport: https://modelcontextprotocol.io/specification/2026-07-28/basic/transports/stdio
- MCP Streamable HTTP transport: https://modelcontextprotocol.io/specification/2026-07-28/basic/transports/streamable-http
