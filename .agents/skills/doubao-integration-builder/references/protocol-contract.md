# Streamable HTTP OAuth 2.1 Contract

Use this only for remote Streamable HTTP. It targets MCP `2026-07-28` and records compatibility requirements for clients on earlier revisions.

Do not apply this OAuth transport flow to stdio. For stdio, the client launches a local subprocess and credentials are supplied through the local execution boundary or used only for downstream APIs; read `transport-selection.md`.

This document is a protocol acceptance contract, not an implementation recipe
for custom OAuth endpoints. Delegate client registration, authorization-code
issuance, PKCE enforcement, token issuance, refresh, revocation, and signing
key management to an existing, managed, or framework-provided authorization
server. Implement only protected-resource metadata, bearer validation, scope
enforcement, and product authorization in the MCP server.

Use a custom authorization facade only for an explicitly disposable prototype
or a separately reviewed compatibility exception. Passing the checks below is
necessary but does not by itself make a custom authorization server
production-ready.

## Roles and Trust Boundaries

Keep these roles explicit:

- MCP client: OAuth public or confidential client.
- MCP server: OAuth protected resource and business-policy enforcement point.
- Authorization server: authenticates the user, obtains consent, and issues tokens.
- Downstream API: a separate protected resource with separate credentials.

The MCP server may share a deployment with the authorization server, but the roles and token audiences remain distinct.

## Minimal HTTP Surface

Select only endpoints required by the chosen identity and registration modes:

| Endpoint | Owner |
| --- | --- |
| `GET /.well-known/oauth-protected-resource/...` | MCP resource server |
| `GET /.well-known/oauth-authorization-server...` | Authorization server |
| Client registration endpoint | Authorization server or a narrow compatibility adapter |
| Authorization endpoint | Authorization server |
| Token endpoint | Authorization server |
| Existing identity-provider callback | Existing login integration, when required |
| MCP endpoint | MCP transport |

Use framework-provided endpoint paths when available and advertise their exact
URLs. Do not create duplicate aliases merely to match a sample. The identity
provider callback is not part of MCP OAuth itself and is unnecessary when the
authorization server already has an authenticated user.

## Canonical Resource URI

Choose one stable HTTPS URI, normally the exact MCP endpoint:

```text
https://mcp.example.com/mcp
```

Use the same value in:

- Protected Resource Metadata `resource`;
- OAuth authorization request `resource`;
- OAuth token request `resource`;
- access-token `aud` or introspection audience;
- resource-server validation;
- Doubao MCP server and credential configuration.

Do not silently alternate between origin-only and path-specific forms. Document the trailing-slash policy and test exact matching.

## Protected Resource Metadata

Serve RFC 9728 metadata through at least one required discovery mechanism and preferably both:

```http
HTTP/1.1 401 Unauthorized
WWW-Authenticate: Bearer resource_metadata="https://mcp.example.com/.well-known/oauth-protected-resource/mcp", scope="tickets:read"
```

For an MCP endpoint at `/mcp`, support:

```text
https://mcp.example.com/.well-known/oauth-protected-resource/mcp
```

Supporting the root fallback improves compatibility:

```text
https://mcp.example.com/.well-known/oauth-protected-resource
```

Return JSON:

```json
{
  "resource": "https://mcp.example.com/mcp",
  "authorization_servers": ["https://auth.example.com"],
  "scopes_supported": ["tickets:read", "tickets:write"]
}
```

Requirements:

- `authorization_servers` contains at least one absolute HTTPS issuer.
- `resource` identifies this MCP server, not the downstream API.
- Metadata endpoints require no bearer token.
- Responses use `application/json` and do not expose secrets.
- Cache headers reflect how quickly issuer or scope changes must propagate.

## Authorization Server Metadata

The authorization server must expose RFC 8414 or OIDC discovery metadata. With issuer `https://auth.example.com/tenant`, clients probe in this order:

1. `https://auth.example.com/.well-known/oauth-authorization-server/tenant`
2. `https://auth.example.com/.well-known/openid-configuration/tenant`
3. `https://auth.example.com/tenant/.well-known/openid-configuration`

The metadata `issuer` must exactly match the issuer used to build the URL.

Require or verify:

- `authorization_endpoint`;
- `token_endpoint`;
- `response_types_supported` includes `code`;
- `grant_types_supported` includes `authorization_code` when declared;
- `code_challenge_methods_supported` includes `S256`;
- a supported client registration mode;
- `authorization_response_iss_parameter_supported: true` when `iss` is emitted.

## Client Registration

When a target client is explicitly in scope, use its behavior already recorded
in the selected integration reference. Do not research it again unless current
documentation or a focused compatibility failure conflicts with that record.
Client-specific requirements override the generic preference order below.

The currently documented Doubao Work MCP OAuth 2.1 connector performs Dynamic
Client Registration and does not ask the user to configure a client ID;
advertise and implement `registration_endpoint` for that surface.

When target-client integration is outside scope, implement the registration
mode frozen in the task contract and verify it with an automated client
fixture. Do not launch a product client merely to choose a registration mode.

Select in this order:

1. Pre-register a known client when the MCP client and authorization server have an operational relationship.
2. Use Client ID Metadata Documents when both sides support them.
3. Use Dynamic Client Registration only for backward compatibility.
4. Require explicit client credentials if none of the above works.

