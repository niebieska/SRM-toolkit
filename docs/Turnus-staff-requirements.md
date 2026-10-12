# Turnus staffing requirements — phase 2

Branch: `feature/turnus-staff-requirements`, based on development after PR #34.

## Agreed scope

Each Winter Turnus has editable staffing targets in its Biuro definition. Targets
are planning information: they do not block acceptance, change total staff
capacity or operate registration switches. Summer staffing requirements are deferred.

Winter categories:

| Category code | Label | Included registration choices |
|---|---|---|
| kucharka | Kucharka | Kucharka |
| kierowca | Kierowca | Kierowca |
| animator | Animatorzy | Animator with no function, or candidate function |
| ksiadz_prowadzacy | Ksiądz prowadzący | Ksiądz + Prowadzący |
| ksiadz | Ksiądz | Plain priest, Pomoc, priest instructor Narty/Snowboard |
| instruktor_narty | Instruktor — narty | Instruktor + Narty |
| instruktor_snowboard | Instruktor — snowboard | Instruktor + Snowboard |

Animator has one combined target. Phase 3 will show regular animator/candidate
counts beneath that combined total. Priest instructor functions count only as
Ksiądz, never again under the standalone instructor category. Summer may use
different grouping later. One person belongs to one category.

## Configuration

`backend/biuro-api/src/main/resources/staff-seasons/winter-staffing.json` defines
category codes, labels and exact role/function mappings. Biuro loads it at startup
and validates unique category codes, valid selections, one category per selection,
and complete coverage of the current Winter role catalogue.

The file uses the same `STAFF_CATALOGUE_LOCATION` folder as the role files.
External configuration folders now need **all three files**: `summer.json`,
`winter.json`, and `winter-staffing.json`. Missing/invalid staffing configuration
fails Biuro startup; no silent fallback is used. Bundled changes require rebuild
and restart; external file changes require restart. No global season switch is added.

## Storage and API

Flyway V4 adds `staff_requirements` to the existing Biuro `turnus` row, with `[]`
as the initial value for every existing Turnus. It is JSON serialized into a TEXT
column, represented by a typed Java list; no separate requirements table is used.

Example Turnus property:

```json
"staffRequirements": [
  { "categoryCode": "animator", "requiredCount": 6 },
  { "categoryCode": "kucharka", "requiredCount": 2 }
]
```

GET list/detail returns the property. POST/PATCH accepts it with the other
Turnus fields. Counts are nonnegative JSON integers within Java Integer range;
unknown categories, duplicates, null entries, negative/fractional counts and
numeric strings are rejected. Zero entries are removed before storage. An empty
list means requirements are unconfigured/cleared; it does not deactivate a Turnus.
There is no restriction relating targets to total capacity.

For backwards compatibility, omitted/null requirements mean an empty list on
create and preserve current requirements on update. Explicit `[]` clears them.
Writes share the existing transaction and Turnus version check; invalid or stale
edits cannot partially overwrite the definition or requirements.

`GET /api/turnuses/staffing-categories?seasonType=WINTER` returns available
categories and mappings under operator JWT authentication. SUMMER returns `[]`.
Unknown/missing season produces 400. Requirements stay in Biuro and are omitted
from `/internal/turnuses`; Registration's hourly role cache is unchanged.

## Editor

The existing definition editor has a “Wymagana kadra” section. Each Winter category
has a number field; zero means no target. Fields remain visible in a compact vertical list inside a right-column panel
matching Parameters, with a planned total in the heading. Animator explains that candidates are
included. Summer displays a deferred-scope note. Save uses the existing button,
unsaved-change protection and conflict behavior. Category lookup failures offer
retry and prevent saving against an unavailable catalogue.

No special season-change workflow is included. A definition containing requirements
incompatible with its selected season is rejected rather than silently discarded.

## Verification

Automated checks cover migration on H2 in MariaDB mode, JSON round trips,
create/edit/clear, older-client preservation, stale and invalid edits, operator
security, strict integer input, category mapping integrity and internal lookup
isolation. Frontend tests cover category lookup and target editing/validation.
Existing capacity and registration regressions remain in the full suites.

Full backend run: 228 executed successfully, ten optional MariaDB cases skipped.
Seven Biuro and five Registration frontend test files pass; both production builds
pass. Saved Vue editor setup/watcher tests cover initial lookup, edits/removal,
preserved flags/version, and retry without data loss. These do not render a browser
DOM or establish visual acceptance.

Real MariaDB migration and browser acceptance remain to be verified; an H2 run is
not proof of execution against MariaDB. No production database is modified by tests.

Final acceptance after all phases:

- [ ] Open an existing Winter Turnus and confirm empty targets start at zero.
- [ ] Set Animator=6 and Kucharka=2, save, reload and confirm persistence.
- [ ] Change a target, set another to zero, save and verify both results.
- [ ] Confirm definition flags and total staff capacity stay as manually set.
- [ ] Verify a Summer Turnus still loads/edits and displays the deferred note.
- [ ] Verify invalid input is rejected; stale edits preserve current saved targets.
- [ ] Complete the final form checklist in [Season staff roles](Season-staff-roles.md).

Phase 3 required/accepted/missing statistics and animator/candidate breakdown are
not implemented in this phase.
