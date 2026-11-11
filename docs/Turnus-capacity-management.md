# Separate participant and staff capacities

Branch: `feature/turnus-capacity-management`.

## Agreed behavior

- Participant and staff capacities are separate and must each be at least 1.
- Existing participant capacities are preserved. Existing staff capacities start
  at 10 and can be edited individually. New editor defaults are 50 / 10.
- Shared `active` controls archival; registration opening is manual and separate
  for participants and staff. Both new switches initially inherit the old switch.
- **Agreed follow-up (2026-11-09, not yet implemented):** acceptance must not
  move a registration to ACCEPTED when the accepted count for its type has reached
  that Turnus's capacity. Enforce this in Registration API within the status-change
  transaction so concurrent requests cannot overbook; return a capacity-specific
  business error and show a clear message in Biuro. WAITLIST/REJECTED decisions
  remain available. Increasing capacity or freeing a place allows later acceptance.
- This rule applies separately to participants and staff. Public submissions may
  still be saved as NEW while a Turnus is full; registration-open switches remain
  manual and independent of capacity.
- Existing implementation still permits acceptance at and beyond capacity until
  this follow-up is delivered. The old behavior is superseded by the decision above.
- Implementation must enforce the limit against the current Turnus capacity, not
  an outdated hourly Registration cache. Decide how Biuro capacity changes become
  visible to the acceptance path, and serialize final-place allocation per Turnus
  and registration type so concurrent acceptances cannot overbook.
- A reduction below the accepted count of that type is rejected with 422. Equality
  is allowed. Only reduced capacities are checked: increasing capacity, unchanged
  limits and editing other fields do not require an accepted-count lookup.
- A failed or invalid count response prevents the reduction and preserves all
  fields/version. Optimistic edit-conflict protection remains in place.
- This check assumes the agreed single-operator workflow. It is not a distributed
  lock against simultaneous acceptance and capacity editing in separate databases.

## API and migration

Biuro Flyway V3 renames `capacity` to `participant_capacity` and
`registration_open` to `participant_registration_open`, adding staff columns.
Do not change V1/V2 or manually rename application database columns.

Catalogue requests/responses now use `participantCapacity`, `staffCapacity`,
`participantRegistrationOpen` and `staffRegistrationOpen`. These replace the old
shared fields; update both APIs and frontends together, then start Biuro before
Registration as previously agreed. Registration still refreshes its cache hourly
and retains the last successful catalogue during outages.

Public endpoints accept `registrationType=PARTICIPANT` (default) or `STAFF`.
Each form requests its own type. Active/open checks at submission use that type;
valid open submissions save as NEW even if accepted count exceeds capacity.
Invalid type parameters return 400.

The service-authenticated `GET /api/registrations/accepted-counts?turnusCode=...`
returns independent persisted ACCEPTED counts, without loading payloads.
Biuro uses it only for capacity reductions. Registration client connections/read
operations have 15-second / 30-second timeouts; writes are not automatically retried.

Biuro statistics return `participants` and `staff` groups with capacity, accepted,
free places, excess and NEW/WAITLIST/REJECTED counts. Gender breakdowns remain,
but totals include records with unknown/missing gender. Counts describe the selected
Turnus independently of row status/search filters. Type selection chooses the
statistics group; All shows both. Status updates refresh statistics, and late
responses from a previous Turnus selection cannot overwrite the current selection.
The dashboard's Turnus filter now uses the complete Biuro catalogue.

## Automated verification

- Full backend suite passed: 155 executed, ten optional MariaDB cases skipped.
- Those ten integrity/migration/database-count cases passed on disposable MariaDB 11.
- All 15 Biuro catalogue/capacity tests passed on MariaDB, including concurrent edits.
- A direct legacy schema upgrade preserved synthetic capacity, availability and version.
- Both frontend builds and Node test commands passed (five Biuro / three Registration files).
- Vue runtime checks verified type-filtered statistics, clearing selection and ignoring
  late responses from previously selected Turnusy. These do not replace browser checks.

## Manual verification

Use synthetic registrations and record results before release:

1. Upgrade an existing test catalogue. Confirm participant values, names, dates,
   flags and versions are preserved; staff capacity is 10 and switches are copied.
2. Create/edit a Turnus with distinct capacities and opposite registration switches.
   Reload and confirm persistence; zero values are rejected. Check list display and sorting.
3. After catalogue refresh, participant and staff forms show different available
   options. Closed-type submissions fail without losing entered data. Inactive
   Turnusy are unavailable to both types.
4. Accept participant/staff registrations independently. Verify both statistics,
   NEW/WAITLIST/REJECTED counts and filtering. At capacity, acceptance to ACCEPTED
   must fail with the capacity-specific response; WAITLIST/REJECTED remain available.
   Verify that freeing a place or increasing capacity permits acceptance. Confirm
   submissions can still be saved as NEW while capacity is full and registration is open.
   Verify a capacity change is respected without waiting for the hourly catalogue
   refresh, and concurrent attempts for the final place cannot both succeed.
5. Reduce each capacity below accepted count: expect 422, useful Polish message,
   preserved inputs and unchanged database version. Reduce to accepted count:
   expect success. Increase capacity and verify the statistics.
6. Stop Registration, then attempt a reduction: expect useful failure and no save.
   Other edits/increases remain possible. Restart Registration and retry.
7. Rapidly select different Turnusy and change statuses; ensure no old statistics
   overwrite the current selection. Check empty Turnusy and archived definitions.
8. Repeat create navigation, duplicate-code errors, stale-version conflict reload,
   unsaved-edit warnings and logout checks from the editor verification document.

Required staff roles/headcounts and per-role staffing statistics are represented
separately from overall capacity. A separate agreed warning should appear when the
sum of required staff-role counts exceeds staff capacity; it is advisory and does
not change either value. See [staff requirements](Turnus-staff-requirements.md).
