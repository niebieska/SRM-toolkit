# Turnus metadata in registration confirmation emails

Confirmation emails now use the immutable Turnus object already fetched and
validated during participant/staff registration. Notification preparation does
not look up the catalogue again or call Biuro. A cache refresh during registration
therefore does not change the definition used in that registration's email.

The former fixed-code switch is removed. Existing template variables come from:

| Variable | Source |
| --- | --- |
| `turnusCode` | Selected Turnus code |
| `turnusName` | Catalogue name |
| `turnusStartDate` / `turnusEndDate` | Catalogue dates formatted `dd.MM.yyyy` |
| `turnusDescription` | Catalogue description; empty text when null |

For example, catalogue ZAGLE26T2 ends on 22.08.2026; the old email switch used
21.08.2026. New messages use the catalogue value. Previously sent messages are
not changed. New Turnus codes no longer need a Java switch entry.

Existing participant/staff templates, recipients, greetings, registration codes
and NEW status remain unchanged. Adult participant confirmation goes to the
participant; minor participant confirmation goes to the guardian. Staff receives
confirmation, with an additional guardian confirmation for minors. Sending or
preparation failure does not undo the saved registration. Notification retry and
outbox reliability remain separate work.

## Automated checks

- Adult/minor participant recipients, greetings and catalogue metadata.
- Adult/minor staff and guardian recipients, roles and consistent metadata.
- Arbitrary new codes, catalogue overrides of a formerly hardcoded code,
  formatted dates and null description.
- Submission services pass the exact Turnus object selected during validation.
- Participant and staff registration still return the saved code when the email
  client fails; no second provider lookup is made.

Run `mvn test` from the repository root.

## Manual verification — pending

Use synthetic data and MailHog:

1. Register an adult participant for ZAGLE26T2. Verify the catalogue name, dates
   (including 22.08.2026), registration code and participant recipient.
2. Register a minor participant. Verify the guardian recipient/greeting and
   participant name in the message.
3. Register adult/minor staff. Verify the staff role and expected recipients;
   the minor's staff and guardian messages should have identical Turnus metadata.
4. When a new active/open Turnus is available, confirm it receives its catalogue
   name/dates without any Java change. A null description must not break sending.

No migrations or template edits are needed. Capacity/statistics changes are
explicitly excluded from this branch.
