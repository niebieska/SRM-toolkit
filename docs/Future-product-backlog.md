# Future product backlog

Updated: 2026-10-08. These items preserve the later roadmap formerly mixed into
deployment-checklist.md. They are not requirements for the first release unless
explicitly promoted into Registration-workflow-release-plan.md and recorded in WIP.

## Optional after core acceptance, if time remains

- Manual payment tracking: amount, date, note and total per registration.
  Agree deposit/balance and correction rules first.
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
- Soft delete, status/history audit and operational notes.
- Person model, identity/deduplication and cross-season registration history.
- Bulk actions and additional Excel/PDF formats beyond agreed release exports.
- Staff requirement planning beyond configured role/certificate selection.
- Broader status transition policy/history UI beyond agreed release behavior.

## Communication

- Editable email subjects/bodies with controlled placeholders, preview and versions.
- Turnus group announcements with recipient preview, explicit send and delivery tracking.
- Scheduled messages/reminders and durable outbox/retry improvements.
- See WIP for the detailed decisions and reliability prerequisites.

## Finance and operations

- Deposits, balances, payment reminders and finance dashboard.
- Attendance and QR workflows only if separately requested. Printable on-site
  registration lists do not imply digital arrival/check-in tracking.
- Richer reporting/charts.

## Access and identity

- Persistent individual users, richer permissions and identity improvements.
- PESEL-exception registrations using the agreed key/fingerprint proposal in WIP.

The first release still requires basic access protection, accurate notification
outcomes/recovery, agreed status behavior and usable insurance/on-site lists.
Deferring richer versions here does not defer those baseline requirements.
