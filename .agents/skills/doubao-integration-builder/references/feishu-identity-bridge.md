# Feishu Identity Bridge

Use this reference when an MCP integration must reuse an existing Feishu or
Lark login. It is framework-neutral. Combine it with the framework-specific
reference selected by the main skill.

Feishu is the upstream identity provider in this pattern. It authenticates the
person and returns identity attributes to the application. A maintained OAuth
authorization server issues the separate MCP access token.

Do not:

- give the MCP client a Feishu user access token;
- use the application's web session cookie as an MCP bearer token;
- implement MCP authorization inside a tool call;
- assume `open_id`, `union_id`, and `user_id` are interchangeable;
- identify or link accounts by mutable profile fields such as name or avatar;
- auto-link accounts by email unless the product has a separately reviewed
  account-linking policy.

## Architecture

```text
MCP client
  -> MCP authorization server
       -> user is not authenticated
       -> application starts Feishu login
            -> Feishu authorization
            -> application callback
            -> Feishu code exchange and user-info lookup
            -> internal user mapping
            -> application principal/session
       -> resume MCP authorization transaction
       -> issue MCP-audience access token
  -> MCP resource server
  -> tool uses verified internal user ID
```

Keep these identities and credentials separate:

| Item | Owner and purpose |
| --- | --- |
| Feishu authorization code | One-time input to the application callback |
| Feishu user access token | Server-side calls to Feishu APIs only |
| Application session | Browser login continuity for the product |
| MCP authorization code | One-time input to the MCP client's token exchange |
| MCP access token | Bearer credential for the canonical MCP resource only |
| Internal user ID | Stable application identity used as MCP token subject |

## Evidence Pass

Inspect only the existing login boundary before designing the bridge:

```bash
rg -n "feishu|lark|open_id|union_id|user_id|user_access_token" .
rg -n "callback|redirect_uri|state|session|cookie|SameSite|logout" .
rg -n "currentUser|current_user|principal|Authentication|user_id" .
```

Record:

```text
Feishu application type:
Authorization and user-info API generation:
Configured callback allowlist:
Existing callback route:
Existing session store and TTL:
Session cookie attributes:
External identity key and its documented scope:
Internal user primary key:
Tenant identifier when applicable:
Current-user service method:
Logout and session revocation behavior:
```

Use the existing login and user repository when they already satisfy these
requirements. Do not create a second Feishu integration solely for MCP.
When login, callback, user mapping, and session behavior already have tests,
treat them as established behavior. Do not reimplement or broadly retest the
provider integration merely because an MCP identity bridge is being added.

## Two Independent Transactions

There are two browser authorization transactions. Never reuse one state value
as the other.

### MCP authorization transaction

Owned by the MCP authorization server:

```text
mcp_transaction_id
oauth_state
client_id
exact_redirect_uri
resource
requested_scopes
pkce_challenge
expires_at
browser_binding
```

### Feishu login transaction

Owned by the application's login adapter:

```text
feishu_login_id
feishu_state
mcp_transaction_id
exact_feishu_callback_uri
expires_at
browser_binding
```

Store both transactions server-side with short TTLs and single-use
consumption. The browser should carry only opaque random identifiers. Do not
put a raw OAuth authorization URL, arbitrary return URL, access token, or
authorization code in a cookie.

Choose the random identifier encoding before defining the database schema.
Derive the column size from the maximum encoded length and add a boundary
test. For example, concatenated UUID strings are longer than one UUID and must
not be stored in a column sized for a single UUID.

After the Feishu callback:

1. validate the Feishu state and browser binding;
2. atomically consume the Feishu login transaction;
3. exchange the Feishu code on the server;
4. fetch the minimum required identity attributes;
5. map the external identity to an internal user;
6. rotate or establish the application session;
7. resume only the bound MCP authorization transaction.

Reject missing, expired, replayed, or mismatched transactions. Do not fall
back to an unbound callback mode when state is absent.

## Identity Mapping

Use an internal immutable user ID as the MCP JWT `sub`.

Persist an explicit external identity binding:

```text
provider = feishu
provider_environment
provider_tenant
external_id_type
external_id
internal_user_id
created_at
last_verified_at
```

Choose the Feishu external identifier only after confirming its scope for the
application type and deployment. Include the provider tenant or application
boundary in the unique key whenever the identifier is not globally scoped.

Treat display name, avatar, email, and phone as profile attributes, not primary
identity keys. Update profile fields without changing the internal user ID.
Require an explicit, audited flow for account merge or relinking.

## Framework-Neutral Ports

Keep provider code behind a small adapter:

