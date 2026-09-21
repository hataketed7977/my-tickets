# Spec Delta

## Purpose

Enables the `my-tickets` command-line tool to authenticate against the ticket center backend
via Feishu OAuth, persist the session token locally, and use it for subsequent authenticated
API calls. This adds a non-browser client path on top of the existing cookie-based web auth.

## ADDED Requirements

### Requirement: CLI can initiate a login session

The system SHALL expose an endpoint to create a CLI login session that returns a session
identifier and a Feishu authorization URL. The session identifier SHALL be an unguessable
random value and SHALL be usable as the OAuth `state` parameter.

#### Scenario: Create CLI login session

- **WHEN** a client sends `POST /api/auth/cli/session`
- **THEN** the response SHALL be `200 OK` with a JSON body containing `sessionId` and
  `authorizeUrl`
- **AND** the `authorizeUrl` SHALL point to the Feishu authorization endpoint with the
  `sessionId` as the `state` parameter
- **AND** the `authorizeUrl` SHALL use the backend-configured Feishu redirect URI

### Requirement: CLI can poll for the login result

The system SHALL expose an endpoint for the CLI to poll the status of a login session
using its session identifier.

#### Scenario: Poll before user completes login

- **WHEN** a client sends `GET /api/auth/cli/session/{sessionId}` and the user has not
  yet completed the Feishu login
- **THEN** the response SHALL be `200 OK` with `{ "status": "pending" }`

#### Scenario: Poll after user completes login

- **WHEN** a client sends `GET /api/auth/cli/session/{sessionId}` after the Feishu
  callback has created a session associated with this `sessionId`
- **THEN** the response SHALL be `200 OK` with `{ "status": "ready", "token": <token>, "user": <user> }`
- **AND** the `oauth_state` value on the underlying session row SHALL be cleared so the
  same `sessionId` cannot be polled again

#### Scenario: Poll with unknown session identifier

- **WHEN** a client sends `GET /api/auth/cli/session/{sessionId}` with an identifier
  that does not correspond to any pending or completed CLI login
- **THEN** the response SHALL be `404 Not Found`

### Requirement: Backend accepts Bearer token authentication

The session interceptor SHALL authenticate requests that carry a valid session token in
the `Authorization: Bearer <token>` header, in addition to the existing cookie-based
authentication. When both are present, the cookie SHALL take precedence.

#### Scenario: Authenticated request with Bearer token

- **WHEN** a request carries `Authorization: Bearer <valid-token>` and no session cookie
- **THEN** the request SHALL be authenticated as the user owning that session

#### Scenario: Request with invalid Bearer token

- **WHEN** a request carries `Authorization: Bearer <invalid-token>` and no session cookie
- **THEN** the response SHALL be `401 Unauthorized` with the message "请先登录"

#### Scenario: Cookie takes precedence over Bearer header

- **WHEN** a request carries both a valid session cookie and a Bearer header
- **THEN** the request SHALL be authenticated using the cookie value

### Requirement: Feishu callback handles CLI sessions

The Feishu OAuth callback endpoint SHALL detect when the `state` parameter corresponds to
a CLI login session and route it through the CLI flow instead of the web flow.

#### Scenario: Callback for a CLI session

- **WHEN** the Feishu callback is invoked with a `state` that matches a CLI login session
  and a valid `code`
- **THEN** the system SHALL exchange the code for a Feishu access token, create or update
  the user, create a session, and store the `state` as `oauth_state` on that session row
- **AND** the response SHALL be an HTML page indicating login success (not a redirect to
  the web app, and no session cookie shall be set)

#### Scenario: Callback for a web session (unchanged behavior)

- **WHEN** the Feishu callback is invoked with a `state` that does NOT match a CLI login
  session
- **THEN** the system SHALL follow the existing web flow: validate the state against the
  `ticket_oauth_state` cookie, create a session, set the `ticket_session` cookie, and
  redirect to the web base URL

### Requirement: CLI login command completes the OAuth flow

The `my-tickets login` command SHALL create a CLI login session, open the Feishu
authorization URL in the default browser, poll the session status until it is ready or
a timeout occurs, and persist the returned token locally.

#### Scenario: Successful login

- **WHEN** the user runs `my-tickets login`
- **THEN** the CLI SHALL call `POST /api/auth/cli/session`, open the returned
  `authorizeUrl` in the browser, and poll `GET /api/auth/cli/session/{sessionId}`
- **AND** once the response is `ready`, the CLI SHALL write `apiBaseUrl`, `token`, and
  `user` to `~/.my-tickets/config.json`
- **AND** the CLI SHALL print a success message containing the user's name

#### Scenario: Login times out

- **WHEN** the user runs `my-tickets login` and does not complete the Feishu login within
  the timeout period
- **THEN** the CLI SHALL print a timeout error message and exit with a non-zero status
- **AND** no token SHALL be written to the config file

### Requirement: CLI status command reports login state

The `my-tickets status` command SHALL report whether the user is logged in and, if so,
display the current user's identity by validating the stored token against the backend.

#### Scenario: Status when logged in

- **WHEN** the user runs `my-tickets status` and a valid token is stored locally
- **THEN** the CLI SHALL call `GET /api/auth/me` with the Bearer token and print the
  user's name

#### Scenario: Status when not logged in

- **WHEN** the user runs `my-tickets status` and no token is stored locally
- **THEN** the CLI SHALL print "未登录" and exit with a non-zero status

#### Scenario: Status when stored token is expired

- **WHEN** the user runs `my-tickets status` and the stored token is no longer valid on
  the backend
- **THEN** the CLI SHALL print "登录已过期，请重新登录" and exit with a non-zero status

### Requirement: CLI logout command clears local credentials

The `my-tickets logout` command SHALL remove the stored token and user information from
the local config file while preserving the `apiBaseUrl`.

#### Scenario: Logout clears credentials

- **WHEN** the user runs `my-tickets logout`
- **THEN** the `token` and `user` fields SHALL be removed from `~/.my-tickets/config.json`
- **AND** the `apiBaseUrl` field SHALL be preserved if present

### Requirement: CLI resolves API base URL with priority order

The CLI SHALL resolve the backend API base URL in the following priority order:
the `--api-base-url` command-line flag, the `MY_TICKETS_API_URL` environment variable,
the `apiBaseUrl` field in `~/.my-tickets/config.json`, and finally the default
`http://localhost:55888`.

#### Scenario: Flag overrides all other sources

- **WHEN** the user runs any command with `--api-base-url http://example.com:8080`
- **THEN** the CLI SHALL use `http://example.com:8080` regardless of environment
  variable or config file values

#### Scenario: Default when nothing is configured

- **WHEN** the user runs any command without `--api-base-url`, without
  `MY_TICKETS_API_URL`, and without `apiBaseUrl` in the config file
- **THEN** the CLI SHALL use `http://localhost:55888`
