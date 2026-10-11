# Registration Turnus catalogue cache — phase 2

Biuro is the source of truth. Registration-api reads `GET /internal/turnuses`
with its dedicated Basic lookup account. Its runtime provider no longer loads
`turnuses.json`. `StubTurnusProvider` remains in the repository without a Spring
component annotation, for reference/manual use. It is not an outage fallback.

## Configuration

Set the same `TURNUS_LOOKUP_USERNAME` and `TURNUS_LOOKUP_PASSWORD` in Biuro and
registration-api. Keep these separate from operator and registration-management
credentials. Missing/blank lookup credentials prevent startup.

| Registration-api variable | Default | Purpose |
| --- | --- | --- |
| `BIURO_TURNUS_URL` | `http://localhost:8081` | Biuro base URL |
| `TURNUS_LOOKUP_USERNAME` | `registration-turnus-reader` | Internal reader |
| `TURNUS_LOOKUP_PASSWORD` | Required | Internal reader password |
| `TURNUS_CONNECT_TIMEOUT` | `PT15S` | Connection timeout |
| `TURNUS_READ_TIMEOUT` | `PT30S` | Read timeout |
| `TURNUS_RETRY_INTERVAL` | `PT30S` | Initial retry delay / scheduler polling delay |
| `TURNUS_REFRESH_INTERVAL` | `PT1H` | Minimum delay between attempts once a catalogue exists |

Durations use ISO-8601 notation. Scheduled delays are measured after work completes;
slow requests therefore extend the effective interval. Normal catalogue reads and
submissions never call Biuro or wait for refresh. The scheduler checks whether an
hourly fetch is due; a check alone does not make an HTTP request.

## Startup and outages

Start MariaDB, then Biuro, then registration-api. Wait for Biuro's internal GET to
respond before starting Registration when possible. The first fetch runs on the
scheduler after application initialization; registration-api can start while
Biuro is unavailable.

Before the first successful fetch, Turnus GET endpoints and otherwise parseable
submissions return HTTP 503 with `TURNUS_CATALOGUE_UNAVAILABLE` and a Polish retry
message. Submissions are not persisted and emails are not sent. Retry every 30
seconds after a failed attempt until a valid catalogue arrives.

After success, refresh hourly. On authentication, network, server or malformed
catalogue errors, retain the last successful catalogue without expiry. A restart
loses the in-memory catalogue and repeats the initial-fetch strategy. There is no
JSON fallback or persistent snapshot.

A valid empty `[]` is a successful catalogue, not an outage. It replaces the old
snapshot: forms show no choices and unknown Turnus codes return `TURNUS_NOT_FOUND`.
Validate the whole response before atomic replacement; malformed definitions or
duplicate codes reject the whole refresh. Reads during refresh use the previous
immutable snapshot.

## Visibility and submission rules

Forms see only `active && registrationOpen`. The cache includes closed/inactive
Turnusy so submission checks can still return the existing `REGISTRATION_CLOSED`
or `TURNUS_INACTIVE` errors. Normal age, PESEL, duplicate and payload checks remain.
Valid registrations remain NEW. No global season switch is introduced.

## Manual verification

1. Set lookup credentials in both services and start Biuro, then registration-api.
2. Read `http://localhost:8080/api/turnuses` (or `/api/turnusy`). Check that only
   active/open catalogue entries appear; inactive TESTOWY2027T1 should be absent.
3. Restart Registration with Biuro stopped. Check GET returns 503, then start Biuro
   and verify recovery after the next initial retry.
4. After a successful fetch, stop Biuro. GET should still return the cached list.
5. Close a Turnus in Biuro. After refresh it disappears from the form list and a
   submission using its code is rejected as closed. For a local verification run,
   temporarily shorten `TURNUS_REFRESH_INTERVAL` and `TURNUS_RETRY_INTERVAL`;
   restore defaults afterwards. Changing the setting requires a restart.
6. Reopen the Turnus and verify it reappears after refresh.

Automated tests cover Basic authentication, timeout/error handling, malformed and
empty responses, retry/refresh scheduling, atomic replacement, outage fallback,
503 mapping, and no persistence/email for submissions before catalogue readiness.
They use isolated local HTTP servers/mocks rather than live Biuro data.

## Manual results — 2026-10-11

User verified the filtered public list, cached reads with Biuro stopped,
`TURNUS_CATALOGUE_UNAVAILABLE` after restarting Registration while Biuro was
stopped, and recovery after starting Biuro without restarting Registration.
The HTTP 503 status is covered by automated tests; the shared manual response
confirmed the error code/message. The edit → refresh → form check is deferred
until a proper Turnus editor is available, by agreement. Automated tests already
cover snapshot replacement and closed/inactive filtering.
