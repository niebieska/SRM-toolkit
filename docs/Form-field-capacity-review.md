# Form field capacity review

2026-11-02. The arbitrary 64 KiB aggregate payload limit was removed at the user's
request. This review distinguishes current UI limits, backend restrictions, and
actual storage capacity. The frontend caps below are usability limits; the
backend retains wider defensive ceilings.

| Field | Frontend maximum | Backend ceiling | Finding |
|---|---:|---:|---|
| PESEL | 11 digits | 11 digits plus checksum/calendar validation | Aligned. |
| Telephone | Existing segmented control: 4-digit prefix plus 9 local digits | Leading +, nonzero country prefix, 10–15 digits in total | Still needs a separate supported-format decision; unchanged here. |
| First name | 60 characters | 100 characters | Frontend usability limit; backend remains wider. |
| Last name | 80 characters | 150 characters | Frontend usability limit; backend remains wider. |
| Mother's/father's name | 60 characters each | 100 characters each | Frontend usability limit; backend remains wider. |
| Birth surname | 80 characters | 150 characters | Frontend usability limit; backend remains wider. |
| Email | 254 characters | 254 characters | Aligned. |
| Combined parents' names | 200 characters | 500 characters | Frontend usability limit; backend remains wider. |
| ICE other relationship | 80 characters | 200 characters | Frontend usability limit; backend remains wider. |
| Street / city / house number | 100 / 80 / 20 characters | 200 / 150 / 30 characters | Frontend usability limits; backend remains wider. |
| Postal code | XX-XXX, six characters | XX-XXX, six characters | Aligned Polish-address scope. |
| Health answer details | 500 characters | 4,000 characters when answer is tak | Frontend usability limit; visible counter. |
| Staff additional health information | 500 characters | No field-specific cap | Frontend limit; backend does not enforce a field-specific cap. |
| Other certificate explanation | 500 characters | 2,000 characters when selected | Frontend usability limit; backend remains wider. |
| Rejection reason | No explicit maximum in Biuro; presets and free text combined | DB VARCHAR(500); no server length validation | Separate issue: validate combined length and show a useful error before DB save. |

## Storage and decisions

Names, addresses, contacts and health answers are stored in the registration
LONGTEXT JSON payload, not dedicated VARCHAR columns. The suggested field sizes
above are application rules introduced in this branch, not existing storage limits.
The capacity of LONGTEXT does not determine sensible user-facing limits.

The frontend validates the usability limits before advancing from a form step;
long free-text fields also show character counts. These limits do not replace
the wider backend checks. Preserve multi-part names, diacritics and multiline
descriptions; do not truncate values in application code.

The fixed PESEL/postal-code formats and rejection-reason database limit have clear
existing foundations. Frontend free-text limits are implemented as usability
caps. International phone-number compatibility remains open. This review does
not change the current country/address scope or claim browser testing.

## Remaining UI cleanup — 2026-11-02

- Certificate descriptions now use the agreed 500-character frontend usability
  cap and a visible counter; the backend keeps its wider 2,000-character ceiling.
- Omit descriptions for unselected or unavailable certificates from the submitted
  payload. Retaining a description after unchecking its certificate already existed
  in the previous form. The new season-catalogue normalization also removes
  unavailable selections without pruning their descriptions, extending that
  existing cleanup gap to catalogue/Turnus changes. Test both cases and preserve
  descriptions for certificates that remain selected and available.

These are deferred UI cleanup tasks, not blockers for the season-role feature.
No UI behavior is changed by this note.
