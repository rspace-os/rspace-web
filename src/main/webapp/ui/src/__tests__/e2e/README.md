# RSpace E2E Tests

Playwright TypeScript framework. UI browser tests (`*.e2e.ts`) and REST API
tests (`*.api.spec.ts`) that run against a real RSpace instance.

## Prerequisites

- Node 24 + pnpm (`corepack enable && pnpm install --frozen-lockfile`)
- Playwright browsers: `pnpm exec playwright install --with-deps`
- A running RSpace instance (local or remote)

## Configuration

All variables have seed-data defaults — no `.env` file is needed for a standard
local stack. Copy the template only if you need to override for a non-seed
environment (pangolin, cloud staging):

```bash
cp src/main/webapp/ui/.env.example src/main/webapp/ui/.env
```

| Variable | Default | Description |
|---|---|---|
| `RSPACE_BASE_URL` | `http://localhost:8080` | Target instance URL |
| `HEADLESS` | `true` | Set `false` to watch browsers |
| `E2E_BROWSER` | _(all)_ | Limit to one project: `chromium`, `firefox`, `webkit`, or `api` |
| `RSPACE_SYSADMIN_USERNAME` | `sysadmin1` | Sysadmin username |
| `RSPACE_SYSADMIN_PASSWORD` | `sysWisc23!` | Sysadmin password |
| `RSPACE_SYSADMIN_API_KEY` | `abcdefghijklmnop12` | Sysadmin API key |

All three sysadmin variables default to the devtest seed values. Override them
when targeting an environment where the sysadmin credentials differ.

Real-mode credentials for the `@apps` specs (`FIELDMARK_API_KEY`,
`ZENODO_API_KEY`, `GALAXY_EU_APIKEY`) are documented in
`docs/e2e-mocking.md`, alongside the mock/real mode switch they pair with.

## Running locally

```bash
# All browsers + API
pnpm run test-e2e

# Single browser (use E2E_BROWSER — --project flag does not work through pnpm)
E2E_BROWSER=chromium pnpm run test-e2e

# API tests only
pnpm run test-e2e:api

# Watch mode (headed)
pnpm run test-e2e:ui

# Tag filter
pnpm run test-e2e --grep @apps
```

> **Reporter hang:** local runs open an HTML report on failure and block the
> process. Kill with Ctrl-C or override: `pnpm run test-e2e --reporter=list`

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
  pageObjects/         # One class per screen, grouped by feature
    BasePage.ts        # Abstract base — not feature-specific, stays at the root
    document/ notebook/ workspace/ inventory/ auth/ system/ apps/
  components/          # Reusable UI fragments composed into page objects, same grouping
    document/ notebook/ workspace/ navigation/ shared/
  api/
    clients/           # One class per API resource
    models/            # TypeScript types for request/response bodies
  fixtures/            # Typed UI, API, flow, and dynamic-user fixture layers
  env.ts               # Environment and mock | real mode configuration
  users.ts             # Seed user map (user1a–user10 + SYSADMIN, password: user1234)
  tags.ts              # @apps and @inventory tag constants
```

Cross-feature-folder imports (e.g. `pageObjects/notebook/` importing from
`components/document/`) use the `@/__tests__/e2e/...` absolute alias, not
`../../` — see `AGENTS.md`.

Locator and page-object conventions live in `AGENTS.md`.

## Cross-browser notes

- **WebKit** — Playwright's bundled WebKit has an older TLS stack. If the
  target host uses certain cipher suites, navigation fails with
  `WebKit encountered an internal error`. The webkit project sets
  `ignoreHTTPSErrors: true` to bypass this. Does not apply to chromium/firefox.

  When a `beforeAll` creates a secondary context via `browser.newContext()`,
  the project-level option does **not** carry over automatically. Use the
  `browserContextOptions` fixture instead of a hand-written condition:

  ```ts
  test.beforeAll(async ({ browser, browserContextOptions, appUser }) => {
    const ctx = await browser.newContext(browserContextOptions);
    // ...
  });
  ```

## Parallel isolation

Each project uses a distinct seed user so parallel shards do not collide:

| Project | User | Roles |
|---|---|---|
| chromium | user1a | PI + USER |
| firefox | user3c | PI + USER |
| webkit | user4d | PI + USER |
| mobile | user7g | PI + USER |
| api | user2b | USER |

All browser projects use PI+USER accounts. See `users.ts` for the full seed
map and `AGENTS.md` for isolation and multi-user rules. A seed user's workspace
root is created by its first UI login, so authenticate any new workspace actor
in `auth.setup.ts` before using its API key.
