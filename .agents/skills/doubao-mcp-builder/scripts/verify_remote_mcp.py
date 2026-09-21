#!/usr/bin/env python3
"""Read-only MCP OAuth discovery probe for explicitly authorized targets."""

from __future__ import annotations

import argparse
import ipaddress
import json
import re
import socket
import sys
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass
from typing import Any


PROTOCOL_VERSION = "2026-07-28"
MAX_RESPONSE_BYTES = 1024 * 1024
RESOURCE_METADATA_RE = re.compile(
    r'(?:^|,)\s*Bearer\b[^\r\n]*?\bresource_metadata=(?:"([^"]+)"|([^,\s]+))',
    re.IGNORECASE,
)


class ProbeError(Exception):
    """A deterministic probe or target-safety failure."""


class NoRedirectHandler(urllib.request.HTTPRedirectHandler):
    def redirect_request(
        self,
        req: urllib.request.Request,
        fp: Any,
        code: int,
        msg: str,
        headers: Any,
        newurl: str,
    ) -> None:
        return None


@dataclass(frozen=True)
class HttpResult:
    url: str
    status: int
    headers: Any
    body: bytes


class Reporter:
    def __init__(self) -> None:
        self.results: list[dict[str, str]] = []

    def pass_(self, check: str, detail: str) -> None:
        self.results.append({"status": "PASS", "check": check, "detail": detail})

    def warn(self, check: str, detail: str) -> None:
        self.results.append({"status": "WARN", "check": check, "detail": detail})

    def fail(self, check: str, detail: str) -> None:
        self.results.append({"status": "FAIL", "check": check, "detail": detail})

    @property
    def failed(self) -> bool:
        return any(item["status"] == "FAIL" for item in self.results)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Probe MCP OAuth protected-resource and authorization-server "
            "metadata without completing login or calling a business tool."
        )
    )
    parser.add_argument("mcp_url", help="Canonical MCP endpoint URL")
    parser.add_argument(
        "--acknowledge-authorized-target",
        action="store_true",
        help="Confirm that you own or are authorized to test the target",
    )
    parser.add_argument(
        "--expected-issuer",
        help="Require this exact authorization-server issuer",
    )
    parser.add_argument(
        "--allow-http",
        action="store_true",
        help="Allow HTTP for local development only",
    )
    parser.add_argument(
        "--allow-private-network",
        action="store_true",
        help="Allow loopback, private, link-local, or reserved target addresses",
    )
    parser.add_argument(
        "--timeout",
        type=float,
        default=8.0,
        help="Per-request timeout in seconds (default: 8)",
    )
    parser.add_argument(
        "--json",
        action="store_true",
        help="Emit machine-readable JSON",
    )
    return parser.parse_args()


def is_private_address(address: str) -> bool:
    ip = ipaddress.ip_address(address)
    return any(
        (
            ip.is_private,
            ip.is_loopback,
            ip.is_link_local,
            ip.is_reserved,
            ip.is_multicast,
            ip.is_unspecified,
        )
    )


def validate_url(
    raw_url: str,
    *,
    allow_http: bool,
    allow_private_network: bool,
    label: str,
) -> urllib.parse.SplitResult:
    parsed = urllib.parse.urlsplit(raw_url)
    if parsed.scheme not in {"https", "http"}:
        raise ProbeError(f"{label} must use HTTPS")
    if parsed.scheme == "http" and not allow_http:
        raise ProbeError(f"{label} uses HTTP; pass --allow-http only for local development")
    if not parsed.hostname:
        raise ProbeError(f"{label} has no hostname")
    if parsed.username or parsed.password:
        raise ProbeError(f"{label} must not contain credentials")
    if parsed.fragment:
        raise ProbeError(f"{label} must not contain a fragment")

    try:
        port = parsed.port or (443 if parsed.scheme == "https" else 80)
        addresses = {
            item[4][0]
            for item in socket.getaddrinfo(
                parsed.hostname,
                port,
                type=socket.SOCK_STREAM,
            )
        }
    except (OSError, ValueError) as error:
        raise ProbeError(f"{label} hostname resolution failed: {error}") from error

    private = [address for address in addresses if is_private_address(address)]
    if private and not allow_private_network:
        raise ProbeError(
            f"{label} resolves to a private or local address; "
            "pass --allow-private-network only for an authorized internal target"
        )
    if parsed.scheme == "http" and not private:
        raise ProbeError(f"{label} uses plaintext HTTP on a non-local address")
    return parsed


