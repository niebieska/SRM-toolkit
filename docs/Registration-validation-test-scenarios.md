# Registration validation verification

Verified on 2026-10-09 on `fix/registration-validation`, at commit `a9a245b`.
The working tree also contained uncommitted changes to both frontend dependency lockfiles, so frontend checks used the locally installed dependencies.

## Automated verification completed

- Backend: 90 tests passed in the root Maven suite; nine database tests were initially skipped.
- Database: all nine integrity and migration tests subsequently passed against a disposable MariaDB 11 instance. Together, 99 backend tests executed successfully.
- Frontend: seven registration logic tests and seven Biuro sorting tests passed.
- Both frontend production builds passed.
- `git diff --check` passed.

The initial sandboxed backend run failed because Mockito could not attach its test agent. The rerun outside the sandbox passed. The disposable database was removed afterward.

These checks do not establish browser interaction correctness or complete submission/email delivery. The user reports that most manual cases have been tested and plans to repeat verification in a few days. Specific cases, environments and outcomes were not supplied, so the rows below remain pending recorded results; this does not imply they have never been exercised.

## Manual scenarios before merge

Use synthetic personal data and a test email destination. For successful submissions, use distinct valid test PESEL values to avoid accidental duplicate rejection. Observe requests in the browser Network panel and inspect saved details in Biuro.

| Status | Scenario | Expected result |
| --- | --- | --- |
| Pending | Submit adult participant, minor participant, adult staff and minor staff registrations | Each valid path succeeds, returns the correct code, and displays accurate details in Biuro. |
| Pending | Set an adult emergency-contact relationship to “Inna” and provide its description | The description survives submission and appears in Biuro. |
| Pending | Submit a minor participant with a separate guardian address; repeat with required address fields missing | Complete address is saved; incomplete address is rejected without saving a registration. |
| Pending | Exercise every staff role/subrole, switch selections, and navigate Back/Next | Appropriate certificate options appear; obsolete selections are cleared; retained values remain consistent. |
| Pending | Select the “Inne” certificate without a description, then supply one | Advancing is blocked until the description is supplied. |
| Pending | Throttle the network and rapidly double-click Submit on both forms | Only one submission request is sent; Submit and Back remain disabled until it completes. |
| Pending | Trigger a validation error, duplicate registration, offline connection and a 502 response | A useful error appears, entered data is retained, and retry is possible after resolving the cause. Check JSON and non-JSON error responses. |
| Pending | Decline each required consent; separately decline only image consent | Required consents block submission; declining optional image consent permits an otherwise valid submission. |
| Pending | Check frontend field limits at the exact maximum and one character over for participant/staff personal data, addresses, health details and certificate descriptions | At-limit values pass; longer values show a field error and are preserved rather than silently truncated. Component-level automated coverage exists on `feature/registration-form-limits`; this manual acceptance scenario remains pending. |
| Pending | Vary phone prefixes, including an empty prefix, one beginning with zero and a normal `+48` number | Supported formats are agreed and frontend/backend accept the same intended values. |
| Pending | Submit adult/minor participants and change their status through Biuro | Registration and status emails reach the intended adult/guardian recipient with correct content. Verify actual delivery rather than relying on an API success response. |

## Decisions and coverage gaps

- Frontend usability limits are implemented on `feature/registration-form-limits`
  and shorter than some backend defensive ceilings; see [field capacity review](Form-field-capacity-review.md).
  Automated component-level checks pass on that branch, while the manual
  acceptance scenario above remains pending. Neither layer truncates input.
- Frontend phone validation accepts some formats rejected by the backend. Include an empty prefix, a prefix beginning with zero, and a normal `+48` number in compatibility checks; decide the supported format.
- Frontend tests on the feature branch exercise the affected Vue setup/validation
  logic. They do not verify browser rendering, responsive layout or end-to-end
  form submission.
- Existing service tests use mocks; complete form-to-database-to-email behavior still needs the manual checks above.

Record the execution date, environment, result and any issue reference when changing a scenario from Pending.
