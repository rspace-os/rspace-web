# AGENTS.md

Playwright TypeScript test suite for RSpace: UI E2E tests (real browser) and
API tests (HTTP against `/api/v1/`). `CLAUDE.md` in this directory is a copy.

## What's where

- `specs/` — test files. Check here before writing a new one; a fixture or page
  object you need may already exist.
- `pageObjects/` — one class per screen. Navigation + user-level actions only.
- `components/` — reusable UI fragments (nav bars, dialogs, toolbars) composed
  into page objects as fields.
- `api/clients/` — one class per API resource (e.g. `DocumentsClient.ts`).
- `api/models/` — TypeScript types for request/response bodies.
- `fixtures.ts` — the only thing specs should import from. Exports `test`,
  `expect`, and re-exports `tags` from `tags.ts`.
- `env.ts` — all config/env reading goes through here, nowhere else.
- `users.ts` — seed user map. Browser projects pick distinct users to avoid
  parallel-run state collisions.
- `playwright-e2e.config.ts` — one directory up (`src/main/webapp/ui/`).

Colocated feature specs live beside the feature:
`src/modules/<feature>/__tests__/<feature>.e2e.ts`
They import page objects from `@/__tests__/e2e/pageObjects/`.

## Running tests — avoid the hang

Local (non-CI) reporter includes `html({open: 'on-failure'})`. On failure it
serves the HTML report and **blocks the process** until killed. When running
from an agent or script, override:

```bash
pnpm run test-e2e -- --reporter=list
```

Default target: `http://localhost:8080` (local dev stack). Override:

```bash
RSPACE_BASE_URL=https://pangolin8086.researchspace.com pnpm run test-e2e
```

## Test scripts

| Script | Purpose |
|---|---|
| `pnpm test-e2e` | Run all e2e tests (browser + API) |
| `pnpm test-e2e:ui` | Playwright UI mode, defaults to chromium browser tests |
| `pnpm test-e2e:api` | API specs only (Node HTTP, no browser) |
| `pnpm test-e2e:debug` | **Flaky test debugging only.** Enables `PW_LOG=trace`: full trace capture, video on every test, screenshots on every test, `DEBUG=pw:api`. Do not use in CI or routine runs — output is large. |

## Must: file naming

File suffix drives `testMatch` in the config — wrong suffix = silently never run.

- UI browser spec: `*.e2e.ts`
- API (Node HTTP) spec: `*.api.spec.ts`

## Must: fixture naming

| Prefix | Lives in | Example |
|---|---|---|
| `page` | `pageObjects/` | `pageLogin`, `pageWorkspace` |
| `component` | `components/` | (none yet) |
| `client` | `api/clients/` | `clientDocuments` |
| `flow` | multi-step setup returning a result | `flowLogin` |

Use `flowLogin` when login is a precondition. Use `pageLogin`/`pageWorkspace`
directly when login is the behaviour under test.

## Must: spec structure

```ts
test.describe(`Feature ${tags.SMOKE}`, () => {
  test("As a <role>, I can <capability>", async ({ ...fixtures }) => {
    await test.step("Given <precondition>", async () => { ... });
    await test.step("When <action>",        async () => { ... });
    await test.step("Then <assertion>",     async () => { ... });
  });
});
```

Filter runs: `--grep @smoke` / `--grep-invert @smoke`.

## Must: locator priority (semantic first)

1. `getByRole('button', { name: 'Log in' })` — ARIA role + accessible name
2. `getByLabel('Username')` — form label
3. `getByText('Some visible text')` — stable visible copy
4. `getByTestId('create-btn')` — `data-test-id` attribute (RSpace's convention;
   configured via `testIdAttribute` in the playwright config)
5. `locator('#id')` / `.css-class` — **banned**

Semantic locators fail when markup loses its role or label — exactly the
feedback that pushes accessible markup.

## Pitfall: don't grow god-class page objects

A page object gets navigation + user-level actions for its own screen only.
The moment you're adding a 4th/5th unrelated thing, extract a `components/`
class and compose it as a field. No inheritance between page objects.

## Pitfall: don't guess selectors or endpoints

- UI: legacy screens are rendered by jQuery, not visible in static JSP. Capture
  real locators against a running instance (Playwright MCP browser tools or
  devtools).
- API: verify endpoint, method, and fields against the actual Spring controller
  in `src/main/java/com/researchspace/api/v1/` before writing a client method.
