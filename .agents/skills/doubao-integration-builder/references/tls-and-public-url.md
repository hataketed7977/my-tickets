# TLS and Public URL

Use this reference for remote Streamable HTTP when certificate trust, browser
login, reverse proxies, or the externally visible MCP URL are part of the
task. Do not apply it to local stdio transport.

This reference defines ownership and acceptance criteria. Keep platform
commands for Docker, CI systems, operating systems, and cloud vendors in the
target repository or a platform-specific skill.

## Core Principle

Treat certificates as an ingress concern, not business-application state.

Keep these values distinct:

- public URL: the stable HTTPS URL configured in the MCP client;
- TLS termination point: the component presenting the leaf certificate;
- certificate authority: the trust anchor that signs the leaf certificate;
- private-key owner: the platform component permitted to use the key;
- application listener: the internal HTTP or HTTPS address serving the app.

The authorization server, MCP resource server, browser application, and
client must agree on the public URL even when an ingress forwards requests to
an internal listener.

## Environment Decision

Choose the first applicable row:

| Environment | Certificate source | TLS termination | Trust distribution |
| --- | --- | --- | --- |
| One-developer local demo | Developer-owned local CA | Local proxy or application | Explicitly trust the CA on that device |
| Shared development or test | Public CA when DNS permits; otherwise enterprise CA | Shared gateway or ingress | Public trust store or centrally managed enterprise trust |
| Production internet service | Public CA or managed cloud certificate service | Load balancer, API gateway, or ingress | Standard operating-system and browser trust stores |
| Production private service | Enterprise CA or managed private CA | Private gateway, service mesh, or ingress | Organization-managed device and workload trust |

Do not use a self-signed leaf certificate for a shared or production service.
A locally trusted development CA is different from an untrusted self-signed
leaf: the CA is installed once as a trust anchor and signs hostname-bound leaf
certificates.

## Local Development

Use a maintained local-CA tool or an organization-provided development CA.
Generate leaf certificates for the exact hostnames used by clients.

Require:

- Subject Alternative Names for every actual hostname or IP address;
- one canonical hostname used consistently by Web, API, OAuth, and MCP;
- CA and leaf private keys outside source control;
- explicit user action to trust the development CA;
- a documented way to remove that trust;
- finite certificate validity and repeatable regeneration;
- client restart when an Electron or Chromium process caches certificate
  state.

Prefer one local HTTPS ingress when browser login is involved:

```text
https://localhost
  /              -> Web development server
  /api/**        -> application API
  /mcp           -> MCP server
  /.well-known/* -> OAuth and MCP discovery
  /authorize     -> authorization server
  /token         -> authorization server
```

A split-port topology can work when every browser-facing URL uses HTTPS, but
it retains CORS, cookie, and configuration complexity. Do not mix an HTTP Web
origin with an HTTPS API when authentication depends on a secure
`SameSite=Lax` cookie.

Do not:

- generate and silently trust a root CA during normal application startup;
- commit a root key, leaf private key, keystore, or certificate bundle;
- use certificate-validation bypass flags as a success test;
- alternate between `localhost`, loopback IPs, and machine names without SANs
  for each identity;
- claim that a certificate works merely because one command-line client
  accepts it.

## Shared Development and Test

Use a stable DNS name. Prefer a public CA when the endpoint and validation
method permit it because browsers, desktop clients, and automation already
trust the chain.

Use an enterprise CA only when the service is intentionally private. In that
case, distribute trust through managed device or workload policy rather than
manual per-user instructions.

Terminate TLS at a shared gateway or ingress unless the application has a
specific end-to-end TLS requirement. Keep certificate issuance and renewal
independent from application deployment.

Do not distribute one private key across developer machines or application
repositories. The component terminating TLS owns access to the key.

## Production

Prefer:

```text
MCP client / browser
  -> public or enterprise-trusted TLS ingress
  -> application network
  -> MCP and authorization services
```

At the ingress:

- issue certificates through a managed certificate service or ACME;
- renew before expiry automatically;
- keep private keys in the platform certificate store, KMS, HSM, or secret
  manager;
