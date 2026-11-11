# Domain DNS and HTTPS setup for the VPS

Status: preparation notes, 2026-11-04. No DNS, hosting, firewall or VPS changes
are made by this document.

## Intended routing

Both public DNS names point to the VPS public IPv4 address. Caddy runs on the VPS
and routes requests by hostname:

- `formularz-srm.watroba.com.pl` → Registration frontend
- `biuro-srm.watroba.com.pl` → Biuro frontend

TLS terminates at Caddy. Frontend Nginx continues to proxy browser API requests
to the appropriate backend over the private Docker network. Do not publish the
Spring APIs, MariaDB or MailHog as public host ports.

## Work for the domain administrator

For Caddy-managed certificates for these two exact names:

1. Create or update an `A` record for `formularz-srm.watroba.com.pl` to the VPS
   public IPv4 address.
2. Create or update an `A` record for `biuro-srm.watroba.com.pl` to the same VPS
   public IPv4 address.
3. Remove stale `AAAA` records unless they point to working public IPv6 on the
   VPS and that traffic reaches Caddy.
4. Allow DNS changes to propagate before certificate issuance is attempted.

The administrator does not need to generate or send a certificate in this
option. Wildcard DNS records and DNS API tokens are not required: Caddy obtains
individual certificates for the two configured hostnames using ACME validation
over HTTP (port 80) or TLS-ALPN (port 443).

If wildcard `*.watroba.com.pl` is explicitly chosen, or neither ACME challenge
port can reach Caddy, use DNS-01 validation. That requires a TXT challenge record
or narrowly scoped DNS-provider API credentials; avoid sharing broad DNS API
credentials. Wildcard issuance is not needed for the two current subdomains.

## Work on our side

1. Configure the production Caddyfile with both hostnames and omit the `http://`
   prefix. The current [GCP TEST Caddyfile](../deploy/caddy/Caddyfile) explicitly
   disables automatic HTTPS and must not be treated as the production TLS config.
2. Create/use the production Compose configuration so only Caddy publishes TCP
   80 and 443. Port 80 serves the ACME HTTP challenge and redirects visitors to
   HTTPS; port 443 serves HTTPS and can also satisfy TLS-ALPN validation.
3. Open inbound TCP 80 and 443 in the VPS firewall/security group. UDP 443 is
   optional for HTTP/3 and is not required for certificates or basic HTTPS.
4. Keep Caddy's `/data` storage persistent and writable so certificates and
   renewal state survive container recreation. Do not remove its volume during
   routine updates.
5. Ensure outbound DNS/HTTPS access from Caddy to the ACME certificate authority.
6. Preserve the existing same-origin frontend API paths. The browser should call
   `/api/...` on the HTTPS hostname; do not configure direct browser access to
   backend ports or add permissive CORS as a workaround.
7. After HTTPS is working, verify automatic HTTP→HTTPS redirection and both
   applications before considering HSTS. Do not enable HSTS preload as part of
   initial TLS setup.

Caddy automatically requests and renews certificates when the site addresses
are domain names, DNS points to the server, and the challenge ports reach Caddy.
See [Caddy Automatic HTTPS](https://caddyserver.com/docs/automatic-https).

## Acceptance checks

- DNS A records for both names resolve to the intended VPS public IP; there are
  no stale AAAA records.
- Caddy starts without ACME errors and reports managed certificates for both
  names.
- `http://` redirects to `https://` for both hostnames and the browser trusts
  the certificate without warnings.
- Registration form submission and Biuro login/API calls work over HTTPS.
- Only the proxy is reachable on public ports; internal API, database and
  MailHog ports remain private.
- Caddy's persistent data remains intact during a routine service recreation;
  certificate renewal can be checked before production use.

Do not use the current HTTP-only GCP TEST configuration for real personal data.
