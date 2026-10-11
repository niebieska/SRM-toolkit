# Turnus catalogue — backend foundation

Implemented on 2026-10-10 on `feat/turnus-catalogue`. This is the first stage of
[Turnus management](Turnus-management-plan.md), not the complete consumer migration.

## Current behavior

Biuro owns a persistent catalogue in its own MariaDB schema, using JDBC and Flyway.
Migration V1 creates the table; V2 imports the two definitions from the existing
JSON, preserving their codes, dates, capacity and manual availability switches.
Registration rows and counter tables are untouched.

Authenticated Biuro operators can call:

- `GET /api/turnuses` — all definitions, including unavailable ones.
- `GET /api/turnuses/{code}` — one definition.
- `POST /api/turnuses` — create; returns 201 and the stored definition.
- `PATCH /api/turnuses/{code}` — replace editable fields using the complete request
  and the last-read `version`. The code must match the path and cannot change.

The existing JWT filter protects these endpoints. Missing/invalid tokens currently
return 403 under the existing security configuration. No internal service lookup
or public Biuro route has been added.

Fields: turnusCode, turnusName, turnusDescription, turnusLocation, seasonType
(SUMMER/WINTER), seasonYear, startDate, endDate, minAge, capacity, active,
registrationOpen. Responses include a version incremented on every update.
Concurrent or stale edits return 409 instead of silently overwriting another edit;
missing codes return 404; duplicate create returns 409. Invalid request types,
dates, capacity and required fields return 400.

String bounds correspond to the new catalogue columns (code 50, name 200, location
500; description application bound 4000). These are catalogue rules, not new
registration form-field decisions. Codes use uppercase letters, digits, underscores
and hyphens. Dates may describe historical Turnusy; end cannot precede start.

## Running Biuro

Provision an **empty, separate `biuro_db` schema** and a user permitted to migrate
and access it. Do not point Biuro Flyway at registration_db: both services own
independent migration histories. No database is created or reset by this change.

Set in the environment before starting Biuro:

```bash
export BIURO_DB_URL=jdbc:mariadb://localhost:3306/biuro_db
export BIURO_DB_USER=biuro_user
# Inject BIURO_DB_PASSWORD securely; no default or committed credential.
```

Keep existing REGISTRATION_SERVICE_PASSWORD and operator/JWT configuration.
Flyway runs on startup. Biuro now requires the database for startup, including
its existing registration proxy routes. Compose/database provisioning remains an
explicit deployment task; the old local startup script inherits exported variables.

## Verification

On 2026-10-10 the full backend suite passed (98 executed tests; nine existing
registration database tests skipped). All eight new catalogue tests passed on H2
and were rerun successfully against real MariaDB 11. No frontend or consumer
migration is implemented in this stage.

Ordinary Biuro tests use H2 in MariaDB compatibility mode. The catalogue tests cover
seed import, persisted create/edit, immutable codes, duplicate/missing codes,
optimistic edit conflicts, concurrent updates, invalid payloads and protected routes.
H2 alone does not prove MariaDB compatibility. Reuse the catalogue tests against a
disposable MariaDB 11 schema named exactly `biuro_turnus_test`:

```bash
TEST_BIURO_DB_URL=jdbc:mariadb://127.0.0.1:33080/biuro_turnus_test \
TEST_BIURO_DB_USER=root TEST_BIURO_DB_PASSWORD=your-isolated-test-password \
mvn -pl backend/biuro-api -Dtest=TurnusCatalogueTest test
```

The tests delete TEST-prefixed fixtures and must never run on application data.

## Next stages and decisions

- Registration-api now reads Biuro through an authenticated hourly cache; see
  [Catalogue cache configuration and verification](Turnus-catalogue-cache.md).
  Public options and submission eligibility use that snapshot. Statistics and
  email metadata migration remain a separate phase.
- Add the simple editor and file-based season roles/subroles/certificate source.
  The existing seasonType/year fields preserve current season identity; a shared
  season-file identifier/contract remains future scope; a global season switch is deferred.
- Confirm manual availability versus scheduled dates and warning versus blocking
  acceptance at capacity. No new capacity enforcement or status restrictions added.
- Date/capacity changes reach the registration cache after refresh. They do not rewrite,
  reject or delete existing registrations. Revalidation of existing registrations
  and capacity reduction behavior must be agreed before integration.
- Accepted-person views, insurance export and printable on-site lists remain later
  stages. No digital arrival tracking, deletion endpoint or finance work added.
