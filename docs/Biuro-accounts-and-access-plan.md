# Biuro accounts, permissions and sessions

Agreed 2026-10-16. Extends the release-plan JWT/session task into individual
accounts and access management. The [Phase 1 backend](Biuro-accounts-phase-1.md)
and [Phase 2 management/access backend](Biuro-accounts-phase-2.md) are implemented
on `feature/biuro-accounts`. UI/session integration and full acceptance remain
pending. This document describes the complete target scope.

## Accounts and login

- Biuro owns accounts in its database. Login uses a unique, case-insensitive
  username and password; display name is separate. Email is optional contact data.
- Store passwords only as secure hashes. SYS_ADMIN creates accounts and sets
  temporary passwords; require a password change at first login/after reset.
- Users can change their own password. Accounts can be activated/deactivated.
- Bootstrap the first SYS_ADMIN through a documented one-time setup using
  deployment-provided credentials, with no built-in default password.
- No public self-registration, email password recovery or MFA in this scope.
- Operator credentials, JWT signing secrets and service credentials stay separate.

## Roles and assignments

Roles are fixed definitions in code, and an account can hold multiple roles.
SYS_ADMIN assigns roles; this does not introduce a configurable permission builder.

| Role | Allowed access |
|---|---|
| SYS_ADMIN | Manage accounts, password resets, roles and VIEWER Turnus assignments. Does not itself grant operational access. |
| OPERATOR | Full access to all Turnusy, their creation/editing, registration processing, statistics, lists and exports. Account management requires SYS_ADMIN as well. |
| VIEWER | Read lists, use every preset and Własny, search/filter/sort and export for assigned Turnusy only. No Turnus or registration/status edits. |

- SYS_ADMIN can assign OPERATOR or VIEWER to themselves without separate approval.
- Turnus assignments restrict VIEWER access only. OPERATOR access covers all
  Turnusy regardless of assignments. Combined roles grant their combined access.
- VIEWER sees a minimal assigned-Turnus index (name, dates, location) for navigation,
  without opening the Turnus editor.
- VIEWER may use all currently available list columns, including their personal
  data. Własny remains column selection; saved templates are not added.
- Enforce authorization in backend endpoints, not only navigation/buttons. Cover
  direct list requests and access to unassigned Turnusy. Exports currently happen
  in the browser from loaded data: protect the source API; there is no separate
  server export endpoint to authorize. Revocation cannot recall downloaded files.
- Internal catalogue/service authentication must continue working independently
  of operator roles and VIEWER assignments.

## Sessions and account safety

- Session duration is two hours, then login is required again. No refresh-token
  workflow is included in this phase.
- Logout invalidates the current session immediately. Password reset or account
  disabling invalidates existing sessions immediately. Permission/assignment
  changes apply on the next request, including when a JWT was already issued.
- The implementation must check revocation/current permissions server-side;
  token expiry or stale JWT role claims alone cannot meet these requirements.
- Prevent disabling the last active SYS_ADMIN or removing their SYS_ADMIN role,
  including through self-management. Enforce the rule safely for concurrent edits.
- Detect expired/invalid sessions on app startup and during requests. Return to
  login with a clear Polish explanation; distinguish 401 authentication failures
  from genuine 403 permission denials and upstream service-auth failures.
- Preserve unsaved form input through reauthentication without automatically
  replaying POST/PATCH operations. Avoid persisting sensitive drafts unnecessarily.

## Administration and audit

- Provide a SYS_ADMIN-only account page for creation, display-name changes,
  roles/assignments, activation/deactivation and temporary-password resets.
- Provide own-password change, including the mandatory temporary-password flow.
- Record actor, target and time for account creation, password resets, activation,
  role/assignment changes (including self-assignment), and authentication events.
  Never log passwords, password hashes or tokens.
- Use individual identity as the foundation for later operational audit. Full
  registration-edit/status history UI is not silently added to this feature.

## Delivery phases and verification

1. Accounts/migrations, first-admin bootstrap and database-backed login.
2. Backend role/Turnus enforcement, session revocation and last-admin protection.
3. Account administration, own-password flow and permission-aware navigation.
4. Central session handling, input preservation and complete acceptance tests.

Each phase must have a documented migration/startup path. Phase 1 uses stored
session IDs in signed JWTs and checks current account state/roles per request.
Management and VIEWER assignment contracts are documented in Phase 2. Frontend
integration and complete deployment acceptance remain pending.

Test username uniqueness/case handling, password hashing/reset, temporary-password
restrictions, role combinations/self-assignment, assigned/unassigned Turnusy,
direct API bypass attempts, list/export availability, two-hour expiry, logout,
disabled accounts, next-request permission changes, last-admin protection,
reauthentication/input preservation and existing workflow/service-auth regressions.

This feature is separate from deferred registration editing, Person/history,
payments, arbitrary permission configuration and broader identity integrations.
