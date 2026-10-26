# GCP TEST: temporary HTTP access

This Compose override publishes the existing frontend containers for a
synthetic-data TEST environment. It does not deploy anything or change GCP
firewall rules.

## Addresses

- Registration: `http://<VM-public-IP>/`
- Biuro: `http://<VM-public-IP>:8080/`

The frontends proxy their browser API requests to the matching backend over the
private Docker network. The APIs, MariaDB and MailHog remain without public port
mappings. The override replaces the local loopback frontend mappings; it does
not expose ports 5173, 5174, 8081, 8082, 8080 (Registration API), 3306 or 8025.

## Validate configuration

From the repository root, using the private deployment environment file:

```bash
docker compose --env-file /etc/srm-toolkit/compose.env \
  -p srm-test \
  -f docker-compose.yml \
  -f docker-compose.gcp-test-http.yml \
  config --quiet
```

Start the deployment only as a separately approved operational step. If
external SMTP is deliberately required, also include
`docker-compose.external-smtp.yml` and keep recipient addresses controlled;
otherwise the base configuration sends test messages to MailHog.

## Security limitation

This is an HTTP-only interim setup. Biuro passwords and bearer session tokens
are transmitted without transport encryption and can be observed or modified on
the network path. Use synthetic data and unique temporary test credentials
only. Do not use this setup for real registrations or ordinary production work.

The later HTTPS change can keep the same frontend applications and internal API
routes. Add a TLS-terminating edge proxy, move the frontend services back to
private Docker-network access, and publish only the proxy's HTTP/HTTPS ports.
Certificate issuance and automated renewal must be prepared and verified before
removing the HTTP-only limitation.