def origin(parsed: urllib.parse.SplitResult) -> str:
    host = parsed.hostname or ""
    if ":" in host and not host.startswith("["):
        host = f"[{host}]"
    default_port = 443 if parsed.scheme == "https" else 80
    authority = host if parsed.port in {None, default_port} else f"{host}:{parsed.port}"
    return f"{parsed.scheme}://{authority}"


def request(
    url: str,
    *,
    method: str,
    timeout: float,
    headers: dict[str, str] | None = None,
    body: bytes | None = None,
) -> HttpResult:
    req = urllib.request.Request(
        url,
        data=body,
        headers={
            "User-Agent": "oauth21-mcp-integration-probe/1.0",
            **(headers or {}),
        },
        method=method,
    )
    opener = urllib.request.build_opener(NoRedirectHandler())
    try:
        response = opener.open(req, timeout=timeout)
    except urllib.error.HTTPError as error:
        response = error
    except urllib.error.URLError as error:
        raise ProbeError(f"request to {url} failed: {error.reason}") from error

    data = response.read(MAX_RESPONSE_BYTES + 1)
    if len(data) > MAX_RESPONSE_BYTES:
        raise ProbeError(f"response from {url} exceeds {MAX_RESPONSE_BYTES} bytes")
    return HttpResult(
        url=response.geturl(),
        status=response.status,
        headers=response.headers,
        body=data,
    )


