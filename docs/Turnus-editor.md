# Biuro Turnus editor — first UI version

Branch: `feature/turnus-editor`. The existing JWT-protected catalogue API is used;
no backend, database, dependency or capacity-enforcement changes are included.

## Pages

- `/dashboard`: existing registrations screen with shared Zgłoszenia/Turnusy navigation.
- `/turnusy`: full catalogue, including closed and inactive Turnusy; search with clear X (including Polish/ISO dates), availability filter and sortable column headers (default: start date ascending).
- `/turnusy/nowy`: creation page, initially inactive with registration closed.
- `/turnusy/:code`: dedicated definition editor; code is read-only, edit version retained for optimistic locking.

Only the working Definicja section is shown. Participant/staff pages, list exports,
payments and separate capacities/switches remain future features. The layout
provides space for future Turnus sections without placing them in a modal.

The editor sends all existing fields, validates required values/date ordering,
converts empty descriptions to null, blocks double submission and displays API
errors without clearing inputs. An edit conflict offers an explicit reload;
reloading or leaving with changes requires confirmation. Browser refresh/close
uses a native unsaved-change warning. Successful creation redirects to that
Turnus's dedicated URL. All new routes use the existing authentication guard.

Changes to public registration options may take approximately an hour to reach
Registration's cache; during a Biuro outage the delay may be longer. Manual
active/open switches are preserved independently. Capacity is the current shared
catalogue field, not the deferred participant/staff acceptance-limit feature.

The Turnus API adapter supports `VITE_API_BASE_URL`, defaulting to
`http://localhost:8081`. This is scoped to the new adapter; existing auth and
registration API URL externalization remains separate deployment work.

## Verification

Production frontend build and Node test command pass. Existing sorting tests and
five new API scenarios cover JWT headers/full catalogue, encoded codes, POST and
versioned PATCH, conflict status/message preservation, authentication errors,
and network/non-JSON response failures. The list layout has been reviewed iteratively with the user; editor create/edit
and conflict interactions still require browser verification. No live Turnus was created or modified by the implementation.

Manual review:

1. Log in; switch between Zgłoszenia and Turnusy. Existing registration actions and
   logout should remain functional. Try a direct Turnus URL without a session.
2. Verify all three current catalogue entries, search, and open/closed/inactive
   filters. Open the inactive winter Turnus and check its flags/dates/capacity.
3. Create a synthetic TEST-prefixed Turnus. Verify safe default flags, validation,
   success navigation and persistence after reload. Duplicate codes should display
   a conflict and retain the entered form.
4. Edit a definition and save. Verify immutable code, incremented version and
   confirmation. Empty optional descriptions should save successfully.
5. Open one Turnus in two tabs. Save in the first, then save the stale second tab.
   Verify the 409 message, preserved inputs and explicit reload confirmation.
6. Change fields then navigate away, refresh or log out. Verify discard warnings.
   While saving, prevent repeated submits and logout.
7. Check narrow and desktop layouts, keyboard navigation, loading/error states,
   and API outage behavior without lost edits.
8. When convenient, finish the deferred edit → cache refresh → public form check;
   temporarily shorten Registration refresh settings only for the test, then
   restore them. Test real synthetic submissions and email metadata afterwards.

The registration screen's hardcoded Turnus filter options and fixed-capacity
statistics remain existing gaps; this editor does not silently change them.
