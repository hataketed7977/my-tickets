# Verification and Release Gate

Use a RED-GREEN-REFACTOR loop for each vertical slice. Add a failing focused test, implement the smallest behavior, then refactor while keeping the focused test green.

## Test Layers

### Unit

Test:

- scope-to-tool mapping;
- principal mapping;
- error envelope;
- pagination and output limits;
- confirmation and idempotency logic.

For HTTP also test canonical resource URI construction, audience validation, and scope parsing.

### HTTP Fixed Gate Order

Keep failures attributable to one layer:

1. compile the selected SDK and security dependencies;
2. test protected-resource metadata and the anonymous `401` challenge;
3. use a test-only signed token or disposable issuer to prove token validation,
   MCP initialization, tool listing, and one tool call;
4. integrate authorization-server discovery, PKCE, resource binding, and code
   exchange;
5. integrate the existing login identity;
6. run the exact Doubao client and model smoke test.

Do not start browser OAuth debugging before the protected tool call works with
a test fixture. Do not start target-client debugging before the complete OAuth
integration test works without that client.

Use this execution cadence:

- run the existing suite once as a baseline when the branch is not already
  known green;
- run only the focused gate tests while implementing;
- finish all planned focused cases before running the full suite;
- run the full suite once and the production build once at the end;
- rerun either only when code changes after that gate.

### stdio Process Contract

Launch the real server command as a subprocess and assert:

| Case | Expected |
| --- | --- |
| Valid request on `stdin` | One valid JSON-RPC response line on `stdout` |
| Startup log | Appears only on `stderr` |
| Missing required environment | Nonzero exit and safe `stderr` message |
| Extra newline or banner | Never emitted on `stdout` |
| Client closes `stdin` | Process exits promptly |
| Cancellation notification | Work stops and no later response is emitted |
| Unexpected process exit | Client can restart without persistent protocol state |

Run the test from a directory different from the source root to catch hidden working-directory assumptions. Test paths and arguments containing spaces.

### HTTP Contract

Assert exact status, headers, content type, and JSON:

| Case | Expected |
| --- | --- |
| Missing bearer token | `401` and Bearer `resource_metadata` |
| Invalid token | `401`, no implementation details |
| Valid token, missing scope | `403` and `insufficient_scope` |
| Invalid Origin | `403` |
| PRM request | `200 application/json` |
| Wrong protocol version | defined compatibility error |
| Oversized body | `413` or configured rejection |
| Valid protected request | MCP response |

### Streamable HTTP Compatibility

Derive the test sequence from the installed SDK and negotiated MCP revision.
For stateful `2025-06-18` implementations:

1. POST `initialize` with both `application/json` and `text/event-stream` in
   `Accept`;
2. capture `Mcp-Session-Id`;
3. POST `notifications/initialized` with that session ID;
4. POST `tools/list`;
5. POST one `tools/call`.

Do not use a direct `tools/list` request as the positive health check for a
stateful server. It proves neither initialization nor session propagation.

Assert response framing as well as status:

- initialization may return `application/json`;
- tool responses may return `text/event-stream`;
- parse the SSE `data:` field instead of treating the whole body as JSON;
- decode response bytes as UTF-8 before asserting localized text;
- verify the explicit MCP tool name, not the Java or TypeScript method name.

For stateless `2026-07-28`, do not require a legacy session header merely
because an older recipe used one. Keep one test case per supported revision
and pin the SDK client version used by each case.

### HTTP Token Validation

Generate test tokens or introspection fixtures for:

- correct issuer, audience, expiry, and scope;
- wrong issuer;
- wrong audience;
- expired token;
- future `nbf`;
- missing scope;
- malformed bearer value;
- unsupported signature algorithm;
- unknown key ID and JWKS refresh;
- cross-tenant subject.

At least one test must prove that a valid token for a different API is rejected.

### HTTP OAuth Flow

In a disposable authorization-server environment, test:

- metadata discovery;
- authorization code with PKCE S256;
- missing or plain PKCE rejection;
- exact redirect URI validation;
- missing `resource` rejection;
- resource mismatch rejection;
- `state` mismatch rejection;
- `iss` mismatch rejection;
- authorization-code replay rejection;
- refresh rotation or documented refresh behavior.

Do not run browser OAuth tests against production.

### Tool Behavior

For every tool:

- valid input;
- invalid and extra fields;
- product permission denial;
- empty result;
- dependency timeout;
- output size boundary;
- annotations and scope mapping.

For writes also test:

