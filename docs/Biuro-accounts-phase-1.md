# Biuro accounts — Phase 1 backend checkpoint

Implemented on `feature/biuro-accounts`, 2026-10-16. Full requirement:
[accounts and access plan](Biuro-accounts-and-access-plan.md).

## Delivered

- Biuro Flyway V5 adds account, fixed-role, session and one-time bootstrap tables.
  Existing Turnus migrations/data are unchanged. No registration database changes.
- Database-backed login replaces BIURO_USERNAME/BIURO_PASSWORD. Usernames are
  normalized to lowercase, 3–64 ASCII letters/digits/dot/underscore/hyphen, starting
  with a letter/digit. Display name is independent. Optional email storage is not
  introduced in this phase.
- BCrypt cost 12 hashes. New/temporary passwords require at least 12 Unicode code
  points and at most 72 UTF-8 bytes (BCrypt limit). Passwords are not trimmed.
- A database session ID is signed into each JWT. Tokens have issuer/audience and
  a two-hour default lifetime; account activation, mandatory-password-change state
  and current roles are read from the database on each authenticated request.
  No role authority is trusted from a token/local UI state.
- Temporary-password accounts can only access profile/password/logout APIs.
  Operational APIs require OPERATOR. SYS_ADMIN alone does not grant operational
  access. VIEWER access is intentionally denied until assignment enforcement exists.
- Own-password change revokes all previous sessions and returns a replacement
  token; logout revokes the current session only. Expired session rows are cleaned
  during successful authentication/password-change session creation.
- Missing/invalid/revoked operator authentication returns 401; authenticated
  permission denial returns 403. Internal Turnus Basic authentication stays separate.
- JWT_SECRET is required and must meet the signing library's minimum key strength.
  Old shared-user JWTs are deliberately invalid. No default operator or generated
  Spring Security user is used.

## First administrator setup

Existing database/registration service and Turnus lookup environment variables
are still required. Set a private JWT_SECRET explicitly (at least 32 UTF-8 bytes,
randomly generated and stored securely); there is no application fallback.
Spring/Maven does not automatically export variables from a repository .env file.

For the first start only, supply:

| Variable | Value |
|---|---|
| BIURO_BOOTSTRAP_ENABLED | true |
| BIURO_BOOTSTRAP_USERNAME | desired unique username |
| BIURO_BOOTSTRAP_DISPLAY_NAME | administrator's display name, 1–150 characters |
| BIURO_BOOTSTRAP_PASSWORD | private temporary password satisfying the policy |

Bootstrap creates SYS_ADMIN only, with a required password change. A locked setup
row serializes concurrent starts. Completed bootstrap never overwrites credentials
or recreates a deleted account. Invalid initial configuration rolls back setup and
fails the application runner; correct it and restart. With bootstrap disabled and
an empty account table the application starts without an operator account; it does
not fall back to a default user.

After successful setup, disable BIURO_BOOTSTRAP_ENABLED and remove the temporary
credential variables. Do not commit credentials. Keep the existing Biuro database
and back it up; deleting setup/account tables is not an account recovery procedure.

## API checkpoint

All request/response bodies are JSON. Protected requests use Authorization: Bearer.

| Endpoint | Contract |
|---|---|
| POST /api/auth/login | username/password → token and user profile |
| GET /api/auth/me | current username, displayName, passwordChangeRequired and roles |
| POST /api/auth/password | currentPassword/newPassword → replacement token/profile |
| POST /api/auth/logout | revoke current session, 204; subsequent use returns 401 |

Login retains the token response field and adds user. No password/hash is returned.
Unknown/wrong/disabled-account login uses the same generic 401 message. Password
validation failures are 400. A malformed JSON body is also a safe 400.

## Deliberate phase boundary

This is a backend checkpoint, not a standalone deployment-ready account feature.
The existing frontend has not been migrated to the temporary-password/profile/
server-logout contracts yet. In particular, its old logout only clears local state.
A new SYS_ADMIN can authenticate/change password through the API but cannot assign
OPERATOR to themselves until account administration is implemented. Do not grant
implicit operational access or edit the database as a workaround.

Still required in subsequent phases:

- Account creation/reset/activation/role management APIs and UI; last-admin rule.
- VIEWER Turnus assignment storage/enforcement and permitted navigation.
- Atomic reset/disable session invalidation, including preventing session revival
  after reactivation; current requests already reject inactive accounts.
- Management/authentication audit records and review of login-abuse protection.
- Mandatory-password-change UI, server logout, central session handling and
  preserving unsaved input through reauthentication.
- Full workflow and deployment acceptance. No refresh tokens or public signup.

## Verification

Account tests use Flyway/H2 by default, including real HTTP requests through the
security filters. Existing Turnus tests now create stored operator/session fixtures
instead of signing standalone tokens. For disposable MariaDB verification, set:

- TEST_BIURO_ACCOUNTS_DB_URL=jdbc:mariadb://HOST:PORT/biuro_accounts_test
- TEST_BIURO_ACCOUNTS_DB_USER and TEST_BIURO_ACCOUNTS_DB_PASSWORD

Only the exact disposable schema name is accepted. These tests delete its account,
role and session rows; never point them at a working database. Run from repo root:

```bash
mvn -pl backend/biuro-api -am test
```

For only the MariaDB-capable account cases:

```bash
mvn -pl backend/biuro-api -am test \
  -Dtest=AccountAuthenticationTest,AccountBootstrapTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

Coverage includes hash-based/case-insensitive login, bootstrap validation/restarts/
concurrency, username uniqueness, preserved Turnus seeds, mandatory password change,
old-session invalidation, logout isolation, disabled-account rejection, current-role
checks, missing/malformed/tampered/expired/unstored tokens, JWT secret startup checks
and safe malformed/oversized input errors. Final run results are recorded in WIP.
