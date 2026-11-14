Jak# Small fixes and follow-ups

Working checklist for small corrections and operational improvements raised in
the November 2026 discussion. This file tracks implementation state; it does not
replace the broader release plan.

## Completed

- [x] Preserve health-question answers when moving Back/Next.
- [x] Improve mobile form layout: space final-step buttons, prevent phone fields
  from overflowing, and stack ICE phone fields on narrow screens.
- [x] Mark participant health questions as required.
- [x] Apply frontend field-length limits for registration forms (PR #55).
- [x] Make personal email optional for minor participants while keeping guardian
  email required; verified on GCP after a hard browser refresh (PR #56).
- [x] Refresh the Biuro registrations list when the tab becomes visible and every
  five minutes while visible, while keeping browser refresh available (PR #57).

## Pending implementation

- [ ] **After deploying the registration-source change, add Flyway baseline
  migrations for clean production initialization.** Keep the existing V1–V6
  migration history for already initialized environments. Add one baseline per
  service/database, representing its complete schema at V6 but excluding the
  legacy `ZAGLE26T1` / `ZAGLE26T2` Turnus seed rows. Verify that a clean database
  uses the baseline and that the existing GCP database ignores it and retains
  its data/history. The project resolves Flyway 11.14.1 through Spring Boot 4.0.6.
- [ ] **Show the email submission outcome in registration details.** Record each
  per-recipient notification attempt against its registration and display its
  timestamp, recipient, template/notification type, and SMTP outcome. The agreed
  scope ends at the configured SMTP server: accepted, definitely not sent, or
  unconfirmed. Do not claim recipient-MX delivery; DSN/bounce reception, retries
  and manual resend are outside this scope.
- [ ] **Enforce capacity during acceptance**, independently for participants
  and staff. At capacity, reject a transition to ACCEPTED with a clear message;
  keep WAITLIST/REJECTED actions available. Registration submissions may still
  be saved as NEW while registration is open. Enforce the limit in Registration
  API atomically, including concurrent attempts for the last place.
- [ ] **Warn about required staffing exceeding staff places.** Compare the sum of
  required role counts with Miejsca dla kadry in the Turnus editor. Keep the two
  values independent; the warning must not block saving or alter either value.
- [ ] Ensure acceptance uses current Turnus capacity without waiting for the
  hourly Registration catalogue refresh. Decide the capacity propagation/check
  design as part of the acceptance-limit implementation.
- [ ] When staff certificates are deselected or become unavailable after a role
  or Turnus change, omit their stale descriptions from the submission payload.
- [ ] Improve the registration error path so the user can return to the relevant
  form step, especially consents/submission, without losing entered data.
- [x] Schedule Turnus catalogue refresh at the start of each hour (Europe/Warsaw),
  with startup loading, retry handling and last-good-cache retention. This is
  separate from refreshing Biuro's registrations table. Manual timing verification
  remains.

## Cosmetic / operational refinements to consider later

- [ ] Include a validated registration code in email-service failure logs so
  they can be correlated with SMTP acceptance logs. The Registration API already
  logs the code and notification outcome; do not log email content or arbitrary
  unvalidated request variables.
- [ ] Keep the “Najbliższa synchronizacja” hint aligned with the backend if the
  configured refresh cron or time zone changes. It currently assumes the default
  hourly schedule in `Europe/Warsaw`; no change is needed while those defaults
  remain in use.
- [ ] Consider reducing repeated warning logs during a prolonged Biuro outage.
  The current retry behavior logs each failed attempt (by default every 30 seconds);
  any log-rate reduction should preserve enough detail to diagnose recovery.

## Decision / verification still needed

- [ ] Revisit international phone-number lengths and align frontend/backend
  validation. The current segmented input caps the national number at 9 digits
  and silently truncates longer input (for example, a 10-digit number with the
  `+1` country prefix). It is retained for now; decide the supported formats
  before changing the control, then verify that valid numbers are never
  silently altered and frontend/backend accept the same values.
- [ ] Manually verify refresh behavior with a new registration, changed filters,
  an open detail modal, a failed request and a hidden/foregrounded browser tab.
- [ ] Verify capacity boundaries for both registration types, including freeing
  a place, increasing capacity, stale catalogue data and concurrent acceptance.
- [ ] Verify the staffing warning for equal totals, required total below capacity,
  required total above capacity and zero role requirements.

## Email status scope decision

The initial implementation is the persistent per-recipient SMTP submission audit
listed above. It records whether the configured SMTP server accepted the message,
the message was definitely not sent, or the outcome is unconfirmed. Show that result
in registration details. SMTP acceptance must not be labelled as confirmed delivery
to the recipient.

| Option | Information and behavior | Requirements / limits |
|---|---|---|
| SMTP submission audit — initial scope | Persist the synchronous result from the configured SMTP server and show it in the registration's notification history. | Uses the existing outbound SMTP integration. An accepted result does not prove recipient-server delivery or inbox placement. |
| DSN delivery report — possible later extension | Request `NOTIFY=SUCCESS,FAILURE`; receive later delivery-status notifications and correlate them with the original per-recipient attempt. | Requires DSN support along the provider/recipient route, a monitored return path or provider API, and a secure/idempotent report receiver. Reports may say delivered, failed, delayed or relayed; none proves the message was read. |

These are layers, not competing choices: DSN could enrich the initial audit. It is
excluded from the initial scope until support from `mail.watroba.com.pl` is verified.
The DSN request and semantics are defined in [RFC 3461](https://www.rfc-editor.org/rfc/rfc3461/)
and [RFC 3464](https://www.rfc-editor.org/rfc/rfc3464.html).
