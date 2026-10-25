# Future product backlog

Updated: 2026-10-08. These items preserve the later roadmap formerly mixed into
deployment-checklist.md. They are not requirements for the first release unless
explicitly promoted into Registration-workflow-release-plan.md and recorded in WIP.

## Optional after core acceptance, if time remains

- Full-hour Turnus catalogue refresh — deferred, discussed 2026-10-21. Replace
  elapsed-hour refresh with a full-hour schedule in `Europe/Warsaw`, retaining
  asynchronous startup loading, last-good cache and separate failed-fetch retries.
  After saving a Turnus, show the next planned refresh time (not a guarantee of
  successful propagation). Update configuration, Compose/env guidance and tests.
  Already-open forms do not automatically reload. Implement in a separate PR;
  this is not part of the current client UI adjustments or an MVP release gate.
- Manual payment tracking was reassessed on 2026-10-20 as **probably required for
  MVP**, pending prioritization. Current scope and unresolved decisions live in
  [Wpłaty](Manual-payments.md) and the registration workflow release plan;
  the earlier optional-only classification no longer applies.
- Staff statistics against required roles/subroles.
- Additional loading/empty/error polish and email-client appearance refinements.

## Registration and domain extensions

- **Biuro staff registration creation** for phone or on-site intake. Scope agreed
  2026-10-25; priority remains to be decided, and it is not thereby promoted to an
  MVP release gate. Add an OPERATOR-only Biuro form and protected Biuro API path to
  an internal Registration API endpoint. Reuse the staff catalogue, validation,
  duplicate protection and registration numbering. Email addresses are optional
  on this manual path; public staff registration rules remain unchanged. Other
  required staff data and consents remain required.
  Create the record as NEW, then transition it to ACCEPTED in the same database
  transaction, recording the authenticated operator in status history. The saved
  registration is ACCEPTED immediately; no separate status-history schema is
  planned. Send the existing confirmation with status ACCEPTED when an email address
  is available; otherwise skip sending. Reuse existing Turnus active/open checks
  and current capacity behavior. The accepted registration appears in normal
  statistics and lists. Approximate implementation size: 14–22 focused hours,
  including tests; not a delivery commitment.
- Biuro registration editing: implementation started on `feature/registration-editing`
  on 2026-10-25; it is not promoted to an MVP release gate. The initial scope keeps
  PESEL, registration code, Turnus, registration type, status, consents and staff
  role/qualifications read-only. It permits corrections to names, address, personal
  and guardian/ICE contact details, staff family-name fields, and existing health
  answers/details/additional information. OPERATOR identity, time and changed field
  names are audited; saves use version checks to reject stale edits. Edits do not
  send email or imply a new consent. Review this initial field scope before merge.
- Turnus transfer remains a separate workflow creating a linked new registration
  and preserving the original registration/code/history, as detailed in WIP.
- Soft delete and operational notes remain deferred. Registration status history
  is implemented in PR #46; deployed/browser acceptance is tracked in the release
  plan and [status history](Registration-status-history.md).
- Person model, identity/deduplication and cross-season registration history.
- Bulk actions and additional Excel/PDF formats beyond agreed release exports.
- Staff requirement planning beyond configured role/certificate selection.
- Broader status transition policy/history UI beyond agreed release behavior.

## Communication

- Manual notification resend from Biuro registration details: deferred on
  2026-10-17 while SMTP TEST prerequisites are completed. OPERATOR explicitly
  chooses registration confirmation or current-status notification; backend
  reconstructs recipient/content from saved registration/catalogue data. No
  registration/status changes, automatic retries or browser-supplied templates.
  Add confirmation, repeated-click protection, actor/code/type logs and honest
  accepted/not-sent/unconfirmed feedback. SYS_ADMIN alone does not grant this
  operational permission. No persistent notification history is assumed in the
  minimal version; an uncertain SMTP outcome can still produce duplicate mail
  on a deliberate retry. Outbox/idempotency/history remain separate decisions.
  This is not a blocker for controlled synthetic-data TEST; current operator
  screens do not expose notification outcomes or offer resend.
- Editable email subjects/bodies with controlled placeholders, preview and versions.
- Turnus informational emails: **nice to have for MVP**, agreed 2026-10-20.
  Predefined templates with a custom message area and preview; accepted participant
  or staff groups, selected people or one person; guardian-first routing for minors
  with optional minor copy. See [scope and open decisions](Turnus-informational-emails.md).
  Sending outcomes do not imply confirmed inbox delivery.
- Scheduled messages/reminders and durable outbox/retry improvements.
- See WIP for the detailed decisions and reliability prerequisites.

## Finance and operations

- Deposits, balances, payment reminders and finance dashboard.
- Attendance and QR workflows only if separately requested. Printable on-site
  registration lists do not imply digital arrival/check-in tracking.
- Richer reporting/charts.

## Access and identity

- Individual Biuro accounts and the agreed fixed roles are now active release
  work: see [accounts/access plan](Biuro-accounts-and-access-plan.md). Broader
  identity integrations and configurable permission models remain future work.
- PESEL-exception registrations using the agreed key/fingerprint proposal in WIP.

The first release still requires basic access protection, accurate notification
outcomes/recovery, agreed status behavior and usable insurance/on-site lists.
Deferring richer versions here does not defer those baseline requirements.
