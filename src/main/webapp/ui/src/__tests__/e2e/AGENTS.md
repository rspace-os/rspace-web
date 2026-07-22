# AGENTS.md

Playwright TypeScript test suite for RSpace: UI E2E tests (real browser) and
API tests (HTTP against `/api/v1/`). `CLAUDE.md` in this directory symlinks here.

## What's where

Shared infrastructure lives here:
- `specs/` — test files for cross-cutting concerns such as auth. Check here before writing a new one.
- `pageObjects/` — page objects grouped by feature subfolder (`document/`,
  `notebook/`, `workspace/`, `inventory/`, `auth/`, `system/`, `apps/`);
  `BasePage.ts` stays at the root — it's the abstract base, not feature-specific.
- `components/` — UI fragments composed into page objects, grouped the same
  way (`document/`, `notebook/`, `workspace/`, `navigation/`), plus
  `shared/` for pieces used across features (`AppHeader`, `ToolbarCreateMenu`,
  `ToolbarCommonActions`).
- `api/clients/` — one class per API resource (e.g. `DocumentsClient.ts`).
- `api/models/` — TypeScript types for request/response bodies.
- `fixtures/` — typed fixture layers. Import `test` from `fixtures/flows`, or
  `dynamicUserTest` from `fixtures/dynamicUser`; import `expect` from
  `@playwright/test` and tags from `tags.ts`.
- `env.ts` — all config/env reading goes through here, nowhere else.
- `users.ts` — seed user map. Browser projects pick distinct users to avoid parallel-run state collisions.
- `tags.ts` — native Playwright tag constants for `test.describe`/`test` metadata.
- `env.ts` — environment and mock/real mode configuration.
- `authState.ts` — storageState path helper shared by `auth.setup.ts` and any `browser.newContext()` call.
- `viewports.ts` — named device-emulation presets for responsive specs (see the mobile pitfall below).
- `playwright-e2e.config.ts` — one directory up (`src/main/webapp/ui/`).

Feature-specific e2e tests live beside the feature they test:
```
src/modules/<feature>/
  __tests__/
    <feature>.e2e.ts          # spec
    pageObjects/              # page objects / components only this feature uses
    mock.ts                   # MSW handlers for the e2e mock server
    fixtures/                 # larger or binary API responses (JSON, CSV, ZIP, …)
```

Examples: `src/modules/pubchem/__tests__/`, `src/modules/fieldmark/__tests__/`.

## Running tests — avoid the hang

Local (non-CI) reporter includes `html({open: 'on-failure'})`. On failure it
serves the HTML report and **blocks the process** until killed. When running
from an agent or script, override:

```bash
pnpm run test-e2e --reporter=list
```

Default target: `http://localhost:8080` (local dev stack). Override:

```bash
RSPACE_BASE_URL=https://pangolin8086.researchspace.com pnpm run test-e2e
```

Tests that mutate instance-global state (`flowIgsnConfig`, `dynamicUserTest`,
anything creating sysadmin-owned accounts) refuse to run against a non-local
`RSPACE_BASE_URL` — those mutations have no teardown/restore step and would
corrupt a shared instance. Set `E2E_ALLOW_GLOBAL_MUTATIONS=true` only if you
deliberately intend to mutate the target instance's global config.

When RSpace runs inside Docker but Playwright runs on the host, set
`E2E_MOCK_BACKEND_URL` to a host URL that the backend container can reach.
`localhost` inside the backend container is the container itself. For example:

```bash
RSPACE_BASE_URL=http://localhost:8263 \
E2E_MOCK_BACKEND_URL=http://<host-lan-ip>:9099 pnpm run test-e2e
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
| `component` | `components/` | `componentToasts`, `componentNotifications` |
| `client` | `api/clients/` | `clientDocuments` |

Authentication is provided by each project's `storageState`. Use
`pageLogin`/`pageWorkspace` directly only when login is the behaviour under
test or when a deliberately unauthenticated context is required.

## Must: import style — relative within a folder, `@/` across feature folders

`pageObjects/` and `components/` are grouped by feature subfolder. Use a
relative import (`./Sibling`, `../BasePage`) within the same folder or one
level up. Use the `@/__tests__/e2e/...` absolute alias for anything that
crosses feature folders — e.g. `pageObjects/notebook/NotebookPage.ts`
importing `components/document/DocumentHeader.ts`:

```ts
// Bad — breaks/needs renumbering every time either file moves a level
import { DocumentHeader } from "../../components/document/DocumentHeader";