- missing confirmation;
- stale expected version;
- duplicate idempotency key;
- audit record;
- retry classification.

### Model Evaluation

Run the cases from `tool-design-for-reliable-model-use.md` with the least capable supported model. Store prompts, expected tool sequence, actual tool calls, and pass/fail result without secrets or personal data.

## Bundled HTTP Probe

The probe performs read-only unauthenticated HTTP discovery. Do not use it for stdio:

```bash
python3 scripts/verify_remote_mcp.py \
  --acknowledge-authorized-target \
  https://mcp.example.com/mcp
```

For local HTTP:

```bash
python3 scripts/verify_remote_mcp.py \
  --acknowledge-authorized-target \
  --allow-http \
  --allow-private-network \
  http://127.0.0.1:3000/mcp
```

Optional checks:

```bash
python3 scripts/verify_remote_mcp.py \
  --acknowledge-authorized-target \
  --expected-issuer https://auth.example.com \
  --json \
  https://mcp.example.com/mcp
```

The probe checks:

- anonymous MCP request returns `401`;
- Bearer challenge and `resource_metadata`;
- RFC 9728 metadata;
- resource and authorization-server URLs;
- authorization-server metadata discovery;
- exact issuer match;
- authorization-code and PKCE S256 advertisement;
- available registration mechanism.

It does not:

- complete browser authorization;
- mint or refresh tokens;
- verify token audience enforcement;
- call business tools;
- prove Doubao compatibility;
- replace integration or security tests.

Exit codes:

- `0`: all required discovery checks passed; warnings may remain;
- `1`: one or more checks failed;
- `2`: the probe could not run or finish because of invalid arguments, an unsafe target, a network failure, or unusable discovery metadata.

Use only against systems the user owns or is explicitly authorized to test. The script rejects private or local addresses unless `--allow-private-network` is supplied.

## MCP Client Tests

Use the official MCP Inspector or SDK test client for the protocol revisions in the implementation contract. Verify:

- tool listing;
- structured schemas;
- valid call;
- cancellation;
- clean newline-delimited messages and process shutdown for stdio;
- JSON and SSE responses for HTTP when supported;
- protocol revision negotiation;
- legacy compatibility only when required.

Pin test-client versions in CI. A moving `latest` client is useful for an upgrade job, not the only release gate.

## Security Review

Before production:

- inspect the complete auth and tool diff;
- confirm no secrets or tokens appear in code, fixtures, snapshots, or logs;
- for stdio, confirm `stdout` contains only MCP messages;
- for stdio, confirm model-provided values never reach a shell interpreter;
- for stdio, confirm command, package version, filesystem roots, and outbound hosts are constrained;
- for HTTP, confirm redirect URI equality;
- for HTTP, confirm issuer and audience checks are independent;
- for HTTP, confirm discovery fetches cannot reach unintended networks;
- for HTTP, confirm rate limits exist on OAuth and MCP endpoints;
- for HTTP, confirm CORS does not replace Origin validation;
- confirm object and tenant authorization use verified principal context;
- confirm downstream credentials are separate;
- confirm write tools enforce confirmation and idempotency.

## Production Smoke Test

For stdio:

1. install the exact packaged command on a clean test machine or account;
2. configure it in Doubao Work without secrets in arguments;
3. list allowlisted tools;
4. perform one read and one denied operation;
5. close Doubao Work and confirm the child process exits;
6. inspect `stderr` and verify `stdout` remained protocol-only.

For HTTP, use a dedicated test user and least-privilege scopes:

1. connect the documented Doubao surface;
2. complete or inject credentials through its documented path;
3. list allowlisted tools;
4. perform one read;
5. verify a forbidden read fails;
6. verify an expired token refreshes if configured;
7. perform one reversible write with confirmation;
8. confirm audit and metrics;
9. revoke the credential;
10. confirm subsequent access fails.

Remove test data and revoke tokens after the smoke test.

## Completion Evidence

Record:

```text
Focused tests:
  <absolute working directory>
  <exact command>
  <pass/fail and count>

Protocol probe:
  <exact command without secrets>
  <PASS/WARN/FAIL summary>

stdio subprocess test:
  <exact command>
  <stdout cleanliness and shutdown result>

Model evaluation:
  <product and model ID>
  <tool list>
  <pass count>

Residual risks:
  <explicit list or "none identified">
```

Do not claim:

- OAuth conformance from a successful login alone;
- authorization from authentication alone;
- model compatibility from tool discovery alone;
- production readiness without a negative authorization test.
