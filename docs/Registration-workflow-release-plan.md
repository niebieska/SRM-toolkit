# First release — registration workflow and Turnus lists

Agreed 2026-10-08. Target: mid-November; date is a target, not a validated estimate.
Primary value: operators can process registrations and produce usable Turnus
lists. This document sets delivery order; detailed findings remain in
First-release-functional-review.md and Turnus-management-plan.md.

Operational deployment gates live in [deployment-checklist.md](deployment-checklist.md).
This file owns functional acceptance; WIP.md records progress and evidence.

Implementation status reconciled 2026-10-16 against local `development` at
`2cdac33` (export PR #40). This records completed implementation, not deployed
acceptance or a new full regression run. Earlier dated assessments are historical;
implementation documents and WIP record subsequent corrections.

## Current state and acceptance gates

"Exists" below means found in code; it does not mean a complete deployed workflow
has been verified. Completed implementation tasks are checked below; release
acceptance and verification tasks are tracked separately.

| Step | Current implementation | Release acceptance |
|---|---|---|
| Submit participant/staff registration | Forms, APIs, persistence, independent counters and duplicate constraint exist. Integrity suite verified previously. | Valid adult/minor submissions survive restart; invalid mandatory data is rejected by backend; no duplicate on repeated click; useful errors preserve input. |
| Find and inspect | Biuro filters status/type/Turnus; local search and sorting; details with payload fields. | Operators can find each scenario, read required person/guardian/contact/role details, and clearly distinguish status and Turnus. |
| Decide | Table and details actions support ACCEPTED/WAITLIST/REJECTED; modal offers preset rejection reasons plus free text; Biuro enforces rejection reason. | All three outcomes persist and refresh correctly; reasons visible; failures show no false success. Agree repeated/concurrent action behavior and capacity policy. |
| Notify | Confirmation and status templates exist with guardian/self routing. | Verify recipients/content for adult/minor and participant/staff; correct dates/payment instructions; SMTP failure does not lose registration or falsely claim delivery; define practical recovery/resend. |
| Assemble Turnus roster | Dedicated accepted-only participant/staff tables, search, type filter, sorting, presets and custom columns are implemented. Staff roles remain visible; qualifications are staff-only. | Accepted participants and staff selectable for the correct Turnus; NEW/WAITLIST/REJECTED excluded from accepted lists. Empty lists supported. |
| Generate lists | XLSX/CSV/PDF exports use current filters, columns and ordering. XLSX sheets/PDF sections separate groups; CSV includes person type. Binary and browser checks passed; intended application/print acceptance remains pending. | Insurance export and printable on-site registration list use agreed columns, correct selection and current data. Staff inclusion is explicit. Print/export verified using synthetic records. |

## Ordered work packages

### 1. Close registration submission gaps

- [x] Enforce current required person/contact, guardian/ICE, address, health,
  consent and role/subrole rules on both backend submission paths. Image consent
  stays optional. Current role choices are mirrored in code pending season work.
- [x] Preserve emergency-contact "other relationship" text through payload/storage/details.
- [x] Correct in-flight submit state and preserve meaningful API error codes.
- [x] Add dedicated PESEL checksum, birth-date/century and age-boundary tests,
  payload-rule tests and rejection-before-persistence/notification regressions.
- [ ] Agree free-text field limits and align frontend/backend phone validation;
  see [Form field capacity review](Form-field-capacity-review.md).
- [ ] Record scenario-specific manual results and repeat valid/invalid
  adult/minor × participant/staff verification in a few days. The user reports
  most cases tested; this is not yet a complete recorded acceptance matrix.
- No aggregate payload-size limit is required in the current scope. The unagreed
  cap was removed; further capacity work is deferred pending field review.
  Typed DTOs/Bean Validation remain implementation options, not completed work.

Verification on 2026-10-09: 99 backend tests (including nine real-MariaDB tests),
14 frontend logic tests and both frontend production builds passed. These do not
replace browser or deployed email verification. Results and repeat scenarios:
[Registration validation verification](Registration-validation-test-scenarios.md).

### 2. Supply consistent Turnus and season data

Implemented and merged through PRs #28–37. Biuro owns the persistent catalogue;
Registration uses authenticated lookup and an hourly atomic in-memory cache,
retaining its last successful catalogue during outages. The stub remains in the
repository but is not the live source.

- [x] Database-backed Turnus management: stable codes, dates, season, capacity,
  independent participant/staff opening switches and active/archived state.
- [x] Public options, submission validation, statistics and confirmation email
  metadata consume catalogue definitions.
- [x] File-based Summer/Winter roles, subroles, certificates and backend validation.
- [x] Single-page editor, seasonal staffing requirements and role statistics.
- [ ] Complete deployed catalogue/edit/cache recovery and season-form acceptance.

Details: [catalogue](Turnus-catalogue-implementation.md),
[cache](Turnus-catalogue-cache.md), [editor](Turnus-editor.md),
[capacity](Turnus-capacity-management.md), [season roles](Season-staff-roles.md),
[email metadata](Turnus-email-metadata.md) and [staffing statistics](Staffing-statistics.md).

### 3. Make decision and notification outcomes dependable

- [x] Implement agreed capacity policy: separate positive limits; reject reductions
  below the accepted count of the affected type. Acceptance remains enabled
  at/beyond capacity; opening/closure is manual under the single-operator assumption.
  Required staffing totals remain independent of available staff places.
- [ ] Confirm birthday/minority reference rules with the client. Lists provisionally
  calculate age at Turnus start; registration minor/adult routing is unchanged.
- [ ] Decide permitted status changes and unchanged-status/resend behavior before
  implementing transition restrictions; verify the agreed behavior explicitly.
- [ ] Verify status persistence, rejection reasons and table/details refresh.
- [ ] Agree participant/staff fees and recipient rules; remove placeholder bank data.
- [ ] Verify SMTP outcomes and a practical recovery/resend path. Full editable
  templates, group announcements and scheduling remain deferred.
- [x] Add automated confirmation-recipient and catalogue-metadata tests for
  adult/minor participants and staff; see [email metadata](Turnus-email-metadata.md).
- [ ] Verify status-notification recipients and deployed mailbox outcomes separately.
- [x] Configure Biuro-to-Registration and Registration-to-Biuro catalogue timeouts
  (15-second connect / 30-second read).
- [ ] Configure Registration-to-email-service timeouts and complete upstream
  business-error mapping. Preserve
  recognized upstream business errors through an explicit mapping; do not blindly
  forward response bodies or treat service-authentication errors as operator 401s.
  Test business errors, connection failures and timeouts. Do not blindly retry
  registration/status calls; email retry requires duplicate-send protection.
- [ ] Handle expired Biuro sessions centrally: clear the token and return to login
  on operator 401 with a Polish explanation; check expiry when restoring a session.
  Parse safe backend error details. Test missing, expired and tampered JWTs and
  public/protected routes, including session behavior in the frontend.
  Apply this consistently to Zgłoszenia and Turnusy. The current backend may
  return 403 for expired/missing operator credentials; distinguish authentication
  failure from a genuine permission denial rather than logging out on every 403.
  Make login reachable after expiry and agree how unsaved editor inputs survive
  reauthentication. Never automatically replay a POST/PATCH after login.
  Cover expiry on startup and during list loading/edit saving, retained inputs,
  successful reauthentication, and permission-denied responses.
- [ ] Deliver the agreed individual-account/access scope in
  [Biuro accounts and access plan](Biuro-accounts-and-access-plan.md): multiple
  fixed roles, SYS_ADMIN account management, unrestricted operational Turnus access
  for OPERATOR, assigned-Turnus lists/custom columns/exports for VIEWER, two-hour
  sessions, revocation and last-administrator protection. Agreed 2026-10-16;
  implementation remains pending.

### 4. Deliver usable Turnus lists

- [x] Agree initial fields and presets: Ogólny, Ubezpieczenie, Rejestracja,
  Podział na grupy and Własny; participant/staff output remains clearly separated.
- [x] Deliver accepted-only views, presets/custom columns and XLSX/CSV/PDF exports,
  merged in PRs #38–40. See [Phase 1](Turnus-lists-phase-1.md),
  [presets](Turnus-list-presets.md) and [exports](Turnus-list-exports.md).
- [x] Check export scope, real binaries, Polish text, identifiers, PDF pagination
  and browser downloads using synthetic data. Latest Biuro frontend tests/build:
  all 14 test files pass; existing PDF lazy-chunk size warning remains.
- [ ] Confirm insurer compatibility and intended Excel/LibreOffice, CSV import and
  PDF print output with operators. Prefer XLSX for typed PESEL/phone identifiers.
- [ ] Verify exclusions, staff selection, new/empty Turnusy, Polish characters,
  export/print output and required data from saved registrations.
- No digital arrival/attendance tracking.

### 5. Verify and deploy the complete workflow

- [ ] Complete the operational gates in
  [deployment-checklist.md](deployment-checklist.md). Controlled synthetic-data
  deployment can support testing; release for real registrations requires both
  operational readiness and the functional acceptance gates here.
- [ ] Run automated integrity/regression checks and deployed E2E with synthetic
  adult/minor participant/staff registrations through all status outcomes/lists.
- [ ] Include negative paths: duplicate, closed Turnus, missing data, invalid role,
  slow/repeated submit, expired authentication, unavailable upstream and SMTP failure.
- [ ] Obtain operator review of lists and workflow; fix defects before release.

## Design decisions before implementation

- Define necessary Biuro detail/export fields, access boundaries, detail-view
  auditing and storage protection for sensitive data before real registrations.
  Authorized operational access is legitimate; returning every payload field
  is not automatically the required contract.
- Decide whether to replace plain PESEL hashes with HMAC. If adopted, define
  secret storage/rotation and migration preserving duplicate detection and its
  database uniqueness constraint. Do not silently change hashes on submission.
- Decide when database filtering/pagination is needed. If adopted, update API
  queries and frontend search/sorting together so ordering is not merely page-local.
- Retain Biuro-owned Turnus management as the destination; JSON-only fixes do not
  replace that agreement. Shared modules/workspaces, circuit breakers, refresh
  tokens, schema normalization and service merging are not release prerequisites.

## Scope guard and tracking

Manual payment tracking is deferred from the deadline plan unless core acceptance
passes with time remaining. Rich UI, Person management, transfer/edit expansion,
PESEL exceptions, editable email templates and group announcements are not required
for this release. Dataset cleanup remains separate and deferred.

Track actual time and accepted outcomes per work package in WIP.md. Re-estimate
weekly from completed end-to-end behavior; code presence or a successful build
alone does not close an acceptance gate. Review list formats and unresolved rules
in parallel with technical work so late decisions do not block exports.
