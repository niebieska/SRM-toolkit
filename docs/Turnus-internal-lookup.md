# Internal Turnus lookup — phase 1

Biuro owns the Turnus catalogue. This phase exposes a read-only service endpoint;
registration-api still uses its existing JSON provider. Hourly cache refresh and
the registration-api HTTP client belong to the next phase.

## Startup configuration

Biuro now requires `TURNUS_LOOKUP_PASSWORD`. A missing or blank value prevents
startup. `TURNUS_LOOKUP_USERNAME` defaults to `registration-turnus-reader`; an
explicitly blank username also prevents startup. Generate a separate password
and supply it through the process environment or deployment secret mechanism.
For a local terminal, enter the password without putting it in shell history:

```bash
read -rsp 'Turnus lookup password: ' TURNUS_LOOKUP_PASSWORD
export TURNUS_LOOKUP_PASSWORD
```

Keep the existing database, JWT/operator, and `REGISTRATION_SERVICE_PASSWORD`
configuration. The new password is independent of those credentials. A root
`.env` is not automatically loaded by `mvn spring-boot:run`.

Compose passes these new variables to Biuro, but its existing packaging/database
configuration still needs the separately planned deployment fixes.

## Contract and security boundary

`GET /internal/turnuses` on biuro-api (default port 8081) requires HTTP Basic
authentication with the lookup username/password. It returns the complete
`TurnusDefinition[]`, including closed and inactive entries, eligibility fields,
dates, capacity, manual flags, and edit version. An empty catalogue returns `[]`;
database failures are errors, not a successful empty catalogue.

Only this GET route is authorized. Other internal paths and write methods are
denied. The lookup account cannot access JWT-protected operator routes. Operator
JWTs cannot access this internal endpoint. The existing `/api/turnuses` operator
CRUD routes continue to use JWT authentication.

Use the credentials only server-side. Do not include them in Vue configuration,
browser requests, or committed files. Basic authentication requires HTTPS when
crossing an untrusted network; deployment must restrict internal route access to
the trusted service network.

## Verification

```bash
curl --user registration-turnus-reader http://localhost:8081/internal/turnuses
```

Curl prompts for the password. Expect HTTP 200 and all catalogue entries.
Without credentials, with a wrong password, malformed Basic authentication, or
an operator JWT, expect 401. With correct lookup credentials, POST/PATCH/DELETE
and operator routes are denied with 403. Verify normal operator login and
JWT-authenticated catalogue CRUD still work.

Automated verification: `mvn -pl backend/biuro-api test`. Tests exercise a real
embedded HTTP server, the credential boundaries, complete catalogue flags, and
startup with missing, blank, and configured lookup credentials. Existing tests
continue to verify operator CRUD and browser CORS/preflight behavior.

Additional HTTP regressions verify an empty catalogue returns `200` with `[]`
and a real catalogue SQL failure returns `500` with a generic JSON error, without
SQL details. The failure test restores the table and confirms GET recovers to
`200`. Both scenarios use only the isolated in-memory H2 test database.
