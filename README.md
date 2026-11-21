# LedgerCore

LedgerCore is an accrual-based, double-entry general ledger. A Spring Boot REST API manages accounts, journals, periods, reusable templates, schedules, and financial reports; an Angular application provides the working UI. MongoDB is the persistence layer.

## Features

- **Chart of accounts:** account codes and names, normal debit/credit sides, cash-flow categories, contra accounts, parent accounts, currencies, and account activation/deactivation.
- **Journal entries:** balanced debit/credit posting, entry and entry-number lookup, recent-entry browsing, account filtering, and reversal with audit fields.
- **Accounting periods:** create periods and transition them through open, closed, locked, and reopened states.
- **Account ledgers:** retrieve dated debit, credit, and running-balance activity for an account.
- **Financial reports:** trial balance, income statement, balance sheet, and cash-flow statement.
- **Journal templates:** create, clone, delete, and post system or custom templates with fixed, percentage, or balancing allocation lines.
- **Recurring schedules:** weekly, monthly, quarterly, or yearly template schedules with configurable intervals and next-run dates.
- **Reliability:** idempotency keys, correlation-ID tracing, domain exceptions, and an outbox event relay.
- **Runtime:** Java virtual threads are enabled for the Spring application.

## Technology

| Area | Current implementation |
| :--- | :--- |
| Backend | Java 25, Spring Boot 4.2.0, Spring MVC, Spring Data MongoDB, Jakarta Validation, Gradle |
| Frontend | Angular 21.2, Angular CLI 21.2.30, TypeScript 5.9, RxJS 7.8 |
| Testing | Angular Vitest/ESLint and Spring/JUnit/Gradle |
| Database | MongoDB |
| Local startup | `dev.sh` |
| Containers and CI | Docker, GitHub Actions, GitHub Container Registry |

## Project layout

```text
.
├── ledger-core-ui/
│   ├── src/app/features/  # Accounts, ledger, journal, periods, statements, templates
│   ├── nginx/             # Production API proxy template
│   └── Dockerfile
└── ledger_service/
    ├── src/main/java/com/ledger_core/ledger_core/
    │   ├── accounts/
    │   ├── idempotency/
    │   ├── journal/
    │   ├── outbox/
    │   ├── periods/
    │   ├── reporting/
    │   ├── shared/
    │   └── templates/
    └── Dockerfile
```

## Prerequisites

- Java 25
- Node.js 22
- npm 11
- MongoDB

The repository includes the Gradle wrapper.

## Configuration

The backend is configured with the `local` Spring profile by default and reads:

```text
MONGODB_URI
MONGODB_AUTO_INDEX_CREATION
```

Example local setup:

```bash
export MONGODB_URI='mongodb://localhost:27017/ledger'
export MONGODB_AUTO_INDEX_CREATION=true
```

Keep database credentials outside source control and use environment variables or a secret manager for deployments.

## Run both applications

From the repository root:

```bash
./dev.sh
```

- Backend: `http://localhost:5555`
- Frontend: `http://localhost:4200`

The script starts both processes, and stopping or failing either process stops the remaining process.

## Run separately

### Backend

```bash
cd ledger_service
./gradlew bootRun
```

Actuator endpoints are exposed at `/actuator/health` and `/actuator/info`.

### Frontend

```bash
cd ledger-core-ui
npm ci
npm start
```

The Angular dev server proxies `/api` to `http://localhost:5555`.

## UI routes

| Route | Page |
| :--- | :--- |
| `/journal` | Journal entry composer, recent entries, reversals, and sample scenarios |
| `/accounts` | Chart-of-account management |
| `/accounts/:accountId/ledger` | Dated account ledger |
| `/periods` | Create, close, lock, and reopen periods |
| `/statements` | Trial balance, income statement, balance sheet, and cash flow |
| `/templates` | Browse, clone, create, delete, schedule, and post journal templates |

The root route redirects to `/journal`.

## API overview

All business endpoints are rooted at `/api/v1`.

| Resource | Available operations |
| :--- | :--- |
| Accounts | List, get by ID, get by code, create, deactivate, and retrieve an account ledger |
| Journal entries | Post, list by account, list recent entries, get by ID or entry number, and reverse |
| Accounting periods | List, get, create, close, lock, and reopen |
| Reports | Trial balance, income statement, balance sheet, and cash-flow statement |
| Templates | List, get, create, delete, and advance a recurring schedule |

Mutation endpoints require an `Idempotency-Key` header. Requests may include `X-Correlation-Id` for tracing.

### Journal and template endpoints

```text
GET    /api/v1/journal-entries?accountId={id}
GET    /api/v1/journal-entries/recent?limit={1-100}
GET    /api/v1/journal-entries/{id}
GET    /api/v1/journal-entries/number/{entryNumber}
POST   /api/v1/journal-entries
POST   /api/v1/journal-entries/{id}/reverse

GET    /api/v1/templates
GET    /api/v1/templates/{id}
POST   /api/v1/templates
DELETE /api/v1/templates/{id}
POST   /api/v1/templates/{id}/advance-schedule
```

Template schedules support `NONE`, `WEEKLY`, `MONTHLY`, `QUARTERLY`, and `YEARLY`, a positive interval, a next-run date, and automatic date advancement after posting.

## Validation

Frontend:

```bash
cd ledger-core-ui
npm ci
npm run lint
npm test -- --watch=false
npm run build
```

Backend:

```bash
cd ledger_service
./gradlew check
```

The CI workflows run these checks on pushes and pull requests targeting `main`, and also verify the container builds.

## Container images

Published images:

- `ghcr.io/<owner>/ledger-core-ui`
- `ghcr.io/<owner>/ledger-service`

The service image runs as an unprivileged user and requires `MONGODB_URI` and `MONGODB_AUTO_INDEX_CREATION`. The UI image serves the Angular build with nginx and proxies `/api/` to `http://ledger-service:5555` by default. Override the upstream with `API_UPSTREAM`.

Example:

```bash
docker network create ledger

docker run -d --name ledger-service --network ledger -p 5555:5555 \
  -e MONGODB_URI='mongodb://mongodb:27017/ledger' \
  -e MONGODB_AUTO_INDEX_CREATION=true \
  ghcr.io/<owner>/ledger-service:latest

docker run -d --name ledger-ui --network ledger -p 8080:8080 \
  ghcr.io/<owner>/ledger-core-ui:latest
```

Open `http://localhost:8080`.
