# CLAUDE.md

## 1. Project Overview

**SRM-toolkit** (System Rejestracji Młodzieży) is a multi-service platform for managing youth camp registrations.

### Purpose
- Collect participant and staff registrations
- Support office-team (biuro) review and decision workflows
- Send operational registration emails

### Users
- **Uczestnicy (Participants)** and **Kadra (Staff)**: submit registration forms
- **Opiekunowie (Guardians)**: provide contact and consent details
- **Biuro (Office admins)**: review, accept, and reject registrations

### Stack Summary
- **Backend**: Java 21, Spring Boot 4.x, Maven multi-module
- **Frontend**: Vue 3 + Vite (+ Tailwind; Pinia in biuro frontend)
- **Database**: MariaDB 11 + Flyway
- **Email**: MailHog (dev), SMTP (prod)
- **Orchestration**: Docker Compose

---

## 2. Repository Structure

```text
SRM-toolkit/
├── pom.xml                    # Maven aggregator (Spring Boot 4.0.6 parent)
├── docker-compose.yml         # Local runtime stack (services + DB + MailHog)
├── registration-api/          # Maven submodule: registration domain API (8080)
├── biuro-api/                 # Maven submodule: office/admin API (8081)
├── email-service/             # Maven submodule: email rendering + SMTP API (8082)
├── frontend/                  # Standalone Vite app for participant/staff flows (5173)
└── biuro-frontend/            # Standalone Vite + Pinia app for office dashboard (5174)
```

### Notes
- `registration-api`, `biuro-api`, and `email-service` are Maven submodules under root `pom.xml`.
- `frontend/` and `biuro-frontend/` are independent frontend apps (not Maven modules).

---

## 3. Architecture

### Service Map

| Service | Port | Responsibility |
|---|---:|---|
| `registration-api` | 8080 | Participant/staff registration, PESEL validation/handling, turnus management, persistence via JPA/Hibernate |
| `biuro-api` | 8081 | Stateless office API, JWT auth (HS256 via JJWT 0.11.5), proxies list + accept/reject to registration-api |
| `email-service` | 8082 | Stateless email dispatch API (`POST /api/email/send`), Thymeleaf template rendering, SMTP delivery via `EmailSender` |
| `frontend` | 5173 | Participant/staff form UI |
| `biuro-frontend` | 5174 | Office dashboard UI |
| `mariadb` | 3306 | Main persistence store |
| `mailhog` | 1025 / 8025 | Dev SMTP sink + web inbox |

### Inter-service Communication

```text
frontend (5173)
   -> registration-api (8080)

biuro-frontend (5174)
   -> biuro-api (8081, JWT)
      -> registration-api (8080, internal REST)

registration-api (8080)
   -> email-service (8082, fire-and-forget REST)

registration-api (8080)
   -> MariaDB (3306)
```

### Data Flow
1. Participant/staff submits form in `frontend`.
2. `registration-api` validates and persists registration data.
3. `registration-api` triggers `email-service` asynchronously (non-blocking for registration success path).
4. Biuro user reviews registrations via `biuro-frontend` → `biuro-api`.
5. Biuro accepts/rejects registration; status is updated through `registration-api`.
6. **Planned**: status-change notifications sent by `email-service`.

---

## 4. Development Conventions for Claude

### Branching and PR Flow
- Feature branches: `feature/*`
- Agent branches: `copilot/feature*`
- PR flow: `feature/*` → `development` → `main`

### Language and UX
- All user-facing strings must be in **Polish**.

### Security and Sensitive Data
- PESEL must be hashed before persistence.
- Never log raw PESEL.
- Never return raw PESEL in API responses.
- JWT handling belongs to `biuro-api`; keep token handling explicit and minimal.

### Error Handling
- Email sending failures must **never block** registration flow.
- Registration/business failures should return structured JSON error responses.

### API and Scalability
- Use pagination for potentially large list endpoints.
- Full-list responses are acceptable only for clearly bounded datasets.

### Testing Expectations
- Add unit tests for service-layer logic changes.
- Keep at least smoke-level controller coverage for new/changed endpoints.

