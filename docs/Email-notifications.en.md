# Registration email notifications

This document describes the current notification behavior. It distinguishes a
submission confirmation from a later message about the registration status.

## Registration submission confirmation

After a public registration is saved with status `NEW`, the application sends a
confirmation with the registration code and selected Turnus details. This confirms
receipt of the application; it does not mean the application has been accepted.

| Registration type | Recipients | Message content |
|---|---|---|
| Adult participant | Participant | Submission confirmation, registration code and Turnus details. |
| Minor participant | Guardian | Confirmation for the participant, registration code and Turnus details. The participant is not sent a copy. |
| Adult staff member | Staff member | Submission confirmation, registration code, Turnus details, and human-readable role/subrole labels. |
| Minor staff member | Staff member and guardian, as separate messages | The same confirmation, including role/subrole labels. |

Recipient routing uses the registration's saved minor flag. For public form
submissions, that flag is calculated when the registration is submitted.

## Status notification

After a status is set to `ACCEPTED`, `WAITLIST` or `REJECTED`, Registration API
sends a message with the current status. The recipient is selected the same way
for participants and staff:

| Registration | Status-message recipient |
|---|---|
| Adult | The person listed in the registration |
| Minor | The guardian listed in the registration |

For minor staff, the submission confirmation goes to both the staff member and
guardian; a later status message goes to the guardian.

### Content by status

| Status | Message content |
|---|---|
| `NEW` | Initial status. Saving the registration sends the submission confirmation above; it is not a decision message. |
| `ACCEPTED` | Acceptance notice. Participants receive deposit instructions when configured. Staff receive deposit instructions only when the submitted role or subrole has code `kandydat_na_animatora`; other staff receive a thank-you for their willingness to attend. Where applicable, the message includes a participant-card link: for minors, and for adult participants if the adult card is enabled. |
| `WAITLIST` | Notice that the registration was placed on the waiting list and that the organizer will contact the recipient if a place becomes available. |
| `REJECTED` | Rejection notice. The rejection reason is included when provided. |

`NEW` is not an allowed target of a status-update operation. It is listed here
because it is the initial registration status.

## Current limitations

- Email sending is best effort after the registration or status has been saved; an
  email failure does not roll back the saved data.
- The backend attempts to send a status notification after every successful
  status-update request, even if the requested status is already current. Status
  history records only actual changes. Repeating a request may therefore send
  another email, but this is not a controlled or audited resend feature.
- SMTP acceptance confirms that the configured SMTP server accepted the message
  for submission. It does not confirm delivery to the recipient's mailbox.
- Persistent notification-attempt history and operator resend controls are not
  implemented. Their agreed follow-up scope is tracked in
  [Future product backlog](Future-product-backlog.md#communication).

