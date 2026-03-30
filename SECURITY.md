# Security Policy

## Supported Versions

| Version | Supported |
|---------|-----------|
| 1.x     | ✅        |

## Reporting a Vulnerability

Please **do not** open a public GitHub issue for security vulnerabilities.

Send an email to **m.nalbandi.r@gmail.com** with:
- Description of the vulnerability
- Steps to reproduce
- Potential impact
- Suggested fix (optional)

Expect acknowledgement within 48 hours and a resolution or status update within 14 days.

## Security Considerations

- **Malformed ZPL** — all exceptions are caught and logged; the renderer returns a partial image rather than crashing
- **Large `^GFA` bitmaps** — `totalBytes` is declared in the ZPL header; validate untrusted input before passing to the renderer to avoid excessive memory allocation
- **No network calls** — the renderer is fully offline; no data leaves the process
