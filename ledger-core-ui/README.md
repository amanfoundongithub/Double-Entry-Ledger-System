# LedgerCore UI

The Angular frontend for LedgerCore. It provides account management, journal entry posting and reversal, period controls, account ledgers, and financial statements.

## Stack

- Angular 21.2
- Angular CLI 21.2.30
- TypeScript 5.9
- RxJS 7.8
- Vitest 4 for unit tests
- ESLint and Prettier

## Routes

- `/journal` - journal entry composer and recent entries
- `/accounts` - chart-of-account management
- `/accounts/:accountId/ledger` - account ledger
- `/periods` - accounting-period lifecycle
- `/statements` - trial balance and financial statements

The application redirects `/` to `/journal`.

## Development

Prerequisites:

- Node.js 22
- npm 11
- LedgerCore backend running at `http://localhost:5555`

Install dependencies and start the development server:

```bash
npm ci
npm start
```

Open `http://localhost:4200`. The server forwards `/api` requests to the backend using `proxy.conf.json`.

To start both the frontend and backend together, run `../dev.sh` from the repository root.

## Commands

```bash
npm start                 # Development server with API proxy
npm run lint              # Lint TypeScript and Angular templates
npm test -- --watch=false # Run unit tests once
npm run build             # Create the production bundle
npm run watch             # Rebuild continuously in development mode
```

The production build writes to `dist/ledger-core-ui/browser`.

## Production container

The Docker image uses Node 22 to build the Angular application and nginx-unprivileged to serve it.

```bash
docker build -t ledger-core-ui .
docker run --rm -p 8080:8080 \
  -e API_UPSTREAM=http://ledger-service:5555 \
  ledger-core-ui
```

Open `http://localhost:8080`.
