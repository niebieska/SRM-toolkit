# Turnus staffing requirements — phase 2

Phase 3 staffing statistics are now implemented on `feature/staffing-statistics`:
see [Staffing statistics](Staffing-statistics.md). Earlier phase notes below describe
the state at their respective implementation dates. The agreed display is
accepted/required with subrole counts; missing/excess figures are not included.

Original phase 2: `feature/turnus-staff-requirements`, merged as PR #35.
Summer extension: `feature/summer-staff-requirements`, based on development after PR #35.

## Agreed scope

Each Summer and Winter Turnus has editable staffing targets in its Biuro definition. Targets
are planning information: they do not block acceptance, change total staff
capacity or operate registration switches. Each season selects its own category mappings.

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
Ksiądz, never again under the standalone instructor category. Summer uses the
separate mappings below. One person belongs to one category.

Summer categories:

| Category code | Label | Included registration choices |
|---|---|---|
| kucharka | Kucharka | Kucharka |
| kierowca | Kierowca | Kierowca |
| animator | Animatorzy | Standalone minor candidate; adult Animator + candidate or Animator function |
| animator_wychowawca | Animator wychowawca | Animator + Animator wychowawca |
| ksiadz_prowadzacy | Ksiądz prowadzący | Ksiądz + Prowadzący |
| ksiadz | Ksiądz | Ksiądz + Wychowawca or Pomoc |
| sternik | Sternik | Sternik, Sternik z opiekunem, Ksiądz + Sternik |
| ratownik | Ratownik | Ratownik |

Summer Animator and candidates share one target; Animator wychowawca has a separate
target. Sternik z opiekunem shares Sternik's target, but must remain visible as a
separate breakdown in phase 3 statistics. Ksiądz + Sternik counts only as Sternik;
leading priest remains separate. Other priest functions remain Ksiądz. No existing
registration role identifiers, age rules, certificate options or saved payloads change.

## Configuration

`backend/biuro-api/src/main/resources/staff-seasons/winter-staffing.json` and
`summer-staffing.json` define category codes, labels and exact role/function mappings.
Biuro loads both at startup and validates unique category codes, valid selections, one category per selection,
and complete coverage of each corresponding season role catalogue.

Both files use the same `STAFF_CATALOGUE_LOCATION` folder as the role files.
External configuration folders now need **all four files**: `summer.json`,
`winter.json`, `summer-staffing.json`, and `winter-staffing.json`. Missing/invalid staffing configuration
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

### Client verification: staff places versus required staffing

For now these values remain independent:

- **Miejsca dla kadry**: the configured total number of staff places.
- **Łącznie wymagana kadra**: the sum of role-specific staffing targets.

Neither value is the accepted staff count. No automatic synchronization,
capacity changes, save restrictions or new warnings are introduced by this decision.

- [ ] Confirm with the client whether these represent separate concepts in their
  workflow or whether staff places should eventually be derived from role targets.
- [ ] If they remain separate, confirm whether a comparison/warning is useful
  when the required total exceeds the available staff places.

Any later change requires an explicit product decision; current behavior is retained.

For backwards compatibility, omitted/null requirements mean an empty list on
create and preserve current requirements on update. Explicit `[]` clears them.
Writes share the existing transaction and Turnus version check; invalid or stale
edits cannot partially overwrite the definition or requirements.

`GET /api/turnuses/staffing-categories?seasonType=WINTER` returns available
categories and mappings under operator JWT authentication. SUMMER returns its
eight categories; WINTER retains its seven categories.
Unknown/missing season produces 400. Requirements stay in Biuro and are omitted
from `/internal/turnuses`; Registration's hourly role cache is unchanged.

## Editor

The single-page editor uses a top-aligned two-column layout: information, dates,
location and description occupy approximately two thirds, with registration
settings below the information card; compact capacity and staffing cards occupy
the sidebar. Cards stack below
1024px; basic fields use two columns from 640px. No technical/spiritual grouping,
wizard or draft feature is introduced.

Staffing rows preserve the configured labels/codes, including Kucharka, with a
live total and optional zero targets. Animator includes candidates. All registration
switch defaults and visibility rules remain unchanged. Staffing statistics still
belong in Zgłoszenia, not this editor.

Required fields have labels/markers, associated help/error text and visible focus.
Client validation mirrors existing request bounds and date rules, without staffing
capacity restrictions. The backend remains authoritative. A reusable
`TurnusFormField` component renders labels/help/errors; styles are scoped to the
editor. Save uses the existing version/POST/PATCH flow, navigation guards and
conflict handling. Submission stays locked until successful creation navigation
finishes. Category failures offer retry without discarding edits.

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
- [ ] Set Summer Animator, Animator wychowawca and Sternik targets; save/reload
  and confirm they remain distinct where agreed. Registration role/certificate
  selections remain unchanged. Verify zero removes a target.
- [ ] Verify invalid input is rejected; stale edits preserve current saved targets.
- [ ] Complete the final form checklist in [Season staff roles](Season-staff-roles.md).

Phase 3 required/accepted/missing statistics and animator/candidate breakdown are
not implemented in this phase.

## 2026-10-13 UI verification

Eight Biuro frontend test files and production build pass. Added validation bounds,
calendar/date-order checks, reactive total checks, complete payload preservation,
creation navigation and duplicate-submit tests (including the navigation interval).
Headless Firefox screenshots were inspected at 1440px desktop, 768px tablet and
390px mobile widths using synthetic data and intercepted API calls. Cards and
bottom actions render without visible overflow or overlap. Screenshots are not
live-backend end-to-end acceptance. No lint/type-check scripts exist in this package.
The temporary preview was removed; no dependency or backend changes were needed.

Unsaved edits now use a styled native dialog for in-app navigation, reload and
logout. Stay cancels the operation; discard permits it without prematurely clearing
form state. Escape stays; the native modal contains focus. Browser refresh/tab close
still uses the browser-managed warning. Nine frontend test files and build pass;
new dialog visual/keyboard review remains pending.

## Summer extension — 2026-10-13

The season loader now requires both staffing catalogues and keeps immutable
snapshots validated against the matching role files. Winter mapping is unchanged.
Summer targets use the existing JSON storage/API/version handling; no migration,
new acceptance restrictions, or statistics calculation is introduced. Statistics
and the Sternik z opiekunem breakdown remain phase 3 work in Zgłoszenia.

Summer extension verification: 233 backend tests executed successfully, ten
optional MariaDB cases skipped; nine Biuro and five Registration frontend test
files and both production builds pass. Configuration tests cover invalid/missing
files and immutable mappings for both seasons. Summer API and persistence tests
cover create/read/edit/clear and rejection of Winter-only categories; editor tests
use the actual Summer resource. Live browser acceptance remains pending.
