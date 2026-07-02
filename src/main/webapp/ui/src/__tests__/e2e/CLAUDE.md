# CLAUDE.md

Playwright TypeScript test suite for RSpace: UI E2E tests (real browser) and
API tests (HTTP against `/api/v1/`). `AGENTS.md` in this directory is a copy.

## What's where

Shared infrastructure lives here:
- `specs/` — test files for cross-cutting concerns (auth, smoke). Check here before writing a new one.
- `pageObjects/` — shared page objects used across multiple features (LoginPage, WorkspacePage, InventoryPage, etc.).
- `components/` — shared UI fragments composed into shared page objects.
- `api/clients/` — one class per API resource (e.g. `DocumentsClient.ts`).
- `api/models/` — TypeScript types for request/response bodies.
- `fixtures.ts` — the only thing specs should import from. Exports `test`, `expect`, and re-exports `tags`.
- `env.ts` — all config/env reading goes through here, nowhere else.
- `users.ts` — seed user map. Browser projects pick distinct users to avoid parallel-run state collisions.
- `playwright-e2e.config.ts` — one directory up (`src/main/webapp/ui/`).

Feature-specific e2e tests live beside the feature they test:
```
src/modules/<feature>/
  __tests__/
    <feature>.e2e.ts          # spec
    pageObjects/              # page objects / components only this feature uses
    <feature>Mock/
      handlers.mjs            # MSW handlers for the e2e mock server
      fixtures/               # harvested API responses (JSON, CSV, ZIP, …)
```

Examples: `src/modules/pubchem/__tests__/`, `src/modules/fieldmark/__tests__/`.

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

## Must: use fixtures, never instantiate page objects directly in tests

Specs must receive page objects via fixtures — never `new PageClass(page)` inside
a test body. The fixture wires the Playwright `page` to the page object; doing it
manually duplicates that wiring and bypasses any setup the fixture performs.

```ts
// Bad
test("...", async ({ page }) => {
  const inventory = new InventoryPage(page); // don't do this
});

// Good
test("...", async ({ pageInventory }) => {
  // pageInventory is already wired to the right page
});
```

**Exception:** `beforeAll` hooks that call `browser.newContext()` to create an
isolated context (e.g. sysadmin setup, per-user app enablement). Those operate
on a custom `page` that fixtures cannot target, so manual instantiation is
correct and intentional. Always pass the `browserContextOptions` fixture to
`browser.newContext()` — it carries the WebKit TLS workaround that the
project-level config sets but that manual contexts do not inherit automatically:

```ts
test.beforeAll(async ({ browser, browserContextOptions, appUser }) => {
  const ctx = await browser.newContext(browserContextOptions);
  // ...
});
```

If a page object fixture is missing from `fixtures.ts`, add it there rather than
instantiating the class in the spec.

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
5. `locator('#id')` — allowed only for verified legacy JSP-rendered IDs
   (e.g. `#recordNameInHeader`, `#editTags`). Never for React components.
6. `locator('.css-class')` — last resort for legacy jQuery/Mustache pages
   with no stable ID and no ARIA role. Document why at the call site.

Semantic locators fail when markup loses its role or label — exactly the
feedback that pushes accessible markup.

## Pitfall: no XPath

**Never use `locator("xpath=...")`** in page objects or specs.

When you need a sibling or parent relationship, derive the stable DOM id from
an attribute instead:

```ts
// Bad — XPath breaks silently under markup refactors
.locator("xpath=following-sibling::tr[1]")

// Good — async getAttribute to get the id, then target it directly
const tdId = await fieldTd.getAttribute("id");
const fieldId = (tdId ?? "").replace("field-name-", "");
return this.page.locator(`#div_rtf_${fieldId}`);
```

XPath expressions are opaque to Playwright's retry mechanism and fail silently
when the surrounding markup is refactored.

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
