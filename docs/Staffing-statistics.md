# Staffing statistics — 2026-10-13

Implemented on `feature/staffing-statistics`. For a selected Turnus, Zgłoszenia
shows accepted / required staff for categories with a positive required target.
Positive targets with no accepted staff show zero; zero targets are hidden.
Catalogue order and the existing Summer/Winter grouping rules are preserved.

The panel appears for Wszystkie and Kadra, and is hidden for Uczestnicy.
Search and status filters do not affect counts: all ACCEPTED staff for the selected
Turnus are counted. Successful status updates refresh statistics. Older responses
cannot replace the result for a newly selected Turnus.

Subrole breakdowns are calculated by the API but hidden in the UI for now, following
the compact-layout review. Only parent accepted/required counts are displayed.
Role counts appear as a compact second line inside the existing Kadra statistics
card, separated from the overall statistics by a subtle divider.

Animator includes candidates. Summer Animator wychowawca stays
separate. Sternik includes Ksiądz sternik and Sternik z opiekunem, with their counts
available in the API. Winter uses its configured candidate, priest and instructor
mappings. Ksiądz prowadzący stays separate. Each person contributes once.

Targets and staff capacity remain independent; no acceptance restrictions or
missing/excess figures were added. Overall statistics remain unchanged. Unknown
historical roles stay in the overall total and produce a warning in the role panel.

Protected registration summaries now include staff role/function metadata. Biuro
uses its existing list request, without individual detail calls. The statistics
response adds `staffRoles` and `unmappedAcceptedStaff`. Deploy both APIs and Biuro
frontend together. No migration, dependencies or public submission changes.

## Manual verification

- Verify accepted/required and grouped-function counts for both seasons.
- Switch Wszystkie/Kadra/Uczestnicy; only Uczestnicy hides the panel.
- Change search/status filters; counts remain unchanged.
- Accept/reject staff; successful updates refresh counts.
- Switch Turnus quickly and clear selection; stale responses must not reappear.
- Check zero/positive targets, empty accepted lists and unknown historical roles.
- Check desktop/mobile layout using synthetic registrations.

Automated tests cover mappings, targets, accepted-status selection, API metadata,
response validation and dashboard request ordering. Live browser/backend acceptance
was reported as passed by the user after the final compact layout was reviewed.
