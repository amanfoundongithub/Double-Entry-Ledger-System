# LedgerCore UI

The Angular frontend for LedgerCore.

## Stack

- Angular 21.2
- Angular CLI 21.2.30
- TypeScript 5.9
- RxJS 7.8
- Vitest 4
- ESLint and Prettier

## Features

- Journal entry composer with account selection, balanced debit/credit validation, sample scenarios, reversal, and recent-entry listing
- Chart-of-account management and dated account ledgers
- Accounting-period creation and lifecycle controls
- Trial balance, income statement, balance sheet, and cash-flow views
- Template browser with search, category and due-date filtering, pagination, cloning, deletion, recurring schedules, and direct posting
- Template creation from scratch or by importing a posted entry
- Fixed, percentage, and balancing template allocations

## Routes

| Route | Page |
| :--- | :--- |
| `/journal` | Journal composer |
| `/accounts` | Chart of accounts |
| `/accounts/:accountId/ledger` | Account ledger |
| `/periods` | Accounting periods |
| `/statements` | Financial statements |
| `/templates` | Journal-entry templates |

The root route redirects to `/journal`.

## Development

Prerequisites:

- Node.js 22
- npm 11
- LedgerCore backend at `http://localhost:5555`

Install dependencies and start the app:

```bash
npm ci
npm start
```

Open `http://localhost:4200`. `/api` requests are proxied to the backend through `proxy.conf.json`.

To start both applications together:

```bash
cd ..
./dev.sh
```

## Commands

```bash
npm start                 # Development server with API proxy
npm run lint              # Lint TypeScript and Angular templates
npm test -- --watch=false # Run unit tests once
npm run build             # Create the production bundle
npm run watch             # Rebuild continuously in development mode
npm run ng -- --help      # Angular CLI help
```

The production bundle is written to `dist/ledger-core-ui/browser`.

## Production container

The Docker build uses Node 22 to compile the app and nginx-unprivileged to serve it.

```bash
docker build -t ledger-core-ui .
docker run --rm -p 8080:8080 \
  -e API_UPSTREAM=http://ledger-service:5555 \
  ledger-core-ui
```

Open `http://localhost:8080`.
