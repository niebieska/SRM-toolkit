# Advice for future deployment

## SIT service authentication

`registration-api` protects registration management endpoints with a dedicated
service account. `biuro-api` uses that account for its backend calls. Both services
must receive the same username and password. These credentials are separate from
the Biuro administrator login and JWT secret.

### Configure credentials on the SIT server

Generate a password once:

```bash
openssl rand -hex 32
```

Store the generated value in a server-only environment file, for example
`/opt/srm/sit.env`:

```dotenv
REGISTRATION_SERVICE_USERNAME=biuro-service
REGISTRATION_SERVICE_PASSWORD=<generated-password>
```

Replace the placeholder with the generated value. Include the other SIT
configuration in this file as needed. Keep the file outside the repository and
restrict access to the deployment account:

```bash
chmod 600 /opt/srm/sit.env
```

The Compose configuration injects these variables into both backend containers.
The password has no default; it must be supplied for startup.

### Deploy

After repairing the existing Dockerfiles and Compose build paths, run from the
repository directory using Docker Compose v2:

```bash
docker compose --env-file /opt/srm/sit.env up -d --build
```

Keep the password stable across restarts. Do not generate a new password on every
startup. Frontend containers must never receive service credentials.

### Rotate credentials

Generate a new password and update `REGISTRATION_SERVICE_PASSWORD` in the server
environment file. Recreate both backend containers so they use the updated value:

```bash
docker compose --env-file /opt/srm/sit.env up -d \
  --force-recreate registration-api biuro-api
```

Plan a brief maintenance window: Compose does not replace both services
atomically, so calls may fail while their credentials differ during recreation.

### Environment and network boundaries

- Use separate credentials for SIT and production.
- Do not commit credential files or copy their contents into frontend configuration.
- Use TLS or an isolated trusted container network for backend service traffic;
  HTTP Basic authentication encodes credentials but does not encrypt them.
- Keep registration management routes internal at the reverse proxy as an
  additional boundary, while exposing public registration and turnus routes.
- For local Maven or IDE runs, explicitly supply the same environment variables
  to both services. Spring Boot does not automatically load the Compose `.env` file.