def parse_json(result: HttpResult, label: str) -> dict[str, Any]:
    content_type = result.headers.get("Content-Type", "")
    if "application/json" not in content_type.lower():
        raise ProbeError(f"{label} did not return application/json")
    try:
        value = json.loads(result.body.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as error:
        raise ProbeError(f"{label} returned invalid JSON: {error}") from error
    if not isinstance(value, dict):
        raise ProbeError(f"{label} must be a JSON object")
    return value


def anonymous_probe(url: str, timeout: float) -> HttpResult:
    payload = {
        "jsonrpc": "2.0",
        "id": "oauth-discovery-probe",
        "method": "tools/list",
        "params": {
            "_meta": {
                "io.modelcontextprotocol/protocolVersion": PROTOCOL_VERSION,
                "io.modelcontextprotocol/clientInfo": {
                    "name": "oauth21-mcp-integration-probe",
                    "version": "1.0.0",
                },
                "io.modelcontextprotocol/clientCapabilities": {},
            }
        },
    }
    return request(
        url,
        method="POST",
        timeout=timeout,
        headers={
            "Accept": "application/json, text/event-stream",
            "Content-Type": "application/json",
            "MCP-Protocol-Version": PROTOCOL_VERSION,
            "Mcp-Method": "tools/list",
        },
        body=json.dumps(payload, separators=(",", ":")).encode("utf-8"),
    )


def resource_metadata_candidates(
    mcp: urllib.parse.SplitResult,
    challenge_url: str | None,
) -> list[str]:
    if challenge_url:
        return [urllib.parse.urljoin(origin(mcp), challenge_url)]
    path = mcp.path if mcp.path.startswith("/") else f"/{mcp.path}"
    candidates = [
        f"{origin(mcp)}/.well-known/oauth-protected-resource{path}",
        f"{origin(mcp)}/.well-known/oauth-protected-resource",
    ]
    return list(dict.fromkeys(candidates))


def authorization_metadata_candidates(
    issuer: urllib.parse.SplitResult,
) -> list[str]:
    base = origin(issuer)
    issuer_path = issuer.path.rstrip("/")
    if issuer_path:
        return [
            f"{base}/.well-known/oauth-authorization-server{issuer_path}",
            f"{base}/.well-known/openid-configuration{issuer_path}",
            f"{base}{issuer_path}/.well-known/openid-configuration",
        ]
    return [
        f"{base}/.well-known/oauth-authorization-server",
        f"{base}/.well-known/openid-configuration",
    ]


def fetch_first_json(
    candidates: list[str],
    *,
    timeout: float,
    allow_http: bool,
    allow_private_network: bool,
    label: str,
) -> tuple[str, dict[str, Any]]:
    failures: list[str] = []
    for candidate in candidates:
        validate_url(
            candidate,
            allow_http=allow_http,
            allow_private_network=allow_private_network,
            label=label,
        )
        result = request(
            candidate,
            method="GET",
            timeout=timeout,
            headers={"Accept": "application/json"},
        )
        if result.status == 200:
            return candidate, parse_json(result, label)
        failures.append(f"{candidate} -> HTTP {result.status}")
    raise ProbeError(f"{label} not found: {'; '.join(failures)}")


def validate_endpoint_url(
    value: Any,
    *,
    allow_http: bool,
    allow_private_network: bool,
    label: str,
) -> None:
    if not isinstance(value, str) or not value:
        raise ProbeError(f"{label} is missing")
    validate_url(
        value,
        allow_http=allow_http,
        allow_private_network=allow_private_network,
        label=label,
    )


def run_probe(args: argparse.Namespace) -> Reporter:
    if not args.acknowledge_authorized_target:
        raise ProbeError("--acknowledge-authorized-target is required")
    if args.timeout <= 0 or args.timeout > 60:
        raise ProbeError("--timeout must be greater than 0 and at most 60")

    reporter = Reporter()
    mcp = validate_url(
        args.mcp_url,
        allow_http=args.allow_http,
        allow_private_network=args.allow_private_network,
        label="MCP URL",
    )
    canonical_mcp_url = urllib.parse.urlunsplit(mcp)
    reporter.pass_("target_safety", f"authorized target accepted: {canonical_mcp_url}")

    anonymous = anonymous_probe(canonical_mcp_url, args.timeout)
    if anonymous.status != 401:
        reporter.fail(
            "anonymous_challenge",
            f"expected HTTP 401, received HTTP {anonymous.status}",
        )
    else:
        reporter.pass_("anonymous_challenge", "anonymous MCP request returned HTTP 401")

    challenges = anonymous.headers.get_all("WWW-Authenticate") or []
    challenge_text = ",".join(challenges)
    challenge_match = RESOURCE_METADATA_RE.search(challenge_text)
    challenge_url = None
    if challenge_match:
        challenge_url = challenge_match.group(1) or challenge_match.group(2)
        reporter.pass_(
            "bearer_challenge",
            "WWW-Authenticate includes Bearer resource_metadata",
        )
    else:
        reporter.warn(
            "bearer_challenge",
            "resource_metadata missing; trying RFC 9728 well-known fallbacks",
        )

    candidates = resource_metadata_candidates(mcp, challenge_url)
    if challenge_url:
        challenge_parsed = validate_url(
            candidates[0],
            allow_http=args.allow_http,
            allow_private_network=args.allow_private_network,
            label="resource metadata URL",
        )
        if origin(challenge_parsed) != origin(mcp):
            raise ProbeError("resource metadata URL must use the MCP server origin")

    prm_url, prm = fetch_first_json(
        candidates,
        timeout=args.timeout,
        allow_http=args.allow_http,
        allow_private_network=args.allow_private_network,
        label="protected resource metadata",
    )
    reporter.pass_("protected_resource_metadata", f"loaded {prm_url}")

    resource = prm.get("resource")
    if resource == canonical_mcp_url:
        reporter.pass_("canonical_resource", "PRM resource exactly matches MCP URL")
    elif isinstance(resource, str):
        resource_parsed = validate_url(
            resource,
            allow_http=args.allow_http,
            allow_private_network=args.allow_private_network,
            label="PRM resource",
        )
        if origin(resource_parsed) == origin(mcp):
            reporter.warn(
                "canonical_resource",
                f"PRM resource differs from MCP URL: {resource}",
            )
        else:
            reporter.fail(
                "canonical_resource",
                f"PRM resource uses a different origin: {resource}",
            )
    else:
        reporter.fail("canonical_resource", "PRM resource is missing")

    authorization_servers = prm.get("authorization_servers")
    if not (
        isinstance(authorization_servers, list)
        and authorization_servers
        and all(isinstance(value, str) and value for value in authorization_servers)
    ):
        raise ProbeError("PRM authorization_servers must be a non-empty string array")
    reporter.pass_(
        "authorization_servers",
        f"PRM advertises {len(authorization_servers)} authorization server(s)",
    )

    expected_issuer = args.expected_issuer
    if expected_issuer:
        if expected_issuer not in authorization_servers:
            reporter.fail(
                "expected_issuer",
                "expected issuer is not listed in PRM authorization_servers",
            )
        selected_issuer = expected_issuer
    else:
        selected_issuer = authorization_servers[0]

    issuer = validate_url(
        selected_issuer,
        allow_http=args.allow_http,
        allow_private_network=args.allow_private_network,
        label="authorization server issuer",
    )
    metadata_url, metadata = fetch_first_json(
        authorization_metadata_candidates(issuer),
        timeout=args.timeout,
        allow_http=args.allow_http,
        allow_private_network=args.allow_private_network,
        label="authorization server metadata",
    )
    reporter.pass_("authorization_server_metadata", f"loaded {metadata_url}")

    discovered_issuer = metadata.get("issuer")
    if discovered_issuer == selected_issuer:
        reporter.pass_("issuer_binding", "metadata issuer matches exactly")
    else:
        reporter.fail(
            "issuer_binding",
            f"metadata issuer {discovered_issuer!r} does not match {selected_issuer!r}",
        )

    for field in ("authorization_endpoint", "token_endpoint"):
        try:
            validate_endpoint_url(
                metadata.get(field),
                allow_http=args.allow_http,
                allow_private_network=args.allow_private_network,
                label=field,
            )
        except ProbeError as error:
            reporter.fail(field, str(error))
        else:
            reporter.pass_(field, f"{field} is an allowed absolute URL")

    response_types = metadata.get("response_types_supported")
    if isinstance(response_types, list) and "code" in response_types:
        reporter.pass_("authorization_code", "response type code is supported")
    else:
        reporter.fail("authorization_code", "response_types_supported lacks code")

    grant_types = metadata.get("grant_types_supported")
    if grant_types is None:
        reporter.warn(
            "authorization_code_grant",
            "grant_types_supported is omitted; verify authorization_code manually",
        )
    elif isinstance(grant_types, list) and "authorization_code" in grant_types:
        reporter.pass_(
            "authorization_code_grant",
            "authorization_code grant is supported",
        )
    else:
        reporter.fail(
            "authorization_code_grant",
            "grant_types_supported lacks authorization_code",
        )

    pkce_methods = metadata.get("code_challenge_methods_supported")
    if isinstance(pkce_methods, list) and "S256" in pkce_methods:
        reporter.pass_("pkce_s256", "PKCE S256 is advertised")
    else:
        reporter.fail("pkce_s256", "code_challenge_methods_supported lacks S256")

    if metadata.get("client_id_metadata_document_supported") is True:
        reporter.pass_(
            "client_registration",
            "Client ID Metadata Documents are supported",
        )
    elif metadata.get("registration_endpoint"):
        reporter.warn(
            "client_registration",
            "only Dynamic Client Registration is advertised; keep it as a compatibility path",
        )
    else:
        reporter.warn(
            "client_registration",
            "no dynamic registration is advertised; pre-registration is required",
        )

    if metadata.get("authorization_response_iss_parameter_supported") is True:
        reporter.pass_(
            "authorization_response_issuer",
            "authorization response iss support is advertised",
        )
    else:
        reporter.warn(
            "authorization_response_issuer",
            "authorization response iss support is not advertised",
        )

    return reporter


def emit(reporter: Reporter, *, as_json: bool) -> None:
    counts = {
        status: sum(item["status"] == status for item in reporter.results)
        for status in ("PASS", "WARN", "FAIL")
    }
    if as_json:
        print(
            json.dumps(
                {"summary": counts, "checks": reporter.results},
                ensure_ascii=True,
                indent=2,
            )
        )
        return

    for item in reporter.results:
        print(f"[{item['status']}] {item['check']}: {item['detail']}")
    print(
        "Summary: "
        f"{counts['PASS']} passed, {counts['WARN']} warnings, {counts['FAIL']} failed"
    )


def main() -> int:
    args = parse_args()
    try:
        reporter = run_probe(args)
    except ProbeError as error:
        if args.json:
            print(
                json.dumps(
                    {
                        "summary": {"PASS": 0, "WARN": 0, "FAIL": 1},
                        "checks": [
                            {
                                "status": "FAIL",
                                "check": "probe",
                                "detail": str(error),
                            }
                        ],
                    },
                    ensure_ascii=True,
                    indent=2,
                )
            )
        else:
            print(f"[FAIL] probe: {error}", file=sys.stderr)
        return 2

    emit(reporter, as_json=args.json)
    return 1 if reporter.failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
