# Work in progress

## Deferred verification — SQL injection protection

- [ ] Review database access, including native counter queries, filters, search,
  sorting and future Turnus operations. Keep user values in bound parameters;
  never concatenate them into SQL. Allowlist any dynamic column/sort identifiers.
- Current inspected counter queries bind Turnus/type values; existing Spring Data
  derived queries do not concatenate submitted values. This is limited inspection,
  not a complete security verification or a claim of an existing vulnerability.
- Add focused injection-like input checks when reviewing those paths. Form field
  validation complements parameter binding; it does not replace it. Names with
  apostrophes must remain valid. Deferred alongside field-capacity review; no
  application changes made for this note.

External review reconciliation: accepted corrections and outstanding decisions
are incorporated into the existing workflow/deployment plans, not a separate roadmap.

Last updated: 2026-10-08

## Agreed direction

This plan follows the roadmap and priorities provided in our conversation.
The repository assessment supplies implementation evidence; it does not replace
that product direction. Progress and discoveries made after the plan are recorded
separately below.

Current priority (agreed 2026-10-08): deliver a complete registration-management
workflow and usable Turnus lists for mid-November. Close existing workflow gaps
before expanding administration features. Minimal Turnus management and file-based
season configuration support that flow; security/deployment gates still apply.

The active delivery sequence and acceptance gates are in
[Registration workflow release plan](Registration-workflow-release-plan.md).
Detailed domain scope remains in [Turnus management delivery scope](Turnus-management-plan.md),
and confirmed corrections in [First-release functional review](First-release-functional-review.md).
No digital arrival tracking. Manual payment tracking is outside the deadline plan
unless core work is complete with time remaining.

## Provided plan (original roadmap)

Retained for context. The 2026-10-08 direction above supersedes its Turnus
sequencing and the former recommendation to defer Turnus management until SIT.

The checklist below tracks completion of each phase. Local implementation,
verification, merge, and deployment are distinguished in the progress section.

### Phase 0 — Make the current MVP safe

The ten items from the provided roadmap:

1. [ ] Secure registration-api management endpoints and authenticate Biuro's calls.
2. [ ] Remove tracked `.env`.
3. [ ] Rotate admin/JWT secrets.
4. [ ] Add `.env.example`.
5. [ ] Restrict Actuator.
6. [ ] Fix capacity: source it from Turnus instead of hardcoding 50.
7. [ ] Fix registration-code concurrency.
8. [ ] Add database uniqueness for `(turnus_code, pesel_hash)`.
9. [ ] Fix Dockerfiles and Compose.
10. [ ] Externalize frontend API URLs and CORS.

Execution order from the provided summary:

1. Security first: protect list/detail/status operations, authenticate service calls,
   remove/rotate secrets, and restrict Actuator.
2. Data integrity: safe registration-code generation, database duplicate prevention,
   and consistent Turnus capacity.
3. Deployment: repair build paths and packaging, add the registration frontend,
   externalize environment configuration, and verify `docker compose build` from
   a clean checkout.

The intended security boundary is:

- Public: participant/staff submission and turnus lookup.
- Internal: registration lists, details, and status changes, reached through
  JWT-protected Biuro and authenticated backend calls.

### Phase 1 — Linux TEST / SIT deployment

The proposed server remains 4 vCPU, 8 GB RAM, approximately 80 GB SSD,
Ubuntu 24.04, Docker, and HTTPS.

- [ ] Configure reverse proxy and HTTPS for frontends and APIs.
- [ ] Configure persistent MariaDB storage and test secrets.
- [ ] Configure real test SMTP.
- [ ] Configure basic backups.
- [ ] Use synthetic registrations.
- [ ] Perform complete E2E tests on the running environment.

### Phase 2 — Finish customer requirements

Audit the existing UI against the customer list and implement actual gaps.
WAITLIST has changed from a build task to a verification task.

- [ ] Verify backend enum/validation, status service, and status notifications.
- [ ] Verify Accept / Reject / Waitlist from registration details.
- [ ] Verify table actions, badges, filters, email templates, and tests.
- [ ] Verify clear-search X.
- [ ] Verify clear-all filters.
- [ ] Verify structured rejection reasons with an optional comment.
- [ ] Complete remaining staff-role statistics requirements.
- [ ] Clarify and implement registration editing.
- [ ] Clarify and implement soft deletion.

Items listed as likely gaps in the original summary must be checked before being
implemented again. Some already exist in PR #22; see progress below.

