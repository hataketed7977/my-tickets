# Tasks

## 1. List command skeleton and option parsing

- [x] 1.1 Create `apps/cli/src/commands/list.ts` with a `list(options)` function that loads config, checks for a stored token, and prints a login hint with exit code 1 when absent; verify by running the command with no token and confirming the message and exit code
- [x] 1.2 Register `my-tickets list` in `apps/cli/src/index.ts` with options `--search`, `--status` (choices: 待处理/处理中/已解决/已关闭), `--priority` (choices: 低/普通/高/紧急), `--category`, `--page`, `--page-size`, `--json`; verify `my-tickets list --help` shows all options and rejected values trigger a commander error
- [x] 1.3 Implement the Chinese-to-enum mappings for status and priority inside `list.ts`; verify with a unit test that each Chinese name maps to the correct backend value

## 2. Category resolution and ticket fetch

- [x] 2.1 Add a helper that calls `GET /api/categories` and returns the `id` of the category whose `name` exactly matches the input, throwing a "category not found" error on no match; verify with a unit test covering match and no-match cases
- [x] 2.2 Build the query string from the resolved options (default `page=1`, `pageSize=100`) and call `GET /api/tickets`; verify with a unit test that the correct URL and parameters are produced for a filtered request

## 3. User name resolution and output

- [x] 3.1 Add a helper that calls `GET /api/users` and returns an `id -> name` map; verify with a unit test that known IDs resolve to names and unknown IDs fall back to the raw ID
- [x] 3.2 Implement table output via `console.table` mapping `reporterUserId`/`assigneeUserId` to names and including all required fields (`ticketNo`, `title`, `description`, `status`, `priority`, `categoryName`, `reporter`, `assignee`, `createdAt`, `updatedAt`, `resolvedAt`); verify by running `my-tickets list` and confirming all columns are present
- [x] 3.3 Implement `--json` output that prints the raw `TicketListResponse` and skips the `/api/users` call; verify by running `my-tickets list --json` and confirming valid JSON with `id`, `categoryId`, `reporterUserId`, `assigneeUserId` present

## 4. Error handling

- [x] 4.1 Handle `401 Unauthorized` from any API call by printing a login-expired message and exiting with code 1; verify with a unit test stubbing a 401 response
- [x] 4.2 Handle other API errors by printing the backend error message and exiting with code 1; verify with a unit test stubbing a 500 response

## 5. Verification

- [x] 5.1 Run `npm run typecheck` and `npm run lint` in `apps/cli` and confirm both pass
- [x] 5.2 Run `npm run build` in `apps/cli` and confirm `dist/commands/list.js` is produced
- [x] 5.3 Run `npm test` in `apps/cli` and confirm all tests pass
- [x] 5.4 Manually run `my-tickets list` (with a logged-in session) and `my-tickets list --json` against the running backend and confirm correct output
