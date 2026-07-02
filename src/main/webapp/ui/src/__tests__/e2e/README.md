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
  users.ts             # Seed user map (user1a–user10 + SYSADMIN, password: user1234)
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

Each project uses a distinct seed user so parallel shards don't collide on
shared backend state:

| Project | User | Roles |
|---|---|---|
| chromium | user1a | PI + USER |
| firefox | user3c | PI + USER |
| webkit | user4d | PI + USER |
| api | user2b | USER |

All browser projects use PI+USER accounts so PI-gated actions behave
consistently across browsers. USER-only accounts (`user6f`, `user8h`) are
reserved for tests that explicitly verify non-PI behaviour.

See `users.ts` for the full seed user map (`user1a`–`user10`).

### Before picking a seed user for a new test

A seed user's workspace root folder is created lazily on their **first UI
login** — never by account creation or by an API-key request. An account
that has never logged in has no root folder yet, and any workspace-touching
call (e.g. `POST /api/v1/documents`) 500s with a `Folder.addChild` NPE. This
resets on every `drop-recreate-db`, so it can resurface even for an account
that worked before.

| User | Role | Group / PI-of | Status |
|---|---|---|---|
| `user1a` | PI + USER | **PI** of a lab group | Used by `chromium` — don't reuse as a second actor in the same run |
| `user2b` | USER | member of `user1a`'s group | Used by `api` — already has a root folder, safe to reuse for a second HTTP-only actor |
| `user3c` | PI + USER | **PI** of a lab group | Used by `firefox` — don't reuse as a second actor in the same run |
| `user4d` | PI + USER | member of `user3c`'s group (not its PI) | Used by `webkit` — don't reuse as a second actor in the same run |
| `sysadmin1` | SYSADMIN | — | Used by `flowSysadminConfig` |
| `user7g` | PI + USER | none — holds the PI role but isn't PI of any group | Free, but has never logged in; also can't do group-PI actions without first creating a group for it |
| `user6f`, `user8h`, `user9i`, `user10` | USER | none | Free, but have never logged in |

If a new project or multi-actor test needs a fresh seed user, prefer one
already marked "free" above — but if it's never logged in, add it to
`auth.setup.ts`'s `accounts` list first so its root folder exists before any
spec runs, rather than discovering the NPE mid-test.
