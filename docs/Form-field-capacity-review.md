# Form field capacity review

Updated 2026-11-05. The arbitrary 64 KiB aggregate payload limit was removed at
the user's request. This review distinguishes frontend usability caps, backend
request ceilings and actual storage capacity.

The frontend limits below are implemented on `feature/registration-form-limits`
(commits `76fdeac` and `73adffb`); they have not yet been merged into
`development`. Frontend caps are usability rules, so they may be shorter than the
backend's defensive ceilings. No value is silently truncated.

| Field | Frontend maximum | Backend ceiling | Finding |
|---|---:|---:|---|
| PESEL | 11 digits | 11 digits plus checksum/calendar validation | Aligned. |
| Telephone | Existing segmented control: 4-digit prefix plus 9 local digits | Leading +, nonzero country prefix, 10–15 digits in total | Supported format remains an open compatibility decision. |
| First name | 60 characters | 100 characters | Frontend usability cap; backend remains wider. |
| Last name | 80 characters | 150 characters | Frontend usability cap; backend remains wider. |
| Mother's/father's name | 60 characters each | 100 characters each | Frontend usability caps; backend remains wider. |
| Birth surname | 80 characters | 150 characters | Frontend usability cap; backend remains wider. |
| Email | 254 characters | 254 characters | Length aligned. |
| Combined parents' names | 200 characters | 500 characters | Frontend usability cap; backend remains wider. |
| ICE other relationship | 80 characters | 200 characters | Frontend usability cap; backend remains wider. |
| Street / city / house number | 100 / 80 / 20 characters | 200 / 150 / 30 characters | Frontend usability caps; backend remains wider. |
| Postal code | `XX-XXX`, six characters | `XX-XXX`, six characters | Aligned Polish-address scope. |
| Health answer details | 500 characters | 4,000 characters when answer is `tak` | Frontend cap and visible counter. |
| Staff additional health information | 500 characters | No field-specific cap | Frontend cap and visible counter. |
| Other certificate explanation | 500 characters | 2,000 characters when selected | Frontend cap and visible counter. |
| Rejection reason | No explicit maximum in Biuro; preset and free text combined | DB `VARCHAR(500)`; no server length validation | Separate issue: validate combined length and show a useful error before DB save. |

Names, addresses, contacts and health answers are stored in the registration
`LONGTEXT` JSON payload, not dedicated `VARCHAR` columns. Its capacity does not
determine sensible user-facing field limits. The frontend validates limits before
advancing from a form step and preserves the entered value when it is too long.

The limits implementation has automated frontend tests for field boundaries,
participant and staff personal data, addresses, health details, certificate
details and the conditional ICE relationship field. On 2026-11-05, all nine
frontend test files passed and the production build succeeded on the feature
branch. This is component-logic/build verification, not browser acceptance.

## Open or deferred items

- Phone format compatibility remains open: the segmented UI accepts a 4-digit
  prefix plus 9 local digits, while the backend accepts `+` followed by 10–15
  digits. A 16-character maximum alone does not make those formats equivalent.
- Biuro rejection reason may exceed the database's 500-character column after
  preset and custom text are combined. Add validation and a useful field error.
- Descriptions for certificates may remain in the payload after a certificate is
  unchecked or becomes unavailable after a Turnus/catalogue change. Omit those
  stale descriptions while preserving details for selected, available
  certificates. This is separate deferred UI cleanup.

Preserve multi-part names, diacritics and multiline descriptions. The frontend
limits do not replace backend validation or imply that backend and frontend caps
must match. This review does not change the current country/address scope or claim
manual browser testing.
