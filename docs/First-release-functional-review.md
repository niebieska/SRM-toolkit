# First-release functional and configuration review

Inspected: 2026-10-08. Static code review of registration forms/payload mapping,
submission and status services, Biuro views/statistics, season/role options and
email templates. No code changes or browser/SMTP tests were performed for this
review. Findings concern existing behavior, not regressions from counter changes.
This complements the security/deployment plan rather than replacing it.

## Confirmed corrections before real registrations

| Finding | Evidence | Required correction / delivery |
|---|---|---|
| Turnus II ends 22 August in JSON but 21 August in confirmation email mapping and Biuro options; statistics uses 50 against JSON capacity 60. New Turnus codes produce blank email dates. | `backend/registration-api/src/main/resources/data/turnuses.json`; `registration/service/submission/RegistrationNotificationService.java` (`turnusVariables`); `frontend/biuro-frontend/src/views/DashboardView.vue`; `backend/biuro-api/.../RegistrationStatisticsService.java` | Include all consumers in the agreed Turnus migration. Test new Turnus codes, dates and capacity end to end. |
| Accepted-status email contains placeholder bank account and fixed 500 PLN deposit. The same template/ACCEPTED block is used for staff, with no registration-type condition. | `backend/email-service/src/main/resources/templates/status-update.html`; `backend/registration-api/.../management/StatusNotificationService.java` | Agree payment applicability for participants/staff and real instructions. Configure values or omit the payment block when not applicable. This correction is necessary independently of optional payment tracking. |
| Staff role/subrole fields have required markers but `goNext` never validates them. Backend eligibility also has no role/subrole/certificate validation. | `frontend/registration-frontend/src/forms/kadra/steps/Step2Role.vue`; `backend/registration-api/.../submission/RegistrationValidationService.java` | Implement mandatory role/subrole checks and allowed combinations/age rules against season configuration. Clarify which certificates are mandatory versus declarations; do not turn all checkbox options into prerequisites. |
| Certificate lookup ignores composite priest-role mappings: e.g. priest + sternik selects generic `sternik`, while `ksiadz_sternik` is defined separately. | `frontend/registration-frontend/src/config/staffRoles.js`; `forms/kadra/steps/Step2Role.vue` (`certificateSource`) | Define an explicit role/subrole -> certificate mapping in season configuration and test every combination. |
| Adult emergency contact "other relationship" is collected and required, but `buildIceBlock` submits only `iceRelation`, discarding `iceRelationOther`. Both forms share this mapper. | `frontend/registration-frontend/src/api/registrationApi.js`; both `Step1PersonalData.vue` forms | Preserve the detail in payload and display it in Biuro; verify round-trip with an example. |
| Backend required data checks are much weaker than the forms: guardian presence checks only first name; only dataProcessing consent is checked, although forms also require regulations and truth declaration. Names/contact/address/health answers are not comprehensively validated by eligibility. | `backend/registration-api/.../parser/RegistrationParser.java`; `.../submission/RegistrationValidationService.java`; both consent steps | Agree the required payload contract and validate it on the backend. Keep image consent optional. Reject incomplete guardian/email data before saving; test direct API submissions. |
| Submit button's busy state resets immediately after synchronous Vue emit, while parent HTTP submission is still running. | Both `forms/*/steps/Step4Consents.vue` (`goSubmit`) and parent form `handleSubmit` | Parent owns in-flight state and passes it down; prevent repeated submit/back navigation while pending. Database duplicate protection remains the final safeguard. |
| Submission client discards structured API errors and throws only HTTP status. Most friendlyError branches expect domain codes that never arrive. | `frontend/registration-frontend/src/api/registrationApi.js`; both parent form views | Preserve code/message/status from error bodies; display age/closed/invalid-data errors while retaining entered data. |
| Success pages state email was sent, but SMTP sender swallows send failures and the email endpoint can report success. | Both forms' `SuccessPage.vue`; `backend/email-service/.../SmtpEmailSender.java` and `EmailController.java` | Registration success must not claim confirmed delivery. Correct outcome reporting and provide a recoverable/manual resend path; durable retries remain reliability work. |

Paths containing `...` are abbreviated package paths within the indicated module.

## Product decisions to settle during Turnus/season delivery

- **Age reference:** adult/minor routing and staff role availability use today's
  age; minimum participant age is calculated at Turnus start. This is not itself
  contradictory, but a person turning 18 before departure needs an explicit
  guardian/role policy. Stored minor status currently does not change automatically.
- **Capacity policy:** acceptance does not currently enforce capacity or warn
  against over-acceptance. Agree warning versus hard limit, whether staff count,
  and behavior for concurrent acceptance/capacity reductions. A hard limit needs
  an atomic backend check, not only a UI count.
- **Staff email recipients:** registration confirmation goes to minor staff and
  guardian; status updates only to guardian. Participants consistently use guardian
  for minors and self for adults. Confirm the difference is intentional.
- **Staff fees:** decide whether any staff group pays a deposit; do not infer it
  from participant payment instructions.
- **Parent fields:** currently one combined parent-name field for minors, not
  separate mother/father fields. Confirm the information needed for agreed lists;
  do not add mandatory parent details for adults without a product requirement.
- **Status changes:** repeating the same status sends another email, and any allowed
  status can replace another. Agree resend behavior and whether stale simultaneous
  operator changes require conflict handling; audit history remains planned.
- **Season options:** define stable role/subrole/certificate codes and historical
  labels. Confirm whether disabling an option affects only new registrations.
- **Documents/contact details:** privacy/regulations URLs and organizer contacts
  are fixed across forms/templates. Verify the intended content and move the
  appropriate values into season/organizer configuration. Decide whether to store
  accepted document versions; no legal compliance conclusion is made here.
- **Exports:** agree insurance fields and staff inclusion, plus printable on-site
  list columns. Do not assume separate parents, raw PESEL or health information
  belong in every export. Digital arrival tracking remains excluded.

## Suggested verification matrix

Cover adult/minor × participant/staff, birthday before Turnus, every staff
role/subrole, guardian versus self recipient, optional image consent declined,
other emergency relationship, new/closed Turnus, all status notifications,
capacity reached, slow/double submit, API business errors, and SMTP unavailable.
Verify payload persistence and Biuro presentation, not only frontend validation.

## Priority and limits

Include shared dates/capacity and season-role corrections in the already agreed
Turnus work. Fix lossy payload mapping, missing mandatory validation, misleading
payment/email content and submit/error behavior before real public registrations.
Editable email bodies and group announcements stay deferred; this review does
not make their full implementation a release prerequisite. Existing secrets,
service security, Docker/API/CORS/HTTPS and backup tasks remain required separately.
