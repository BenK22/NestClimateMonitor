# Security policy

## Reporting a vulnerability

Do not put credentials, authorization URLs, tokens, personal device identifiers or private
logs in public issues. Report suspected vulnerabilities privately to the maintainer at
[benjkar@hotmail.com](mailto:benjkar@hotmail.com), using the subject “Nest Climate Monitor security”.
Describe the affected version, impact and minimal reproduction using synthetic data. Do not
send live secrets or APK signing material. Agree on a safe channel before sharing sensitive details.

This is a volunteer, experimental personal-use project. Reports are reviewed on a best-effort
basis; there is no response-time or security-support guarantee. Coordinated disclosure is
appreciated. The latest published version is the only release considered for fixes; older versions
are not maintained separately. A prerelease is not a production-security certification.

## Security boundaries

The app is read-only. It holds user-supplied Nest OAuth credentials on the phone and makes direct
requests to Google and Open-Meteo. It has no backend. Keystore encryption and backup exclusions
reduce accidental disclosure; they do not protect an unlocked, compromised or rooted phone.
See [PRIVACY.md](PRIVACY.md) and the [architecture guide](docs/ARCHITECTURE.md).

If a credential leaks, revoke or rotate it through Google and reconnect. Removing it from the
latest Git revision does not remove it from history or invalidate it. A lost release signing key
cannot be recovered from an APK: keep an encrypted off-machine backup, separate from its passwords.

Fork pull-request checks have read-only permissions and no SDK/signing secrets. Only trusted
release builds receive private inputs; a separate publishing job receives repository write access.
Full-length action commit pins and SDK/wrapper hashes are reviewed inputs, not a guarantee that
dependencies contain no vulnerabilities. Dependency updates still need review and tests.
