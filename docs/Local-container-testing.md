# Local container packaging — Phase 1

This phase makes the complete toolkit runnable locally in containers. It preserves
the existing Maven/Vite development workflow. It does not enable public TEST access,
HTTPS or real SMTP, rotate previously committed secrets, or replace the deployment
checklist.

## Prerequisites and private configuration

Use Docker Engine and Docker Compose v2 (`docker compose version`). Legacy Python
`docker-compose` 1.x is not supported. Run all commands below from the repository
root. Building needs access to container, Maven and npm registries. Images use Java 21
(Temurin), Maven 3.9, Node 22, nginx stable Alpine, MariaDB 11.4 and MailHog 1.0.1.

Copy `.env.example` to a private file outside the repository. Fill these values
with new local test credentials:

- `DB_ROOT_PASSWORD`, `DB_PASSWORD`, `BIURO_DB_PASSWORD`
- `REGISTRATION_SERVICE_PASSWORD`, `TURNUS_LOOKUP_PASSWORD`
- `JWT_SECRET` (at least 32 UTF-8 bytes)

For a fresh database also set `BIURO_BOOTSTRAP_ENABLED=true`,
`BIURO_BOOTSTRAP_USERNAME`, `BIURO_BOOTSTRAP_DISPLAY_NAME` and
`BIURO_BOOTSTRAP_PASSWORD` (at least 12 characters, at most 72 UTF-8 bytes).
The initial account has SYS_ADMIN only; change its temporary password and grant
OPERATOR to administer Turnusy/registrations. Disable bootstrap and remove its
password from the private file after setup, then recreate Biuro.

Keep Registration and Biuro database usernames distinct. The Biuro username must
contain ASCII letters, digits or underscores and must not be `root`.
Both schemas and their users are provisioned only when the named MariaDB volume
is first initialized. Environment changes do not rotate passwords or repair an
existing volume. Each application runs its own Flyway migrations. There is no
automatic baseline of an existing database.

Use an explicit `--env-file` on every invocation; do not rely on the tracked root
`.env`. Exported shell variables take precedence over an env file, so use a clean
terminal if you previously exported credentials. Do not print resolved `config`
output with real secrets; use `config --quiet`.

```bash
docker compose --env-file "$HOME/.config/srm/containers.env" -p srm-local config --quiet
docker compose --env-file "$HOME/.config/srm/containers.env" -p srm-local build
docker compose --env-file "$HOME/.config/srm/containers.env" -p srm-local up -d --wait --wait-timeout 240
docker compose --env-file "$HOME/.config/srm/containers.env" -p srm-local ps
```

Use a distinct project name for isolation. Do not reuse the volume of an existing
development deployment. Builds skip Maven tests; run the documented backend and
frontend regression suites separately before release.

## Services and routing

- Public form: <http://localhost:5173>
- Biuro: <http://localhost:5174>
- MailHog messages: <http://localhost:8025>
- No database, SMTP or backend API ports are published to the host.
- Both frontend images build with `VITE_API_BASE_URL=/`. Nginx proxies `/api/`
  to the respective API, preserving authentication and HTTP status codes.
  Browser calls remain on the same origin. SPA deep links return the app shell.
- MariaDB must be healthy before Biuro starts. Registration then waits for Biuro
  and email-service; this avoids a circular startup dependency. Biuro needs
  Registration only when an operator requests registration data.
- API health checks verify TCP readiness, not all workflows or catalogue freshness.
  Use the smoke checks below for application-level readiness.
- The local email-service always targets MailHog with SMTP auth/TLS disabled,
  even when the private file contains real-provider settings. No external mail is
  sent. Real SMTP testing remains a separate controlled Maven/provider exercise.
- Backend images run as a non-root numeric user. Build contexts exclude `.env`
  files, Git metadata, local build outputs and common private-key files.

## Smoke checks

1. Open both frontends and refresh a nested route (`/uczestnik` on Registration).
   Check that browser requests use the frontend's `/api/` URL, not a host API port.
2. Log in with the bootstrap account, change the password and grant OPERATOR.
   Confirm unauthorized management requests are rejected.
3. Verify Turnus options load through Registration. Create/open a synthetic Turnus
   in Biuro if needed; remember the Registration catalogue's hourly refresh.
4. Submit a synthetic registration, inspect its confirmation in MailHog, process
   its status in Biuro and inspect the status email. Check lists/export.
5. Stop/start the same project; confirm accounts, Turnusy and registrations remain.
   Do not delete the volume to test a restart.

To stop without deleting data:

```bash
docker compose --env-file "$HOME/.config/srm/containers.env" -p srm-local down
```

Do not use `down --volumes` unless deliberately discarding that project's test
database. Backup/restore and any existing-data migration need their own reviewed
procedure.

## Remaining deployment phases

Private env files and unpublished ports are a local packaging baseline. Before
external access: remove tracked secrets/rotate exposed values, authenticate
email-service callers, configure HTTPS and deployed origins/routing, verify proxy
timeouts, establish backups/restore and run full permissions/workflow acceptance.
Image tags are version-family tags; digest pinning and an upgrade policy remain
deployment follow-up work. No data from host MariaDB is mounted into this stack.

## Verification recorded 2026-10-17

All five application images built and the seven-service stack started under
Compose v2.39.4 with synthetic credentials and isolated test ports/volumes.
HTTP smoke checks passed for app shells/deep links, protected routes,
bootstrap/password change/role grant, catalogue/staff options, registration,
duplicate rejection, acceptance, lists and two MailHog messages. Container
recreation retained accounts/roles, Turnusy and the accepted registration.
Nginx syntax, built API base replacement, non-root Java users, unpublished API/DB
ports, missing-credential refusal, shell syntax and whitespace checks passed.
The five Registration frontend Node test files passed. No external SMTP or
host database was used. No manual browser visual/interaction acceptance or export
file inspection was performed in this phase. Test containers were stopped and
the test volumes retained. Existing npm advisories remain outside this change.
