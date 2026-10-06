# SRM-toolkit


## Registration management security

Public requests remain available without credentials:

- `POST /api/registrations/participant`
- `POST /api/registrations/staff`
- `GET /api/turnuses` and `GET /api/turnusy`
- `GET /actuator/health` (status only; no component or health details)

All registration list/detail/status endpoints, including participant/staff lists,
require the dedicated Biuro service account through HTTP Basic authentication.
The Biuro frontend continues to use its existing admin JWT; service credentials
belong only to the two backend processes and must never enter frontend bundles.
Other registration-api routes are denied by default. Only Actuator health is exposed.

Set the same `REGISTRATION_SERVICE_PASSWORD` for registration-api and biuro-api.
There is no default password: missing or blank values prevent startup.
`REGISTRATION_SERVICE_USERNAME` defaults to `biuro-service` and must also match.
Use a randomly generated password (for example, `openssl rand -hex 32`), inject it
through your environment or secret manager, and do not commit it to the repository.
For local development, export these variables before starting both services.
Compose passes them to both backends and requires the password to be set.

Use TLS or an isolated trusted container network for backend service traffic:
HTTP Basic credentials are encoded, not encrypted. Production routing should expose
public registration routes only and keep management routes internal as an additional
boundary. Existing Compose packaging issues are handled separately.

This change does not rotate existing admin/JWT credentials or remove the previously
tracked `.env`; those require the separate secrets-hardening change.
