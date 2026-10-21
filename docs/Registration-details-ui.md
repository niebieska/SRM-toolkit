# Registration details — data variants first

Branch: `feature/registration-details-ui`. The initial layout experiment was
withdrawn. This first step preserves the original modal shell, header and actions;
it changes how saved data is selected and presented, and compacts the registration
summary. No backend, permission,
database or API contract changes. Staff family fields remain on their own branch.

## Presentation rules

- Summary shows the name with type/status badges and the code underneath. Turnus
  and registration date sit alongside when space allows, with a small right inset;
  the rejection reason is full-width below. The modal title/actions remain unchanged.
- Person/contact, address, questionnaire and submitted consents remain visible.
- Guardian section for saved minor registrations; ICE for saved adult registrations.
  Use the response's `minor` classification, never current age or a contradictory
  client payload. Historical responses lacking classification retain available
  sections rather than guessing.
- Staff roles, subroles and qualification fields are shown for STAFF only. Existing
  generic identifier formatting remains; no new seasonal catalogue lookup is added.
- Display each saved health question with its answer and explanation. Preserve
  additional/unfamiliar health fields and staff additional-information content.
- False means Nie; absent values mean Brak danych. Unknown gender values are no
  longer treated as female. Unknown top-level sections/scalars appear under
  Pozostałe informacje. Older payloads do not require newly added fields.
- Health text retains line breaks; values use Vue text interpolation. No HTML
  rendering, data mutation or new data exposure is introduced.

## Verification

Presentation tests cover the four type/minority combinations, stored classification
versus current age, health grouping, unknown-field retention and legacy payloads.
All 19 Biuro frontend test files and production build passed before committing.
Manual acceptance should check adult/minor participant/staff registrations, actual
saved health questions, long values and unchanged accept/waitlist/reject actions.

Further layout, native-dialog/focus, fixed-footer, retry and request-lifecycle
improvements are outside this first step and remain for discussion.
