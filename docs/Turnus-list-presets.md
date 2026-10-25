# Turnus lists — Phase 2: presets and custom columns

Implemented on `feature/turnus-list-presets`, 2026-10-15, with separate feature,
test and documentation commits.
Extends the existing Listy page; no additional page, endpoint, database table or dependency.
Existing protected list responses are extended with the agreed optional fields.

## Views

| View | Participant columns | Default sort |
|---|---|---|
| Ogólny | Imię, nazwisko, PESEL, adres, wiek, telefon, telefon rodzica / ICE | Surname ascending |
| Ubezpieczenie | Imię, nazwisko, PESEL, adres | Surname ascending |
| Rejestracja na miejscu | Imię, nazwisko, PESEL, telefon, telefon rodzica / ICE | Surname ascending |
| Podział na grupy | Imię, nazwisko, data urodzenia, płeć, wiek | Age ascending |
| Własny | Selected available columns | Keeps the sort on entry; adjusts if its column is removed |

Staff tables include Rola / funkcja in addition to the selected columns. Both
groups stay separate and include only accepted registrations from Phase 1.

## Additional custom columns

Własny additionally offers E-mail osoby, Imię i nazwisko rodzica / ICE,
E-mail opiekuna, Relacja kontaktu, Miasto, Uprawnienia kadry and Zgoda na
wykorzystanie wizerunku. They are not selected automatically and do not change
any predefined view's columns. The selection panel groups options as Wspólne and
Kadra. Kadra-only options are hidden when the list is filtered to Uczestnicy.
Options flow down each column before continuing in the next column. The combined
special-needs/organizational-information column appears in Wspólne for both lists.

- Contact name/relation follows saved minor/adult status: guardian for minors,
  ICE for adults. The "other relationship" detail is preserved.
- City is the person's address city. Guardian email is separate from person email.
- Only selected staff qualifications are included, with their entered details.
  The Uprawnienia kadry column is shown only in the staff table, including Własny.
  Biuro resolves labels through the applicable season role/function catalogue;
  unknown historical identifiers remain visible rather than discarded.
- Własny can optionally show chronic-condition details and allergies/dietary
  details for either group, plus one **Szczególne potrzeby / dodatkowe informacje**
  column. It displays participant special-needs details for participants and
  staff organizational information for staff. Health answers (Tak/Nie) are not
  displayed; only saved detail text is shown. Empty values remain unavailable.
- These optional fields are available to every role that can access the assigned
  Turnus list, including VIEWER, and can be included in exports when selected.
- Staff-only options include birth year, mother's first name, father's first name
  and birth surname.
- Image consent displays Tak / Nie only for explicit boolean values. Missing or
  invalid historical values remain unavailable (shown as —), not inferred as refusal.
- Registration API adds typed health/staff detail blocks to its protected DTO; Biuro
  relays them and adds qualificationLabels. Other consents remain excluded.
  Deploy Registration API, Biuro API and frontend together for the new columns.

## Behavior

- Toolbar order: Typ listy, people/role filters, Wyszukaj, Wyczyść filtry.
- Presets choose columns and default sorting. Changing presets preserves search,
  group and staff-role filters and does not make another API request.
- Selecting Własny starts from the currently selected preset's columns and sort.
  Checkboxes allow choosing columns in the existing page; available column order
  is fixed, with no drag-and-drop or saved templates. The picker displays its
  grouped options top-to-bottom in each column, then continues in the next column.
- No columns selected shows an explicit selection prompt for both tables.
- Removing a sorted custom column chooses surname if available, otherwise the
  first remaining column. Staff role sorting remains possible because that column
  is always included when columns are selected.
- Birth dates display as DD.MM.YYYY and sort using their ISO dates. Missing dates
  and ages stay last in both directions. Age still uses Turnus start provisionally,
  pending client confirmation; youngest-first is the initial grouping default.
- Phone selection remains based on saved minor/adult status, independently of age
  at Turnus start. No change to eligibility, recipients or stored registration data.
- Retry keeps filters/view/custom columns. Changing Turnus resets them to Ogólny.
  Settings are not persisted after leaving/reloading the page. Selecting a preset
  then entering Własny again begins from that preset, rather than a saved template.
- No Uwagi, Dopłata, payments or export controls. XLSX/CSV/PDF remain Phase 3.

## Verification

- All 13 Biuro frontend test files and the production build pass.
- Tests cover preset fields, role preservation, custom and empty selections,
  chronological birth-date sorting, missing values, grouping age order, filters,
  no additional requests, retry retention and Turnus-change reset.
- Existing filtering, contact, column visibility and stale-response regressions pass.
- Headless Firefox inspected at 1440px (group preset) and 390px (custom controls)
  using synthetic data and intercepted API requests. Tables remain horizontally
  scrollable within cards. Temporary preview removed.
- No lint/type-check scripts are defined. After adding optional columns, the
  Registration and Biuro backend suites pass; 11 optional MariaDB tests are skipped.
  Coverage includes projection, wire mapping, stored contact selection, legacy
  consent and qualification label behavior.

## Manual acceptance

1. Open a saved Turnus, switch all presets and compare columns with this table.
2. Check accepted-only participant/staff separation and role/function labels.
3. Set search/group/role filters, switch preset, and confirm filters remain applied.
4. Verify grouping ages ascend; test birth-date sorting across different years.
5. Enter Własny from insurance or grouping and adjust columns. Remove the sorted
   column, then clear all columns and reselect some; check both groups.
6. Retry after a connection failure; selection should remain. Change Turnus;
   controls should reset. Review desktop/mobile usability with saved registrations.
7. Select each additional custom column. Compare adult ICE/minor guardian details,
   person/guardian emails, city, staff qualifications/details and image consent
   with saved entries. Select the health-detail columns and confirm they show
   detail text only. Check staff family details and birth year, and verify exports
   include selected values only. Missing consent must not appear as Nie. Verify
   presets remain unchanged after using the extra columns.

Live-backend manual acceptance remains pending.
