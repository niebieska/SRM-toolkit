# Biuro accounts — frontend integration

Implemented on `feature/biuro-accounts` against the existing
[Phase 1 authentication](Biuro-accounts-phase-1.md) and
[Phase 2 administration/access APIs](Biuro-accounts-phase-2.md).
No API, schema, fixed role or session-duration changes in this phase.

## Screens and navigation

- `/konta`: SYS_ADMIN account list/search, account creation, display name,
  activation, fixed roles, Turnus assignments and temporary-password reset.
  Account edits/reset send the displayed version. A 409 keeps edits intact;
  **Wczytaj aktualne dane** explicitly reloads, asking before discarding changes.
- `/haslo`: mandatory first/reset-password change and optional own-password change.
  Confirms the new password and checks the existing 12-character/72-byte UTF-8
  policy. Successful changes use the replacement token returned by the backend.
- `/listy`: minimal assigned-Turnus navigation for VIEWER. The existing shared
  list screen uses VIEWER endpoints for non-operators and retains presets,
  custom columns, filters, sorting and XLSX/CSV/PDF. It hides the definition link.
- SYS_ADMIN alone opens account administration. OPERATOR opens Zgłoszenia and has
  all operational access. VIEWER opens assigned lists. Combined roles are additive;
  OPERATOR ignores assignments. Mandatory-password users only enter password flow.
- Every protected route verifies the server profile; the backend remains the
  authorization boundary. A 403 refreshes profile/navigation without treating an
  ordinary permission denial as logout. Assignment denial retains its safe error.
- Account history displays the latest 100 events with Polish labels, actor/time
  and allowlisted before/after account changes. No raw request/details rendering,
  secrets, password hashes or tokens are displayed.

## Sessions and unsaved input

- One shared HTTP client handles all Biuro APIs, `VITE_API_BASE_URL`, safe backend
  messages, network errors and authenticated 401 responses. It never retries a
  request. Business errors/403/503 do not automatically log out a valid session.
- Startup checks token expiry and `/api/auth/me`. An outage retains the stored
  token but shows a retry screen instead of rendering a protected page.
- Expiry while working opens a blocking native sign-in dialog with Polish text.
  The underlying view is inert and remains mounted, preserving unsaved input in
  memory. Same-user reauthentication with the required permissions resumes it.
  The rejected save is **not** replayed; the user must submit deliberately again.
- Different-user reauthentication remounts the screen and goes to that user's
  permitted home. Lost page permissions or a mandatory password change also
  navigate away; input retention does not override access restrictions.
- The user may explicitly abandon the expired screen. Logout calls the backend
  before clearing local state. Connection/server failures show an error so logout
  can be retried; an already-invalid session can be cleared. Late 401 responses
  for an older token cannot expire a newer session.
- Temporary passwords are cleared after successful creation/reset. Passwords and
  drafts are never stored in browser storage. Token storage remains the existing
  localStorage mechanism; this phase adds no cookie or refresh-token architecture.
- A session version prevents delayed logout responses from clearing a newer login
  or redirecting it to login. Password changes block ordinary navigation during
  the request; completed/failed requests release the guard, and an unmounted
  password screen cannot redirect the user.
- Already downloaded files/data cannot be recalled after assignment removal.
  Pending export completion checks authentication and route before download.

## Automated checks

The existing Node test runner is retained, without new dependencies. Tests cover
profile initialization/outage recovery, expiry and old-response handling, role
combinations/deep links, mandatory password change, server logout, forbidden
responses, no request replay, the blocking dialog, stale edits/reload confirmation,
duplicate creation prevention, temporary-password clearing, reset versions and
Polish audit rendering. Review regressions cover delayed logout responses, pending
password navigation/unmount, new-account discard/reload success and failure, and
compilation of the actual template username pattern with Unicode-set validation.
Existing editor/list runtime regressions also run.

Run:

```bash
npm test --prefix frontend/biuro-frontend
npm run build --prefix frontend/biuro-frontend
```

No lint or separate type-check script exists in this JavaScript frontend.
Automated setup/runtime tests do not replace a real browser acceptance run.
Large lazy PDF/font chunk warnings remain pre-existing.

## Manual acceptance before rollout

Use synthetic accounts and registration data with the matching backend migrations.

1. Bootstrap SYS_ADMIN, sign in with the temporary password, verify no operational
   navigation and that a direct `/dashboard` URL goes to password change. Wrong
   current password/mismatched confirmation must leave the form available.
2. Finish password change. SYS_ADMIN alone sees Konta. Grant OPERATOR to self and
   verify Zgłoszenia/Turnusy become available without a fresh login. Check existing
   registration accept/reject/waitlist, Turnus editing, statistics and list exports.
3. Create a VIEWER assigned one Turnus. Verify temporary-password change, only
   assigned navigation, all presets/Własny/filter/sort and all three exports.
   A direct unassigned-list URL must fail, and the definition/account routes must
   redirect to permitted navigation. Check an unassigned VIEWER's empty state.
4. Verify SYS_ADMIN+VIEWER and OPERATOR+VIEWER role unions. Remove OPERATOR from
   a combined account and verify its next navigation/request is restricted.
   Removing an assignment must deny the next list load; existing downloads remain.
5. Reset an account with two active sessions: both must expire; the temporary
   password must require change. Disable/reactivate an account: its old token
   must remain invalid. Last-active-admin removal/disable must show a conflict.
6. Open an account editor in two browsers. Save in one; the second must retain
   its draft on 409. Confirm/cancel reloading, account switching, navigation and
   logout with unsaved edits. Verify actor/time/changes in history.
7. Let a session expire while editing a Turnus/account or submitting a change.
   The sign-in dialog must block background controls and trap keyboard focus.
   Same-user reauthentication must preserve the draft and issue no automatic
   POST/PATCH. Reauthentication as a different user must discard the previous
   screen. Test explicit abandon, reset-password reauthentication and lost roles.
8. Test expired/revoked startup, wrong login, ordinary 403, API offline/503 and
   recovery. A 503 must not appear as a wrong-password error or clear the token.
   Logout outage must show a retryable error; successful logout revokes the token.
9. At desktop/tablet/mobile widths check account editor/table overflow, navigation,
   password fields, sign-in dialog focus/Escape blocking and discard confirmation.
   Exercise the existing Turnus editor and list/export layout as regressions.

Login rate limiting, audit retention and deployment secrets/configuration remain
separate outstanding release work. This UI does not declare rollout acceptance.
