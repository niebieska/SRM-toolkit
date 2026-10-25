# Registration status history — required for MVP

Agreed 2026-10-20. Implementation merged in PR #46 to `development`.
Deployed/browser acceptance remains part of the release verification.

## Required outcome

Biuro registration details must show a read-only **Historia statusów** section,
newest first. Each actual status change records previous/new status, server
timestamp, acting account ID and username at the time, and rejection reason
where applicable.

Account audit already exists, but does not record registration decisions.
Current status/update time and notification logs are not persisted status history.

## Implementation scope

- **registration-api:** Flyway migration and history storage in the same database
  as registrations; save the status change and history entry in the same database
  transaction. Include ordered history in the existing registration-detail
  response; do not add a separate history endpoint for this scope. Notification
  failure must not undo a committed decision.
- **biuro-api:** derive operator identity from the authenticated account and pass
  it through the trusted service connection; never trust a browser-supplied actor.
  Return the detail and history through the existing detail route and permissions.
  No additional VIEWER access is granted by this task.
- **biuro-frontend:** render the history already included with registration
  details in a separate **Historia statusów** section at the end of the modal,
  after the registration data sections. Keep Polish labels and handle empty history.
- **Tests:** account attribution, ordering, rejection reasons, atomic rollback
  when history persistence fails, access control and UI states.

Existing registrations collect history from implementation onward. Do not invent
earlier entries or operators. Preserve attribution if an account is renamed or
disabled.

## Boundaries and decisions

- Required for MVP functional acceptance. Implementation is complete; controlled
  synthetic TEST deployment and deployed/browser acceptance remain open.
- No public form changes, registration editing, Person history, notification
  history or resend controls in this task.
- History is audit-only: it records actual changes and does not restrict which
  status transitions operators can make. Preserve the existing status actions
  and permissions. A change back to a previously used status (for example,
  re-accepting a registration) is a real transition and follows the existing
  status-update and notification workflow. A request that leaves the status
  unchanged creates no history entry.
- Future notification attempts/outcomes may link to a history entry. SMTP
  acceptance does not prove inbox delivery.

Initial estimate: 4–7 focused hours including tests and manual verification,
subject to implementation review; not a delivery commitment.
