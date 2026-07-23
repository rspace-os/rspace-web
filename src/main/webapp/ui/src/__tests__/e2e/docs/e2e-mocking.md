# E2E Mocking

How third-party integrations are faked in the Playwright e2e suite.

## Why a real HTTP server

RSpace integration calls (PubChem, Fieldmark, etc.) are made **server-side** by
the Java backend via Spring `RestTemplate`. The browser only talks to
`/api/v1/<integration>/...` on the RSpace server and never reaches the
third-party API directly.

MSW's **Node interceptor** (`msw/node`) patches Node's `http` module — it
cannot intercept outgoing requests from a separate JVM process. Instead, the
e2e suite starts a real TCP listener on `localhost:9099`. JVM startup args
override each integration's base URL (e.g. `-Dpubchem.base.url=http://localhost:9099`)
so Spring `RestTemplate` calls land on this local server rather than the live API.

In the Docker dev stack, `rspace-dev up --e2e` starts that server inside the
frontend/backend shared network namespace. Its per-worktree port is published
on host loopback, so `localhost:<mock-port>` reaches the same listener from both
the Java container and Playwright on the host; no LAN address or
`host.docker.internal` mapping is needed.

The server is built with Node's `http.createServer`, MSW handlers running as
Express-style middleware via `@mswjs/http-middleware`, and **Hono**
(`@hono/node-server`) as the fallback listener for unmatched requests.

## Why MSW handlers

Handlers are written using MSW's `http.*` API — the same format used by Vitest
Browser Mode component tests (`worker.use(...)`). This means fixture data and
handler patterns can be shared across both test layers without any translation.

## Integration modes

Controlled by the `E2E_INTEGRATION_MODE` environment variable:

| Value | Behaviour | When used |
|-------|-----------|-----------|
| `mock` (default) | Backend points at the local mock server | PR gates, local dev |
| `real` | Backend hits the live third-party APIs | Nightly drift checks |

The Playwright config reads this through `src/__tests__/e2e/env.ts`. Mocked and
real runs are separate, so the mode applies to every integration in a run.

## Mock server

**Entry point:** `src/__tests__/e2e/mockServer.ts`
**Default port:** `9099` (override with `E2E_MOCK_PORT`)

When overriding the port locally, export `E2E_MOCK_PORT` before both starting
RSpace and running Playwright; the runbook below uses the exported value for
every JVM integration URL as well as the mock listener.

Playwright's `webServer` block starts it automatically before any test runs and
waits for `GET /e2e-health → 200` before proceeding.

### Handler format

Each integration exports a `handlers` array of MSW `RequestHandler`s:

```js
import { HttpResponse, http } from "msw";

export const myHandlers = [
  http.get("/some/path", () => HttpResponse.json(myFixture)),
  http.post("/other/path", () => HttpResponse.json({ ok: true })),
  // Binary response (Buffer is a valid BodyInit):
  http.get("/files/:filename", () =>
    new HttpResponse(zipBuffer, { headers: { "Content-Type": "application/zip" } }),
  ),
];
```

Prefer exact string paths and named path parameters. MSW ignores query parameters
while matching, so a handler such as `"/invocations"` also matches
`/invocations?view=collection`. Use a regex only when the route shape cannot be
expressed with an MSW path mask.

`mockServer.ts` passes all integration handlers (plus a health probe) to
`createMiddleware` from `@mswjs/http-middleware`. MSW handles matched requests
first; unmatched ones fall through to Hono's `notFound` handler (404).

## Fixtures

Fixture JSON files live alongside their handlers:

```
src/modules/<feature>/__tests__/
  mock.ts
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

1. Create `src/modules/<feature>/__tests__/mock.ts`
   with a named export `<feature>Handlers` using MSW `http.*` handlers.
2. Inline small response bodies. Put larger JSON or binary responses under
   `src/modules/<feature>/__tests__/fixtures/`.
3. Import and spread into the `handlers` array in `mockServer.ts`:
   ```js
   import { myHandlers } from "../../modules/<feature>/__tests__/mock.ts";
   const handlers = [healthHandler, ...pubchemHandlers, ...myHandlers];
   ```
4. Override the integration's base URL in the CI `jetty:run-war` command
   (and in the local runbook below) so the JVM points at `http://localhost:9099`.

