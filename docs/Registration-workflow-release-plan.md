# First release — registration workflow and Turnus lists

Agreed 2026-10-08. The original mid-November target was not re-estimated after
TEST deployment began; treat it as a historical target, not a current forecast.
Primary value: operators can process registrations and produce usable Turnus
lists. This document sets delivery order; detailed findings remain in
First-release-functional-review.md and Turnus-management-plan.md.

Operational deployment gates live in [deployment-checklist.md](deployment-checklist.md).
This file owns functional acceptance; WIP.md records progress and evidence.

Reconciled 2026-10-28 against `development` at `d9d6ebf` (Docker TEST packaging,
PR #52). Feature PRs #46 (status history), #49 (registration editing), #50
(acceptance email updates), #51 (custom list fields) and #52 (TEST packaging)
are merged. This plan distinguishes implementation from deployed acceptance;
see the dated checkpoint in WIP for operator-reported GCP verification. Earlier
dated assessments are historical and must not be read as the current status.

## Current state and acceptance gates

"Exists" below means found in code; it does not mean a complete deployed workflow
has been verified. Completed implementation tasks are checked below; release
acceptance and verification tasks are tracked separately.

| Step | Current implementation | Release acceptance |
|---|---|---|
| Submit participant/staff registration | Forms, APIs, persistence, independent counters and duplicate constraint exist. Integrity suite verified previously. | Valid adult/minor submissions survive restart; invalid mandatory data is rejected by backend; no duplicate on repeated click; useful errors preserve input. |
| Find and inspect | Biuro filters status/type/Turnus; local search and sorting; details with payload fields. | Operators can find each scenario, read required person/guardian/contact/role details, and clearly distinguish status and Turnus. |
| Decide | Table and details actions support ACCEPTED/WAITLIST/REJECTED; modal offers preset rejection reasons plus free text; Biuro enforces rejection reason. Status history is audit-only; existing workflow permits normal status changes, including returning to a previous status. | Finish deployed checks for all outcomes, reasons, refresh and history. Notifications remain a separate best-effort action. Capacity policy is agreed and implemented. |
| Notify | Confirmation and status templates exist with guardian/self routing; GCP TEST external SMTP send is operator-confirmed. | Verify recipients/content for adult/minor and participant/staff; correct dates/payment instructions; confirm SMTP failure does not lose registration or falsely claim delivery. The backend attempts a notification after each successful status-update request, including a same-status request; the UI does not provide a controlled resend action. Manual resend and notification-attempt audit are tracked in the future backlog. |
| Assemble Turnus roster | Dedicated accepted-only participant/staff tables, search, type filter, sorting, presets and custom columns are implemented. Staff roles remain visible; qualifications are staff-only. | Accepted participants and staff selectable for the correct Turnus; NEW/WAITLIST/REJECTED excluded from accepted lists. Empty lists supported. |
| Generate lists | XLSX/CSV/PDF exports use current filters, columns and ordering. XLSX sheets/PDF sections separate groups; CSV includes person type. Binary and browser checks passed; intended application/print acceptance remains pending. | Insurance export and printable on-site registration list use agreed columns, correct selection and current data. Staff inclusion is explicit. Print/export verified using synthetic records. |

## Ordered work packages

### 1. Close registration submission gaps

- [x] Enforce current required person/contact, guardian/ICE, address, health,
  consent and role/subrole rules on both backend submission paths. Image consent
  stays optional. Current role choices are mirrored in code pending season work.
- [x] Preserve emergency-contact "other relationship" text through payload/storage/details.
- [x] Correct in-flight submit state and preserve meaningful API error codes.
- [x] Add dedicated PESEL checksum and birth-date/century tests, rocznikowy
  minimum-age boundary tests, payload-rule tests and
  rejection-before-persistence/notification regressions.
- [x] Agree and implement frontend usability caps for registration fields. The
  implementation and component-level tests were merged in PR #55; see
  [Form field capacity review](Form-field-capacity-review.md).
- [ ] Decide supported phone formats and align frontend/backend phone validation;
  see [Form field capacity review](Form-field-capacity-review.md).
- [ ] Record scenario-specific manual results and repeat valid/invalid
  adult/minor × participant/staff verification in a few days. The user reports
  most cases tested; this is not yet a complete recorded acceptance matrix.
- No aggregate payload-size limit is required in the current scope. The unagreed
  cap was removed. Frontend field caps are implemented on the feature branch;
  remaining capacity follow-ups are the phone-format compatibility decision,
  rejection-reason validation against its database column, and stale certificate
  detail handling, as listed in the field review.
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

- [x] Implement required MVP **Historia statusów** in registration details: actual
  status changes record previous/new status, server time, authenticated actor and
  rejection reason in the same transaction as the status update. The detail response
  includes ordered history and the modal shows it after registration data. No
  historical entries are invented; status history remains audit-only. Implemented
  in PR #46. See [status history scope](Registration-status-history.md).
- [ ] Complete browser/deployed acceptance of status history, including actor,
  ordering, rejection reason and existing-registration behavior. Notification
  history/resend remain separate follow-ups.
- [x] Implement separate positive limits and reject reductions below the accepted
  count of the affected type.
- [ ] Enforce the agreed hard acceptance limit separately for participants and
  staff: reject transition to ACCEPTED when that type's accepted count has reached
  capacity, with a clear Biuro message. Keep registration-open switches manual;
  valid public submissions may remain NEW while full. The current implementation
  still permits over-capacity acceptance until this follow-up is delivered.
- [ ] Show an advisory warning in Turnus definition when summed required staff
  roles exceed staff capacity. Keep role targets and capacity independently editable;
  the warning must not block saving. See [staff requirements](Turnus-staff-requirements.md).
- [x] Agree Turnus minimum age as rocznikowy: compare the Turnus start year with
  the birth year from PESEL, regardless of whether the birthday has occurred by
  the start date. This rule is implemented and covered by backend boundary tests.
  Lists separately display age at Turnus start provisionally; registration
  minor/adult routing remains based on age at submission and is unchanged by
  this decision.
- Status history is audit-only. Keep the existing status workflow and permissions;
  do not add transition restrictions. Returning to an earlier status uses the
  same existing action flow. A same-status request does not create a history row,
  but the backend still attempts the status notification; this is not a controlled
  resend feature.
- [ ] Verify status persistence, rejection reasons, table/details refresh and audit timeline in deployed Biuro.
- [ ] Agree participant/staff fees and recipient rules; remove placeholder bank data.
- [x] Verify that the GCP TEST email-service sends through the configured external
  SMTP server (operator confirmed successful delivery). This confirms SMTP
  transport, not every template/recipient/content combination or inbox placement.
  MailHog remains the local default. Manual resend controls are deferred in
  [the future backlog](Future-product-backlog.md#communication), not a blocker for
  controlled synthetic TEST. Full editable templates, group announcements and
  scheduling remain deferred.
- [x] Add automated confirmation-recipient and catalogue-metadata tests for
  adult/minor participants and staff; see [email metadata](Turnus-email-metadata.md).
- [ ] Verify status-notification recipients and deployed mailbox outcomes for adult/minor participants and staff separately; a generic successful SMTP send does not close this matrix.
- [x] Configure Biuro-to-Registration and Registration-to-Biuro catalogue timeouts
  (15-second connect / 30-second read).
- [x] Configure Registration-to-email-service HTTP and SMTP timeouts; surface SMTP
  failures and disabled sends accurately and log code/template/outcome safely.
  No automatic retries or workflow response changes; see the Email TEST document.
- [ ] Complete upstream business-error mapping. Preserve
  recognized upstream business errors through an explicit mapping; do not blindly
  forward response bodies or treat service-authentication errors as operator 401s.
  Test business errors, connection failures and timeouts. Do not blindly retry
  registration/status calls; email retry requires duplicate-send protection.
- [ ] Handle expired Biuro sessions centrally: clear the token and return to login
  on operator 401 with a Polish explanation; check expiry when restoring a session.
  Parse safe backend error details. Test missing, expired and tampered JWTs and
  public/protected routes, including session behavior in the frontend.
  Implemented centrally for Zgłoszenia and Turnusy in the
  [frontend phase](Biuro-accounts-frontend.md), including blocking reauthentication
  during editing. The account backend returns 401 for invalid sessions and 403
  for permission denial; ordinary 403 must not clear a valid session.
  Browser acceptance remains pending.
  Make login reachable after expiry and agree how unsaved editor inputs survive
  reauthentication. Never automatically replay a POST/PATCH after login.
  Cover expiry on startup and during list loading/edit saving, retained inputs,
  successful reauthentication, and permission-denied responses.
- [x] Implement the agreed individual-account/access scope in
  [Biuro accounts and access plan](Biuro-accounts-and-access-plan.md): multiple
  fixed roles, SYS_ADMIN account management, unrestricted operational Turnus access
  for OPERATOR, assigned-Turnus lists/custom columns/exports for VIEWER, two-hour
  sessions, revocation and last-administrator protection. Agreed 2026-10-16;
  backend implementation is recorded in [Phase 1](Biuro-accounts-phase-1.md) and
  [Phase 2](Biuro-accounts-phase-2.md). Account UI/session integration is
  implemented in the [frontend phase](Biuro-accounts-frontend.md). Deployed browser
  checks for role boundaries, session expiry, password change and account lifecycle
  remain pending.

### 4. Deliver usable Turnus lists

- [x] Agree initial fields and presets: Ogólny, Ubezpieczenie, Rejestracja,
  Podział na grupy and Własny; participant/staff output remains clearly separated.
- [x] Deliver accepted-only views, presets/custom columns and XLSX/CSV/PDF exports,
  merged in PRs #38–40. See [Phase 1](Turnus-lists-phase-1.md),
  [presets](Turnus-list-presets.md) and [exports](Turnus-list-exports.md).
- [x] Check export scope, real binaries, Polish text, identifiers, PDF pagination
  and browser downloads using synthetic data. Latest Biuro frontend tests/build:
  all 14 test files pass; existing PDF lazy-chunk size warning remains.
- [x] Extend Własny with optional health-detail columns and staff family/birth-year
  details, grouped by applicability (PR #51). Manual verification of selection,
  VIEWER access and selected values in XLSX/CSV/PDF remains pending; see
  [custom list fields](Turnus-list-presets.md).
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

Manual **Wpłaty** tracking remains a probable, not confirmed, MVP requirement. A
frontend-only prototype is on the separate, unpushed
[`feature/manual-payments-ui`](https://github.com/niebieska/SRM-toolkit/tree/feature/manual-payments-ui)
branch; it uses demo data and does not provide persistent payments, discounts or
Turnus fee configuration. Decide whether to promote the feature to a release gate
before implementing its backend and data model. See [agreed scope and open
decisions](Manual-payments.md): Turnus participant/role-based staff fees, operator
payment recording, PLN discounts and a top-level Wpłaty tab alongside Zgłoszenia.
Turnus shortcuts may open the same register with a filter applied. Balance columns
in Lists and payment exports remain deferred.

Turnus informational emails are **nice to have for MVP**, not a release gate:
predefined templates, custom text and preview, accepted participant/staff selection
and optional copies to minors. See [scope](Turnus-informational-emails.md).
This does not promote general editable-template management into MVP.

Rich UI, Person management, transfer/edit expansion, PESEL exceptions and editable
template management remain outside required release scope. Dataset cleanup remains
separate and deferred.

Track actual time and accepted outcomes per work package in WIP.md. Re-estimate
weekly from completed end-to-end behavior; code presence or a successful build
alone does not close an acceptance gate. Review list formats and unresolved rules
in parallel with technical work so late decisions do not block exports.