### Phase 3 — Reliability

Before heavier use, and while the customer is unavailable:

- [ ] Add a CI pipeline.
- [ ] Configure RestClient timeouts.
- [ ] Add a transactional notification outbox and retries.
- [ ] Add status audit history.
- [ ] Add integration tests.
- [ ] Improve upstream failure handling.
- [ ] Document and exercise backup/restore procedures.

Keep the intended email behavior: an SMTP failure must not undo a saved
registration. Improve delivery reliability so a failed email is recoverable
rather than permanently lost after a log entry.

### Phase 4 — Domain architecture

Return to the previously planned domain work:

- [ ] Turnus database and administrative model.
- [ ] Person database/model.
- [ ] Registration history.
- [ ] Attendance history.
- [ ] Staff requirements.
- [ ] Notes.
- [ ] Remove `turnuses.json` after its consumers migrate to the Turnus model.

Preserve the intended service responsibilities:

- registration-api: Registration, registration workflow, validation, notifications,
  and ownership of registration persistence.
- biuro-api: administrative workflows and future Person, Turnus, history, and
  operational data.

Original recommendation: use the existing Turnus source before a database migration.
Superseded on 2026-10-08: implement shared Turnus management next and include
the statistics capacity correction in that delivery.

### Phase 5 — Finance and operational features

- [ ] Deposits.
- [ ] Payments.
- [ ] Reminders.
- [ ] Attendance.
- [ ] Exports.
- [ ] QR workflows.
- [ ] Reporting.
- [ ] Richer roles.

## Deployment priorities from the provided summary

**Must fix before test deployment:** security exposure, secrets, Docker,
environment configuration, capacity, database integrity, HTTPS, and basic backups.

**Can improve while the customer is unavailable:** outbox, audit, CI/CD, Turnus DB,
Person/history model, normalization, and monitoring. These are not all prerequisites
for a controlled test server.

Do not automatically require transactional outbox, per-user identity, a normalized
registration schema, OpenAPI, central observability, IaC, or full CI/CD before SIT.

## Agreed registration rules — 2026-10-07

These are design decisions for submission integrity and future editing, not
implemented features.

### Numbering and duplicate prevention

- Use independent database-backed counters per `(turnus_code, registration_type)`.
  Staff and participants have separate numbering within each Turnus.
- Preserve the existing code format: `REG-P-<turnus>-<number>` and
  `REG-S-<turnus>-<number>`.
- Prevent more than one registration for the same person in the same Turnus,
  regardless of registration type: unique `(turnus_code, pesel_hash)`.
- Keep registration codes unique. Never decrement counters after deletion or
  rejection; gaps are acceptable and codes are not registration counts or queue positions.
- Current registrations are test data and may be erased during the implementation.
  Counters can start at zero, producing 1 for the first registration. No data has
  been erased as part of recording this decision.
- Rejected or transferred registrations still count toward the uniqueness rule.
  A return to a previously used Turnus requires an explicit action on the existing
  registration rather than silently creating a duplicate.

### Counter implementation plan — confirmed 2026-10-07

The agreed choice is independent counters per Turnus/type, preserving readable
numeric suffixes for Biuro users. The discussed global auto-increment ID alternative
is not the chosen public-code numbering scheme. Sorting complements the suffix;
neither the suffix nor the current row index is a live waitlist position.

- [ ] Use a focused implementation branch, `fix/registration-sequence-integrity`.
- [ ] Add a Flyway migration creating an InnoDB `registration_counter` table with
  composite primary key `(turnus_code, registration_type)` and `last_number BIGINT`.
- [ ] Add named uniqueness for `(turnus_code, pesel_hash)` and retain unique public codes.
- [ ] Reset the explicitly identified disposable test database before using new
  numbering. Keep destructive cleanup out of general schema migrations. Do not
  reset counters independently of the registrations they identify.
- [ ] Add a counter repository using atomic insert-or-increment, followed by a
  read of the resulting value on the same transaction/connection. First use must
  work under concurrency; no separate check-then-insert operation.
- [ ] Allocate the number and save/flush the registration in one short persistence
  transaction invoked through a Spring bean. Replace count-plus-one allocation
  and use `long` for sequence values in code generation.
- [ ] Keep parsing, remote Turnus lookup, and SMTP outside that database transaction.
  Send confirmation only after registration commit; notification durability remains
  separate planned reliability work.
