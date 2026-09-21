# Proposal

## Why

The `my-tickets` CLI currently only supports authentication (`login`/`status`/`logout`). Users authenticated via the CLI have no way to inspect tickets from the terminal, which defeats the purpose of a headless client. Adding a `list` command that mirrors the web ticket page's filter conditions lets users query tickets directly from the shell and opens the door for scripting and AI-driven workflows.

## What Changes

- Add a `my-tickets list` command that queries `GET /api/tickets` with the same filter conditions as the web ticket page.
- Query options: `--search`, `--status`, `--priority`, `--category`, `--page`, `--page-size`.
- `--status` and `--priority` accept Chinese display names (待处理/处理中/已解决/已关闭 and 低/普通/高/紧急).
- `--category` accepts a category name and resolves it to a category ID by calling `GET /api/categories` and performing an exact match; no match is an error.
- Output defaults to a full-field table; `--json` emits the raw response.
- Reporter and assignee user IDs are resolved to display names via `GET /api/users`.
- Default page size is 100 (the backend cap); `--page` defaults to 1.
- Reuse the existing Bearer-token auth flow; unauthenticated or expired tokens surface a re-login hint and exit code 1.

## Capabilities

### New Capabilities
- `cli-ticket-query`: The `my-tickets list` command, its query options, output formats, user-name resolution, and error handling.

### Modified Capabilities

## Impact

- **CLI code**: New `apps/cli/src/commands/list.ts`; register the command in `apps/cli/src/index.ts`.
- **CLI dependencies**: No new runtime dependencies; table rendering uses the built-in `console.table` to avoid adding a table library.
- **Backend**: No API changes—`GET /api/tickets`, `GET /api/categories`, and `GET /api/users` already support the needed behavior.
- **Config**: Reuses `~/.my-tickets/config.json` (token, apiBaseUrl) and the existing `--api-base-url` global option.
