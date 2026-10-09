# SRM Toolkit — deployment readiness checklist

Updated: 2026-10-08. This file tracks installation, configuration, security,
operations and deployment verification. It does not define product feature scope.

- Functional scope and acceptance: [Registration-workflow-release-plan.md](Registration-workflow-release-plan.md).
- Turnus/season requirements: [Turnus-management-plan.md](Turnus-management-plan.md).
- Functional findings: [First-release-functional-review.md](First-release-functional-review.md).
- Progress and evidence: [WIP.md](WIP.md).

Unchecked means deployment acceptance is still pending, even where code has
already been implemented and tested locally. Record environment, date and evidence
in WIP rather than treating local implementation as deployed verification.

## A. Prerequisites for a controlled TEST/SIT deployment

Use synthetic data and controlled access. A technical test deployment can happen
before every functional acceptance gate is closed; it is not approval to collect
real registrations or a change to the agreed first-release scope.

### Build and runtime

- [ ] Dockerfiles/Compose contexts match the repository layout and include both frontends and all required services.
- [ ] Maven wrapper execution and complete backend/frontend builds succeed from a clean checkout.
- [ ] `docker compose build` succeeds; required images and runtime versions are documented.
- [ ] Frontend API URLs and backend CORS match the deployed environment.
- [ ] Frontend hosting supports SPA refresh/deep links.
- [ ] Service URLs, database and SMTP variables match application configuration.
- [ ] Document a reproducible local/server startup path without GUI terminal assumptions.

### Security and access

- [ ] Management endpoints require service authentication; Biuro endpoints require valid operator authentication.
- [ ] Public submission and Turnus lookup remain usable; unauthenticated management access is rejected.
- [ ] Database and internal services, including email-service, are not publicly exposed.
- [ ] Authenticate email-service callers; allowlist template identifiers and prevent
  caller variables from overriding trusted link/contact configuration. Verify
  intended template escaping rather than assuming all rendering is unsafe.
- [ ] Remove unnecessary published ports; bind development-only database/MailHog
  access to localhost when host access is needed.
- [ ] HTTPS/reverse proxy and network exposure are configured and checked.
- [ ] Use non-default operator credentials, JWT signing secret and shared service credentials.
- [ ] Remove tracked secret files, rotate exposed credentials, and document required variables in `.env.example` without secrets.
- [ ] Required release credentials fail startup when missing/blank; validate JWT
  signing-key strength. Do not retain known JWT/operator/database password fallbacks.
- [ ] Actuator exposes only intended health information; secrets and detailed environment data are not public.
- [ ] Review logs for credentials and unnecessary personal data; restrict log access.

### Database and recovery

- [ ] Database storage persists across service/container recreation.
- [ ] Flyway migrations run successfully and schema versions are recorded.
- [ ] Existing databases without history are checked/backed up before explicit one-time baseline; automatic baselining is not permanently enabled.
- [ ] Review existing duplicates before the integrity migration; preserve counter rows during registration cleanup.
- [ ] Establish backup storage, access and retention; perform and document a restore test.
- [ ] When Biuro gains persistent Turnus data, include its migrations, volumes and backup/restore in the deployment procedure.

### SMTP and test isolation

- [ ] Configure real test SMTP or a controlled mail sink; verify environment variable names and sender identity.
- [ ] Prevent synthetic scenarios from sending to unintended real recipients.
- [ ] Keep MailHog/local development SMTP usable.
- [ ] Any test payment instructions are unmistakably test-only; prevent actual payment requests during synthetic tests.
- [ ] Verify connectivity and observable send failures without losing saved registrations.
- [ ] Verify SMTP exceptions produce an accurate email-service failure response,
  and configured inter-service connect/read timeouts take effect. Workflow recovery
  and retry semantics are defined in the functional release plan.

### Deployed smoke checks

- [ ] Services start, health checks work and intended routes are reachable.
- [ ] Login/logout work; invalid/expired credentials do not grant access.
- [ ] Synthetic registration can be saved, reviewed and updated through the deployed applications.
- [ ] Test notification reaches the intended controlled mailbox.
- [ ] Restart/recreate services and confirm registrations, counters and Turnus data persist.
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
