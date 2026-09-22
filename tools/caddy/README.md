# Bundled Caddy

This directory contains unmodified Caddy release archives used by the local
development setup scripts.

- Version: see `VERSION`
- Source: https://github.com/caddyserver/caddy/releases
- License: Apache-2.0
- Integrity: verified against the bundled official SHA-512 checksums file

Included targets:

- macOS arm64
- macOS amd64
- Windows amd64
- Windows arm64

The setup scripts extract only the archive for the current platform into the
git-ignored `.local-tools` directory. Caddy creates a separate local CA on each
machine. Generated certificates, CA private keys, and extracted binaries must
not be committed.

Prepare the local binary and trust its CA once:

```bash
./scripts/setup-https.sh
```

```powershell
.\scripts\setup-https.ps1
```

The setup uses `trust.Caddyfile` only to create and trust the machine-local CA.
It does not configure or start the application.