- restrict key access to the TLS terminator;
- monitor expiry, renewal failures, hostname mismatch, and chain errors;
- rotate keys without changing the canonical public URL.

Between ingress and application:

- HTTP is acceptable only inside an explicitly trusted network boundary;
- use TLS when traffic crosses an untrusted or shared boundary;
- use mTLS when workloads must authenticate each other, not merely to satisfy
  browser HTTPS;
- do not expose an internal plaintext listener directly to clients.

The application should not package production certificates into its binary or
container image. If application-level TLS is required, inject the key material
at runtime through the deployment platform and keep rotation independent from
the build artifact.

## Public URL Contract

Configure the externally visible values explicitly:

```text
public MCP URL
OAuth issuer
protected resource URI
authorization endpoint
token endpoint
registration endpoint or client metadata URL
browser base URL
identity-provider callback URL
```

Require:

- HTTPS for every non-loopback remote endpoint;
- exact issuer and resource values;
- exact callback and redirect URI registration;
- one documented trailing-slash policy;
- no internal hostnames or plaintext ports in discovery metadata;
- actual TLS listener behavior matching advertised URLs.

Do not derive security-sensitive public identifiers solely from arbitrary
incoming `Host` or forwarding headers. Prefer explicit configuration. When a
reverse proxy is authoritative, trust forwarded headers only from known proxy
addresses and test the effective generated URLs.

## Browser and Desktop Client Trust

For configuration or implementation work, validate configuration syntax and
automated TLS assertions first. Do not launch every client class as routine
evidence.

During explicit client integration or final release validation, verify the
complete certificate chain from each real client class that is in scope:

- operating-system HTTP client;
- supported browser;
- Doubao or another Electron/Chromium desktop client;
- CI or server-side probe when it is part of the deployment;
- callback browser used by the OAuth flow.

System trust does not guarantee that a long-running desktop client has
reloaded its certificate state. Restart the process after adding or replacing
a development CA before diagnosing OAuth discovery.

Certificate trust proves server identity and channel encryption. It does not
replace OAuth client validation, user authentication, token audience checks,
or product authorization.

## Release Verification Gate

Run this complete gate only for final integration or release validation. For a
narrow TLS configuration task, stop after the affected automated checks and
record the remaining runtime checks.

For each supported environment in scope, verify:

1. the public MCP URL succeeds with normal certificate verification enabled;
2. the certificate chain reaches an expected trust anchor;
3. SAN contains the exact configured hostname;
4. the certificate is currently valid and has an expiry monitor or local
   regeneration path;
5. HTTP is unavailable or redirects according to the documented policy;
6. protected-resource and authorization-server metadata contain only the
   canonical HTTPS URLs;
7. browser-login cookies have deliberate `Secure` and `SameSite` attributes;
8. Web-to-API requests use a compatible scheme and origin policy;
9. when target-client integration is in scope, the target surface completes
   discovery without a certificate warning;
10. certificate and private-key files are absent from source history and build
    output.

Do not use `curl -k`, disabled hostname verification, or browser warning
bypasses for release evidence. Such probes may help isolate a failure, but
they cannot satisfy the TLS gate.

## Failure Triage

| Symptom | Likely cause |
| --- | --- |
| No request reaches the server | DNS, listener, route, firewall, or TLS handshake |
| Browser works but desktop client fails | Separate trust store or cached certificate state |
| Hostname mismatch | Public URL does not match certificate SAN |
| Discovery contains HTTP or an internal host | Public URL or forwarded-header configuration is wrong |
| Login returns to the sign-in page | Cross-scheme cookie policy, CORS, or callback URL mismatch |
| Works with validation disabled | Certificate chain, trust anchor, SAN, or expiry failure |
| Fails after certificate renewal | Incomplete chain, stale process cache, or key/certificate mismatch |

Diagnose trust and routing before changing OAuth protocol code.

## Completion Report

Record:

```text
Public MCP URL:
TLS termination point:
Certificate authority type:
Hostname and SANs:
Trust distribution:
Renewal or regeneration owner:
Application listener protocol:
Browser and Doubao verification:
Residual TLS risks:
```
