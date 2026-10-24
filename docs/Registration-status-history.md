# Registration status history — required for MVP

Agreed 2026-10-20. Status: planned, not implemented.

## Required outcome

Biuro registration details must show a read-only **Historia statusów** section,
newest first. Each actual status change records previous/new status, server
timestamp, acting account ID and username at the time, and rejection reason
where applicable.

Account audit already exists, but does not record registration decisions.
Current status/update time and notification logs are not persisted status history.

## Implementation scope

- **registration-api:** Flyway migration and history storage; save the status
  change and history entry in the same database transaction. Expose a protected
  service-authenticated history endpoint. Notification failure must not undo a
  committed decision.
- **biuro-api:** derive operator identity from the authenticated account and pass
  it through the trusted service connection; never trust a browser-supplied actor.
  Proxy history reads with existing registration-detail permissions. No additional
  VIEWER access is granted by this task.
- **biuro-frontend:** add a separate **Historia statusów** section at the end of
  the existing registration details modal, after the registration data sections.
  Include Polish labels and loading, empty and error states.
- **Tests:** account attribution, ordering, rejection reasons, atomic rollback
  when history persistence fails, access control and UI states.

Existing registrations collect history from implementation onward. Do not invent
earlier entries or operators. Preserve attribution if an account is renamed or
disabled.

## Boundaries and decisions

- Required for MVP functional acceptance; controlled synthetic TEST deployment
  may precede it. Implementation and deployed acceptance remain open.
- No public form changes, registration editing, Person history, notification
  history or resend controls in this task.
- Settle unchanged-status requests before coding: they must not invent a status
  transition. Notification resend behavior is a separate explicit decision; no
  silent transition restrictions are introduced.
- Future notification attempts/outcomes may link to a history entry. SMTP
  acceptance does not prove inbox delivery.

Initial estimate: 4–7 focused hours including tests and manual verification,
subject to implementation review; not a delivery commitment.
