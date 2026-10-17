# Biuro accounts — Phase 2 backend

Implemented 2026-10-17 on `feature/biuro-accounts`. Extends the
[Phase 1 backend](Biuro-accounts-phase-1.md) under the agreed
[account/access plan](Biuro-accounts-and-access-plan.md). [Frontend integration](Biuro-accounts-frontend.md) now consumes these APIs.

## Accounts and administrative permissions

Only SYS_ADMIN can create accounts, edit display names/active state/roles/Turnus
assignments, reset passwords or read account audit history. SYS_ADMIN alone still
has no operational registration/Turnus access. Self-assignment of OPERATOR or
VIEWER is allowed and audited.

- Username remains immutable after creation and is normalized by the existing policy.
- Every account has at least one fixed role. VIEWER may have no assignments; it
  then sees an empty Turnus index. Accounts can have multiple roles.
- OPERATOR retains full access to all Turnusy and registration processing; assignments
  restrict VIEWER access only. Stored assignments are inert without VIEWER and do
  not restrict OPERATOR. No new configurable permission catalogue is introduced.
- Account edits and password resets require the current version. A stale version
  returns 409. Own-password changes increment the version too, so an old admin
  form cannot silently overwrite intervening password state.
- Reset produces a required temporary-password change and revokes all sessions.
  Disable also revokes all sessions; reactivation never revives old tokens.
- Role/assignment changes apply on the next request through current database state;
  no new login or token replacement is required for those changes.
- Never disable/remove SYS_ADMIN from the last active administrator. A dedicated
  singleton database lock serializes administrative mutations; target account
  locking/version checks also protect against own-password changes. Authorization
  is checked again inside the mutation transaction after acquiring the admin lock.
- BCrypt hashing for account creation/reset happens before taking the admin lock.
  Account/role/assignment/session changes and their audit event commit atomically.

## Account APIs

JWT required; all endpoints below require SYS_ADMIN and a completed password-change
flow. Responses never contain password hashes or the supplied temporary password.

| Endpoint | Purpose |
|---|---|
| GET /api/admin/accounts | List account profiles, roles, assignments and versions |
| GET /api/admin/accounts/turnus-options | Minimal complete Turnus catalogue for assignment selection |
| POST /api/admin/accounts | Create an active account with a temporary password |
| PATCH /api/admin/accounts/{id} | Replace editable account properties using a version |
| POST /api/admin/accounts/{id}/password | Set a temporary password and revoke sessions |
| GET /api/admin/accounts/{id}/audit?limit=100 | Latest account events; limit 1–200 |

Creation fields: username, displayName, password, roles, turnusCodes.
Update fields: version, displayName, active, roles, turnusCodes. This is a complete
replacement of editable properties, not a JSON Patch/partial-field operation.
Password reset fields: version, password. An empty turnusCodes array is valid.
Use SYS_ADMIN / OPERATOR / VIEWER as the role identifiers. Assignment codes must
exist in the Biuro catalogue, including archived Turnusy where appropriate.

Creation returns 201; update/reset return the sanitized account profile. Invalid
fields/roles/assignments return 400, duplicate usernames/stale versions/last-admin
violations 409, and unknown account IDs 404. Missing authentication is 401;
insufficient permission is 403. Account database/transaction failure returns a safe
503. Account deletion and username changes are not provided.

A new first administrator can now change their password through the Phase 1 API,
read their account ID/version here and assign themselves OPERATOR without direct
SQL. Self-password reset invalidates the current session as well; log in using the
new temporary password and complete its change before resuming administration.

## VIEWER lists

| Endpoint | Allowed roles / data |
|---|---|
| GET /api/viewer/turnuses | VIEWER: assigned summaries; OPERATOR: all summaries |
| GET /api/viewer/turnuses/{code}/lists | VIEWER: assigned code only; OPERATOR: any existing code |

Summaries contain code, name, location and start/end dates only. These routes never
expose the Turnus editor definition. An unassigned or unknown code requested by a
VIEWER returns 403 before calling Registration API. VIEWER cannot use operator
registration/Turnus endpoints or write through the viewer routes.

List entries reuse the accepted-only source and the same agreed participant/staff
fields, role labels and optional columns as existing operator lists. There are no
preset/column restrictions for VIEWER. The frontend will use these data to provide
presets, Własny, filters/sorting and XLSX/CSV/PDF export in its integration phase.
The existing operator endpoints/contracts remain unchanged.

Export is local to the browser. Revocation prevents subsequent API reads; it cannot
recall files or personal data already legitimately loaded/downloaded.

## Audit

V6 adds append-only application audit records: time, actor ID/username, target ID,
event type and allowlisted non-secret details. Account changes retain before/after
sanitized profiles, so self-role grants and assignment changes are attributable.
Password reset details contain versions only. Authentication success/failure,
password change, logout and initial bootstrap are recorded too. Failed-login
records use a known target ID where available; unknown supplied usernames,
passwords, hashes, tokens and session IDs are not stored in event details.

Successful mutations and their audit event use the same transaction. Audit failure
rolls back account/session changes. Audit logging begins with V6; historical events
are not invented/backfilled. Records have no account FK so later account removal
would not erase history. There is no audit-edit/delete endpoint or audit UI in this
phase. Failed attempts for unknown accounts have no target and are available in the
audit table, not the per-account history endpoint. Retention and login-abuse
protection remain follow-up operational/security work.

## Migration and verification

Flyway V6 adds the account version, assignments, admin lock and audit table. No
Registration database or frontend changes. Do not edit V5 or recreate a working
Biuro database. Existing accounts, hashes, roles, sessions and completed bootstrap
state are preserved; existing account versions begin at zero.

Run from the repository root:

```bash
mvn -pl backend/biuro-api -am test
```

Optional MariaDB checks use the Phase 1 disposable account schema/credentials:
TEST_BIURO_ACCOUNTS_DB_URL, TEST_BIURO_ACCOUNTS_DB_USER, TEST_BIURO_ACCOUNTS_DB_PASSWORD.
The independent upgrade test also accepts TEST_BIURO_ACCOUNTS_MIGRATION_DB_URL,
restricted to a fresh `biuro_accounts_migration_test` schema. It migrates V1–V5,
seeds an existing account/session, then applies V6; recreate only this disposable
schema between repeated upgrade-test runs. Account tests delete synthetic account
rows and must never target a working environment.

Coverage includes direct unauthorized API calls, self-role grants, temporary
password restrictions, duplicate usernames, password reset/disable/reactivation,
next-request roles/assignments, minimal VIEWER data, stale edits, last-admin races,
audit rollback/redaction and existing-data migration. Results are recorded in WIP.

## Remaining phases

- Account/password UI, VIEWER navigation and centralized session handling are
  implemented in the linked frontend phase; real browser acceptance remains.
- Deployment configuration/secrets/login-abuse protection and full operator E2E.

The backend can be checked through authenticated API requests. The integrated
browser flow is available for manual acceptance; deployment approval is still pending.