- [ ] Translate the specific person/Turnus uniqueness failure into `ALREADY_REGISTERED`
  / HTTP 409 outside the failed transaction. Do not classify every database error
  as a duplicate. Any bounded transient-lock retry repeats the entire transaction.
- [ ] Verify with real MariaDB integration tests on separate connections: concurrent
  first use, many submissions to one group, independent groups, duplicate submissions
  including cross-type duplicates, rollback, deletion, rejection, and persistence
  across application restart.
- [ ] Separately fix the form's submission-busy state so it follows the actual parent
  request instead of resetting immediately after emitting an event.

Allocation rules: the first successful registration receives 1. Committed numbers
are stable and never reused after deletion/rejection. Rolled-back, unissued numbers
may be reused; gaps are acceptable. The suffix reflects allocation order within its
Turnus/type, not when an applicant opened the form or an automatic acceptance priority.

Future Biuro Turnus integration does not change the allocator: Biuro owns the stable
Turnus definition/code, registration-api owns counters and registrations. Counter
rows are created on first registration, without per-Turnus DDL or a distributed
Turnus-creation transaction. Editing and transfers remain outside this change.

### Editing and Turnus transfers — future scope

- Only authenticated Biuro users may edit registrations. Applicants have no
  self-service editing or transfer capability.
- Ordinary permitted corrections update the existing registration and retain its code.
  The complete editable-field list and rules for identity/type changes remain to be defined.
- In Biuro, changing Turnus can appear as “Edit → change Turnus.” The backend treats
  it as a transfer, not an in-place change of the original record's Turnus or code.
- Validate destination eligibility and duplicate constraints, create a destination
  registration with a new destination-specific code, mark the original as transferred,
  and link both records to preserve history.
- Persist both changes atomically. If transfer validation or persistence fails,
  leave the original registration unchanged. Release its occupied place, if applicable,
  only when the transfer succeeds.
- Destination registration normally starts as `NEW`. Preserving acceptance would
  require an explicit administrative decision; detailed transition rules remain to be designed.
- If a registration already exists for the person in the destination Turnus, block
  creation and require an explicit decision about that existing registration.
- Send notifications after a successful commit. Future audit history should record
  the Biuro user, time, and changes made.
- Editing/transfers, the transferred status, record links, and audit history are
  future implementation work. They are not part of the immediate counter fix.

## Updates and progress

### 2026-10-06 — Existing feature work merged

- `development` and the security branch base are at `4c72119`, PR #22.
- Existing work includes waitlist support, status notifications, details-modal
  actions, badges/filters, clear-search, clear-all filters, structured rejection
  reasons, and statistics refresh after status changes.
- These need customer-requirement and E2E verification, not automatic reimplementation.
- No SIT deployment or customer acceptance has been verified.

### 2026-10-06 — Security boundary implemented locally

Current branch: `security/registration-api-management`.

- [x] Protect registration list/detail/status routes, including participant/staff lists.
- [x] Authenticate Biuro calls with a dedicated backend service account.
- [x] Preserve public submissions and turnus lookup.
- [x] Restrict Actuator to health status without details/components.
- [x] Add and run security/client tests.
- [x] Document local/SIT credentials in [README.md](../README.md) and
  [Advises-for-future.md](Advises-for-future.md).
- [x] Commit the initial security implementation and initial test coverage.
- [ ] Commit the additional regression tests and work-tracking documents.
- [ ] Open and merge the security PR into `development`.
- [ ] Verify the implementation in SIT.

Phase 0 items 1 and 5 are implemented and tested locally, but remain unchecked in
the phase checklist because they have not been merged. Secrets, integrity, and
packaging work remain pending.

### 2026-10-06 — Verification and review

- All 37 maintained backend tests passed, including 12 new security/client tests.
- Tests cover denied anonymous/invalid management access, authenticated management
  calls, public routes, browser preflight, statelessness, blank credentials, and
  actual Actuator health output containing only status.
- Three temporary probes reproduced the upstream error-handling issues below.
  All 40 tests passed with those probes; their source and compiled class were then
  removed. They assert the observed problematic behavior, not its correction.
- Mockito required test execution outside the sandbox for Java agent attachment.
- `git diff --check` passed.
- Compose validation/build remains unverified: this workstation lacks Compose v2,
  and its legacy Compose rejects the existing file version.
- No new authentication bypass was found in the tested paths. This is not a full
  penetration test or a deployment-security guarantee.

### 2026-10-06 — Startup and SIT configuration clarified

