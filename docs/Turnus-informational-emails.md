# Turnus informational emails — nice to have for MVP

Agreed 2026-10-20. Status: planned, not implemented. Optional for MVP if time
permits; not a release blocker. Required status history and probable payment
scope take priority.

## Intended workflow

- An OPERATOR selects accepted **Uczestnicy** or **Kadra** separately within a
  Turnus: whole group, selected registrations or one person.
- Choose a predefined template, enter custom text in its designated message area
  and preview the completed email before explicitly sending.
- Templates provide the shared layout and footer. Viewing/previewing a template
  does not include editing its structure. Template management remains future work.
- Each recipient receives an individual message; other recipients are not exposed.
- Record sender account, time, message content, selected recipients and individual
  outcomes: accepted by SMTP, failed/not sent or uncertain. SMTP acceptance is not
  confirmed inbox delivery.

## Recipient rules

- Minor participant: guardian is the primary recipient. An optional copy can go
  to the minor's own email if provided. Proposed control:
  **Wyślij również do niepełnoletnich uczestników**.
- Adult participant and staff: use their own email for these informational messages.
- Preview intended recipients, skip absent optional minor addresses, and avoid
  duplicate delivery when guardian and minor have the same address.
- Confirm what to do when the primary address is missing and how to handle
  multiple selected registrations sharing a guardian address; do not silently
  change recipient rules or combine personalized content.
- These rules apply to informational messages only. Existing confirmation and
  status-notification routing remains unchanged.

## Expected work and open decisions

Reuse email-service rendering/delivery. Biuro needs recipient selection and
permission checks, preview, sending coordination and history/outcome UI. Group
sending needs bounded background processing, partial-failure handling and
protection against repeated sends. No automatic retry of uncertain outcomes is
assumed; choose persistence and duplicate-send protection before implementation.

Confirm template catalogue, subject controls, custom-text formatting, treatment of
staff minors, and how personalized previews behave for groups. Proposed location
is a Wiadomości area in Turnus details; final layout remains to be designed.

Attachments, scheduling, editing template structures and confirmed-delivery/read
tracking are excluded from the initial scope.

Provisional estimate: 12–20 focused hours including tests and manual verification.
Possible phases: preview/one-person send (5–8 hours), then group selection,
background sending and outcome history (7–12 hours). These are planning ranges,
not a delivery commitment. Test routing, permissions, HTML escaping, missing/shared
addresses, partial failures, repeated submissions and controlled real SMTP.
