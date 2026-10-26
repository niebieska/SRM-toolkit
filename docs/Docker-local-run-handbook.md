# Docker local-run handbook

This handbook covers running and monitoring the SRM toolkit locally with Docker
Compose. It is for an isolated local/test environment. It does not describe GCP
deployment or enable public access.

## 1. Before starting

Run commands from the repository root. Use Docker Compose v2 (`docker compose`),
not the legacy Python `docker-compose` command. Confirm the plugin is available:

```bash
docker compose version
docker version
```

Keep a private Compose env file outside the repository. The existing
[local container guide](Local-container-testing.md#prerequisites-and-private-configuration)
describes the required local credentials. Set these shell variables once in the
terminal you will use for the commands below; change the path or project name to
match your existing local stack:

```bash
SRM_ENV_FILE="/tmp/srm-toolkit-local-test/compose.env"
SRM_PROJECT="srm-local-test-20261026"
```

These values match the local stack used for this test setup. If you move the env
file, update `SRM_ENV_FILE`; keep `SRM_PROJECT` unchanged to reconnect to the
existing database. A different project name creates a separate stack with a new,
empty MariaDB volume. Always reuse the same project name when returning to a stack.
Use synthetic registrations and test credentials only. Do not paste the env file
or resolved Compose configuration into chat or logs.

## 2. Validate, build, and start

Validate configuration without printing resolved values or starting services:

```bash
docker compose --env-file "$SRM_ENV_FILE" -p "$SRM_PROJECT" -f docker-compose.yml config --quiet
```

Build the application images only; this does not start or recreate containers:

```bash
docker compose --env-file "$SRM_ENV_FILE" -p "$SRM_PROJECT" -f docker-compose.yml build
```

Start or reconcile the seven services, waiting for their configured health
checks:

```bash
docker compose --env-file "$SRM_ENV_FILE" -p "$SRM_PROJECT" -f docker-compose.yml up -d --wait --wait-timeout 240
```

`up` may recreate a container when its image or configuration changes, but it
does not delete the named MariaDB volume. Reuse the same `SRM_PROJECT` to attach
to the same volume. Only explicitly deleting the volume (for example with
`down --volumes`) removes the database data.

The services are MariaDB, Biuro API, email-service, Registration API, both
frontends, and MailHog. Database and backend API ports are not published to the
host. Frontend ports bind to loopback only:

- Registration: <http://localhost:5173>
- Biuro: <http://localhost:5174>
- MailHog: <http://localhost:8025>

MailHog displays messages only when the default Compose configuration is used.

## 3. Check service status and health

List services, container state, health and published ports:

```bash
docker compose --env-file "$SRM_ENV_FILE" -p "$SRM_PROJECT" -f docker-compose.yml ps --all
```

Expected state after startup: all seven services are running; MariaDB, both APIs,
email-service, and both frontends report `healthy`. MailHog reports `healthy` if
its health check has completed.

The health checks establish basic readiness (MariaDB initialization or a local
TCP connection). They do not verify that a user can sign in, that the Turnus
catalogue is current, or that an SMTP server accepted and delivered an email.
Use the workflow checks in the next section for those behaviors.

Inspect resource use for this Compose project:

```bash
docker stats --no-stream $(docker compose --env-file "$SRM_ENV_FILE" -p "$SRM_PROJECT" -f docker-compose.yml ps -q)
```

For a live view, remove `--no-stream` and press `Ctrl+C` to stop monitoring.

## 4. Follow logs

Follow all application logs:

```bash
docker compose --env-file "$SRM_ENV_FILE" -p "$SRM_PROJECT" -f docker-compose.yml logs --follow --tail=100 biuro-api registration-api email-service mariadb
```

Stop following logs with `Ctrl+C`; this does not stop the containers. To inspect
only recent errors from one service, for example email-service:

```bash
docker compose --env-file "$SRM_ENV_FILE" -p "$SRM_PROJECT" -f docker-compose.yml logs --since=15m email-service
```

Logs may contain personal data such as recipient addresses or registration
identifiers. Keep access restricted and redact those details before sharing log
snippets. Never share passwords, tokens, or resolved Compose configuration.

## 5. Verify the local application flow

1. Open both frontends and confirm their pages load.
2. Sign in to Biuro with the local bootstrap account, if bootstrap was enabled for
   this new database. Change its temporary password after first sign-in.
3. Check that Turnus options load in the Registration form.
4. Submit a synthetic registration with an address you control.
5. Process it in Biuro and inspect the resulting email.
6. Check list and export behavior if those are part of the current verification.

See [Local container testing](Local-container-testing.md#smoke-checks) for the
complete smoke checklist and the public-proxy regression command.

## 6. Email modes

### Default: MailHog

The base Compose file configures email-service to send to MailHog at
`mailhog:1025`. No external email is sent. Open <http://localhost:8025> to inspect
messages.

### Switch an existing project between email modes

The base Compose file hardcodes MailHog. Values such as `MAIL_HOST` in the env
file do not change that by themselves. The SMTP override selects the external
provider. Keep the same project name while changing modes so the same MariaDB
volume is reused.

Switch the selected project to external SMTP (this enables real email sending):

```bash
docker compose --env-file "$SRM_ENV_FILE" -p "$SRM_PROJECT" -f docker-compose.yml -f docker-compose.external-smtp.yml up -d --no-deps email-service
```

Switch it back to MailHog:

```bash
docker compose --env-file "$SRM_ENV_FILE" -p "$SRM_PROJECT" -f docker-compose.yml up -d --no-deps email-service
```

These commands recreate only email-service and do not rebuild its image or
delete the database volume. They do not send an email by themselves; subsequent
application email actions use the selected route.

### Explicit external SMTP test

The `docker-compose.external-smtp.yml` override routes email-service through the
SMTP provider configured in the private env file. Include this override on every
Compose command for this project while using that mode. For example, validate:

```bash
docker compose --env-file "$SRM_ENV_FILE" -p "$SRM_PROJECT" -f docker-compose.yml -f docker-compose.external-smtp.yml config --quiet
```

It enables real sending. Use only addresses you control. Starting or rebuilding
the containers does not itself send an email, but a later registration or status
action can. MailHog may still appear as running because the base stack retains
that service; its health does not indicate where email-service sends mail.

Check the active SMTP settings without showing the password:

```bash
docker compose --env-file "$SRM_ENV_FILE" -p "$SRM_PROJECT" -f docker-compose.yml -f docker-compose.external-smtp.yml exec -T email-service sh -c 'printf "MAIL_HOST=%s\\nMAIL_PORT=%s\\nMAIL_SMTP_AUTH=%s\\nMAIL_STARTTLS=%s\\n" "$MAIL_HOST" "$MAIL_PORT" "$MAIL_SMTP_AUTH" "$MAIL_STARTTLS"'
```

Check whether both participant payment settings are present without printing the
bank account or amount:

```bash
docker compose --env-file "$SRM_ENV_FILE" -p "$SRM_PROJECT" -f docker-compose.yml -f docker-compose.external-smtp.yml exec -T email-service sh -c 'test -n "$PARTICIPANT_BANK_ACCOUNT" && echo PARTICIPANT_BANK_ACCOUNT=set || echo PARTICIPANT_BANK_ACCOUNT=empty; test -n "$PARTICIPANT_DEPOSIT_AMOUNT" && echo PARTICIPANT_DEPOSIT_AMOUNT=set || echo PARTICIPANT_DEPOSIT_AMOUNT=empty'
```

The payment block is rendered for a participant acceptance email only when both
settings are populated. A previously sent email does not change after a
configuration update. Recreate email-service and trigger a new participant
acceptance notification to verify the updated template.

## 7. Apply configuration or code changes

After changing Compose environment settings, recreate the affected service. For
example, with the whole stack already running, rebuild and recreate email-service
without restarting its dependencies:

```bash
docker compose --env-file "$SRM_ENV_FILE" -p "$SRM_PROJECT" -f docker-compose.yml -f docker-compose.external-smtp.yml up -d --build --no-deps email-service
```

Omit the external SMTP file when using MailHog. If the build fails, the new image
was not applied; check the error and confirm the service is running the expected
image before testing. A registry HTTP 502 during Maven dependency download is a
temporary download failure; retry the build when the registry is available.

After recreating a service, inspect its status and recent logs:

```bash
docker compose --env-file "$SRM_ENV_FILE" -p "$SRM_PROJECT" -f docker-compose.yml -f docker-compose.external-smtp.yml ps
docker compose --env-file "$SRM_ENV_FILE" -p "$SRM_PROJECT" -f docker-compose.yml -f docker-compose.external-smtp.yml logs --since=10m email-service
```

Use the same project name and volume when restarting. Changing database passwords
in the env file does not rotate passwords already stored in an initialized
MariaDB volume.

## 8. Stop, restart, and clean up

Stop the project but retain the containers and database volume. For the default
MailHog setup:

```bash
docker compose --env-file "$SRM_ENV_FILE" -p "$SRM_PROJECT" -f docker-compose.yml stop
```

For a stack started with the external SMTP override, use the same override when
stopping it:

```bash
docker compose --env-file "$SRM_ENV_FILE" -p "$SRM_PROJECT" -f docker-compose.yml -f docker-compose.external-smtp.yml stop
```

`stop` does not send email and does not delete the database volume.

Start it again:

```bash
docker compose --env-file "$SRM_ENV_FILE" -p "$SRM_PROJECT" -f docker-compose.yml up -d --wait --wait-timeout 240
```

Use the external SMTP override on these commands if that is how the stack was
created. `down` removes containers and the project network but keeps the named
database volume. **Do not add `--volumes` unless you intend to permanently delete
the local test database**, including accounts, Turnusy and registrations.

## 9. Common issues

| Symptom | What to check |
| --- | --- |
| `docker compose` is unknown | Install the Docker Compose CLI plugin; legacy `docker-compose` 1.x is unsupported. |
| `config` says a required variable is missing | Check the private env file path and required entries; use `config --quiet` to avoid printing secrets. |
| UI opens but API requests fail | Check `ps`, then logs for `biuro-api` or `registration-api`; frontend ports are 5173 and 5174. |
| No initial Biuro account | Bootstrap only runs when enabled and the database setup is incomplete. Check Biuro startup logs and bootstrap settings; retrieve the temporary password only from the private env file. |
| No email in MailHog | Confirm the stack is using the base Compose file without external SMTP override, then inspect email-service logs. |
| External SMTP connection/authentication fails | Check DNS/connectivity from the host and verify non-secret SMTP settings in the container. A healthy email-service TCP check does not prove SMTP connectivity or delivery. |
| Participant acceptance email has no payment details | Confirm both payment settings are populated, the updated email-service image was rebuilt, and the new email is for a participant. Previous messages are unchanged; staff acceptance emails omit participant payment details. |
| Maven download returns HTTP 502 | This is a registry response during build, not necessarily a source error. Retry the build; verify it completes before assuming the image changed. |
| Data appears missing after a restart | Check the Compose project name. A different name selects a different MariaDB volume. |

## Scope boundary

This handbook covers local Docker use with synthetic data. It does not authorize
or document starting services on GCP, exposing ports, applying production
credentials, or opening public access. See the [deployment checklist](deployment-checklist.md)
for those separately reviewed deployment tasks.
