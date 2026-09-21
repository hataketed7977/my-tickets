# Design

## Context

The CLI already has `login`/`status`/`logout` and an `apiRequest<T>(baseUrl, path, { method, token, body })` helper that injects the Bearer token. The backend already exposes `GET /api/tickets` (filtering by `search`, `status`, `priority`, `categoryId` plus `page`/`pageSize`), `GET /api/categories`, and `GET /api/users`. No backend changes are needed. See proposal.md for motivation.

## Goals / Non-Goals

**Goals:**
- Provide a `my-tickets list` command with the same filter surface as the web ticket page.
- Keep output usable both by humans (table) and scripts (`--json`).
- Resolve user IDs and category names so terminal output is readable without extra lookups.

**Non-Goals:**
- No `--all` auto-pagination.
- No fuzzy category matching (exact name match only).
- No sorting options (backend order is fixed by `updated_at DESC`).
- No ticket create/update/delete commands (out of scope for this change).
- No new runtime dependencies.

## Decisions

### 1. Command structure: single `list` subcommand

Add `my-tickets list` registered in `index.ts` with options `--search`, `--status`, `--priority`, `--category`, `--page`, `--page-size`, `--json`. The global `--api-base-url` option is reused.

**Rationale:** Mirrors the existing `login`/`status`/`logout` command style and keeps the CLI flat. Subcommands like `tickets list` add nesting with no benefit for a single-entity tool.

### 2. Status/priority input: Chinese names only

`--status` accepts `待处理|处理中|已解决|已关闭` and maps to `open|in_progress|resolved|closed`. `--priority` accepts `低|普通|高|紧急` mapping to `low|medium|high|urgent`. `commander`'s `.choices()` enforces the allowed values and prints usage on mismatch.

**Rationale:** The user explicitly requested Chinese values. `commander` choices give a free, consistent error message. The `--help` text documents the accepted values so AI callers can pick the right one.

### 3. Category resolution: exact name match via `/api/categories`

When `--category` is provided, call `GET /api/categories`, find the item whose `name` strictly equals the input, and pass its `id` as `categoryId`. No match → error exit. Category names are unique on the backend (enforced by a duplicate-key check on create), so exact match yields at most one.

**Rationale:** Users cannot know category UUIDs. Exact match keeps behavior predictable; the user explicitly rejected fuzzy matching.

### 4. Table rendering: built-in `console.table`

Use Node's built-in `console.table` for the default output. No new dependency.

**Rationale:** The proposal requires no new dependencies. `console.table` handles the full field set adequately for a developer CLI. `--json` bypasses it entirely.

### 5. User name resolution: fetch `/api/users` once

Call `GET /api/users` once per `list` invocation and build an `id -> name` map. Replace `reporterUserId`/`assigneeUserId` with names in the table output. Unknown IDs fall back to the raw ID.

**Rationale:** A single bulk lookup is simpler and cheaper than per-ticket calls. The fallback to raw ID prevents data loss when a user has been removed.

### 6. API call order: categories and users first (only when needed), then tickets

- If `--category` is given: fetch categories → resolve ID.
- Always fetch users (needed for table output).
- Then fetch tickets with the resolved query.
- If `--json` is set, users are still fetched so the output contract is consistent? No—`--json` emits the raw backend response, so user resolution is only for the table path. Skip the `/api/users` call when `--json` is passed to avoid an unnecessary request.

**Rationale:** `--json` returns the raw response unchanged; resolving names there would be a transformation, violating the "raw" requirement.

### 7. Error handling: reuse `status` command pattern

- No token → print login hint, exit 1.
- `401` from any API call → print expired hint, exit 1.
- Other API errors → print the backend error message, exit 1.
- Invalid option values → handled by `commander`.

**Rationale:** Consistent with the existing `status` command behavior.

## Risks / Trade-offs

- **`console.table` truncates wide rows in narrow terminals** → Mitigation: `--json` is available for full fidelity; `description` may be long but the spec requires all fields.
- **Two extra API calls (`/api/categories`, `/api/users`) per invocation** → Mitigation: acceptable for a CLI; `/api/categories` only when `--category` is used; `/api/users` only for table output.
- **User list could be large** → Not a concern for the current single-team scale; revisit only if pagination is added to `/api/users`.
