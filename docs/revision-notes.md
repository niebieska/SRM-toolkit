# Revision Notes

This file tracks issues and decisions to revisit after the first test deployment.

## Status Workflow Restrictions

Current state:

- Biuro action buttons are mostly limited only by the current target status, for example an already accepted registration does not show `Zaakceptuj`, but may still show other status-change actions.
- Backend validation checks whether the requested status value is supported, not whether the transition from the current status is allowed.
- `backend/registration-api/src/main/resources/data/turnuses.json` contains turnus metadata such as dates, capacity, age limits, active state, and registration-open state. It does not currently define status workflow rules.

Desired launch workflow to formalize:

```text
NEW
 ├── ACCEPTED
 ├── WAITLIST
 └── REJECTED

WAITLIST
 ├── ACCEPTED
 └── REJECTED
```

Open decisions:

- Should `ACCEPTED` be terminal in Biuro, or should admins have a correction/undo path?
- Should `REJECTED` be terminal, or should admins be able to restore a rejected registration?
- Should transition rules live only in `RegistrationStatusService`, or also be exposed to the frontend so buttons are generated from the same policy?
- Should invalid transitions return a dedicated error code distinct from `INVALID_STATUS`, for example `INVALID_STATUS_TRANSITION`?

Recommended revision:

- Add explicit transition validation in `RegistrationStatusService`.
- Mirror allowed actions in Biuro frontend through one shared local transition map until the backend exposes status-action metadata.
- Add tests for `NEW -> ACCEPTED`, `NEW -> WAITLIST`, `NEW -> REJECTED`, `WAITLIST -> ACCEPTED`, `WAITLIST -> REJECTED`, and rejected invalid transitions from terminal states once terminal behavior is decided.

## Authentication, JWT, And Roles

Current state:

- Biuro uses a simple username/password login configured from environment/application properties.
- A JWT is issued after login and used by the Biuro frontend for authenticated API calls.
- There is no persistent user model.
- There are no real roles or permissions yet.
- All authenticated Biuro users effectively have the same access level.
- JWT secret and default credentials must be treated as deployment secrets, not production-ready defaults.

Minimum test deployment requirement:

- Keep Biuro protected by login.
- Use non-default test credentials and a non-default JWT secret in the test environment.
- Make sure test credentials are not committed.
- Confirm token expiry and logout behavior are acceptable for the test deployment.

Open decisions:

- Which roles are needed for production, for example admin, biuro, finance, staff coordinator, read-only?
- Which actions require elevated permissions, for example delete, edit, payment changes, exports, and user management?
- Should users be stored in `biuro-api` DB or delegated to an external identity provider later?
- Should the current single-user JWT setup remain for alpha, or be replaced before wider customer use?

Recommended revision:

- Add persistent Biuro users before production.
- Add role/permission model before introducing sensitive operations such as delete, edit, finance, exports, or user management.
- Add tests for unauthorized, authenticated, and forbidden role cases once roles exist.
- Keep JWT config environment-driven and document required secret length/rotation.
