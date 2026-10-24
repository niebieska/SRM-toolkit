# Season staff roles — phase 1

Branch: `feature/season-staff-roles`, based on development after PR #33.

## Agreed rules

Summer keeps its existing role codes, age restrictions, mandatory function choices
and certificate lists. No saved registrations or historical payloads are rewritten.

Winter:

| Role | Functions | Eligibility |
|---|---|---|
| Kucharka | None | Adult |
| Kierowca | None | Adult |
| Animator | None, or Kandydat na animatora | Regular animator: adult; candidate: minor or adult |
| Ksiądz | None, or one of Prowadzący / Pomoc duszpasterska / Instruktor narciarstwa / Instruktor snowboardu | Adult |
| Instruktor | Narty or Snowboard; exactly one required | Adult |

Certificates are optional declarations. Selecting an option with `hasDetails=true`
requires a description. Winter reuses the relevant Summer options for Animator,
candidate Animator, Ksiądz/Prowadzący, Kucharka and Kierowca. Ski and snowboard
instructor functions have separate optional declarations: `instruktor_narciarstwa`
and `instruktor_snowboardu`; a priest selecting either instructor function sees
the matching discipline option too. These are declarations only: missing
qualifications do not block submission or acceptance.

For later staffing statistics: Ksiądz + Prowadzący counts separately as Ksiądz
prowadzący. All other priest functions count as Ksiądz, including instructor
functions. One person occupies one staff place and one staffing category.
Required headcounts and those statistics are NOT implemented in phase 1.

## Configuration ownership and update path

Biuro owns the two JSON files in
`backend/biuro-api/src/main/resources/staff-seasons/`: `summer.json` and `winter.json`.
They define stable codes, editable labels, age rules, optional/required functions
and certificate choices. Selected Turnus `seasonType` chooses the configuration;
there is no global active-season switch. These type-level defaults currently apply
across years; per-year configuration overrides remain future work.

Biuro reads and validates both files at startup. Missing/malformed files, wrong
season names, duplicate codes and invalid age rules prevent startup; no silent
fallback is used. Models defensively copy their collections.

Default source is `classpath:staff-seasons/`. Changes to bundled resource files
require rebuilding the application and restarting Biuro.

For direct file edits without rebuilding, copy both files into an external folder
and set Biuro's `STAFF_CATALOGUE_LOCATION=file:/absolute/path/to/staff-seasons/`.
Edit those files and restart Biuro. Keep existing codes stable; removing/changing
codes affects future submissions, not saved data. Role-catalogue editing in the
Biuro UI remains future work.

The existing authenticated `GET /internal/turnuses` adds `staffCatalogue` to each
Turnus response. Registration validates and caches the complete enriched catalogue
in its existing hourly refresh; no additional Biuro requests are needed for role
validation. A malformed entry rejects the complete refresh and retains the last
successful definitions AND staff options. Start/update Biuro before Registration.

Registration exposes public read-only options:

```text
GET /api/turnusy/staff-options?turnusCode=TESTOWY2027T1
GET /api/turnuses/staff-options?turnusCode=TESTOWY2027T1
```

The ordinary public Turnus list omits the nested catalogue. Management endpoints
remain service-authenticated. No service credentials are sent to the browser.
Options for an unknown Turnus return 404; missing/blank codes return 400; a cache
without a usable catalogue returns 503. Public options reads do not grant permission
to submit to a closed or inactive Turnus; submission still checks availability.

Staff submissions validate roles, one selected function, age eligibility and
certificate declarations against the same immutable Turnus object already selected
for that submission. No extra catalogue lookup occurs inside payload validation.
Participant submission rules, registration numbering, capacities and acceptance
behavior remain unchanged.

## Form behavior

Step 2 loads options for the selected Turnus. It does not fall back to hardcoded
Summer options on failure; errors offer retry and Back while Next is disabled.
Changing season/age clears unavailable role/function choices and certificate
selections without discarding personal data. Choices still valid in the new
catalogue are retained; unavailable certificate IDs are removed. Returning through
Back preserves selected certificates and details. Instructor discipline is required;
optional functions show a no-additional-function option. For a minor Winter
candidate, Animator requires selecting Kandydat even though the adult function is
optional.

## Verification and manual scenarios

Automated verification includes the producer configuration loader, external file
reload, immutable data, Summer compatibility, Winter age/function rules, optional
certificates, public/internal security, cache failure retention and HTTP error states.
Node tests load the producer files directly rather than a second frontend role list.
Vue runtime checks cover Back, season changes, preserved personal data, certificate
cleanup and minor-candidate selection. These checks do not replace browser review.

Manual acceptance pending:

1. Start Biuro then Registration with existing credentials. Confirm Summer staff
   options match the current form, including both minor roles and priest certificate
   precedence. Verify the ordinary participant form still works.
2. Activate a synthetic Winter Turnus and manually open staff registration. Wait for
   the hourly cache refresh, restart Registration, or temporarily use
   `TURNUS_REFRESH_INTERVAL=PT10S` for testing and restore PT1H afterward.
3. Adult Winter: test regular Animator, candidate, plain Ksiądz, each priest function,
   instructor Narty/Snowboard, Kucharka and Kierowca. Instructor without a discipline
   must fail; no certificate selection must be accepted.
4. Minor Winter: only Animator → Kandydat is available. Tampering with another role
   or multiple functions must fail before persistence/notification.
5. Select certificates, go Back then return: choices/details remain. Change Turnus
   between Summer/Winter: unavailable choices clear, personal details remain.
6. Select Inne without details: show a useful error. Add details and proceed. Add a
   certificate option to an external file, restart Biuro and refresh Registration;
   verify it appears and submission validates it without Java/Vue changes.
7. Stop Biuro after a successful cache load: options and validation still use the
   last successful snapshot. Initial empty-cache failures show useful errors rather
   than Summer fallback. Check Step 2 retry and Back behavior.
8. Repeat valid adult/minor staff submissions through persistence and confirmation
   email using synthetic data. Existing saved registrations remain readable.

Phase 2: required staff headcounts in Turnus definitions; implementation details
in [Turnus staffing requirements](Turnus-staff-requirements.md). Winter Animator
combines regular animators and candidates; phase 3 shows their breakdown.
Summer grouping is defined in the linked staffing requirements document.
Phase 3: required / accepted / missing staffing categories.

## Final browser verification after all three phases

Run this checklist after the catalogue, Turnus staffing requirements and staffing
statistics phases are delivered. These checks are deferred final acceptance, not
additional prerequisites for committing phase 1.

Current manual evidence: the user confirmed that the form loads certificates as
defined and that a Winter registration was created. Other combinations below are
not yet confirmed by browser testing.

- [ ] Submit a Summer staff registration using existing roles and certificates.
- [ ] Verify a Winter minor can select Animator → Kandydat; adult-only roles are
  unavailable.
- [ ] Verify a Winter instructor cannot advance without selecting Narty or
  Snowboard, and can select only one discipline.
- [ ] Navigate Back and Next: selected certificates and descriptions remain.
- [ ] Switch Summer/Winter Turnus: unavailable choices clear while personal data
  remains.
- [ ] Inspect the new Winter registration in Biuro: status is NEW and role,
  function and certificates display correctly.

The broader manual matrix above remains available for final acceptance too.
