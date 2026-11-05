# SRM Toolkit — deployment readiness checklist

Updated: 2026-10-28. This file tracks installation, configuration, security,
operations and deployment verification. It does not define product feature scope.

- Functional scope and acceptance: [Registration-workflow-release-plan.md](Registration-workflow-release-plan.md).
- Turnus/season requirements: [Turnus-management-plan.md](Turnus-management-plan.md).
- Functional findings: [First-release-functional-review.md](First-release-functional-review.md).
- Progress and evidence: [WIP.md](WIP.md).

Unchecked means deployment acceptance is still pending, even where code has
already been implemented. GCP TEST is running with HTTP and synthetic data; the
operator-reported checks are recorded in [WIP.md](WIP.md). Do not infer production
readiness from the TEST deployment.

## A. Prerequisites for a controlled TEST/SIT deployment

Use synthetic data and controlled access. A technical test deployment can happen
before every functional acceptance gate is closed; it is not approval to collect
real registrations or a change to the agreed first-release scope.

### Build and runtime

- [x] Dockerfiles/Compose contexts match the repository layout and include both frontends and all required services (local Phase 1 verified).
- [x] Application images were built from the packaging branch and started on the GCP TEST VM. Re-run builds from a clean checkout as part of release verification.
- [x] Compose v2 build succeeds for all five application images; runtime versions and local commands are documented in [Local container testing](Local-container-testing.md).
- [x] Frontend API routing works through same-origin proxy paths in GCP TEST (both app roots returned HTTP 200; operator verified form submission and Biuro use).
- [x] Container frontend hosting supports SPA refresh/deep links (HTTP smoke checks passed).
- [x] TEST service URLs and external SMTP configuration are set in the VM's private env file; operator confirmed SMTP delivery.
- [x] Document reproducible local/server commands in [Local container testing](Local-container-testing.md) and [GCP HTTP access](GCP-test-http-access.md).

GCP TEST is externally reachable over HTTP at the two configured hostnames.
This is an operator-reported synthetic-data test deployment, not a secure
production release. HTTPS, reboot and database recovery remain separate gates.
Builds invoke Maven directly in the builder image; host Maven-wrapper permissions
were not changed.

### Security and access

- [x] Management endpoints require service authentication; Biuro operations require authenticated accounts (implementation and local automated coverage; complete deployed role-boundary acceptance remains pending).
- [x] Public submission and Turnus lookup work through the Registration frontend proxy; management routes are restricted by the proxy and service authentication.
- [x] Database and internal services are not published as host ports in the GCP TEST Compose setup; the operator removed GCP ingress for TCP 8080 and verified only TCP 80 was listening on the host.
- [x] Authenticate email-service callers; allowlist template identifiers and prevent
  caller variables from overriding trusted link/contact configuration. Actual-template
  escaping and negative authentication are covered by automated tests (local TEST verified).
- [x] Restrict public Registration proxy routes to submissions and catalogue reads;
  management paths remain blocked even with valid service credentials.
- [x] GCP TEST publishes only the Caddy HTTP entry point on TCP 80; internal APIs, MariaDB and MailHog have no public mappings. Development-only access stays on loopback.
- [ ] HTTPS/reverse proxy for production and secure client access. Current GCP TEST intentionally uses HTTP only.
- [x] GCP TEST uses private, non-default operator/JWT/service credentials (operator setup confirmed); review/rotate any credential previously exposed before production.
- [x] Untrack root `.env` and document required variables in `.env.example` without secrets.
- [!] Rotate credentials previously exposed in chat/Git history where still active; verify SMTP credential rotation before any production use. Do not copy old values from Git history.
- [ ] Required release credentials fail startup when missing/blank; validate JWT
  signing-key strength. Do not retain known JWT/operator/database password fallbacks.
- [ ] Actuator exposes only intended health information; secrets and detailed environment data are not public.
- [ ] Review logs for credentials and unnecessary personal data; restrict log access.

