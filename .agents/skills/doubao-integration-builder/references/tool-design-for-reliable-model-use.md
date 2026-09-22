# Tool Design for Reliable Model Use

Apply these rules so tool use does not depend on frontier-model reasoning.

## Tool Budget

- Start with 3 to 8 tools.
- Keep each enabled client profile at 12 tools or fewer unless evaluation proves more are reliable.
- Allowlist tools per assistant or workflow.
- Prefer one complete user workflow over broad endpoint coverage.
- Add a tool only when an evaluation case cannot be completed cleanly with the existing set.

Do not expose CRUD mechanically. Model the user's tasks.

## Names

Use:

```text
verb_object
```

Examples:

```text
search_tickets
get_ticket
create_ticket
add_ticket_comment
get_current_user
```

Rules:

- snake_case ASCII;
- one verb and one concrete object;
- globally understandable from the tool name and description;
- no abbreviations unless users already use them;
- no version suffixes;
- no similar pairs such as `find_ticket` and `search_tickets`.

Add a domain prefix only when the target client merges tools from multiple
servers into one namespace and an observed collision exists. Do not produce
redundant names such as `tickets_search_tickets`.

## Descriptions

Use this fixed shape:

```text
<Action and result>. Use when <specific user intent>. Requires <scope or prerequisite>. Does not <nearest confusing alternative>.
```

Example:

```text
Return one ticket by its exact ticket ID, including status and assignee. Use when the user already supplied or selected an ID. Requires tickets:read. Does not search by title; use search_tickets.
```

Keep the first sentence decisive. Do not include protocol history, implementation details, or marketing language.

## Input Schemas

Hard limits for the first release:

- at most 8 top-level fields;
- at most 2 object nesting levels;
- `additionalProperties: false`;
- explicit required fields;
- strings have length limits;
- arrays have item and length limits;
- list sizes default to 20 and never exceed 100;
- enums replace free-form mode strings;
- timestamps use RFC 3339;
- identifiers and display names are separate fields.

Avoid:

- `oneOf`, `anyOf`, and deeply nested unions;
- polymorphic payloads;
- free-form SQL, expressions, or filter DSLs;
- parameters whose meaning changes based on another vague field;
- boolean flags with unclear names such as `force` or `advanced`;
- asking the model to construct URLs.

Prefer:

```json
{
  "ticket_id": "TKT-1234",
  "comment": "Customer confirmed the fix.",
  "expected_version": 7,
  "idempotency_key": "..."
}
```

over:

```json
{
  "target": {"type": "ticket", "value": "TKT-1234"},
  "operation": {"mode": "append", "payload": {"text": "..."}},
  "options": {}
}
```

## Read Before Write

Separate lookup and mutation:

```text
search -> get -> preview -> mutate -> verify
```

Mutation tools must not search by fuzzy name and then modify the first match. Require a stable ID obtained from a prior read tool.

For irreversible or high-impact operations:

- provide a preview or `dry_run`;
- return a confirmation token bound to the normalized operation;
- require the token for execution;
- expire it quickly and make it single-use.

Do not rely on a natural-language `"confirmed": true` field as the only safeguard.

## Idempotency and Concurrency

For writes:

- accept an `idempotency_key` when the underlying operation supports it;
- accept `expected_version` or an equivalent precondition for updates;
- return the resulting version;
- classify retries explicitly;
- make duplicate requests return the original result where possible.

## Outputs

Return concise text plus structured content. Keep default output under 8 KB.

Use a stable envelope:

```json
{
  "ok": true,
  "data": {
    "id": "TKT-1234",
    "title": "Cannot submit reimbursement",
    "status": "OPEN",
    "version": 7
  },
  "meta": {
    "request_id": "req_...",
    "has_more": false,
    "next_cursor": null
  }
}
```

For list tools:

- return no more than the requested limit;
- include `has_more` and `next_cursor`;
- return stable IDs with human labels;
- omit large bodies unless explicitly requested;
- expose filters that narrow results before retrieval.

Do not return raw backend responses, HTML, stack traces, or unbounded text.

## Errors

Use one error envelope:

```json
{
  "ok": false,
  "error": {
    "code": "TICKET_VERSION_CONFLICT",
    "message": "Ticket TKT-1234 changed after it was read.",
    "retryable": false,
    "next_action": "Call get_ticket, review the new version, then retry."
  },
  "meta": {
    "request_id": "req_..."
  }
}
```

Use a small documented code set:

- `INVALID_INPUT`;
- `NOT_FOUND`;
- `AMBIGUOUS_MATCH`;
- `UNAUTHENTICATED`;
- `INSUFFICIENT_SCOPE`;
- `PERMISSION_DENIED`;
- `VERSION_CONFLICT`;
- `RATE_LIMITED`;
- `DEPENDENCY_UNAVAILABLE`;
- domain-specific conflicts.

The `next_action` must name an exact corrective step or tool. Do not tell the model only to "try again later" when a better recovery path exists.

## Annotations and Risk

Set tool annotations truthfully:

| Tool class | readOnly | destructive | idempotent |
| --- | --- | --- | --- |
| Search/get | true | false | true |
| Create with idempotency key | false | false | true |
| Update with version precondition | false | depends | true |
| Delete/cancel | false | true | depends |

Annotations are hints, never authorization controls.

## Scope Mapping

Map every tool to exactly one minimum scope in a checked-in table:

| Tool | Scope | Product check |
| --- | --- | --- |
| `search_tickets` | `tickets:read` | tenant and visibility filter |
| `get_ticket` | `tickets:read` | object visibility |
| `create_ticket` | `tickets:write` | category and assignee validity |
| `add_ticket_comment` | `tickets:write` | ticket visibility and state |

Do not let the model supply a tenant ID or user ID to bypass the verified principal context.

## Weak-Model Evaluation

Build at least 10 realistic prompts:

- 4 direct read tasks;
- 2 ambiguous searches requiring disambiguation;
- 2 multi-step read-before-write tasks;
- 1 permission denial;
- 1 destructive action requiring confirmation.

Measure:

- correct tool selected;
- valid arguments on first attempt;
- no mutation before required reads or confirmation;
- correct recovery from structured errors;
- final answer grounded in tool output;
- total tool calls and tokens.

Run with deterministic settings when available. Use the least capable model expected in production, not only the strongest model.

Fail the design when:

- first-choice tool accuracy is below 90 percent on direct tasks;
- invalid argument rate exceeds 5 percent;
- any destructive action bypasses confirmation;
- the model invents IDs instead of calling a lookup tool;
- output size regularly causes truncation.

Fix names, descriptions, schemas, and tool count before adding prompt instructions. Prompting is not a substitute for a clear tool contract.