- The new service password is mandatory; a local startup attempt failed because it
  was missing. This is intentional startup protection, not a logic regression.
- Both backends must receive the same `REGISTRATION_SERVICE_PASSWORD`.
- Local Maven/IDE runs must explicitly receive it; Spring Boot does not automatically
  read the Compose `.env` file.
- SIT should inject it from a restricted server-only environment file using
  Compose `--env-file`. Keep it stable across restarts and recreate both backends
  when rotating it.
- Service credentials belong only to backends. Use TLS or an isolated trusted
  backend network because HTTP Basic credentials are not encrypted by encoding.

### 2026-10-06 — Additional security regression tests completed

- Initial implementation and test coverage are now committed locally as
  `6bd42d3` and `c4408ad`; the branch is two commits ahead of local `development`.
  This check did not refresh remote references or establish remote PR status.
- Added 23 maintained regression cases; **all 60 backend tests pass**.
- Eight startup-configuration cases load each backend's actual `application.yml`
  in a minimal application context: missing/empty/whitespace service passwords
  prevent startup, while configured environment credentials permit startup.
  Tests isolate developer/CI environment variables to avoid accidental passes.
- Six malformed/unsupported authentication cases return 401 without invoking
  management operations: incomplete Basic headers, invalid Base64, missing colon,
  unknown service username, and Bearer credentials.
- Six public validation cases run through the real security filter and exception
  handler with mocked submission services. Invalid PESEL, missing guardian/consents,
  and duplicate registration errors retain their expected 400/409 JSON responses
  and public-origin CORS headers for participant/staff routes.
- Connection failures are simulated for all three Biuro client operations:
  list, detail, and status update. Each produces 502 and does not return a successful
  result or expose service credentials in the public error reason.
- `git diff --check` passed. Production implementation files were unchanged.
- These additional tests and WIP documentation changes remain uncommitted.
- Live cross-service/Compose E2E and configured-timeout behavior remain pending
  for SIT and the planned reliability work; these tests do not claim to cover them.

### 2026-10-07 — Biuro list sorting

- Added clickable sortable headers for code, type, Turnus, name, age, status, and
  submission date. Headers indicate direction and expose accessible sort state.
- Default is oldest submission first. Repeated clicks toggle ascending/descending;
  changing column starts ascending. Sorting is kept across filtering and list reloads.
- Code sorting uses numeric comparison (`2` before `10`); dates use timestamps,
  names use Polish collation, and missing values stay last in either direction.
- Equal sort values use the registration code as a deterministic tie-breaker.
  This does not establish exact arrival order for simultaneous submissions across
  different counters or compensate for timestamp precision limits.
- Sorting runs locally over the full list currently returned by Biuro. Future API
  pagination will require server-side sorting before pagination.
- Counter implementation is planned above; backend allocation and test data are
  unchanged. Sorting changes are currently uncommitted on `security/secrets-hardening`.
- Verification: Biuro production build passed; all seven sorting regression checks
  passed when running `node src/utils/registrationSorting.test.js`. The new
  `npm test` command also passed. `git diff --check` passed. Browser interaction
  testing has not been performed; the tests cover ordering logic, not click/keyboard flows.

### 2026-10-07 — Frontend dependency audits

- User ran `npm audit fix`; the lockfile changed, while Tailwind remains 3.4.19.
- The user subsequently ran the same command in registration-frontend. Its
  lockfile also changed, with Tailwind remaining 3.4.19 and the same affected
  dependency versions. Both supplied reports list the same two advisories.
- The supplied audit reports seven remaining findings (two moderate, five high)
  in the Tailwind dependency chain, representing two underlying advisories rather
  than seven independent application flaws.
