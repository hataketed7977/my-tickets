# Official MCP Java SDK Streamable HTTP

Use this reference when a Java Servlet or Spring Boot application integrates
the official MCP Java SDK directly. Select this path when a framework adapter
cannot prove compatibility with the target client's MCP revision or state
model.

Do not copy versions from this reference. Resolve one SDK release that matches
the repository's Java, JSON stack, Servlet generation, and target protocol,
then inspect only that release.

## Gate 1: Executable Transport Spike

Dependency resolution and compilation do not prove transport compatibility.
Before editing production security or OAuth code, build a disposable spike
that:

1. creates the selected Streamable HTTP transport;
2. registers it through the host's real Servlet container;
3. creates one server with one no-op read tool;
4. sends `initialize` over real HTTP;
5. calls the no-op tool;
6. asserts the negotiated protocol revision, status, content type, and framing;
7. asserts the required state model.

For a stateless target, use the SDK's stateless transport type and prove that
initialize and tool calls succeed without a session identifier. For a stateful
target, capture and propagate the server-issued session identifier. Do not
start with one model and switch after application code is written.

The spike must use the same JSON mapper family as the host application. Select
the SDK JSON module that matches the resolved Jackson generation or other JSON
stack; do not add multiple mapper modules speculatively.

## Servlet Test Boundary

A transport registered as a raw Servlet is not dispatched through Spring MVC's
`DispatcherServlet`. `MockMvc` therefore does not prove that the MCP endpoint
is reachable.

Test a raw Servlet transport with:

- an embedded server on a random port;
- the JDK HTTP client, Spring `RestClient`, or another real HTTP client;
- explicit request headers and body;
- bounded connect and request timeouts.

Use MVC slice tests only for MVC controllers such as metadata or registration
adapters. Do not create and delete temporary debug tests to discover which
dispatcher owns an endpoint.

## Spring Security Principal Propagation

Spring Security authenticates the incoming Servlet request before protocol
dispatch. MCP tool handlers may execute on another thread, so they must not
assume `SecurityContextHolder` remains populated.

Use the transport's request-context extraction hook:

1. read the verified `Authentication` on the Servlet thread;
2. copy only the normalized principal data needed by tools into the MCP
   transport context;
3. read that context in the tool handler;
4. reject calls when the context is absent or malformed.

Do not copy bearer tokens or mutable request objects into tool context. Prefer
an immutable principal containing subject, tenant when applicable, and scopes.

Test:

- valid principal reaches the tool;
- anonymous request is rejected before dispatch;
- a valid token for another resource is rejected;
- concurrent calls do not exchange principals.

## Request and Response Contract

Build requests from the negotiated revision and installed SDK behavior.
Typically:

- send `Content-Type: application/json`;
- advertise both `application/json` and `text/event-stream` when required by
  the selected transport;
- accept plain JSON or SSE only where the selected SDK documents both;
- parse SSE `data:` fields rather than the entire body;
- treat JSON-RPC notifications as having no response object;
- assert request responses contain the matching JSON-RPC ID.

Do not loosen every status or content assertion after one mismatch. First
identify whether the message is a request or notification and whether the
selected transport is stateless or stateful.

## Minimal Production Shape

Keep these components distinct:

```text
Servlet registration
  -> Spring Security bearer validation
  -> MCP transport context extraction
  -> MCP server and tool catalog
  -> application service
```

Use the SDK to own JSON-RPC framing and protocol negotiation. Use Spring
Security to own bearer validation. Use application services to own product
authorization.

Register only one transport implementation for the endpoint. Do not leave a
stateful and stateless transport active in parallel.

## Spring Authorization Server Integration

Complete the protected transport with a test-only token before adding the
authorization server.

When self-hosting Spring Authorization Server, provide the selected release's
required framework beans, including:

- registered-client repository;
- authorization service;
- authorization-consent service when required;
- authorization-server settings;
- signing key source and encoder;
- resource-server decoder and validators.

Start from the release's official minimal security-chain configuration. Add
custom DCR, resource binding, and identity bridging only after the standard
authorization endpoint is reachable.

If a custom RFC 7591 registration controller is used:

- advertise its exact URL as `registration_endpoint`;
- validate exact redirect URIs and the client authentication profile;
- keep it outside an authorization-server matcher that would require an
  already registered client;
- configure CSRF narrowly for that endpoint instead of weakening unrelated
  application routes;
- store clients through the framework repository.

## Servlet Filter Registration

A Spring `Filter` bean may be auto-registered globally by the Servlet
container and also inserted into a Spring Security chain. A
`OncePerRequestFilter` can then run globally first and skip its intended
security-chain execution.

When a bridge filter belongs only to a security chain:

- disable its global Servlet registration;
- insert it after `SecurityContextHolderFilter` and before the authorization
  endpoint observes authentication;
- prove the principal is visible at the authorization endpoint with one
  focused test;
- do not move it repeatedly among unrelated filter classes.

## Gradle Execution Discipline

Resolve the dependency graph once after versions are frozen. For one unknown
API, inspect one official example or one installed source/JAR, then code
against that result.

Do not scan every cached SDK release. Do not use broad cache searches as the
normal API discovery mechanism.

Keep the Gradle daemon enabled for repeated compile and test feedback unless
the repository or CI explicitly disables it. A `--no-daemon` workaround for a
long-running server task does not imply that every focused test should start a
new JVM.

Use this cadence:

1. one dependency resolution;
2. one executable transport spike;
3. focused test for the current gate;
4. full module suite once after all focused gates pass.

## Focused Test Ladder

Use separate focused tests so failures remain attributable:

1. real HTTP stateless or stateful transport spike;
2. anonymous challenge and protected tool with a test token;
3. authorization-server metadata;
4. dynamic registration when required;
5. anonymous authorization redirect;
6. authenticated code issuance and PKCE exchange;
7. MCP call with the issued access token;
8. existing-login resume flow;
9. exact target-client smoke test.

Do not use one full-flow test as the diagnostic loop for every lower layer.
Once a lower layer passes, keep its focused test unchanged while progressing.

## Completion Evidence

Record:

- selected SDK and resolved JSON module;
- target protocol revision and state model;
- real Servlet test command and result;
- principal propagation mechanism;
- exact request headers and response framing;
- focused gate test results;
- Gradle commands and invocation count;
- exact target-client result or an explicit unverified status.
