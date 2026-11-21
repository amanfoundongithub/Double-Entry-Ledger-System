# LedgerCore

LedgerCore is an accrual-based, double-entry bookkeeping application with a Spring Boot REST API and an Angular web UI. It supports chart-of-account management, journal posting and reversal, accounting-period controls, account ledgers, and financial reporting backed by MongoDB.

## Current features

- **Chart of accounts** - create accounts, filter by account type, deactivate accounts, and open an account ledger.
- **Journal entries** - post balanced journal entries with debit and credit lines, look up entries, review recent entries, and reverse posted entries.
- **Accounting periods** - create periods and manage open, closed, locked, and reopened states.
- **Financial reports** - trial balance, income statement, balance sheet, and cash-flow statement.
- **Reliability controls** - idempotency keys for mutations, correlation IDs for request tracing, and an outbox event relay.
- **Virtual threads** - enabled for the Spring application.

## Technology

| Area | Current stack |
| :--- | :--- |
| Backend | Java 25, Spring Boot 4.2.0, Spring Data MongoDB, Gradle |
| Frontend | Angular 21.2, Angular CLI 21.2.30, TypeScript 5.9, RxJS 7.8 |
| Test tools | Vitest 4, JUnit, Gradle test task |
| Data store | MongoDB |
| Local orchestration | Bash (`dev.sh`) |
| Packaging | Docker and GitHub Actions |

## Project layout

```text
.
├── ledger-core-ui/       # Angular application
│   ├── src/app/features/ # Accounts, journal, periods, statements, and ledger pages
│   └── Dockerfile        # Production nginx image
└── ledger_service/       # Spring Boot REST API
    ├── src/main/java/    # Accounts, journals, periods, reporting, and shared services
    └── Dockerfile        # Production JRE image
```

## Prerequisites

- Java 25
- Node.js 22 and npm 11
- MongoDB

The repository includes a Gradle wrapper, so a separate Gradle installation is not required.

## MongoDB configuration

The backend reads these environment variables:

```text
MONGODB_URI
MONGODB_AUTO_INDEX_CREATION
```

Set both variables before starting the service:

```bash
export MONGODB_URI='mongodb://localhost:27017/ledger'
export MONGODB_AUTO_INDEX_CREATION=true
```

The Spring `local` profile is active by default. `application-local.yaml` is intended for local Spring configuration and may contain a local MongoDB connection override. Avoid committing real database credentials.

## Run the complete application

From the repository root:

```bash
./dev.sh
```

This starts both processes and stops the remaining process if one exits:

- Backend: `http://localhost:5555`
- Frontend: `http://localhost:4200`

Press **Ctrl+C** to stop both.

## Run services separately

### Backend

```bash
cd ledger_service
./gradlew bootRun
```

The backend exposes Spring Actuator health and info endpoints at `/actuator/health` and `/actuator/info`.

### Frontend

```bash
cd ledger-core-ui
npm ci
npm start
```

The Angular development server forwards `/api` requests to `http://localhost:5555` using `proxy.conf.json`.

## Frontend routes

| Route | Screen |
| :--- | :--- |
| `/journal` | Journal entry composer and recent entries |
| `/accounts` | Account management |
| `/accounts/:accountId/ledger` | Account transaction ledger |
| `/periods` | Period lifecycle management |
| `/statements` | Trial balance and financial statements |

The root route redirects to `/journal`.

## API overview

All business resources are served below `/api/v1`.

| Resource | Endpoints |
| :--- | :--- |
| Accounts | `GET /accounts`, `GET /accounts/{id}`, `GET /accounts/code/{code}`, `POST /accounts`, `PATCH /accounts/{id}/deactivate`, `GET /accounts/{accountId}/ledger` |
| Journal entries | `GET /journal-entries`, `GET /journal-entries/{id}`, `GET /journal-entries/number/{entryNumber}`, `POST /journal-entries`, `POST /journal-entries/{id}/reverse` |
| Accounting periods | `GET /periods`, `GET /periods/{id}`, `POST /periods`, `PATCH /periods/{id}/close`, `PATCH /periods/{id}/lock`, `PATCH /periods/{id}/reopen` |
| Reports | `GET /reports/trial-balance`, `GET /reports/income-statement`, `GET /reports/balance-sheet`, `GET /reports/cash-flow` |

Mutation endpoints accept `Idempotency-Key`. Requests accept `X-Correlation-Id` for distributed tracing.

## Validation and testing

### Frontend

```bash
cd ledger-core-ui
npm ci
npm run lint
npm test -- --watch=false
npm run build
```

### Backend

```bash
cd ledger_service
./gradlew check
```

GitHub Actions runs the Angular lint, test, and production build checks and the backend `check` task for pushes and pull requests targeting `main`. A separate workflow also builds and verifies both container images.

## Container images

The repository publishes:

- `ghcr.io/<owner>/ledger-core-ui`
- `ghcr.io/<owner>/ledger-service`

Images receive commit-SHA tags. Images pushed from `main` also receive the `latest` tag. Pull-request and manual workflow runs build images without publishing them.

The service container requires `MONGODB_URI` and `MONGODB_AUTO_INDEX_CREATION` at runtime. The UI container uses nginx and defaults to `http://ledger-service:5555`; override that upstream by setting `API_UPSTREAM`.

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

Open `http://localhost:8080`. The GHCR packages may require repository package visibility to be adjusted for unauthenticated pulls.
