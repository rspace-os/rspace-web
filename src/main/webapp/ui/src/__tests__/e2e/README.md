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
| `E2E_BROWSER` | _(all)_ | Select `chromium`, `firefox`, `webkit`, `mobile`, or `api`. Cloud mode defaults to the three desktop browsers. |
| `E2E_CLOUD` | `false` | Set `true` to select community UI specs against RSpace running with `deployment.cloud=true`. |
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

# Single browser
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

Use the app URL printed by `./docker/dev/rspace-dev ps` as `RSPACE_BASE_URL`.
The default is `http://localhost:8080`; other worktrees may use another port.

## Running the community (cloud) specs

`specs/cloud/` covers community-only flows (email-verified signup, cloud group
creation and invitations, directory search, confirmed email change). They need
a server started with `-Ddeployment.cloud=true`, which is fixed at startup,
and Mailpit. `E2E_CLOUD=true` selects these specs in the same `chromium`,
`firefox`, and `webkit` projects used for standard RSpace. Without it, the
runner excludes community specs. This flag selects tests; it does not change
the deployment property on an already running server.

On the Docker dev stack, add `deployment.cloud=true` to the worktree's
gitignored `src/main/resources/deployments/dev/deployment.properties`, then:

```bash
./docker/dev/rspace-dev up --mailpit
```

If the app was already running, run `./docker/dev/rspace-dev restart` to apply
the changed deployment property. Wait for Jetty to finish starting, then use
the app and Mailpit ports printed by `./docker/dev/rspace-dev ps`:

```bash
E2E_CLOUD=true RSPACE_BASE_URL=http://localhost:<app port> \
  MAILPIT_HTTP_URL=http://localhost:<mailpit port> pnpm run test-e2e --reporter=list
```

`E2E_CLOUD=true` runs all three desktop browsers. Add `E2E_BROWSER=chromium`,
`firefox`, or `webkit` to select one. `mobile` is rejected in cloud mode because
no community spec is tagged `@mobile`, and `E2E_CLOUD` accepts only `true` or
`false`.
Unknown `E2E_BROWSER` values fail before authentication or test execution.
Check selection without starting browsers or contacting RSpace with
`E2E_CLOUD=true pnpm run test-e2e --list --reporter=list`.

`projects.ts` defines browser engines, seed users, and project selection for
both the Playwright config and authentication setup. Add or change browser
projects there so their login state stays consistent with the runner.

CI varies the `cloud` deployment property independently of the browser in
`.github/workflows/e2e.yml`. Community jobs run unsharded in both mock and real
integration modes, each with its own server. The same `E2E_CLOUD` value sets
the server property and selects the test suite.

Accounts created through the signup form and groups created through the UI are
left behind; only `clientSysadmin.createUser` accounts are disabled at teardown.
Names are unique, so leftovers don't affect later runs; reset the database to
clear them.

## File naming — wrong suffix = test silently never runs

| Suffix | Project | Description |
|---|---|---|
| `*.e2e.ts` | chromium, firefox, webkit (and mobile if tagged `@mobile`) | UI browser spec |
| `specs/cloud/**/*.e2e.ts` | chromium, firefox, webkit with `E2E_CLOUD=true` | Community UI spec |
| `*.api.spec.ts` | api | Node HTTP spec (no browser) |

## Directory layout

```
src/__tests__/e2e/
  specs/               # Test files (*.e2e.ts, *.api.spec.ts)
    cloud/             # Community UI specs selected by E2E_CLOUD=true
  pageObjects/         # One class per screen, grouped by feature
    BasePage.ts        # Abstract base — not feature-specific, stays at the root
    document/ notebook/ workspace/ inventory/ auth/ system/ apps/ gallery/ myrspace/ groups/
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
