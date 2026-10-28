# GCP TEST: domain-based HTTP access

This Compose override routes both TEST frontends through one Caddy proxy using
their domain names. It is for synthetic data only. It does not deploy anything
or change DNS records or GCP firewall rules.

## Addresses and DNS

- Registration: `http://formularz-srm.watroba.com.pl/`
- Biuro: `http://biuro-srm.watroba.com.pl/`

Create an `A` record for each name pointing to the VM's reserved external IPv4
address. Do not add `AAAA` records unless the VM has working public IPv6. Caddy
matches the request hostname and forwards it to the corresponding frontend.
The frontend Nginx servers continue proxying browser API calls to their own
backend over the private Docker network.

Only the proxy publishes a host port (TCP 80). Frontends, APIs, MariaDB and
MailHog have no public port mappings. The override replaces the local frontend
loopback mappings and does not publish ports 5173, 5174, 8080, 8081, 8082, 3306
or 8025.

## Validate configuration

From the repository root, using the private deployment environment file:

```bash
docker compose --env-file /etc/srm-toolkit/compose.env \
  -p srm-test \
  -f docker-compose.yml \
  -f docker-compose.gcp-test-http.yml \
  config --quiet
```

Start or update the deployment only as a separately approved operational step.
For HTTP, GCP ingress must allow TCP 80 to the VM. Port 8080 is no longer used;
remove it from the public firewall rule after confirming the domain routing. If
external SMTP is deliberately required, also include
`docker-compose.external-smtp.yml` and keep recipient addresses controlled;
otherwise the base configuration sends test messages to MailHog.

## Security limitation

This is an HTTP-only interim setup. Biuro passwords and bearer session tokens
are transmitted without transport encryption and can be observed or modified on
the network path. Use synthetic data and unique temporary test credentials
only. Do not use this setup for real registrations or ordinary production work.

The Caddyfile explicitly uses `http://` site addresses to disable automatic
HTTPS. A later HTTPS change can use the same proxy and upstream routes: change
the site addresses to the domain names without the `http://` prefix, publish
TCP 443, allow it through GCP, and verify certificate issuance and renewal.