```text
FeishuIdentityProvider
  build_authorization_uri(login_transaction)
  exchange_code(code)
  fetch_identity(user_access_token)

ExternalIdentityRepository
  find_binding(provider, tenant, id_type, external_id)
  create_binding(external_identity, internal_user_id)
  update_profile(internal_user_id, profile)

ApplicationLogin
  establish_session(internal_user_id)
  resolve_principal(session)
  revoke_sessions(internal_user_id)

AuthorizationResume
  resume(mcp_transaction_id, internal_user_id)
```

The framework adapter may represent the principal as a Spring
`Authentication`, a Node request context, a Python dependency, or another
trusted server-side type. Tool handlers should receive only the normalized
internal principal, not Feishu response objects.

## Token Boundaries

The Feishu user access token:

- stays on the application server;
- is used only for approved Feishu APIs;
- is stored encrypted only when later Feishu API delegation is required;
- is otherwise discarded after obtaining identity;
- never appears in cookies, URLs, logs, MCP results, model context, or MCP
  token claims.

The MCP access token:

- has the MCP authorization server as issuer;
- has the canonical MCP resource as audience;
- uses the internal user ID as subject;
- carries only MCP scopes;
- is validated before MCP protocol dispatch;
- is never forwarded to Feishu.

Feishu scopes and MCP scopes are separate namespaces. A Feishu permission does
not grant a product or MCP operation.

## Security Defaults

Require:

- exact Feishu callback URLs registered in the developer console;
- standard URI builders and encoding instead of query-string concatenation;
- HTTPS outside loopback development;
- random, short-lived, single-use state;
- server-side secrets from a secret manager;
- `HttpOnly`, `Secure`, and deliberate `SameSite` cookies;
- session rotation after login;
- bounded login attempts and provider-call timeouts;
- sanitized provider errors;
- tenant-aware identity mapping;
- server-side product authorization after authentication.

Do not log:

- application secrets;
- Feishu or MCP access and refresh tokens;
- authorization codes;
- full callback URLs containing codes;
- session cookie values;
- raw user-info responses.

## Bridge Implementation

When the product already has working Feishu login:

1. resolve the current application session through the existing login service;
2. map the resolved internal user to the authorization server's trusted
   principal type;
3. insert the bridge at the framework-defined point before the authorization
   endpoint consumes authentication;
4. preserve and resume the original authorization request when login is
   required;
5. leave provider exchange, profile mapping, session persistence, logout, and
   existing callback behavior unchanged unless a focused bridge test proves a
   concrete incompatibility.

Do not replay completed MCP transport or authorization-server work while adding
the bridge. Do not connect a product client as part of bridge implementation
unless client integration is explicitly in scope.

## Focused Bridge Tests

Select tests for behavior changed by the bridge:

- an existing valid application session becomes the expected internal
  authorization principal;
- an absent, expired, or revoked session does not authenticate;
- a signed-out authorization request enters the existing login path and resumes
  the same bounded authorization transaction;
- the resulting MCP token subject equals the mapped internal user ID;
- a Feishu access token is rejected at the MCP resource endpoint.

Use simulated provider responses or an existing application session fixture.
Do not call the real Feishu service or manually inspect session tables for
routine bridge verification.

Only when the task changes provider login or identity mapping itself, add the
relevant provider-level tests for state, callback, account binding, profile
updates, tenant isolation, and logout.

## Common Mistakes

| Mistake | Corrective rule |
| --- | --- |
| Reuse one `state` for Feishu and MCP OAuth | Keep two typed, independently consumed transactions |
| Store the original authorization URL in a cookie | Store a server-side transaction and send only an opaque ID |
| Use `open_id` directly as MCP `sub` | Map to an internal immutable user ID |
| Treat login as product authorization | Enforce MCP scope and object permission separately |
| Return Feishu tokens to the MCP client | Issue a separate MCP-audience token |
| Fetch Feishu identity inside every tool | Resolve identity once during login and use the internal principal |
| Manually concatenate authorization URLs | Use a URI builder and exact configured callbacks |
| Support a callback without validated state | Reject it; use a separate explicit flow when another client type is required |

## Freshness and Sources

Provider endpoints, token request formats, scopes, and identity-field semantics
change independently of MCP. Check current official documentation only when
implementing or changing provider calls. A bridge that reuses an established
provider adapter should not research provider APIs again. When provider code
does change, record the verification date and do not copy an endpoint merely
because it worked for an earlier API generation.

Primary sources:

- Feishu browser application authorization guide:
  https://open.feishu.cn/document/common-capabilities/sso/web-application-end-user-consent/guide
- Feishu authorization-code endpoint:
  https://open.feishu.cn/document/authentication-management/access-token/obtain-oauth-code
- Lark authorization-code endpoint:
  https://open.larkoffice.com/document/authentication-management/access-token/obtain-oauth-code
