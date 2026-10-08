Registration integrity tests use a real, disposable MariaDB 11 database. They
are skipped unless TEST_DB_URL is set; ordinary unit tests need no database.
Never use application data: the integrity tests delete fixtures before each test.
The schema must be named integrity_test. Migration tests also create and drop
an isolated integrity_migration_test schema, so the test user needs that permission.

Example from the repository root (choose an unused local port):

```bash
docker run --detach --rm --name srm-registration-integrity-test \
  -e MARIADB_ROOT_PASSWORD=isolated-test-only \
  -e MARIADB_DATABASE=integrity_test \
  -p 127.0.0.1:33079:3306 mariadb:11
```

Wait for MariaDB to be ready, then run:

```bash
TEST_DB_URL=jdbc:mariadb://127.0.0.1:33079/integrity_test \
TEST_DB_USER=root TEST_DB_PASSWORD=isolated-test-only \
bash mvnw -pl backend/registration-api test

docker stop srm-registration-integrity-test
```

Coverage: first-use concurrency, independent group counters, concurrent duplicates
across types, rollback, rejection/deletion without number reuse, database-enforced
uniqueness, old-code migration seeding, migration rerun after reconnect, and
migration refusal when old person/turnus duplicates exist.

Before applying V2 to an existing environment, inspect duplicates:

```sql
SELECT turnus_code, pesel_hash, COUNT(*)
FROM registration
GROUP BY turnus_code, pesel_hash
HAVING COUNT(*) > 1;
```

Resolve duplicates deliberately before migration. V2 neither deletes nor
renumbers registrations. MariaDB DDL is not fully transactional; if a migration
fails, inspect its state and use the established Flyway repair procedure after
resolving the cause. Counter rows must be retained when registrations are deleted.

Legacy database without Flyway history
-------------------------------------

If the original table was created outside Flyway, normal startup refuses the
non-empty schema. Back it up and compare `SHOW CREATE TABLE registration` with
V1__init.sql before adopting version 1. From backend/registration-api, use the
usual service/database environment and run once:

```bash
bash mvnw spring-boot:run -Dspring-boot.run.arguments="--spring.flyway.baseline-on-migrate=true --spring.flyway.baseline-version=1"
```

This acknowledges V1 and runs V2. Return to normal startup afterward; do not
permanently enable automatic baselining. A regression test verifies the original
refusal, explicit adoption, preserved data/counters, and subsequent normal migrate.
Retry unit tests also verify deadlock rollback ordering, retry exhaustion,
non-deadlock errors, and interrupt handling. The HTTP error mapper has an explicit
ALREADY_REGISTERED -> 409 regression check.