// Good
import { DocumentHeader } from "@/__tests__/e2e/components/document/DocumentHeader";
```

Never write `../../` (or deeper) — if an import needs two or more `../`, it
should be the alias instead. The alias needs no extra config: `@/` → `src/`
is already defined in `tsconfig.json` and Playwright's test runner already
resolves it (proven in use by specs outside this tree, e.g.
`modules/pubchem/__tests__/pubchem.e2e.ts`).

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

**Exception:** scenarios that deliberately exercise multiple users may call
`browser.newContext()` to create an isolated context. Those operate on a custom
`page` that fixtures cannot target, so manual instantiation is correct and
intentional. Always pass the `browserContextOptions` fixture to
`browser.newContext()` — it carries the WebKit TLS workaround that the
project-level config sets but that manual contexts do not inherit automatically:

```ts
test.beforeAll(async ({ browser, browserContextOptions, appUser }) => {
  const ctx = await browser.newContext(browserContextOptions);
  // ...
});
```

If a page object fixture is missing from `fixtures/ui.ts`, add it there rather than
instantiating the class in the spec.

## Must: spec structure

```ts
test.describe("Feature", { tag: tags.INVENTORY }, () => {
  test("As a <role>, I can <capability>", async ({ ...fixtures }) => {
    // Arrange, act, and assert directly.
  });
});
```

Use `test.step` only when a substantial phase benefits from its own report
entry, especially when the step returns setup data. Do not wrap every simple
action or assertion. Filter tagged runs with `--grep @apps`,
`--grep @inventory`, or `--grep @mobile`.

## Must: locator priority (semantic first)

1. `getByRole('button', { name: 'Log in' })` — ARIA role + accessible name
2. `getByLabel('Username')` — form label
3. `getByText('Some visible text')` — stable visible copy
4. `locator('#id')` — allowed only for verified legacy JSP-rendered IDs
   (e.g. `#recordNameInHeader`, `#editTags`). Never for React components.
5. `locator('.css-class')` — last resort for legacy jQuery/Mustache pages
   with no stable ID and no ARIA role. Document why at the call site.

Semantic locators fail when markup loses its role or label — exactly the
feedback that pushes accessible markup.

Start with the real accessible name. If the same semantic control has proven
copy variants, use a partial match or an explicit regex covering those variants.

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

## Must: an action method doesn't return until its effect is observable

Any method that triggers navigation, a search, or a data-dependent
re-render must not resolve just because the triggering click was dispatched
— it must wait until the *result* of that click is actually visible/queryable.
Returning early pushes the wait onto whatever the caller does next, which
usually isn't the same locator Playwright is already auto-retrying, so the
gap goes uncaught until a specific interleaving exposes it.

## Pitfall: don't guess selectors or endpoints

- UI: legacy screens are rendered by jQuery, not visible in static JSP. Capture
  real locators against a running instance rather than guessing from markup alone.
- API: verify endpoint, method, and fields against the actual backend controller in  in `src/main/java/com/researchspace/api/v1/` before writing a client method.
- Cross-component sharing: verify a locator's role and accessible name resolve
  uniquely in every context before sharing it. Matching copy alone does not
  prove that two controls are the same component.

## Pitfall: parallel tests sharing a seed user race on account-level state

The suite uses one seed user per project and global integration settings, so it
runs with one worker. Do not increase `workers` without isolating accounts and
integration configuration.

Prefer isolated data or `dynamicUserTest`. Use `test.describe.serial` only when
the behaviour under test is an intentionally ordered sequence that shares
persistent state, and document that dependency next to the declaration.

`.serial` only protects one file against itself. For shared monotonic state,
assert growth rather than an exact value and prove the test's own effect through
unique content, such as its UUID-based document name.

For state that changes what renders, such as Inventory's List/Card/Tree mode
or right-panel visibility, there is no "assert growth instead" equivalent.
Use `dynamicUserTest` so every test gets a fresh account in every project:

```ts
import { dynamicUserTest as test } from "@/__tests__/e2e/fixtures/dynamicUser";
```

The fixture creates and authenticates one account per test, so it isolates
both parallel files and parallel browser projects. It is deliberately more
expensive than the normal seeded-user fixture; reserve it for tests that
mutate persistent account-level state which affects later rendering.

## Pitfall: `page.setViewportSize()` isn't mobile emulation

It only resizes the viewport — it can't set user agent, touch support,
`isMobile`, or device pixel ratio, all of which real mobile browsers set and
which some responsive code paths key off. For genuine mobile emulation use the
shared device preset with `test.use`:

```ts
test.describe("mobile", () => {
  test.use(MOBILE_DEVICE);
  // tests tagged with tags.MOBILE
});
```

See `viewports.ts` and `gallery.e2e.ts`'s mobile test for a worked example.
