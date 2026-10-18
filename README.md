# SRM-toolkit

Project plans, progress notes, reviews and deployment guidance are in [docs/](docs/README.md).

## Local environment configuration

[.env.example](.env.example) documents the current backend variables, database and
service credentials, first-admin setup, supplied SMTP settings and public Biuro
frontend URL. Copy it to a private file outside the repository, fill required
secrets, and explicitly export it in each backend's startup terminal as described
in the template. Maven does not load `.env` automatically. The existing tracked
root `.env` is not a place for new secrets. Real SMTP sending is disabled in the
draft until deliberately enabled. For the local Docker stack, see
[local container testing](docs/Local-container-testing.md); that stack uses MailHog.


## Registration management security

Public requests remain available without credentials:

- `POST /api/registrations/participant`
- `POST /api/registrations/staff`
- `GET /api/turnuses` and `GET /api/turnusy`
- `GET /actuator/health` (status only; no component or health details)

All registration list/detail/status endpoints, including participant/staff lists,
require the dedicated Biuro service account through HTTP Basic authentication.
Biuro operator authentication uses JWTs; service credentials belong only to the
backend processes and must never enter frontend bundles.
Other registration-api routes are denied by default. Only Actuator health is exposed.

Set the same `REGISTRATION_SERVICE_PASSWORD` for registration-api and biuro-api.
There is no default password: missing or blank values prevent startup.
`REGISTRATION_SERVICE_USERNAME` defaults to `biuro-service` and must also match.
Use a randomly generated password (for example, `openssl rand -hex 32`), inject it
through your environment or secret manager, and do not commit it to the repository.
For local development, export these variables before starting both services.
Compose passes them to both backends and requires the password to be set.

Use TLS or an isolated trusted container network for backend service traffic:
HTTP Basic credentials are encoded, not encrypted. Production routing should expose
public registration routes only and keep management routes internal as an additional
boundary. The local Compose stack binds frontends/MailHog to loopback and keeps
backend/database ports unpublished; external TEST deployment remains separate.

This change does not rotate existing admin/JWT credentials or remove the previously
tracked `.env`; those require the separate secrets-hardening change.

## Biuro account implementation checkpoint

On `feature/biuro-accounts`, the backend replaces the shared configured login with
stored accounts and revocable sessions. `JWT_SECRET` is required; first SYS_ADMIN
setup and API contracts are documented in [Phase 1](docs/Biuro-accounts-phase-1.md).
Account-management APIs, VIEWER assignments and audit are documented in
[Phase 2](docs/Biuro-accounts-phase-2.md). Account management, VIEWER navigation and
password/session UI are implemented in the [frontend phase](docs/Biuro-accounts-frontend.md);
manual browser/deployment acceptance remains pending.
Existing shared-user tokens
are invalidated; `BIURO_USERNAME`/`BIURO_PASSWORD` no longer configure operator login.