For Client ID Metadata Documents:

- use an HTTPS URL with a path as `client_id`;
- make the document's `client_id` exactly equal its URL;
- include `client_name` and exact `redirect_uris`;
- validate fetched documents against SSRF and redirect attacks;
- advertise `client_id_metadata_document_supported: true`.

For DCR:

- treat it as deprecated, not the default;
- require exact redirect URI matching;
- use `application_type: native` for desktop, CLI, and loopback clients;
- bind stored registration to the authorization server `issuer`;
- rate-limit and audit registration.

## Authorization Code Flow

Require:

- authorization code flow;
- PKCE with `S256`;
- exact redirect URI matching;
- cryptographically random, single-use `state`;
- short-lived, single-use authorization codes;
- `resource` in both authorization and token requests;
- least-privilege scopes;
- TLS for non-loopback endpoints.

Do not support implicit grant or resource-owner password credentials.

When the authorization server advertises `authorization_response_iss_parameter_supported: true`, include `iss` in success and error redirects. Clients must compare a received `iss` to the recorded issuer using exact string comparison before exchanging the code.

## Access Tokens

Prefer short-lived JWT access tokens when local validation is practical. Opaque tokens with introspection are valid when revocation latency or central policy requires them.

Validate before MCP dispatch:

- signature or active introspection result;
- exact trusted issuer;
- audience includes the canonical MCP resource;
- expiry and not-before with a small documented clock skew;
- required scope for the selected tool;
- authorized subject and tenant boundary;
- token type and algorithm allowlist.

Never:

- accept ID tokens as access tokens;
- decode a JWT without verifying it;
- accept `alg: none`;
- infer authorization from a model-provided user ID;
- forward the MCP bearer token to a downstream API;
- accept a token issued only for a downstream API.

Map the verified token subject to the product's existing user identity. Enforce object-level access in the existing service layer as well as scope checks at the MCP boundary.

## Refresh Tokens

If refresh tokens are issued:

- bind them to the client and authorization server;
- use rotation or sender-constraining where supported;
- reject replay of rotated tokens;
- preserve or reduce the original scope;
- allow revocation;
- never log or return them from MCP tools.

The MCP resource server normally never receives refresh tokens.

## Error Contract

Use transport-level status codes for authentication:

- missing or invalid bearer token: `401` with a valid Bearer challenge;
- authenticated token lacking scope: `403` with `error="insufficient_scope"` and required scope;
- invalid origin: `403`;
- malformed MCP request: `400`;
- unsupported protocol version: protocol-defined error response.

Do not return login HTML from `/mcp`. Keep OAuth browser pages on authorization-server endpoints.

## Streamable HTTP

For current MCP:

- expose one POST endpoint;
- accept `application/json` and request-scoped `text/event-stream`;
- validate `Origin` when present;
- require the current protocol metadata and headers;
- do not rely on server affinity or protocol sessions for `2026-07-28`;
- disable proxy buffering for SSE responses;
- apply request timeouts, body limits, and rate limits.

Use an official SDK implementation rather than hand-writing JSON-RPC framing.

## Backward Compatibility

Do not guess client support. Record and test each target client's protocol revision.

| Concern | 2026-07-28 | 2025-era clients |
| --- | --- | --- |
| Core state | Stateless per request | Initialize/session behavior may be expected |
| HTTP endpoint | POST; no legacy GET stream | Streamable HTTP may use session and GET |
| Request routing | Protocol metadata plus required headers | Headers may be absent |
| Registration | CIMD preferred; DCR fallback | DCR often expected |
| OAuth discovery | RFC 9728 PRM | Some clients need root fallback or explicit config |
| Authorization | PKCE, resource indicator, audience binding | Still required for secure interoperability |

Prefer an SDK with explicit backward-compatibility support. Do not recreate both protocol eras in application code. Keep legacy behavior behind a compatibility option and schedule its removal.

## Threat Checklist

Block release on any failure:

- token audience confusion or token passthrough;
- redirect URI wildcard or substring matching;
- missing PKCE enforcement;
- missing `resource` enforcement;
- authorization-server mix-up due to unvalidated issuer;
- discovery SSRF;
- DNS rebinding or unvalidated Origin;
- cross-tenant object access;
- tool scope broader than the corresponding product permission;
- secrets in source, logs, tool output, or model context;
- mutation without idempotency or required user confirmation.

## Primary Sources

- MCP Authorization `2026-07-28`: https://modelcontextprotocol.io/specification/2026-07-28/basic/authorization
- Authorization server discovery: https://modelcontextprotocol.io/specification/2026-07-28/basic/authorization/authorization-server-discovery
- Client registration: https://modelcontextprotocol.io/specification/2026-07-28/basic/authorization/client-registration
- Streamable HTTP: https://modelcontextprotocol.io/specification/2026-07-28/basic/transports/streamable-http
- MCP security guidance: https://modelcontextprotocol.io/docs/2026-07-28/tutorials/security/security_best_practices
- OAuth security best current practice: https://www.rfc-editor.org/rfc/rfc9700
- Protected Resource Metadata: https://www.rfc-editor.org/rfc/rfc9728
- Resource Indicators: https://www.rfc-editor.org/rfc/rfc8707
- Authorization Server Issuer Identification: https://www.rfc-editor.org/rfc/rfc9207
