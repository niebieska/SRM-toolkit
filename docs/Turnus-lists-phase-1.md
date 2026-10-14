# Turnus lists — Phase 1 implementation

Recorded 2026-10-14. Implemented on `feature/turnus-lists`, with separate feature,
test and documentation commits.

## Delivered

- Saved Turnus details offer Definicja / Listy; creation stays unchanged.
- Separate accepted participant/staff tables, displayed/total counts, name search
  with clear ×, group and staff-role filters, alphabetical default and sorting.
- Compact initial columns: first name, surname, age, gender, person/guardian/ICE
  phones. Staff additionally show role/function. PESEL, structured person address
  and birth date are supplied in the protected API for Phase 2 column selection.
- Age uses Turnus start, provisionally pending client confirmation. Existing
  adult/minor registration and email behavior is unchanged.
- Missing/invalid historical names or PESEL produce an incomplete-data warning;
  birth date/age are not invented. Unknown roles retain their code as a fallback label.
- Read-only lists, loading/error/retry/empty/filter-empty states and stale-response protection.
- No Person register, copied person data in Biuro, migration or new dependency.

## Endpoints

- Registration API: `GET /api/registrations/turnus-list?turnusCode=...`, existing
  management service credentials required. Database query selects Turnus/status
  before mapping JSON; explicit DTO excludes health answers and consents.
- Biuro API: `GET /api/turnuses/{code}/lists`, operator JWT required. Loads the
  authoritative Turnus then makes one registration list request. Returns Turnus
  metadata and entries with person data, age and season role/function labels.
- Unknown Turnus returns 404; upstream failure returns a safe 502, not an empty list.
- Both updated APIs and the Biuro frontend must be deployed together.

## Verification

- Full backend suite: 255 executed tests pass; 11 optional MariaDB tests skipped.
  Includes a new optional real-MariaDB accepted-list query test.
- Biuro: 13 frontend test files and production build pass. No lint/type-check scripts.
- Coverage includes explicit field mapping, malformed historical payloads, age
  boundaries, season labels, protected endpoints, timeout/invalid upstream replies,
  search, type/role filtering, numeric age sorting, retries and stale responses.
- Headless Firefox screenshots inspected at 1440px and 390px with synthetic data
  and intercepted requests; tables scroll inside cards on narrow screens. Temporary
  preview removed. This is not live-backend end-to-end acceptance.

## Manual acceptance still needed

1. Open a saved Turnus and switch Definicja / Listy; confirm accepted people only.
2. Compare names, groups, phone columns and season role labels with saved form data.
3. Verify search, clear ×, group/role filters and age/name sorting.
4. Check empty Turnus, missing historical data and retry after upstream failure.
5. Confirm leaving dirty Definicja still asks before discarding edits.
6. Run optional query/integrity tests only against the disposable test database.

## Following phases

Phase 2 adds presets and custom columns to this same view. Phase 3 adds XLSX, CSV
and PDF. Uwagi source, Dopłata/payment rules and PDF implementation remain open.
No status updates, payments or exports were added in Phase 1.

## Follow-up after the baseline commit

Implemented as separate corrections after the baseline:

- PESEL and formatted person address are visible in Ogólny for both groups.
- Gender is hidden in Ogólny; it remains in the API for the group-management
  preset and later custom columns.
- Person phone is labelled Telefon.
- Telefon rodzica / ICE uses the guardian phone for minors and ICE for adults.
  No fallback to the other contact if the selected phone is missing.
- Contact choice follows the saved registration minor/adult status, independently
  of the displayed age at Turnus start. Existing birthday/guardian policies are
  unchanged and remain a client verification topic.
- Protected person DTO now includes the saved `minor` flag; both APIs/frontend
  must be updated together. No database migration.
