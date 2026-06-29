# RSpace E2E Tests

Playwright TypeScript framework. UI browser tests (`*.e2e.ts`) and REST API
tests (`*.api.spec.ts`) that run against a real RSpace instance.

## Prerequisites

- Node 24 + pnpm (`corepack enable && pnpm install --frozen-lockfile`)
- Playwright browsers: `pnpm exec playwright install --with-deps`
- A running RSpace instance (local or remote)

## Configuration

Copy the template and fill in values:

```bash
cp src/main/webapp/ui/.env.example src/main/webapp/ui/.env
```

| Variable | Default | Required | Description |
|---|---|---|---|
| `RSPACE_BASE_URL` | `http://localhost:8080` | No | Target instance URL |
| `HEADLESS` | `true` | No | Set `false` to watch browsers |
| `RSPACE_SYSADMIN_USERNAME` | `sysadmin1` | No | Sysadmin username |
| `RSPACE_SYSADMIN_PASSWORD` | — | **Yes** | Sysadmin password |
| `RSPACE_TEST_API_KEY` | — | **Yes** | API key for API tests |
| `RSPACE_TEST_USERNAME` | `user1a` | No | Override test user (UI tests use `appUser`) |
| `RSPACE_TEST_PASSWORD` | `user1234` | No | Override test user password |

UI tests use seed users (`user1a`–`user3c`) with password `user1234` via the
`appUser` fixture — no env vars needed for them on a fresh local stack.

## Running locally

```bash
# All browsers + API
pnpm run test-e2e

# Single browser
pnpm run test-e2e -- --project=chromium

# API tests only
pnpm run test-e2e:api

# Watch mode (headed)
pnpm run test-e2e:ui

# Specific file
pnpm run test-e2e -- src/__tests__/e2e/specs/auth/login.e2e.ts

# Tag filter
pnpm run test-e2e -- --grep @smoke
pnpm run test-e2e -- --grep @apps
```

> **Reporter hang:** local runs open an HTML report on failure and block the
> process. Kill with Ctrl-C or override: `pnpm run test-e2e -- --reporter=list`

## Running against pangolin

```bash
RSPACE_BASE_URL=https://pangolin<xx>.com pnpm run test-e2e
```

Credentials for pangolin go in `.env` — never commit that file.

## Running against local Docker stack

Boot the dev stack (see `docker/dev/README.md`):

```bash
./docker/dev/rspace-dev up
```

Then run tests with the default `RSPACE_BASE_URL=http://localhost:8080`. No
extra config needed — the Docker stack listens on 8080 by default.

## File naming — wrong suffix = test silently never runs

| Suffix | Project | Description |
|---|---|---|
| `*.e2e.ts` | chromium, firefox, webkit | UI browser spec |
| `*.api.spec.ts` | api | Node HTTP spec (no browser) |

## Directory layout

```
src/__tests__/e2e/
  specs/               # Test files (*.e2e.ts, *.api.spec.ts)
  pageObjects/         # One class per screen
  components/          # Reusable UI fragments composed into page objects
  api/
    clients/           # One class per API resource
    models/            # TypeScript types for request/response bodies
  fixtures.ts          # Import test + expect from here — not from @playwright/test
  env.ts               # All env/config reading — nowhere else
  users.ts             # Seed user map (user1a–user8h, password: user1234)
  tags.ts              # @smoke, @apps tag constants
  integrationMode.ts   # mock | real switch (used by @apps specs)
```

## Locator priority (semantic first)

1. `getByRole('button', { name: 'Save' })` — ARIA role + accessible name
2. `getByLabel('Username')` — form label
3. `getByText('Some copy')` — stable visible text
4. `getByTestId('create-btn')` — `data-test-id` attribute (RSpace convention)
5. `locator('#id')` / `.css-class` — **banned**

RSpace uses `data-test-id` (not `data-testid`). The config sets
`testIdAttribute: "data-test-id"` so `getByTestId()` maps correctly.

## Cross-browser notes

- **WebKit** — Playwright's bundled WebKit has an older TLS stack. If the
  target host uses certain cipher suites, navigation fails with
  `WebKit encountered an internal error`. The webkit project sets
  `ignoreHTTPSErrors: true` to bypass this. Does not apply to chromium/firefox.

## Parallel isolation

Each browser project uses a distinct seed user so shards don't collide on
shared backend state:

| Project | User |
|---|---|
| chromium | user1a |
| firefox | user2b |
| webkit | user3c |

Override per-test via the `appUser` fixture option.
