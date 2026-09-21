# Doubao and Volcano Ark Integration

Use current official documentation for the exact Doubao surface named by the user. "Doubao" can refer to different products with different MCP and credential capabilities.

This reference separates Doubao Work's local stdio and remote HTTP paths, then records the verified Volcano Ark Managed Agents path as of 2026-09-21. Do not assume Doubao Work, Responses API, AgentKit, and Managed Agents share configuration fields or OAuth behavior.

## Confirm the Target Surface

Identify one:

- Volcano Ark Managed Agents;
- Volcano Ark Responses API;
- AgentKit MCP gateway;
- Doubao Work desktop;
- another host using a Doubao model.

If the repository or user does not identify the surface, ask whether the process will run locally under Doubao Work or at a network URL. Do not select the transport from the source application's framework.

## Doubao Work Transport Paths

### Local stdio

Use stdio when Doubao Work launches the MCP server on the same machine:

```text
Doubao Work -> command + args -> local MCP subprocess
             stdin -------> JSON-RPC
             stdout <------ JSON-RPC
             stderr <------ logs
```

Provide:

- a deterministic executable command and argument list;
- required runtime and package version;
- working-directory requirements;
- environment variable names;
- local install and upgrade instructions;
- least-privilege filesystem and network access.

Do not configure MCP OAuth for this channel. A token used by the local process to call a remote product API is a downstream credential.

### Remote Streamable HTTP

Use HTTP when Doubao Work connects to a deployed MCP URL:

```text
Doubao Work -> HTTPS /mcp -> shared MCP service -> product services
```

Provide:

- canonical HTTPS URL;
- OAuth 2.1 discovery and token contract when authentication is required;
- per-user or service credential setup supported by the exact Doubao surface;
- allowed tool set and confirmation policy;
- deployment, audit, rate limiting, and tenant isolation.

### Dual transport

Support both only through shared tools and separate launch adapters:

```text
                         -> stdio adapter
shared tool definitions
                         -> HTTP adapter + OAuth
```

Tool names, schemas, errors, and business authorization must remain identical. Verify each transport independently in Doubao Work.

## Managed Agents Configuration Model

Managed Agents separates:

- Agent definition: MCP server URL and enabled tool set.
- Session: per-user Vault IDs.
- Vault Credential: token material associated with the exact MCP server URL.

Declare the server and its toolset together:

```json
{
  "mcp_servers": [
    {
      "type": "url",
      "name": "tickets",
      "url": "https://mcp.example.com/mcp"
    }
  ],
  "tools": [
    {
      "type": "mcp_toolset",
      "mcp_server_name": "tickets",
      "default_config": {
        "enabled": false
      },
      "configs": [
        {"name": "tickets_search_tickets", "enabled": true},
        {"name": "tickets_get_ticket", "enabled": true}
      ]
    }
  ]
}
```

Use an allowlist, especially for weaker models. Enable write tools only after read-only evaluation passes.

Create a Session with the current user's Vault:

```json
{
  "agent": "agent-...",
  "environment_id": "env-...",
  "vault_ids": ["vlt-..."]
}
```

Keep the application-user to `vault_id` mapping in the application backend. Do not put user tokens in the Agent definition.

## Vault MCP OAuth Credential

Official Managed Agents documentation defines `mcp_oauth` credentials with an access token and optional refresh configuration:

```json
{
  "display_name": "Alice Tickets",
  "auth": {
    "type": "mcp_oauth",
    "mcp_server_url": "https://mcp.example.com/mcp",
    "access_token": "REDACTED",
    "expires_at": "2026-09-21T12:00:00Z",
    "refresh": {
      "token_endpoint": "https://auth.example.com/oauth/token",
      "client_id": "doubao-managed-agent",
      "scope": "tickets:read",
      "refresh_token": "REDACTED",
      "token_endpoint_auth": {
        "type": "none"
      }
    }
  }
}
```

Supported documented token endpoint authentication values are:

- `none` for a public client;
- `client_secret_basic`;
- `client_secret_post`.

Use `none` with PKCE for public clients. Use a client secret only when the credential exchange is performed by a confidential backend and the secret is stored in the Vault.

Important:

- The documented Vault flow accepts already obtained token material and refreshes it.
- Do not assume the platform initiates MCP OAuth discovery or an interactive authorization-code flow unless the selected product's current docs or a live test confirm it.
- Implement the user authorization callback in the product backend when needed, then write the resulting credential to that user's Vault.
- Treat token, refresh token, and client secret fields as write-only secrets.

## Exact URL Matching

Managed Agents matches a credential to an MCP server by `mcp_server_url`. Use one canonical value everywhere:

```text
https://mcp.example.com/mcp
```

Test these failure cases:

- different path;
- added or removed trailing slash;
- HTTP instead of HTTPS;
- old hostname after migration;
- credential stored for a staging URL.

Do not solve mismatches by accepting multiple token audiences. Migrate configuration and credentials to the canonical URI.

## Per-User OAuth Bootstrap

When the product needs user-delegated access:

1. User starts "Connect to Doubao" in the product.
2. Product backend creates `state`, PKCE verifier, and authorization transaction.
3. Browser goes to the authorization server with exact `client_id`, `redirect_uri`, `scope`, and MCP `resource`.
4. Backend validates `state`, `iss`, and callback binding.
5. Backend exchanges the code with the PKCE verifier and `resource`.
6. Backend creates or locates the user's Vault.
7. Backend writes an `mcp_oauth` credential.
8. Product creates the Managed Agent Session with that `vault_id`.

Do not expose access or refresh tokens to the browser after the callback.

## Tool Controls

For initial rollout:

- set `default_config.enabled` to `false`;
- enable only evaluated read tools;
- keep destructive tools disabled;
- use the platform's confirmation or `always_ask` policy for high-risk tools when supported;
- bind every write to server-side product authorization regardless of client policy.

Client confirmation improves safety but does not replace server authorization.

## Compatibility Test

Run common tests with the exact Doubao model and product:

1. one enabled read tool is discovered and selected;
2. a disabled tool cannot be called;
3. one multi-step prompt succeeds on the least capable target model;
4. mutation confirmation is enforced;
5. audit records identify the user, tool, and result.

For stdio also verify:

1. Doubao Work launches the configured command;
2. no non-JSON output reaches `stdout`;
3. environment variables arrive without appearing in logs;
4. local file and network boundaries are enforced;
5. closing the client stops the child process.

For HTTP also verify:

1. anonymous request receives the expected `401`;
2. the platform token reaches the MCP endpoint as Bearer authentication;
3. resource audience is accepted;
4. expired access token refreshes through the documented credential path;
5. insufficient scope produces a recoverable error.

Record the model ID, product surface, date, protocol version, and enabled tool list. Compatibility is an observed result, not an inference from general MCP support.

## Official Sources

- Managed Agents MCP configuration: https://docs.volcengine.com/docs/82379/2553718?lang=zh
- Managed Agents Vault authentication: https://docs.volcengine.com/docs/ark/use-vaults-authentication?lang=zh
- AgentKit MCP service invocation: https://www.volcengine.com/docs/86681/2222941?lang=zh
- AgentKit Studio HTTP and stdio support: https://docs.volcengine.com/docs/86681/2630032?lang=zh
- Volcano Ark documentation root: https://www.volcengine.com/docs/82379?lang=zh
- MCP stdio transport: https://modelcontextprotocol.io/specification/2026-07-28/basic/transports/stdio
