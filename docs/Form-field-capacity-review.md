# Form field capacity review

2026-10-08. The arbitrary 64 KiB aggregate payload limit was removed at the user's
request. This review distinguishes current UI limits, new backend restrictions,
and actual storage capacity. No new frontend caps are introduced by this review.

| Field | Current frontend maximum | Backend in validation branch | Finding |
|---|---|---|---|
| PESEL | 11 digits | 11 digits plus checksum/calendar validation | Aligned. |
| Telephone | Prefix input 4 digits plus 9 local digits | Leading +, nonzero country prefix, 10–15 digits in total | Lengths compatible for emitted values, but UI prefix/validator permits values the backend rejects. Confirm international-number scope. |
| First name | None | 100 characters | New backend choice, not inherited from UI or DB. |
| Last name | None | 150 characters | Same mismatch. |
| Email | None | 254 characters | UI has no explicit maximum; format validation also differs. |
| Combined parents' names | None | 500 characters | New backend choice. |
| ICE other relationship | None | 200 characters | New backend choice. |
| Street / city / house number | None | 200 / 150 / 30 characters | New backend choices. |
| Postal code | Formats to five digits with hyphen | XX-XXX, six characters | Aligned Polish-address scope. |
| Health answer details | None in shared HealthQuestion | 4,000 characters when answer is tak | Frontend can submit values rejected by backend. |
| Staff additional health information | 1,000 characters | No field-specific cap | Opposite mismatch; backend does not enforce UI cap. |
| Other certificate explanation | None | 2,000 characters when selected | New backend choice. |
| Rejection reason | No explicit maximum in Biuro; presets and free text combined | DB VARCHAR(500); no server length validation | Genuine storage limit: validate combined length and show a useful error before DB save. |

## Storage and decisions

Names, addresses, contacts and health answers are stored in the registration
LONGTEXT JSON payload, not dedicated VARCHAR columns. The suggested field sizes
above are application rules introduced in this branch, not existing storage limits.
The capacity of LONGTEXT does not determine sensible user-facing limits.

Agree field sizes before treating the new bounds as the final contract. Then align
frontend maxlength/validation and backend checks, with exact-boundary tests and
counters for long descriptions. Do not silently truncate imported or submitted
values. Preserve multi-part names, diacritics and multiline descriptions.

The fixed PESEL/postal-code formats and rejection-reason database limit have clear
existing foundations. Free-text limits and international phone/address support
need explicit agreement. This review does not change the current country/address
scope or claim browser testing.
