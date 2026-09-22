---
name: api-url-isolation-review
description: Review backend HTTP API design and verify that different consumers (web, CLI, mobile, MCP, internal services, third-party) have URL-isolated endpoints with per-consumer namespaces, auth, and versioning. Use when the user asks for an API/interface code review, wants to check URL isolation between consumers, or reviews controller/diff changes. Do not use for security scanning or general code quality review.
---

# API URL Isolation Review

Review backend interfaces to ensure different consumer types are isolated by URL namespace, auth mechanism, and response contracts. The goal is that changing one consumer's API never silently breaks another.

## When to use

- User asks to review controllers, REST API design, or a diff touching `@RequestMapping` / `@RestController`.
- User asks "do different consumers have isolated URLs?" or wants an API contract review.
- Reviewing Spring Boot `controller/`, Servlet registrations, or MCP transport endpoints.

## Core principle

A single HTTP endpoint must serve at most one consumer type. If web, CLI, mobile, MCP, internal, or third-party callers share a path, that is a finding — even if the handler branches on a header or cookie.

## Review procedure

Run these steps in order. Stop and report after step 4 unless the user asks for a deeper audit.

### Step 1 — Inventory consumers

List every distinct caller of the API. Evidence sources:

- Controllers: look for branching on `User-Agent`, cookie presence, `Accept` header, or auth type inside a single handler.
- Auth filters/interceptors: which token/cookie types are accepted where.
- CORS config: which origins are allowed.
- Reverse proxy (Caddy/Nginx): which upstream paths are exposed.
- OpenAPI / route registrations.
- MCP, WebSocket, or SSE endpoints.

For this repo, known consumers are **Web (browser, cookie session)**, **CLI (Bearer token, polling OAuth)**, and **MCP clients (AI agents)**.

### Step 2 — Map URL namespaces

For each consumer, record the URL prefix it uses. Acceptable isolation looks like:

```
/api/web/v1/...      # browser
/api/cli/v1/...      # CLI
/api/mcp/v1/... or /mcp/...  # MCP (separate transport is fine)
/api/internal/v1/... # internal services
/api/open/v1/...     # third-party
```

Flag any consumer that has no dedicated prefix and falls back to a shared `/api/...` root.

### Step 3 — Detect shared endpoints

Scan controllers for paths that are consumed by more than one caller type. Red flags:

- A handler that checks `if (cookie != null) { web } else { cli }`.
- A `@RequestMapping("/api/...")` controller used by both browser and CLI.
- A callback endpoint (e.g. OAuth callback) that serves multiple clients by inspecting state.
- Endpoints under `/api/**` that accept both Cookie and Bearer auth without a consumer prefix.

For each shared endpoint, record: path, which consumers hit it, and how the handler disambiguates them.

### Step 4 — Check per-consumer concerns

For each consumer namespace, verify:

1. **Auth method matches consumer**: browser → cookie session; CLI/third-party → Bearer token; MCP → OAuth2/MCP auth. Mixed auth on one path is a finding.
2. **Version in path**: `/v1/` or date-based version present. Unversioned shared endpoints are findings.
3. **CORS scoped to consumer origin**: a single `addMapping("/api/**")` rule that allows all consumer origins is a finding — CORS should be per-namespace or per-origin-group.
4. **Response shape stable**: web may return HTML-redirects or rich JSON; CLI expects machine JSON only. A handler that sometimes returns HTML and sometimes JSON is a finding.
5. **Rate limit / audit hook exists** (optional, mention if missing but not block).

### Step 5 — Report

Return findings as a table with severity. Use this shape:

| Severity | Path | Issue | Suggested fix |
|----------|------|-------|---------------|
| High | `/api/auth/feishu/callback` | Shared web + CLI callback branches on cookie | Split into `/api/web/v1/auth/feishu/callback` and `/api/cli/v1/auth/feishu/callback` |
| Medium | `/api/tickets` | Shared by web and CLI, no consumer prefix | Move to `/api/web/v1/tickets` and `/api/cli/v1/tickets`, or extract a shared service behind two thin controllers |

Severity scale:
- **High**: shared endpoint with branching logic; wrong auth method for consumer; unversioned breaking-change risk.
- **Medium**: shared prefix but no runtime branching; CORS too broad; response shape ambiguity.
- **Low**: naming inconsistency; missing version on a stable internal endpoint.

End with a one-line verdict: `URL isolation: PASS` when every consumer has a dedicated namespace and no shared handler exists, otherwise `URL isolation: FAIL` with the count of High findings.

## Spring Boot specifics

- Check `@RequestMapping` at class and method level. A class-level `/api` means every method is shared unless subclassed.
- Check `WebMvcConfigurer.addInterceptors` and `addCorsMappings` — broad `/api/**` rules mask missing isolation.
- Check `MappedInterceptor` beans (MCP) — confirm they only match the MCP namespace.
- Check `HttpServletStatelessServerTransport` / Spring AI MCP registration — MCP should live under its own path (e.g. `/mcp`), not under `/api`.

## Do not

- Do not rewrite controllers as part of the review — only report.
- Do not flag endpoints that are intentionally public and consumer-agnostic (e.g. health checks, OpenAPI docs) — note them as exempt.
- Do not require URL isolation between endpoints of the *same* consumer (e.g. web CRUD sharing `/api/web/v1/...` is fine).
