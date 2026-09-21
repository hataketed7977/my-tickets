# Spec Delta

## Purpose

Enables the `my-tickets` command-line tool to list tickets from the ticket center backend with the same filter conditions available in the web UI, supporting both human-readable table output and machine-readable JSON output.

## ADDED Requirements

### Requirement: CLI exposes a ticket list command

The system SHALL provide a `my-tickets list` command that fetches tickets from `GET /api/tickets` and prints them to standard output.

#### Scenario: List tickets with no filters

- **WHEN** the user runs `my-tickets list`
- **THEN** the CLI SHALL request `GET /api/tickets?page=1&pageSize=100` with the stored Bearer token
- **AND** print the returned tickets in a table

### Requirement: List command supports search, status, and priority filters

The `my-tickets list` command SHALL accept `--search`, `--status`, and `--priority` options whose values are forwarded to the backend as the `search`, `status`, and `priority` query parameters respectively.

#### Scenario: Filter by search keyword

- **WHEN** the user runs `my-tickets list --search 登录`
- **THEN** the CLI SHALL request `GET /api/tickets` with `search=登录`

#### Scenario: Filter by status

- **WHEN** the user runs `my-tickets list --status 待处理`
- **THEN** the CLI SHALL request `GET /api/tickets` with `status=open`

#### Scenario: Filter by priority

- **WHEN** the user runs `my-tickets list --priority 紧急`
- **THEN** the CLI SHALL request `GET /api/tickets` with `priority=urgent`

### Requirement: Status and priority accept Chinese display names

The `--status` option SHALL accept exactly `待处理`, `处理中`, `已解决`, and `已关闭`, mapping them to `open`, `in_progress`, `resolved`, and `closed` respectively. The `--priority` option SHALL accept exactly `低`, `普通`, `高`, and `紧急`, mapping them to `low`, `medium`, `high`, and `urgent` respectively. Any other value SHALL produce an error and exit with a non-zero status.

#### Scenario: Invalid status value

- **WHEN** the user runs `my-tickets list --status foo`
- **THEN** the CLI SHALL print an error stating the accepted status values
- **AND** exit with a non-zero status

#### Scenario: Invalid priority value

- **WHEN** the user runs `my-tickets list --priority foo`
- **THEN** the CLI SHALL print an error stating the accepted priority values
- **AND** exit with a non-zero status

### Requirement: List command supports category filter by name

The `my-tickets list` command SHALL accept a `--category <name>` option. The CLI SHALL resolve the category name to a category ID by calling `GET /api/categories` and finding the category whose `name` exactly matches the provided value. The resolved ID SHALL be forwarded to `GET /api/tickets` as the `categoryId` parameter. If no category matches, the CLI SHALL print an error and exit with a non-zero status.

#### Scenario: Category matches exactly

- **WHEN** the user runs `my-tickets list --category 网络问题` and a category named `网络问题` exists
- **THEN** the CLI SHALL request `GET /api/tickets` with that category's `id` as `categoryId`

#### Scenario: Category does not exist

- **WHEN** the user runs `my-tickets list --category 不存在` and no category has that name
- **THEN** the CLI SHALL print an error indicating the category was not found
- **AND** exit with a non-zero status

### Requirement: List command supports pagination parameters

The `my-tickets list` command SHALL accept `--page <n>` and `--page-size <n>` options. `--page` SHALL default to 1 and `--page-size` SHALL default to 100. The values SHALL be forwarded to the backend as the `page` and `pageSize` query parameters.

#### Scenario: Default pagination

- **WHEN** the user runs `my-tickets list` without pagination options
- **THEN** the CLI SHALL request `GET /api/tickets?page=1&pageSize=100`

#### Scenario: Explicit pagination

- **WHEN** the user runs `my-tickets list --page 2 --page-size 50`
- **THEN** the CLI SHALL request `GET /api/tickets?page=2&pageSize=50`

### Requirement: Default table output includes all ticket fields

When no `--json` flag is provided, the `my-tickets list` command SHALL print a table containing every field of each ticket: `ticketNo`, `title`, `description`, `status`, `priority`, `categoryName`, `reporter`, `assignee`, `createdAt`, `updatedAt`, and `resolvedAt`. The `reporter` and `assignee` columns SHALL show the user's display name (resolved from the user ID), or an empty string when the ID is null.

#### Scenario: Table output with all fields

- **WHEN** the user runs `my-tickets list`
- **THEN** the CLI SHALL print a table with one row per ticket containing all the fields listed above

### Requirement: JSON output emits raw response

When the `--json` flag is provided, the `my-tickets list` command SHALL print the raw JSON response from `GET /api/tickets` to standard output with no table formatting.

#### Scenario: JSON output

- **WHEN** the user runs `my-tickets list --json`
- **THEN** the CLI SHALL print the full `TicketListResponse` JSON (including `id`, `categoryId`, `reporterUserId`, and `assigneeUserId`) to stdout

### Requirement: User names resolved from user list

The `my-tickets list` command SHALL call `GET /api/users` to obtain the list of users and SHALL replace `reporterUserId` and `assigneeUserId` with the corresponding user `name` in the table output. If a user ID cannot be found in the list, the CLI SHALL show the raw ID.

#### Scenario: User name resolved

- **WHEN** a ticket has `assigneeUserId` equal to a user whose `name` is `张三`
- **THEN** the `assignee` column SHALL display `张三`

#### Scenario: Unknown user ID

- **WHEN** a ticket has an `assigneeUserId` not present in the user list
- **THEN** the `assignee` column SHALL display the raw user ID

### Requirement: List command requires authentication

The `my-tickets list` command SHALL require a valid stored token. If no token is stored or the backend responds with `401 Unauthorized`, the CLI SHALL print a message prompting the user to log in again and exit with a non-zero status.

#### Scenario: Not logged in

- **WHEN** the user runs `my-tickets list` and no token is stored in `~/.my-tickets/config.json`
- **THEN** the CLI SHALL print a message instructing the user to run `my-tickets login`
- **AND** exit with a non-zero status

#### Scenario: Token expired

- **WHEN** the user runs `my-tickets list` and the backend returns `401 Unauthorized`
- **THEN** the CLI SHALL print a message indicating the login has expired
- **AND** exit with a non-zero status
