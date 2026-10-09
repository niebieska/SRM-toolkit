# Registration submission validation — first implementation

2026-10-08; branch fix/registration-validation. Existing JSON shape and historical
records are preserved. Validation runs for new participant/staff submissions after
PESEL/Turnus eligibility and before counter allocation, persistence or notification.

- Both types require person first/last name, email and country-prefixed telephone,
  address (street, house number, Polish postal code and city), and health answers.
- The backend derives minority from PESEL at submission time, as before; submitted
  adult flag and gender must agree. Minimum age still uses Turnus start date.
- Minors require guardian names, relationship, combined parent names and contact.
  Minor participants also need the separate guardian address when sameAddress=false.
- Adults require ICE names, relationship and telephone. The "inna" relationship
  requires relationOther; this is now preserved and labelled in Biuro details.
- dataProcessing, regulations and truthDeclaration must be actual boolean true.
  Image consent remains optional and false is accepted.
- Health answers must be tak/nie; tak requires details. Participants answer q1–q3;
  staff answer q1–q2.
- Staff need a current age-appropriate role and valid subrole where applicable.
  Certificate selections remain declarations, not required qualifications.
  Other-certificate selections require an explanation. Role/subrole options are
  temporarily mirrored in backend code; replace with the agreed season source.
  Certificate allowlists/required qualification policies remain season work.
- No aggregate payload-size limit is introduced. Per-field bounds added in this
  branch need alignment with frontend fields and product review; see
  Form-field-capacity-review.md. They are not database column limits.

Frontend changes retain server code/message/status, keep the submit lock through
HTTP completion, prevent back navigation during submission, and preserve entered
form data when returning from an error. Role/subrole required checks and composite
priest certificate mapping are corrected. No new dependencies are needed.

Verification includes backend payload rules, PESEL checksum/calendar/centuries/age
boundaries, rejection before persistence/notification, and
frontend payload/error mapping. On 2026-10-09, 99 backend tests (including nine
real-MariaDB tests), 14 frontend logic tests and both production builds passed.
The user reports testing most manual cases; individual outcomes are not yet
recorded and repeat verification is planned in a few days. API unit tests do not
prove the submit UI. See [verification scenarios](Registration-validation-test-scenarios.md).
