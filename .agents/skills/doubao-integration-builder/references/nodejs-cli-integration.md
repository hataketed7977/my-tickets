# Node.js CLI Integration

Use this reference when the selected agent-facing integration is a Node.js
command-line interface. It applies to new CLIs and extensions of existing
ones. Preserve the repository's runtime, package manager, module system,
command framework, lint rules, and test runner unless evidence requires a
change.

This reference defines an implementation method, not a product-specific
command set. Derive commands and authentication from the target system.

## When CLI Is the Right Boundary

Choose CLI when:

- the target agent or automation runtime can launch local processes;
- the required operations already exist behind local code or an HTTP API;
- people should be able to use the same interface from a terminal;
- shell composition, batch use, or low implementation cost matters;
- protocol-level capability discovery and remote multi-user sessions are not
  required.

Choose stdio MCP instead when the named client needs MCP-native discovery,
typed schemas, notifications, cancellation, or lifecycle management. Choose
remote MCP when the integration must be centrally deployed or shared.

When both are required, share domain adapters, validation, result types, and
errors. Keep CLI and MCP as thin entry points.

## Evidence Pass

Inspect only enough repository context to freeze the command contract:

```text
package manager and lockfile
Node engine and module system
existing bin entry points and command framework
package scripts for typecheck, lint, test, build, and pack
existing API client and authentication helpers
configuration and credential sources
stdout, stderr, color, and exit-code conventions
packaging and installation method
target operating systems and shells
first user-visible operation
```

Stop discovery when the executable, first command, dependency boundary,
configuration source, output modes, exit codes, and focused test command are
known. Do not inspect every dependency version or redesign unrelated product
APIs.

Prefer an existing command framework. For a new CLI, choose one maintained
parser that supports subcommands, typed option parsing, generated help, and
validation. Respect the repository's Node engine; otherwise target the current
active LTS release rather than copying a version from an example.

## Freeze the Command Contract

Record before coding:

```text
executable name
subcommand and aliases
positional arguments
options, defaults, and constrained values
stdin behavior
human stdout format
machine stdout JSON shape
stderr diagnostics
success and failure exit codes
TTY and non-TTY behavior
color policy
configuration precedence
credential source
request timeout and cancellation behavior
working-directory assumptions
package, install, or repository-local launch command
explicit non-goals
```

Treat this as the CLI equivalent of a protocol schema. Do not let command
names, JSON fields, or exit meanings drift while implementing.

## Architecture Boundaries

Keep these layers separate:

```text
command parser
  -> command handler
  -> input validation
  -> application or API adapter
  -> normalized result
  -> human formatter or JSON formatter
```

Keep configuration, credentials, process exit handling, and browser opening
behind small adapters. Domain services must not call `console.log`, terminate
the process, or parse command-line arguments.

Prefer returning typed results or throwing typed errors from command handlers.
Set the process exit code only at the entry point.

## Fixed Implementation Gates

Keep the plan to three to five behavior-complete gates. Do not create one task
per file, helper, command option, or test.

### Gate 1: Contract and Executable Spike

1. freeze the command contract;
2. add or verify the package entry point and bin mapping;
3. prove `--help` and `--version` through the real executable path;
4. run the repository's focused typecheck or compile command once.

This gate is not complete if it only creates a package manifest or source
files without an executable command.

### Gate 2: One Read Command End to End

Implement one narrow read-only command:

1. parse and validate arguments;
2. call one existing service or API adapter;
3. render concise human output;
4. emit stable JSON in machine mode;
5. return defined exit codes for success, invalid input, authorization
   failure, and dependency failure;
6. test the complete command with a mocked dependency.

Do not add every planned command before this gate passes.

### Gate 3: Authentication and Configuration

Only when required:

1. reuse the product's existing authentication boundary;
2. implement the frozen configuration precedence;
3. keep secrets out of arguments, stdout, logs, and snapshots;
4. test missing, expired, and malformed credentials;
5. test an interactive login with mocked browser and network boundaries;
6. run one real interactive login only during final smoke testing.

Do not repeatedly wait for a person to complete browser login during
development. Automated tests should control the callback, polling, timeout,
and cancellation states.

