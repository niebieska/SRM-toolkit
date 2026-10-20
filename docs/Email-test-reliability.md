# Email reliability for controlled TEST

Scope agreed 2026-10-17: make existing SMTP testing honest and bounded before
adding operator resend controls. This is not an outbox, delivery-history or retry
implementation. Manual resend from Biuro is tracked in
[the future backlog](Future-product-backlog.md#communication).

## Behaviour

- Successful email-service calls return HTTP 200, `success=true`, and
  `Message accepted by SMTP server.` This confirms SMTP acceptance, not inbox
  delivery or the absence of a later bounce/spam classification.
- `MAIL_ENABLED=false` returns HTTP 200, `success=false`, and an explicit disabled
  message. No SMTP connection is attempted. Template/request validation still runs.
- SMTP/connection/authentication/rendering errors return a non-success response;
  internal exception messages are not exposed. A 5xx may follow partial/uncertain
  SMTP acceptance, so no automatic resend follows it. Invalid requests/templates
  remain HTTP 400 with a safe message.
- Registration reads the response body. `success=false`, missing/invalid response,
  HTTP error, connection failure and timeout never become a false success log.
- Registration confirmations and status updates keep their existing API contracts.
  The registration/status save happens before notification. Email failure does not
  undo it or cause a retry of the registration/status operation.
- No automatic email retries are added. Minor staff's person and guardian remain
  separate intended notifications; failure of the first HTTP email call does not
  prevent the second recipient attempt.

Deploy the updated Registration and email-service versions together: older
email-service code could report success after swallowing an SMTP failure.

## Configuration

Use the commented root [.env.example](../.env.example), filling secrets only in a
private file. Maven needs explicitly exported variables; it does not load `.env`.
The old Compose SMTP overrides still need separate correction; use direct Maven
runs for this test. No actual SMTP credentials are committed by this work.

| Variable | Consumer | Default | Meaning |
|---|---|---|---|
| MAIL_CONNECT_TIMEOUT_MS | email-service | 5000 | SMTP connection wait, milliseconds |
| MAIL_READ_TIMEOUT_MS | email-service | 10000 | SMTP socket read wait, milliseconds |
| MAIL_WRITE_TIMEOUT_MS | email-service | 10000 | SMTP socket write wait, milliseconds |
| EMAIL_CONNECT_TIMEOUT | registration-api | PT5S | HTTP connection wait to email-service |
| EMAIL_READ_TIMEOUT | registration-api | PT30S | HTTP response read wait from email-service |

Timeouts must be positive and bounded to integer milliseconds. SMTP settings use
integer milliseconds; Registration HTTP settings use ISO-8601 durations. SMTP
validation reads the effective JavaMail session properties, including bracket-map
and externally supplied Session overrides; separate duplicate values are not used. They
bound individual operations, not a total workflow deadline. Multiple SMTP command
exchanges/two recipients can take longer than one timeout value. If HTTP times out,
email-service may still complete SMTP sending; treat the outcome as unconfirmed.

The default inner HTTP connection/read waits total 35 seconds, leaving 25 seconds
inside Biuro's 60-second Registration read wait for database/template work
and response transfer. If overriding these settings, review the enclosing Biuro/
reverse-proxy/browser deadlines together. Biuro’s read wait is defined in
`registration.api.read-timeout` in its `application.yml`; the budget regression
loads that production file as a test resource, rather than repeating its value. This headroom addresses the stalled-email
path, not a guarantee against arbitrary slow DB/DNS/request-write operations. Public
minor-staff submissions can make two sequential notification calls; their total
request duration is not limited to a single email wait.

For local MailHog, keep `MAIL_HOST=localhost`, `MAIL_PORT=1025`, blank SMTP
credentials, `MAIL_SMTP_AUTH=false`, `MAIL_STARTTLS=false`, `MAIL_ENABLED=true`.
For the supplied real server use the private mailbox credentials and provider
settings on port 587; STARTTLS is assumed until verified. Do not disable certificate
checking to work around TLS errors. Keep test recipients under your control.

## Logs and diagnosis

Registration logs the registration code and allowlisted notification template:

- `outcome=SMTP_ACCEPTED`: email-service reported SMTP acceptance.
- `outcome=NOT_SENT`: missing recipient, service-reported disabled/not-sent result,
  or rejected HTTP request. Check the request and service configuration.
- `outcome=UNCONFIRMED`: transport/server error, timeout or invalid response.
  Investigate before any deliberate resend; duplication remains possible.

Registration logs omit recipients, rendered HTML, payload variables, provider
response bodies and raw exception messages/stack traces. Preparation failures
identify code/template and exception type. Email-service failure logs include the
recipient, template and full exception with nested causes for SMTP diagnosis.
These restricted server logs may contain provider details and personal data;
API error responses remain generic. Passwords and rendered payloads are not
explicitly logged. Registration provides the code/template correlation.
Successful SMTP sends log recipient, template, registration code and the MIME
Message-ID when available. This records server acceptance, not inbox delivery.

Operators still have no failure marker/history/resend action in Biuro. During
controlled TEST, technical support monitors logs and mailbox/provider evidence.
Absence from the sender's Sent folder is not failure proof: this phase does not
save IMAP Sent-folder copies. Logs alone are not proof of inbox delivery.

## Verification

Automated tests use mocks and loopback-only fake servers, never the configured
provider. They cover success/disabled/failure API contracts, SMTP authentication
exception propagation, bound property/default/environment mapping, rejection of
zero/negative/malformed effective timeouts (including bracket overrides), an actual
silent-SMTP read timeout, a nested Biuro-style HTTP status response despite stalled
email, actual HTTP failures
and read timeout, no retry, response validation, safe logging, and preservation of
status/participant/staff submission results after email HTTP failure.

```bash
mvn -pl backend/email-service,backend/registration-api,backend/biuro-api -am test
```

Opt-in real-MariaDB tests can remain skipped in this SMTP-only run; no schema or
persistence code changes are introduced. Full deployment/database acceptance
continues to be tracked separately.

Manual test sequence:

1. With MailHog, send a synthetic confirmation/status message and inspect the
   sink, API response and Registration log. Verify adult/minor recipient rules.
2. With `MAIL_ENABLED=false`, verify `success=false` and no SMTP message.
3. Use a deliberately incorrect SMTP password or unavailable local SMTP endpoint;
   verify a non-success email response and bounded waiting. A registration/status
   must remain saved. Restore correct configuration afterward.
4. Enable the private real-SMTP configuration, send only to a controlled mailbox,
   and confirm receipt/spam folder, sender, Polish text, recipient, dates and links.
   Avoid ACCEPTED templates/payment instructions until their test content is agreed.
5. Do not infer failure solely from a timeout, and do not blindly repeat the request.
   Check provider/sink/mailbox evidence first. Record actual results in WIP.

## Still separate

Operator warning/resend controls, persisted notification history, transactional
outbox/idempotency, automatic retries, Sent-folder copies, email-service caller
authentication/network isolation, rate limiting and complete Compose/deployment
hardening remain separate work. This phase is not approval for public exposure or
real registration collection.
