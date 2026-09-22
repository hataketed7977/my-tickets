# Spring Authorization Server for MCP

Use this reference when a Spring Boot application hosts Spring Authorization
Server for a remote MCP resource. It applies whether the MCP transport uses
Spring AI or the official MCP Java SDK.

Prefer an existing standards-compliant authorization server when it can issue
tokens for the canonical MCP resource. Self-host Spring Authorization Server
only when the product requires it.

When the repository already pins Spring Authorization Server or manages it
through the current Spring Boot dependency set, preserve that choice. Do not
compare releases or upgrade unless a focused compile or contract test proves
incompatibility.

## Ownership Boundaries

Spring Authorization Server owns:

- client registration storage;
- authorization-code and refresh-token lifecycle;
- exact redirect URI and client authentication validation;
- PKCE validation;
- token issuance and revocation;
- JWK publication and signing.

Spring Security Resource Server owns bearer validation for `/mcp`.

Application code owns only:

- compatibility adapters required by the selected client;
- existing-login identity bridging;
- MCP resource and scope mapping;
- product authorization after authentication.

Do not retain custom authorization, token, code, or refresh implementations
beside the framework flow.

Standalone demos that implement `/register`, `/authorize`, `/token`, codes,
refresh rotation, or token storage with controllers and in-memory maps are
useful protocol fixtures, not implementation templates for this route. Reuse
their request/response examples and negative test vectors while leaving those
lifecycles in Spring Authorization Server.

## Required Framework Components

Verify the selected Spring Authorization Server release's official minimal
configuration and provide its required beans:

```text
RegisteredClientRepository
OAuth2AuthorizationService
OAuth2AuthorizationConsentService when required
AuthorizationServerSettings
JWKSource or equivalent signing-key source
JwtEncoder
JwtDecoder with issuer, audience, time, and algorithm validation
```

Use framework JDBC implementations when persistent production state is
required. Start from the schema bundled with the resolved framework version.
In-memory implementations are suitable only for disposable tests or explicitly
ephemeral development.

Signing keys must be stable and rotatable outside disposable tests. Do not
generate a new production key on every process start.

## Authorization-Server Filter Chain

Start from the selected release's official minimal filter-chain configuration.
Where available, apply
`OAuth2AuthorizationServerConfiguration.applyDefaultSecurity(http)` before
adding narrow customizations. For releases using the newer configurer API,
follow that release's official example exactly.

Do not manually recreate the core endpoint matcher and filters.

After the standard chain works, add only:

- the existing-login authentication bridge;
- a browser authentication entry point;
- exact CORS or CSRF exceptions;
- reviewed DCR or RFC 8707 adapters.

Prove discovery and an unauthenticated authorization request before adding the
identity bridge.

## MCP Client Registration

Freeze:

```text
registration mode
client authentication method
exact redirect URIs
authorization grant types
PKCE requirement
allowed scopes
token lifetimes
refresh behavior
canonical MCP resource
```

Public clients must use PKCE S256 and must not receive a client secret.
Confidential clients use only the authentication method documented by the
target client.

### Dynamic Client Registration

Use the framework's registration endpoint when its security profile matches
the target client.

If the target performs unauthenticated RFC 7591 registration but the framework
endpoint requires an initial access token, add one narrow adapter that:

1. validates redirect URIs and supported metadata;
2. rejects unsupported grants, scopes, and authentication methods;
3. creates a framework `RegisteredClient`;
4. saves it through `RegisteredClientRepository`;
5. returns only standard registration metadata.

Advertise the adapter's exact URL as `registration_endpoint`.

Keep a custom registration controller outside a matcher that assumes the
client is already registered. Configure CSRF narrowly for that endpoint; do
not weaken unrelated application routes or add a broad `permitAll` chain.

## MCP Resource Binding

Use one stable external MCP resource URI. Validate it during authorization and
token exchange when the selected profile requires RFC 8707.

Customize MCP access tokens so:

```text
iss = stable authorization-server issuer
aud contains exact canonical MCP resource
sub = stable internal application user ID
scope contains only granted MCP scopes
```

Do not add the MCP audience to every token from a shared authorization server.
Bind it only to MCP clients or validated resource-indicator context.

The resource server must validate issuer, audience, expiry, `nbf`, allowed
algorithm, and required scope independently.

## Existing-Login Identity Bridge

Reuse the product's current authenticated user. Do not give the MCP client the
application session cookie or upstream identity-provider token.

When a custom filter maps an application session into Spring Security:

1. validate the session through the existing service;
2. create an `Authentication` whose name is the stable internal user ID;
3. place the filter after `SecurityContextHolderFilter`;
4. ensure it runs before the authorization endpoint consumes authentication;
5. prove the authorization endpoint observes the principal.

A Spring `Filter` bean may be auto-registered globally by the Servlet
container and separately inserted into a Spring Security chain. When the
filter belongs only to the security chain, disable its global Servlet
registration. Otherwise a `OncePerRequestFilter` may mark the request as
already filtered and skip its intended chain execution.

Use Spring Security's bounded saved-request mechanism to resume the original
authorization request after upstream login. Do not store arbitrary return URLs
in browser-controlled parameters.

## Capability Test Ladder

Do not diagnose the complete browser flow with one large test. Select focused
checks through the last capability required by the task and run them in order:

1. authorization-server context loads with all required beans;
2. metadata exposes issuer, authorization, token, JWK, PKCE, and registration
   fields required by the target;
3. DCR accepts a valid profile and rejects invalid redirect URIs and metadata;
4. an anonymous authorization request reaches the authorization endpoint and
   redirects to login;
5. an existing authenticated application session is visible to the
   authorization endpoint;
6. authorization returns a code with matching `state`;
7. PKCE exchange returns an MCP-audience token;
8. missing PKCE, redirect mismatch, resource mismatch, and code replay fail;
9. the issued token calls one protected MCP tool;
10. signed-out upstream login resumes the same authorization transaction.

Checks 5 and 10 apply only when existing-login identity integration is in
scope. A protocol-only authorization-server task may stop after the issued
token calls the protected tool. Real identity-provider and target-client
validation belong to explicit integration or final release scope.

Do not rerun step 9 to diagnose a failure in steps 2-7.

Construct authorization GET requests with a real encoded query string. Some
test helpers populate a parameter map without setting the raw query string
read by authorization-server validators.

## Diagnostic Order

For an unexpected authorization response, inspect in this order:

```text
request method and raw query
selected SecurityFilterChain
authorization endpoint filter presence
required framework beans
registered client and exact redirect URI
current Authentication at the endpoint
PKCE and resource values
consent and saved-request state
```

Use one targeted filter-chain dump when ordering is uncertain. Do not move the
bridge among multiple filters speculatively.

For a custom registration `403`, inspect endpoint matcher, authorization rule,
and CSRF ownership before changing client metadata.

## Completion Evidence

Record:

- Spring Boot and Spring Authorization Server versions;
- issuer and canonical MCP resource;
- registration mode and client authentication profile;
- persistence and signing-key strategy;
- identity bridge and filter position when identity integration was in scope;
- focused test-ladder results through the requested capability;
- negative PKCE, redirect, resource, replay, and audience tests;
- signed-out resume-flow result when identity integration was in scope;
- target-client result only when client compatibility was in scope.

Do not claim production readiness from successful code exchange alone.

## Primary Sources

- Spring Authorization Server getting started:
  https://docs.spring.io/spring-authorization-server/reference/getting-started.html
- Spring Authorization Server configuration model:
  https://docs.spring.io/spring-authorization-server/reference/configuration-model.html
- Spring Security OAuth2 Resource Server:
  https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/index.html
