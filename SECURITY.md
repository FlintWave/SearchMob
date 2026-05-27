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
- Bypasses of the loopback-only binding (the local server must never be reachable off-device until
  the opt-in network mode ships, and even then only as configured).
- Weaknesses in encryption-at-rest or the optional zero-knowledge mode.
- Supply-chain issues in dependencies or CI.

## Good to know

- The app contains no telemetry and collects no analytics or device identifiers.
- Releases are signed; verify checksums/signatures on downloaded artifacts.
- Third-party GitHub Actions are pinned by commit SHA.