### Gate 4: Package and Real Smoke Test

1. run the focused suite;
2. run the full CLI suite once;
3. run typecheck, lint, and production build once;
4. create the actual package or repository-local executable;
5. launch it as a subprocess from a clean temporary directory;
6. run one real read and one negative case;
7. verify the documented install or invocation command.

Add later commands as independent behavior slices after these gates are green.

## Execution Efficiency

Use this cadence:

- install dependencies once after the dependency set is frozen;
- do not rerun installation when the manifest and lockfile are unchanged;
- run a focused test for the command under development;
- run typecheck after a coherent slice, not after every helper;
- let the test runner compile tests when it already provides that feedback;
- run lint, full tests, build, and package once at the final gate;
- rerun a broad check only when code changed after it passed;
- do not start a backend or watch process when a mock or in-process adapter
  proves the current gate;
- use one final managed backend process for the real smoke test.

If the same failure signature survives two attempts, capture the exact argv,
environment names, cwd, stdout, stderr, exit code, and dependency version
before changing code again.

After three rejected hypotheses or ten diagnostic actions without a new
observable, stop the gate and report the evidence. Do not accumulate temporary
logging or keep rewriting the entry point.

## Agent-Reliable Command Design

Require:

- explicit subcommands and long option names;
- constrained option values where the parser supports them;
- deterministic defaults;
- `--help` and `--version`;
- a machine-readable mode such as `--json`;
- no prompts in machine mode;
- no color or progress animation when stdout is not a TTY;
- bounded output, pagination, or explicit limits;
- stable identifiers in output;
- actionable errors that name the invalid field or next command;
- documented exit codes.

Keep successful machine data on stdout. Send diagnostics and errors to stderr.
Do not mix banners, debug logs, warnings, or progress text into JSON output.

For writes, separate preview from mutation when impact is meaningful. Require
stable target identifiers, explicit confirmation semantics, and an
idempotency mechanism when retries could duplicate work.

## Safe Process and Input Handling

- Launch child processes with argument arrays through `spawn`, `execFile`, or
  an equivalent API. Do not concatenate model-controlled values into a shell
  command.
- Validate paths, URLs, numeric bounds, enumerations, and output destinations
  before calling dependencies.
- Honor cancellation and set request and subprocess timeouts.
- Do not write credentials to source control, command history, process
  arguments, stdout, or debug logs.
- Use the product's existing credential helper, environment convention,
  operating-system credential store, or protected config file. Do not invent a
  new credential system without a requirement.
- When a config file stores sensitive values, create it with restrictive
  permissions where the platform supports them.

## Test Layers

### Pure Tests

Test parsers, mappings, validation, configuration precedence, formatters, and
error classification without starting a subprocess or network server.

### Command Tests

Invoke the command handler with controlled adapters. Assert:

- parsed inputs;
- dependency calls;
- stdout and stderr separately;
- exit code;
- human and JSON output;
- missing and extra inputs;
- authorization and dependency failures.

### Subprocess Contract

Launch the real executable with an argument array and assert:

| Case | Expected |
| --- | --- |
| `--help` | usage on stdout, exit `0` |
| `--version` | version only, exit `0` |
| valid machine request | parseable JSON only on stdout |
| invalid option | stable stderr message and nonzero exit |
| missing credential | actionable stderr and nonzero exit |
| dependency timeout | bounded completion and classified error |
| non-TTY execution | no prompt, spinner, or color escape |
| signal or cancellation | child work stops and process exits |

Run at least one subprocess test from outside the source directory and with a
path containing spaces to expose hidden cwd and quoting assumptions.

## Completion Evidence

Report:

- why CLI was selected over MCP;
- runtime, package manager, and command framework;
- executable and command inventory;
- argument, output, error, and exit-code contract;
- configuration precedence and credential source;
- package and invocation method;
- focused tests, subprocess tests, and final broad checks;
- real smoke test result;
- target agent or runtime actually exercised;
- deferred environment and distribution risks.

Do not call the CLI integration complete from unit tests alone. Completion
requires the real executable path, machine output, a negative exit case, and
the documented package or repository-local invocation.
