# Security Policy

SearchMob handles people's search activity, so we take security and privacy seriously.

## Reporting a vulnerability

**Please do not open a public issue for security vulnerabilities.**

Use GitHub's private vulnerability reporting:
**Security → Report a vulnerability** (Private Vulnerability Reporting) on this repository.

Please include: a description, steps to reproduce, affected version/commit, and impact. We aim to
acknowledge reports within 7 days and to coordinate a fix and disclosure timeline with you.

If you cannot use GitHub's private reporting, contact the maintainer at **flintwave@tuta.com**. Email
is not encrypted in transit by default, so use it only to request a secure channel, not to send the
vulnerability details themselves.

## Scope

In scope: the SearchMob app, its local HTTP server, storage/encryption, and the build/release
pipeline. Of particular interest:

- Anything that leaks user queries or identity to upstream engines or third parties.
- Bypasses of the loopback-only binding. By default the local server binds to loopback (127.0.0.1)
  only and must never be reachable off-device. An opt-in network mode now exists: it is OFF by
  default and, before it can be enabled, the user must confirm an explicit warning. When it is on,
  the server binds to all interfaces (0.0.0.0) so it is reachable on the local network, and it has no
  authentication, so anyone who can reach the device on that network can run searches through it. A
  bypass that exposes the server off-device without the user enabling network mode is in scope.
- Weaknesses in encryption-at-rest or the optional zero-knowledge mode.
- Supply-chain issues in dependencies or CI.

## Good to know

- The app contains no telemetry and collects no analytics or device identifiers.
- The only outbound traffic is the searches you run, plus an optional launch-time update check that
  queries the GitHub Releases API (https://api.github.com/repos/FlintWave/SearchMob/releases/latest)
  about once a day to see whether a newer version exists. It is on by default, routed through the same
  privacy proxy as searches (no cookies, stripped headers, rotated User-Agent), sends no query or
  identifier, never auto-downloads or auto-installs, and can be turned off in Settings.
- Releases are signed; verify checksums/signatures on downloaded artifacts.
- Third-party GitHub Actions are pinned by commit SHA.
