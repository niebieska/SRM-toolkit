# Deployment security preparation

## Implemented safeguards

- Root `.env` is no longer tracked. The existing local file is retained and
  ignored. Git history still contains previously committed values; deleting the
  current file does not make those values safe to reuse.
- Registration and Biuro database configuration no longer supplies fallback
  passwords. Compose also rejects missing/empty required database/service secrets.
- Email-service requires HTTP Basic authentication for POST `/api/email/send`.
  Other routes/methods are denied. Registration supplies a dedicated service
  credential; missing/blank usernames/passwords prevent startup.
- `EMAIL_SERVICE_USERNAME` defaults to `registration-email-sender` and
  `EMAIL_SERVICE_PASSWORD` is required on Registration and email-service. Use a
  unique random ASCII password, within BCrypt's 72-byte limit. Never reuse the
  management/Turnus lookup passwords or give this credential to a browser.
- Caller variables cannot replace reserved link/contact configuration in the
  renderer. Corrected the properties prefix to match the existing `templates`
  application configuration; binding is tested against that real configuration.
  The three allowed email templates escape caller text; current hardcoded
  template contact/link content remains unchanged.
- Disabled SMTP still requires service authentication. This credential differs
  from `MAIL_USER`/`MAIL_PASSWORD`, which authenticate to the SMTP provider.
- The Registration frontend proxy exposes only POST participant/staff submissions
  and GET Turnus catalogue/staff-options, including the existing Turnus aliases.
  Management paths return 404 even with valid Basic credentials; inappropriate
  methods on public paths return 405. Biuro's authenticated backend calls use the
  direct private Registration URL and remain available.
- APIs, database and MailHog SMTP stay unpublished; frontends and MailHog UI bind
  to loopback. This remains a local/private baseline until HTTPS is configured.
- Authentication failure does not roll back a saved registration/status, trigger
  a resend or expose HTTP/SMTP passwords in notification logs.

## Required operator actions before external TEST

Provision fresh, distinct values for the GCP deployment:

- MariaDB root, Registration and Biuro database passwords.
- Biuro JWT signing secret (at least 32 UTF-8 bytes).
- Registration management, Turnus lookup and email-service passwords.
- First-admin temporary password and any test-user credentials.
- SMTP password if it was ever exposed/committed and real SMTP will be used.

Do not copy values from the old `.env` or Git history. Store deployment values in
a private file outside the checkout with restrictive permissions, or an approved
secret store. Never put them in frontend variables, command arguments, logs or
chat. The public template contains empty secret placeholders only.

For a fresh isolated database, new credentials are provisioned at first startup.
For an existing database, changing the env file does not rotate its user passwords:
back up data, coordinate password changes using the database administrator, update
the private configuration and recreate affected services. Do not delete an existing
volume to change a password. Test restore separately.

Changing the JWT signing secret invalidates existing browser tokens; users must
sign in again. Coordinate both sides of each service-password change. Deploy
Registration and email-service together when adding this authentication.

Disable bootstrap and remove its temporary password after first-admin setup.
An existing administrator remains available in the database. Use individual Biuro
accounts and grant the client only the roles/Turnusy they need.

Untracking the file is implemented; rotation of real/external credentials has not
been performed automatically and must not be marked complete without confirmation.
No Git history rewriting is included.

## Remaining before public client access

- GCP host, static public IP and restricted administrative access.
- Public HTTPS routing for Registration and Biuro, trusted IP certificates and
  automatic renewal. No certificate-warning bypasses for the client.
- Correct trusted proxy/scheme handling and deployed-origin verification.
- Public entry-point abuse controls, including login/submission rate limits,
  configured with the actual proxy/client-IP topology.
- Firewall exposure limited to intended frontend/certificate-validation ports.
- Backups/restore and deployed permission/workflow acceptance with synthetic data.

Keep MailHog for the initial test until real-provider acceptance is deliberately
enabled. See the deployment checklist for the remaining operational gates.

## Verification — 2026-10-18

- Final backend suites: 363 passed, zero failures/errors; 11 optional MariaDB cases
  skipped. Registration has 196 cases, Biuro 136 and email-service 42.
- Four checked-in public proxy regressions passed against the isolated synthetic
  stack, including valid-credential management denial, method restrictions and
  preserved catalogue/submission validation.
- Live email calls with missing/malformed/wrong/other-service credentials were
  rejected without dispatch. Unknown templates remain rejected after authentication.
- A synthetic registration, duplicate rejection, Biuro acceptance and Turnus list
  access worked, with confirmation/status mail received only by MailHog.
- All five application images rebuilt. The final email image also sent escaped
  template text safely; caller link/contact overrides did not enter the HTML.
- Private env file retention, Git untracking, empty template secrets, Compose
  configuration, shell/Node syntax and whitespace checks passed.
- No GCP resources, public listener or external SMTP were used. No real credentials
  were rotated. Verification containers were stopped and test volumes retained.
  Changes are recorded in separate implementation, regression-test and documentation commits
  on `feature/test-deployment-packaging`; no push or public deployment was performed.
