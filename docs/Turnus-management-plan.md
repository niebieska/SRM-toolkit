# Turnus management — agreed delivery scope

Decision date: 2026-10-08. Status: planning; implementation and UI details remain
open. This document records product decisions from the discussion, rather than
assuming that the experimental prototype defines the requirements.

## Priority

Workflow-first refinement: [Registration-workflow-release-plan.md](Registration-workflow-release-plan.md)
sets the current delivery order. Turnus management supports registration processing
and operational lists; richer administration UI is not a separate release goal.

After registration integrity is merged, deliver core Turnus management before
TEST/SIT deployment, accepting a modest deployment delay. Fix the capacity
mismatch as part of migrating consumers to the shared Turnus source, rather than
as a separate temporary patch. Security, secrets, integrity and deployment
requirements still apply before exposing the application.

## Required delivery

- [ ] Biuro users can create and edit Turnusy: name, dates, participant capacity,
  and whether registration is available. Exact opening/closing scheduling rules
  need agreement before implementation.
- [ ] Use a stable Turnus code that does not change when its name or dates change.
- [ ] biuro-api owns persistent Turnus definitions. registration-api retains
  ownership of registrations and per-Turnus/type counters.
- [ ] Public registration options, registration validation, confirmation/status
  emails and Biuro statistics consume the same Turnus definition. Remove the
  hardcoded statistics capacity and retire turnuses.json after consumers migrate.
- [ ] In Biuro, show accepted participants and accepted staff for each Turnus.
- [ ] Generate an insurance list/export. Agree fields and output format using the
  insurer's requirements before implementation.
- [ ] Generate a printable on-site registration list for staff. Agree columns
  before implementation.

On-site registration means a generated list used by staff at the venue. It does
not mean arrival checkboxes, arrival timestamps, attendance tracking or a digital
check-in workflow. Those prototype controls were a misunderstanding and are
outside this deliverable.

## Season configuration — included in this version

- [ ] Audit hardcoded options in both frontends and email templates before
  defining the configuration fields.
- [ ] Keep version-controlled season files with one active season selected
  through application configuration. Retain previous season definitions.
- [ ] Initially configure staff roles, subroles and certificate requirements.
  Use stable codes separately from editable labels, preserving the meaning of
  existing registrations when options change.
- [ ] The backend loads and validates the configuration, exposes options to the
  frontends, and validates submitted selections against the same source.
  Decide service ownership and internal access during the architecture design;
  avoid independently maintained frontend/backend copies.
- [ ] Associate Turnusy with their season. Turnus-specific dates, capacity and
  availability remain in the Turnus model rather than duplicated season files.
- Changes take effect on restart initially. A season configuration editor,
  runtime switching and Turnus-specific role overrides are outside this first scope.
- Historical season browsing and which seasons appear publicly remain decisions
  to clarify; selecting one active season does not settle archival behavior.

## Optional extension if time permits

- [ ] Manual payment tracking by a Biuro user against a registration: amount,
  payment date, note, and total paid.

Confirm deposit/balance rules and how to correct mistakes before implementing.
No payment-provider integration is planned here. Defer this extension if it would
delay the core delivery. Broader finance workflows remain future scope.

## Decisions needed before coding

- Simplify the experimental UI with the user's feedback; do not copy all controls
  or the card/tab layout into the MVP by default.
- Decide minimum Turnus fields and registration availability rules.
- Define the authenticated internal Turnus lookup used by registration-api,
  avoiding a circular proxy dependency; decide failure handling when unavailable.
- Agree validation for date changes and capacity reductions on existing Turnusy.
  Do not silently reject or delete existing accepted registrations.
- Agree insurance and on-site list columns, inclusion of staff, and formats.
- Confirm migration of existing Turnus codes and data without breaking registrations.

## Delivery sequence

1. Merge registration-code integrity; dataset cleanup remains deferred.
2. Audit hardcoded options; agree season configuration, minimum fields, UI and list layouts.
3. Implement persistent Turnus management in Biuro and its internal lookup.
4. Implement file-based season options, migrate consumers and verify consistent
   dates/capacity and staff-option validation throughout the workflow.
5. Deliver accepted-person views and agreed lists/exports.
6. Add manual payment tracking only if time allows.
7. Complete outstanding hardening/deployment prerequisites, then deploy TEST/SIT.

Suggested branch: feat/turnus-management. No branch creation or merge is implied
by this planning document. The experimental UI stays separate for reference.
