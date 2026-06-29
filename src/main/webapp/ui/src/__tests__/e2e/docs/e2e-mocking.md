# E2E Mocking

How third-party integrations are faked in the Playwright e2e suite.

## Why a real HTTP server, not MSW

RSpace integration calls (PubChem, GitHub, etc.) are made **server-side** by the
Java backend via Spring `RestTemplate`. The browser only talks to
`/api/v1/<integration>/...` on the RSpace server and never reaches the
third-party API directly.

MSW's Node interceptor patches Node's `http` module — it cannot intercept
outgoing requests from a separate JVM process. So instead, the e2e suite starts
a real TCP listener on `localhost:9099`. JVM startup args override each
integration's base URL (e.g. `-Dpubchem.base.url=http://localhost:9099`) so
Spring `RestTemplate` calls land on this local server rather than the live API.

## Integration modes

Controlled by the `E2E_INTEGRATION_MODE` environment variable:

| Value | Behaviour | When used |
|-------|-----------|-----------|
| `mock` (default) | Backend points at the local mock server | PR gates, local dev |
| `real` | Backend hits the live third-party APIs | Nightly drift checks |

The Playwright config reads this via
`src/__tests__/e2e/integrationMode.ts` and only starts the mock server's
`webServer` block when mode is `mock`.

## Mock server

**Entry point:** `src/__tests__/e2e/mockServer.mjs`
**Default port:** `9099` (override with `E2E_MOCK_PORT`)

Playwright's `webServer` block starts it automatically before any test runs and
waits for `GET /e2e-health → 200` before proceeding.

### Handler format

Each integration exports a `handlers` array:

```js
export const myHandlers = [
  {
    method: "GET",
    match: (pathname) => /^\/some\/path/.test(pathname),
    respond: (req) => ({ status: 200, body: myFixture }),
  },
];
```

`body` can be:
- a plain object — sent as `application/json`
- a string — sent as `text/plain`
- a `Buffer` — sent as `application/octet-stream` (or set `contentType` explicitly)

`ALL_HANDLERS` in `mockServer.mjs` assembles all integration handler arrays.
The health probe handler is always first.

## Fixtures

Fixture JSON files live alongside their handlers:

```
src/__tests__/e2e/specs/<feature>/<feature>Mock/
  handlers.mjs
  fixtures/
    search.json
    synonyms.json
    ...
```

Fixtures are harvested once from the real API and committed. They are the
**single source of truth**: the mock returns them in mock mode, and real-mode
drift tests assert that the live API still returns the same shape. This means a
fixture change is always intentional and reviewable in a diff.

## Adding a new integration mock

1. Create `src/__tests__/e2e/specs/<feature>/<feature>Mock/handlers.mjs`
   with a named export `<feature>Handlers`.
2. Add fixture JSON files under `.../fixtures/` — harvest from the real API.
3. Import and spread into `ALL_HANDLERS` in `mockServer.mjs`:
   ```js
   import { myHandlers } from "./specs/<feature>/<feature>Mock/handlers.mjs";
   const ALL_HANDLERS = [healthHandler, ...pubchemHandlers, ...myHandlers];
   ```
4. Override the integration's base URL in the CI `jetty:run-war` command
   (and in the local runbook below) so the JVM points at `http://localhost:9099`.

## Mocked integrations

| Integration | Property overridden | Handlers |
|-------------|---------------------|---------|
| PubChem | `pubchem.base.url` | `specs/apps/pubchemMock/handlers.mjs` |
| Fieldmark | `fieldmark.api.url` | `specs/apps/fieldmarkMock/handlers.mjs` |

## CI setup

The `e2e-mocked.yml` workflow calls the reusable `e2e.yml` with `mode: mock`.
The e2e job passes `-Dpubchem.base.url=http://localhost:9099 -Dfieldmark.api.url=http://localhost:9099`
(and any future integration URL overrides) to `jetty:run-war`. Playwright starts
the mock server via its `webServer` block before the test run begins.

A separate `e2e-real.yml` runs nightly with `mode: real` to catch API drift.

## Running locally

```bash
# 1. Build WAR with React bundles (once)
./mvnw clean package -DgenerateReactDist -DskipTests=true -Dtimestamp=e2e-local

# 2. Start RSpace pointing at the mock server
./mvnw jetty:run-war \
  -DskipTests=true -Dmaven.war.skip=true -Dtimestamp=e2e-local \
  -Denvironment=drop-recreate-db -Dspring.profiles.active=run \
  -DreactDevMode=false \
  -Djdbc.url=jdbc:mysql://localhost:3306/rspace -Djdbc.db.maven=rspace \
  -DRS_FILE_BASE=/tmp/e2e-filestore \
  -Dpubchem.base.url=http://localhost:9099 \
  -Dfieldmark.api.url=http://localhost:9099

# 3. Run e2e tests (mock server starts automatically via webServer)
RSPACE_BASE_URL=http://localhost:8080 \
RSPACE_SYSADMIN_PASSWORD= \
E2E_INTEGRATION_MODE=mock \
pnpm run test-e2e
```

> `/tmp/e2e-filestore` must be owned by your user — create it with
> `mkdir -p /tmp/e2e-filestore` (not `sudo`).