### Guardrails for Changes
- Do **not** modify `frontend/` or `biuro-frontend/` unless explicitly requested.
- Do **not** add database dependencies to `email-service` or `biuro-api`.

---

## 5. Claude Integration Architecture Plan

### 5.1 Roles Claude Plays in This Repo
- **Code generation**: implement features and bugfixes via focused PR tasks.
- **Code review**: identify architectural, security, and correctness issues.
- **Deep research**: trace multi-service flows and dependency impacts.
- **Documentation**: generate and maintain architecture/runbook docs.

### 5.2 Recommended Workflow
1. Start from a clear user story and acceptance criteria.
2. Select target branch (`feature/*` or agent branch as instructed).
3. Scope impacted services/modules before coding.
4. Ask Claude to propose minimal, testable backend-first changes.
5. Implement in small commits with targeted tests.
6. Run module-specific checks (and integration checks when cross-service).
7. Open/update PR with architecture notes and risk summary.
8. Run Claude-assisted review for defects and security gaps.
9. Merge into `development`, then promote to `main`.

### 5.3 Claude Context Checklist
Provide Claude with:
- Active branch name and merge target
- Target service(s) and impacted modules
- Existing files/classes/endpoints to align with
- Requirement that user-facing text is Polish
- Constraints (e.g., no DB in `email-service`/`biuro-api`, no frontend edits unless requested)
- Required validations (tests/builds, manual checks)

### 5.4 Planned Claude-Assisted Features (Next Phases)
1. **RabbitMQ integration** replacing `registration-api` → `email-service` REST call.
   - Keep `EmailSender` interface; swap implementations via Spring profile (e.g., `SmtpEmailSender`).
2. **`biuro-api` person-management screens** (deferred from thin slice).
3. **Status-change email notifications** using existing `status-update.html` template.
4. **Shared API error response model** across all services (currently inconsistent).
5. **Pagination** for registration list endpoints.
6. **`.env.example`** documenting all required environment variables.
7. **GitHub Actions CI pipeline** (build + test all Maven modules).
8. **Production SMTP configuration** replacing MailHog defaults.
9. **`biuro-frontend` extended filtering and CSV export**.

### 5.5 Known Issues / Technical Debt
- `biuro-api` filters registrations in-memory — should be delegated to `registration-api` query params.
- Default JWT secret in `docker-compose.yml` is weak; must be overridden in production via `.env`.
- No correlation/trace IDs across services — debugging inter-service calls requires log timestamps.
- `organizer-new-registration` email template referenced in `EmailServiceClient` but template file not yet created in `email-service`.
- Automated test coverage is minimal (smoke-level only).

---

## 6. Running the Project Locally

### Prerequisites
- Java 21
- Maven Wrapper (`./mvnw`)
- Node.js + npm
- Docker + Docker Compose

### Start Full Stack with Docker Compose
```bash
docker compose up --build
```

### Start Services Individually (Development)
```bash
# registration-api (port 8080)
./mvnw -pl registration-api spring-boot:run

# biuro-api (port 8081)
./mvnw -pl biuro-api spring-boot:run

# email-service (port 8082)
./mvnw -pl email-service spring-boot:run

# frontend (port 5173)
cd frontend && npm install && npm run dev

# biuro-frontend (port 5174)
cd biuro-frontend && npm install && npm run dev
```

### Run Backend Tests
```bash
JAVA_HOME=/usr/lib/jvm/temurin-21-jdk-amd64 \
  PATH="/usr/lib/jvm/temurin-21-jdk-amd64/bin:$PATH" \
  sh ./mvnw test
```

### MailHog
- Web UI: `http://localhost:8025`
- SMTP: `localhost:1025`

---

## 7. Glossary

| Polish | English |
|---|---|
| Turnus | Camp session |
| Uczestnik | Participant |
| Kadra | Staff |
| Opiekun | Guardian |
| Biuro | Office (admin team) |
| PESEL | Polish national ID number |
| Zgłoszenie | Registration |
