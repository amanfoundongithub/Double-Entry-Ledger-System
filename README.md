# LedgerCore

LedgerCore is a double-entry bookkeeping and financial reporting application. It has a Spring Boot API backed by MongoDB and an Angular frontend.

## Features

- **Chart of accounts**: Create and filter asset, liability, equity, revenue, and expense accounts.
- **Journal entries**: Post balanced entries and reverse posted entries with an audit trail.
- **Accounting periods**: Create periods and manage their open, closed, and locked states.
- **Financial statements**: Generate trial balance, income statement, balance sheet, and cash flow reports.
- **Request tracing and idempotency**: Support correlation IDs and idempotency keys for traceable mutations.

## Project layout

```text
.
├── ledger-core-ui/   # Angular frontend
└── ledger_service/   # Spring Boot backend
```

## Applications

- **Backend (`ledger_service/`)**: Java 25 and Spring Boot REST API for accounts, journal entries, accounting periods, and financial reports. MongoDB stores application data; Gradle is used to build and run the service.
- **Frontend (`ledger-core-ui/`)**: Angular web application for working with accounts, journals, periods, and financial statements. Its development server proxies API requests to the backend.

## Prerequisites

- Java 25
- Node.js and npm
- MongoDB, with a connection URI configured for the backend

## Run both applications

The root-level `dev.sh` script starts the backend and frontend together. From the repository root, run:

```bash
./dev.sh
```

This starts the backend at `http://localhost:5555` and the Angular frontend at `http://localhost:4200`. The frontend proxies `/api` requests to the backend. Press **Ctrl+C** to stop both services. If either service exits, the script stops the other one as well.

The backend uses the `local` Spring profile and reads its MongoDB settings from `ledger_service/src/main/resources/application-local.yaml`. Set `MONGODB_URI` and `MONGODB_AUTO_INDEX_CREATION` in your environment to configure them without editing that file.

## Run each service separately

### Backend

```bash
cd ledger_service
./gradlew bootRun
```

### Frontend

```bash
cd ledger-core-ui
npm install
npm start
```

The Angular dev server uses `ledger-core-ui/proxy.conf.json` to forward `/api` requests to `http://localhost:5555`.

## API overview

The API is served under `/api/v1`.

| Resource | Endpoints |
| :--- | :--- |
| Accounts | `GET /accounts`, `POST /accounts` |
| Periods | `GET /periods`, `POST /periods`, `PATCH /periods/{id}/close`, `PATCH /periods/{id}/lock`, `PATCH /periods/{id}/reopen` |
| Journal entries | `POST /journal-entries`, `POST /journal-entries/{id}/reverse` |
| Reports | `GET /reports/trial-balance`, `GET /reports/income-statement`, `GET /reports/balance-sheet`, `GET /reports/cash-flow` |

Mutating endpoints accept an `Idempotency-Key`; requests support `X-Correlation-Id`.