### Database and recovery

- [!] GCP TEST reboot and MariaDB backup/isolated-restore are not verified. These were explicitly parked for disposable synthetic TEST; they are not complete and do not satisfy the separate production/VPS recovery gate. See [recovery runbook](GCP-test-recovery-runbook.md).
- [ ] Database storage persists across service/container recreation.
- [ ] Flyway migrations run successfully and schema versions are recorded.
- [ ] Existing databases without history are checked/backed up before explicit one-time baseline; automatic baselining is not permanently enabled.
- [ ] Review existing duplicates before the integrity migration; preserve counter rows during registration cleanup.
- [ ] Establish backup storage, access and retention; perform and document a restore test.
- [ ] When Biuro gains persistent Turnus data, include its migrations, volumes and backup/restore in the deployment procedure.

### SMTP and test isolation

- [x] Configure external test SMTP on GCP TEST; operator confirmed mail was sent. Local default remains MailHog. Verify template-specific recipients/content separately.
- [ ] Prevent synthetic scenarios from sending to unintended real recipients.
- [ ] Keep MailHog/local development SMTP usable.
- [ ] Any test payment instructions are unmistakably test-only; prevent actual payment requests during synthetic tests.
- [x] SMTP connectivity and successful delivery were confirmed by the operator.
- [ ] Reconfirm failure handling and saved-registration behavior in deployed acceptance.
- [ ] Verify SMTP exceptions produce an accurate email-service failure response,
  and configured inter-service connect/read timeouts take effect. Workflow recovery
  and retry semantics are defined in the functional release plan.

### Deployed smoke checks

- [x] Services started healthy on GCP TEST; both public HTTP hostnames and local host-routed checks returned HTTP 200. Recheck after future deploys.
- [ ] Login/logout work; invalid/expired credentials do not grant access.
- [ ] Synthetic registration can be saved, reviewed and updated through the deployed applications.
- [x] Operator confirmed external SMTP delivery.
- [ ] Complete recipient/template matrix and ensure test recipients remain controlled.
- [ ] Recreate/update services without deleting volumes and confirm persistence. Controlled VM reboot and SQL backup/restore remain unverified/deferred for GCP TEST.
- [ ] Record defects and environment-specific limitations in WIP.

## B. Additional gates before release for real registrations

TEST/SIT success alone does not close these gates.

- [ ] Complete the functional acceptance gates in [Registration-workflow-release-plan.md](Registration-workflow-release-plan.md), including operational Turnus lists.
- [ ] Verify configured Turnus/season values throughout the deployed workflow; no stale or placeholder dates/capacity/role options.
- [ ] Replace or omit test bank/payment instructions according to agreed participant/staff rules.
- [ ] Verify recipient routing and content in real email clients using controlled addresses; report failures accurately and verify the agreed recovery/resend path.
- [ ] Run the full backend regression suite with real-MariaDB integrity/migration tests explicitly enabled; verify both frontend builds.
- [ ] Run deployed functional E2E and negative scenarios defined by the workflow plan; obtain operator acceptance of lists and processing workflow.
- [ ] Confirm security/access gates, HTTPS and backup/restore above for the release environment.
- [ ] Separate synthetic/test data from real registrations using an explicit reviewed procedure; never reset counters independently of the data policy.
- [ ] Record deployed commit, migration versions, configuration version, release owner and recovery procedure.

## C. Later operational improvements

These do not silently become requirements for the first release. Promote an item
only when a concrete release risk requires it and record the decision in WIP.

- [ ] Full CI/CD automation and release promotion.
- [ ] Central monitoring/error reporting and richer operational dashboards.
- [ ] More extensive deployment automation/IaC.

Domain features, editable emails, group announcements, payment/attendance
workflows and Person/history expansion are tracked in the functional plans and
WIP, not in this deployment checklist.
