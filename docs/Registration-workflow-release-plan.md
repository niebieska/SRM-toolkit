# First release — registration workflow and Turnus lists

Agreed 2026-10-08. Target: mid-November; date is a target, not a validated estimate.
Primary value: operators can process registrations and produce usable Turnus
lists. This document sets delivery order; detailed findings remain in
First-release-functional-review.md and Turnus-management-plan.md.

Operational deployment gates live in [deployment-checklist.md](deployment-checklist.md).
This file owns functional acceptance; WIP.md records progress and evidence.

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
| Assemble Turnus roster | Registrations already filter by Turnus/type/status, but dedicated operational lists are missing on this branch. | Accepted participants and staff selectable for the correct Turnus; NEW/WAITLIST/REJECTED excluded from accepted lists. Empty lists supported. |
| Generate lists | No production export/print implementation found; experimental prototype is reference only. | Insurance export and printable on-site registration list use agreed columns, correct selection and current data. Staff inclusion is explicit. Print/export verified using synthetic records. |

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

- [ ] Implement the minimum database-backed Turnus management already agreed,
  including stable codes, dates, capacity, availability and season association.
- [ ] Migrate public options, validation, statistics and email metadata to it.
- [ ] Add file-based season roles/subroles/certificate mapping and backend validation.
- [ ] Keep editing UI simple; avoid building a broader administration suite.

### 3. Make decision and notification outcomes dependable

- [ ] Agree capacity warning/limit behavior and minority/birthday reference rules.
- [ ] Decide permitted status changes and unchanged-status/resend behavior before
  implementing transition restrictions; verify the agreed behavior explicitly.
- [ ] Verify status persistence, rejection reasons and table/details refresh.
- [ ] Agree participant/staff fees and recipient rules; remove placeholder bank data.
- [ ] Verify SMTP outcomes and a practical recovery/resend path. Full editable
  templates, group announcements and scheduling remain deferred.
- [ ] Test notification recipient selection for adult/minor participants and staff.
- [ ] Configure connect/read timeouts for both inter-service clients. Preserve
  recognized upstream business errors through an explicit mapping; do not blindly
  forward response bodies or treat service-authentication errors as operator 401s.
  Test business errors, connection failures and timeouts. Do not blindly retry
  registration/status calls; email retry requires duplicate-send protection.
- [ ] Handle expired Biuro sessions centrally: clear the token and return to login
  on operator 401 with a Polish explanation; check expiry when restoring a session.
  Parse safe backend error details. Test missing, expired and tampered JWTs and
  public/protected routes, including session behavior in the frontend.

### 4. Deliver usable Turnus lists

- [ ] Agree insurer template/columns and printable on-site list layout early,
  before implementing exports.
- [ ] Add accepted participant/staff views, insurance export and printable list.
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