## Mocked integrations

| Integration | Property overridden | Handlers |
|-------------|---------------------|---------|
| PubChem | `pubchem.base.url` | `src/modules/pubchem/__tests__/mock.ts` |
| Fieldmark | `fieldmark.api.url` | `src/modules/fieldmark/__tests__/mock.ts` |
| Zenodo | `zenodo.url` | `src/modules/zenodo/__tests__/mock.ts` |
| Galaxy | `galaxy.server.config` (JSON array; mock uses alias `"mock"`) | `src/modules/galaxy/__tests__/mock.ts` |
| Dataverse | none — Server URL is a per-connection field set in the Apps page UI, not a JVM property. Point it at `http://localhost:9099` when saving the mock connection. | `src/modules/dataverse/__tests__/mock.ts` |
| DSW / FAIR Wizard | none — same per-connection UI field pattern as Dataverse. | `src/modules/dsw/__tests__/mock.ts` |
| PyRAT | `pyrat.server.config` (JSON **object** keyed by alias — `{"mock":{"url":"http://localhost:9099","token":"..."}}`, NOT an array like Galaxy's) | `src/modules/pyrat/__tests__/mock.ts` |
| OMERO | `omero.api.url` — unlike Dataverse/DSW's per-connection field, OMERO's base URL is a static JVM property, so the mock server stands in for the *whole* third-party OMERO JSON API (version/urls/servers/token/login/projects/screens), not just one endpoint. | `src/modules/omero/__tests__/mock.ts` |
| DataCite IGSN | configured on demand by the `flowIgsnConfig` fixture through the sysadmin API | `src/__tests__/e2e/mocks/datacite.ts` |

## CI setup

The `e2e-mocked.yml` workflow calls the reusable `e2e.yml` with `mode: mock`.
The e2e job passes `-Dpubchem.base.url=http://localhost:9099 -Dfieldmark.api.url=http://localhost:9099 -Dzenodo.url=http://localhost:9099 -Dgalaxy.server.config=[{"alias":"mock","url":"http://localhost:9099"}] -Dpyrat.server.config={"mock":{"url":"http://localhost:9099","token":"mock-server-token"}} -Domero.api.url=http://localhost:9099`
(and any future integration URL overrides) to `jetty:run-war`. Playwright starts
the mock server via its `webServer` block before the test run begins.

A separate `e2e-real.yml` runs nightly with `mode: real` to catch API drift.

## Real-mode integration credentials (`@apps` specs)

The `fieldmark`, `zenodo`, and `galaxy` specs under `src/modules/` run against
the mock server by default (`E2E_INTEGRATION_MODE=mock`, the PR-gating default
above — no extra config needed). Setting `E2E_INTEGRATION_MODE=real` points
the backend at the live third-party APIs instead, which requires real
credentials in `.env`:

| Variable | Used by | Where to get it |
|---|---|---|
| `FIELDMARK_API_KEY` | Fieldmark real-mode spec | `dashboard.fieldmark.app` → Profile → **Long-Lived API Tokens** → generate new token |
| `ZENODO_API_KEY` | Zenodo real-mode spec | `zenodo.org` → Applications → Personal access tokens (`deposit:write` + `deposit:actions` scopes) |
| `GALAXY_EU_APIKEY` | Galaxy real-mode spec | `usegalaxy.eu` → user menu → Preferences → Manage API key |
| `DATAVERSE_API_TOKEN` | Dataverse real-mode spec | Target Dataverse server → account settings → API Token |
| `DATAVERSE_SERVER_URL` | Dataverse real-mode spec | e.g. `https://demo.dataverse.org` |
| `DATAVERSE_NAME` | Dataverse real-mode spec | Alias of an **existing** collection on that server — an arbitrary/made-up alias 404s (see below) |
| `DSW_API_KEY` | DSW real-mode spec | Target DSW/FAIR Wizard instance → account settings → API Token |
| `DSW_SERVER_URL` | DSW real-mode spec | The instance's base URL, e.g. `https://your-dsw-instance.example` |
| `PYRAT_API_KEY` | PyRAT real-mode spec (per-user key) | Target PyRAT instance → Apps page → PyRAT → Add server → generate a user API key |
| `OMERO_USERNAME` | OMERO real-mode spec | An OMERO account username (e.g. via `demo.openmicroscopy.org` self-service signup) |
| `OMERO_PASSWORD` | OMERO real-mode spec | That account's password |
| `IGSN_ACCOUNT_ID` | IGSN real-mode specs | DataCite Repository Account ID |
| `IGSN_PASSWORD` | IGSN real-mode specs | DataCite Repository Account password |
| `IGSN_REPO_PREFIX` | IGSN real-mode specs | Prefix assigned to that DataCite repository account |
| `IGSN_SERVER_URL` | IGSN real-mode specs | DataCite API URL; defaults to `https://api.test.datacite.org` |

Missing a key skips with an actionable message for most specs.

**Fieldmark tokens expire** (observed: about two weeks from generation — check
Fieldmark's own docs for their exact policy, it isn't published anywhere we
control). When one expires, the real-mode spec fails with a generic "Could not
get notebooks from Fieldmark" toast — the RSpace backend swallows the real
401/expiry detail, so don't waste time chasing it in RSpace logs. To refresh:

1. Go to `dashboard.fieldmark.app` → Profile → **Long-Lived API Tokens** →
   generate a new token. Copy it via the page's copy button, not manual
   selection — it's only shown once, and manual selection has silently
   dropped characters before.
2. Update `FIELDMARK_API_KEY` in your local `.env`.
3. Update the `FIELDMARK_API_KEY` secret in the GitHub repo settings — the
   nightly `e2e-real.yml` workflow reads it from there, not from `.env`.

**`DATAVERSE_NAME` must be an alias that already exists** on
`DATAVERSE_SERVER_URL` — Dataverse's test-connection endpoint
(`GET /api/v1/dataverses/{alias}`) 404s on an unknown/made-up alias rather
than creating one. It is not a free-form label like a document title.

## Running locally

### Docker dev stack (recommended)

```bash
./docker/dev/rspace-dev up --e2e
./docker/dev/rspace-dev ps

# Use the app and mock ports printed above.
RSPACE_BASE_URL=http://localhost:<app-port> \
E2E_MOCK_PORT=<mock-port> \
E2E_INTEGRATION_MODE=mock \
pnpm run test-e2e
```

Playwright reuses the already-running mock server. DataCite URLs configured by
the tests also use `localhost:<mock-port>`, which is reachable from the backend
because the mock and backend share a Docker network namespace.

### Manual Maven/Jetty

```bash
export E2E_MOCK_PORT="${E2E_MOCK_PORT:-9099}"

# 1. Build WAR with React bundles (once)
./mvnw clean package -DgenerateReactDist -DskipTests=true -Dtimestamp=e2e-local

# 2. Start RSpace pointing at the mock server
./mvnw jetty:run-war \
  -DskipTests=true -Dmaven.war.skip=true -Dtimestamp=e2e-local \
  -Denvironment=drop-recreate-db -Dspring.profiles.active=run \
  -DreactDevMode=false \
  -Djdbc.url=jdbc:mysql://localhost:3306/rspace -Djdbc.db.maven=rspace \
  -DRS_FILE_BASE=/tmp/e2e-filestore \
  -Dpubchem.base.url="http://localhost:${E2E_MOCK_PORT}" \
  -Dfieldmark.api.url="http://localhost:${E2E_MOCK_PORT}" \
  -Dzenodo.url="http://localhost:${E2E_MOCK_PORT}" \
  -Dgalaxy.server.config="[{\"alias\":\"mock\",\"url\":\"http://localhost:${E2E_MOCK_PORT}\"}]" \
  -Dpyrat.server.config="{\"mock\":{\"url\":\"http://localhost:${E2E_MOCK_PORT}\",\"token\":\"mock-server-token\"}}" \
  -Domero.api.url="http://localhost:${E2E_MOCK_PORT}"

# 3. Run e2e tests (mock server starts automatically via webServer)
RSPACE_BASE_URL=http://localhost:8080 \
E2E_INTEGRATION_MODE=mock \
pnpm run test-e2e
```

> `/tmp/e2e-filestore` must be owned by your user — create it with
> `mkdir -p /tmp/e2e-filestore` (not `sudo`).
