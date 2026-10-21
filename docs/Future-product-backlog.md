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

- Registration creation by an authenticated Biuro operator (participant/staff),
  for phone or on-site entries. Recorded 2026-10-15; priority to be decided after
  current prioritized work is finished, not added to the committed MVP scope yet.
  Prefer reusing existing submission validation, numbering and duplicate protection.
  Decide closed-Turnus access, initial status, notification behavior, consent
  recording and operator attribution before implementation. NEW is a proposal,
  not an agreed initial-status rule for this workflow.
- Biuro registration editing: scope discussion recorded 2026-10-16; deferred while
  remaining release work is completed. Assume PESEL was entered correctly and keep
  it read-only, together with registration code, Turnus and registration type.
  Status changes continue through the existing separate workflow.
  Candidate editable fields: names, address, person/guardian/ICE contacts, health
  information and staff role/subrole/qualifications. Confirm the exact field list
  before implementation; reuse backend validation and season rules. Include an
  audit trail and protection against overwriting newer changes. Consent editing
  and notification behavior require explicit decisions; operator edits must not
  imply a new declaration of consent or automatically resend confirmation emails.
  A contact/address-only first version remains an option. This is not promoted
  into the committed MVP scope.
- Turnus transfer remains a separate workflow creating a linked new registration
  and preserving the original registration/code/history, as detailed in WIP.
- Soft delete and operational notes. Registration status history is now a required
  MVP task; see [status history](Registration-status-history.md).
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