- Installed affected dependencies include braces 3.0.3 and postcss-selector-parser
  6.1.4. The braces advisory lists no patched release; selector-parser is patched
  in 7.1.6. Sources:
  [braces advisory](https://github.com/advisories/GHSA-vfj7-8cjw-p6xm) and
  [selector-parser advisory](https://github.com/advisories/GHSA-rj75-hqrm-r3gf).
- These dependencies are in the CSS/build tooling chain. No application path was
  identified that feeds registration input into them. This reduces exposure for
  static production assets, but does not remove risks from untrusted build inputs.
- Avoid an automatic `npm audit fix --force`: it proposes a breaking Tailwind 4
  upgrade and requires explicit migration and visual verification.
- After the user's dependency updates, Biuro's production build, `npm test`, all
  seven directly executed sorting checks, and `git diff --check` passed.
- Registration frontend's production build also passed after its dependency
  updates. This verifies compilation; browser and visual checks remain pending.
- [ ] Address remaining findings in a focused frontend dependency change: evaluate
  supported patched dependencies or Tailwind migration, then verify styling and
  re-run the audit. No dependency overrides or forced upgrades were applied here.

### 2026-10-07 — Turnusy UI prototype

- Added a Biuro navigation link and standalone `/turnusy-demo` route. The route
  intentionally needs no backend or login and contains only synthetic fixtures.
- Prototype includes turnus cards, participant/staff/waitlist views, search,
  temporary arrival checkboxes, editable settings, capacity warnings, and
  insurance/arrival list previews with CSV download and print layout.
- All changes are in memory and reset on refresh. No registration API calls or
  real personal data are used. Insurance fields are placeholders pending an
  insurer template; backend ownership and persistence remain future work.
- Production build, fixture sanity checks, and whitespace checks passed.
  Browser interaction, print output, and visual verification remain pending.

### 2026-10-07 — Registration-code integrity implementation

- Added V2: named `(turnus_code, pesel_hash)` uniqueness and InnoDB counters keyed
  by `(turnus_code, registration_type)`. Existing numeric suffix maxima seed each
  group; existing codes are retained. Duplicate legacy people require explicit
  resolution before migration; no automatic deletion is performed.
- Allocation uses MariaDB atomic upsert and SELECT in the same short transaction
  as save/flush. Participant and staff suffixes are independent. Rollback undoes
  allocation; committed counters survive rejection/deletion. Notifications remain
  outside the persistence transaction, after commit.
- A real concurrent cross-type duplicate test exposed a MariaDB deadlock. Added
  at most four retries only for confirmed deadlocks (1213), after rollback, with
  randomized backoff. Other database failures are not reported as duplicates;
  only the named person constraint maps to ALREADY_REGISTERED.
- Clean-schema startup exposed missing Boot 4 Flyway integration. Replaced bare
  flyway-core with spring-boot-starter-flyway and added flyway-mysql support.
- Verification: 48 registration-api tests passed, zero skips, against disposable
  MariaDB 11.8.9, including eight new integrity/migration tests. Covers first-use
  concurrency, duplicate races across types, independent groups, rollback,
  rejection/deletion, direct DB uniqueness, migration seeding/rerun, and refusal
  of legacy duplicates. Test container stopped afterward; app data untouched.
- Reproduction and migration notes: backend/registration-api/src/test/INTEGRATION_TESTS.md.
  Database tests are opt-in via TEST_DB_URL and otherwise skipped. No actual
  server-restart or browser E2E test was performed; migration rerun/reconnect was
  verified. Pending commit, review, merge, and deployment.

### 2026-10-08 — Integrity coverage review

- User confirmed startup and registration numbering on the existing local
  dataset. Historical combined suffixes are preserved; counters are independent
  going forward. Dataset cleanup is explicitly deferred.
- Reviewed committed integrity implementation (624bbc3). Added regression
  coverage for legacy schema adoption without Flyway history, default refusal
  before baseline, preservation of records/counters, and subsequent normal startup
  migration checks. No deployed migration SQL was changed.
- Added deterministic retry tests for rollback-before-retry, one successful commit,
  five-attempt exhaustion, no retry for other database errors, and interrupted
  backoff. Added explicit duplicate error -> HTTP 409 mapper coverage.
- Tightened the integrity-test environment guard to require the integrity_test
  schema name before Spring initializes, matching the migration test guard.
- Full backend verification: 74 tests passed, zero failures/errors/skips
  (54 registration-api, 16 biuro-api, 4 email-service), including nine real-MariaDB
  integrity/migration tests. These DB tests remain opt-in for normal test runs.
- Updated backend/registration-api/src/test/INTEGRATION_TESTS.md with the one-time
  baseline procedure and coverage details. Review additions remain uncommitted;
  unrelated frontend dependency lockfiles remain outside this change.
- No MVP blocker found in counter/uniqueness logic. Full server restart and browser
  E2E are not covered by this suite; persistent counters and migration reruns are.

### 2026-10-08 — File-based season configuration agreed

- Include a small file-based season configuration in this version alongside
  Turnus management: active season, staff roles/subroles and certificate requirements.
- Backend-owned options and validation; frontends consume the same source.
  Stable codes, retained previous season files, and changes applied on restart.
- Audit hardcoded values first. No configuration editor or runtime switching in
  this scope. Service ownership and historical/public season behavior remain open.
- Detailed scope recorded in Turnus-management-plan.md; implementation pending.

## Future extensions — editable email templates

- [ ] Allow authorized Biuro users to edit notification subjects and bodies
  without changing registration-api or redeploying email-service. Deferred;
  this is not a current MVP requirement.
- Keep notification triggers, recipient selection (including minor/guardian
  rules), and supplied data in application code. Editing controls wording only.
- Retain stable template identifiers and a documented, allowlisted set of
  placeholders such as recipientName, registeredName, turnusName and registrationCode.
  Validate placeholders before publishing; do not execute arbitrary template expressions.
- Provide draft/published versions, sample-data preview, a test-email action,
  change history and restoration of an earlier version. Test-email delivery
  requires an explicit user action and recipient selection.
- Current foundation: registration-api supplies variables; email-service owns
  file-based HTML templates. Future storage and editor design remain undecided.

## Future extensions — Turnus group announcements

- [ ] Let authorized Biuro users compose and explicitly send an announcement
  to a selected Turnus group, for example pre-departure information. Deferred
  beyond core Turnus delivery; scheduling is a separate later option.
- Proposed groups: accepted participants, accepted staff, or both. Preview
  message content and recipient count before confirmation. Drafts may start
  from the editable templates described above.
- For participants, use guardian addresses for minors and personal addresses
  for adults. Decide minor-staff routing separately; do not assume that
  confirmation-email rules automatically define announcement recipients.
- Send individual messages without exposing other recipients' addresses.
  Decide shared-address deduplication and per-person personalization rules.
- Reuse email-service for template rendering and SMTP delivery. biuro-api
  selects recipients and owns announcement orchestration/history; the frontend
  handles composition, preview and explicit send confirmation.
- Before relying on group delivery, replace swallowed send failures with
  reliable queued delivery, per-recipient outcomes, bounded retries and duplicate
  send protection. Record the selected recipient snapshot and announcement
  version so later registration/template changes do not alter a queued send.

## Optional backlog — registration without PESEL

- [ ] Allow registration without PESEL when a valid shared exception key is
  entered. Deferred; implement if time permits after the current MVP priorities.
- Validate the key on the backend, rate-limit attempts, and retain all other
  required validation. Keep PESEL absent (`NULL`), rather than creating a fake
  PESEL. Record that the exception was used, without storing the entered key
  on the registration or logging it.
- Require first name, last name, and date of birth for this path. Generate a
  versioned HMAC fingerprint from normalized names and date of birth using a
  separate server secret. Rotating the shared exception key must not change
  fingerprints. Define normalization and secret-rotation handling before implementation.
- Compare fingerprints within the same turnus to flag potential duplicates
  for Biuro review, rather than automatically rejecting a matching person.
  Names and birth dates are not unique identifiers; spelling differences can
  also hide duplicates. Date of birth plus the shared key alone is insufficient:
  unrelated people born on the same day would match.
- Preserve the existing PESEL-based duplicate checks for submissions with PESEL.
  Matching a person across PESEL and non-PESEL submissions remains a limitation
  requiring manual review.
- Verify valid/invalid keys, required fields, fingerprint normalization,
  same-turnus warnings, different-turnus submissions, and exception-key rotation.

## Discovered issues and their place in the plan

These issues were pre-existing, not introduced by the security implementation.
The current implementation addresses some locally; remaining work stays in its
planned phase.

| Issue | Plan location | Progress / discovery |
|---|---|---|
| Unauthenticated registration management endpoints | Phase 0 security | Protected and tested locally; initial implementation committed, pending merge/deployment. |
| Tracked `.env`, known admin password/JWT defaults | Phase 0 secrets | Pending; blocks SIT. Default Biuro credentials can still undermine the boundary if overrides are omitted. |
| Actuator environment/detailed health exposure | Phase 0 security | Restricted and tested locally; initial implementation committed, pending merge/deployment. |
| Turnus capacity 60 in JSON versus 50 in statistics | Phase 0 correctness | Pending. Correct as part of shared Turnus management before deployment; see Turnus-management-plan.md. |
| Count-plus-one registration-code race | Phase 0 integrity | Implemented locally on bugfix/registration-code-integrity; independent transactional counters and bounded deadlock retries, verified against MariaDB 11. Pending commit/merge/deployment. |
| No database uniqueness for duplicate registrations | Phase 0 integrity | Named turnus/PESEL unique constraint added in V2; concurrent violations map to ALREADY_REGISTERED (409). Pending commit/merge/deployment. |
| Broken Compose/Dockerfile paths and missing registration frontend | Phase 0 deployment | Pending. |
| Localhost API URLs/static CORS | Phase 0 environment configuration | Pending. |
| Upstream 404/business errors converted into 502 by Biuro | Phase 3 upstream failure handling | Reproduced with a mock upstream; pending. |
| Empty successful upstream responses become empty lists or misleading detail 404s | Phase 3 upstream failure handling | Reproduced; pending. Exceptions on list calls now correctly produce 502, but empty bodies retain the old fallback. |
| Missing explicit upstream timeouts and raw custom RestClient builder | Phase 3 timeouts | Pending. Configure the actual client; merely adding Boot properties may not affect the raw builder. |
| Email failures logged/swallowed without durable retry | Phase 3 outbox/reliability | Pending. |
| Missing status audit history and CI | Phase 3 stabilization | Pending. |

Code smell: duplicated broad exception handling in `RegistrationApiClient`.
Address it with an explicit shared error-mapping policy during Phase 3.
The upstream handling and timeout findings do not need to expand the focused
security PR; secrets hardening must still precede SIT.

## What to do next

1. Merge the reviewed registration-integrity changes; leave dataset cleanup for later.
2. Agree and implement the core scope in [Turnus-management-plan.md](Turnus-management-plan.md).
3. Include consistent capacity/dates, accepted-person views, insurance export and
   printable on-site registration list. No digital arrival tracking.
4. Add manual payment tracking only if time permits without delaying core work.
5. Complete remaining secrets, Docker/Compose, API/CORS, HTTPS and backup work;
   verify the complete build before TEST/SIT and E2E testing.

### 2026-10-08 — Product priority and scope clarification

- User explicitly prioritizes Turnus management and useful delivery over an earlier
  deployment date. Capacity correction belongs in the shared-source migration.
- On-site registration means generating a printable list, not tracking arrivals.
  Arrival checkboxes in the experimental prototype are outside agreed scope.
- Manual payment tracking is optional; amount/date/note/total are proposed fields,
  with deposit, balance and correction rules still to agree.
- Added Turnus-management-plan.md as the focused scope document. Original roadmap
  is retained above with superseded sequencing clearly marked. No implementation
  or merge is claimed by this planning update.

### 2026-10-08 — First-release functional/configuration scan

- Recorded static findings and open product decisions in
  [First-release-functional-review.md](First-release-functional-review.md).
- Confirmed gaps include Turnus date/capacity duplication, placeholder payment
  instructions applied to staff too, missing role validation/certificate mapping,
  lost emergency-contact relationship details, incomplete backend validation,
  submit/error handling and misleading email-success reporting.
- Birthday/guardian policy, capacity enforcement, staff fee/recipient rules and
  export fields need agreement. Existing MVP hardening prerequisites remain.
- No implementation or new runtime test is claimed by this scan.

### 2026-10-08 — Workflow-first release audit

- User prioritizes registration tracking and Turnus lists for mid-November.
- Static audit confirmed existing submit/list/filter/sort/details/status flows,
  including WAITLIST and preset rejection reasons. These must be verified rather
  than rebuilt. Production print/export code was not found on the current branch.
- Added Registration-workflow-release-plan.md with implementation status,
  acceptance gates, ordered work packages and a scope guard. No new runtime
  verification or implementation is claimed by this audit.
- Next package: submission validation, lossy emergency-contact mapping, and
  pending-submit/API error handling. Agree export columns and open business rules
  early alongside implementation. Track actual hours and accepted outcomes weekly.

### 2026-10-08 — Separate functional scope and deployment readiness

- Registration-workflow-release-plan.md owns first-release functional acceptance.
- deployment-checklist.md now owns operational checks, with separate controlled
  synthetic-data TEST/SIT and real-registration release gates. It no longer defers
  required Turnus/list work or treats integrity as an optional alpha improvement.
- Preserved later product ideas in Future-product-backlog.md. Optional payment
  tracking remains conditional on core completion; no digital arrival tracking
  was added to release scope.
- Documents cross-link rather than duplicate operational prerequisites. WIP
  remains the progress/evidence index. All acceptance checks remain unchecked;
  this documentation restructuring claims no deployment or runtime verification.

### 2026-10-08 — Registration validation implementation

- Added SubmissionPayloadValidator on both submission paths, preserving the
  existing JSON contract and historical payloads. Enforces current required
  person/contact, guardian/ICE, address, health and consent fields; roles/subroles
  follow current frontend choices pending shared season configuration.
- Certificates remain declarations. Backend validates their boolean shape and
  other-certificate explanations; full catalogue/qualification rules remain
  season work. No new mandatory qualifications or minority-date policy introduced.
- Corrected lost ICE relationOther and Biuro label, composite priest certificate
  mapping, parent-owned submission busy state and API error-code preservation.
- Added backend payload/PESEL/oversize/pre-persistence regressions and five frontend
  API/payload tests. Both frontend builds pass. Backend suite passed: 91 executed tests, zero failures/errors, with nine
  database tests skipped because TEST_DB_URL was not set. Existing real-MariaDB tests are opt-in and were not
  rerun in this validation run; persistence/migrations were not changed.
- See Registration-validation-contract.md for current requirements and limits.
  Application 64 KiB check is not a streaming/ingress body limit. Browser validation,
  slow/double-click and back-navigation E2E remain pending.
- Changes uncommitted; unrelated dependency lockfiles were not altered by this work.

### 2026-10-08 — Remove aggregate payload cap; review field capacity

- Removed the unagreed 64 KiB check, associated test, HTTP mapping and frontend
  message. Earlier notes/test counts describe the previous iteration.
- Form-field-capacity-review.md compares actual UI limits, backend bounds and
  database storage. Most free-text bounds are backend-only choices needing
  agreement and UI alignment, not database constraints.
- Staff additional-health UI maximum (1000) lacks backend enforcement. Combined
  rejection reason can exceed its actual VARCHAR(500) database column.
- No new frontend caps or truncation added. Per-field backend bounds retained
  pending product review. Frontend API tests passed; whitespace checks passed.

### 2026-10-08 — Pre-commit validation diff review

- Review caught that the previously claimed composite certificate-source fix had
  not actually landed. Added resolveCertificateSource with a dedicated regression
  test, then wired it into the staff step. Corrected HTTP status fallback after
  domain-code handling, and JSON null error-body handling with a regression.
- Current verification: 90 backend tests passed, nine opt-in database tests skipped;
  seven frontend logic tests passed; registration frontend build and whitespace
  checks passed. Biuro build passed in the preceding implementation run; its only
  component change remains the ICE relationOther label. No browser/component/E2E
  verification or database-suite rerun is claimed for this review.
- Remaining assumptions: backend-only field sizes are still pending agreement;
  role rules are temporarily duplicated rather than shared season configuration;
  certificates are declarations, not checked qualification/allowlist policies.
  New JSON field/type checks are not a complete typed payload schema.
- Suggested commits: backend rules/tests; frontend submission/error/ICE fixes and
  API tests; staff role/certificate UI/helper/tests; documentation separately.
  Dependency audit lockfile changes remain a separate change/branch. No commits
  were created by this review.


## 2026-10-08 — Documentation organisation

Moved project plans, progress notes and assessments into `docs/`. The root README links to the documentation index. Integration-test instructions remain beside the tests. Source paths mentioned in these documents are relative to the repository root unless stated otherwise.


## 2026-10-09 — Validation branch status and verification

- Current branch: `fix/registration-validation`, commit `a9a245b`, seven commits ahead of local `development`. Validation, frontend fixes, tests and existing planning documents are committed. Test-scenario documentation/index changes and both dependency lockfiles remain uncommitted at this review.
- Automated verification: 90 backend tests passed in the normal suite, then nine integrity/migration tests passed against disposable MariaDB 11; 99 backend tests executed successfully in total. All 14 frontend logic tests and both production builds passed. Initial sandbox Mockito attachment failure was resolved by rerunning outside the sandbox. The disposable database was removed.
- Frontend checks used locally installed dependencies with uncommitted lockfile updates; they are not a clean-checkout dependency verification.
- The user reports testing most manual cases and plans repeat verification in a few days. Individual outcomes remain to be recorded in [the scenario document](Registration-validation-test-scenarios.md); do not infer complete browser or deployed email acceptance.
- Updated the functional plan to distinguish completed implementation from pending acceptance. Removed the stale aggregate payload-limit requirement: that addition was rejected and removed. Free-text bounds and phone compatibility still need agreement; shared season roles remain future work.
- Earlier entries describe earlier iterations and their test counts. This entry records the latest verification, without marking the complete release workflow accepted.
