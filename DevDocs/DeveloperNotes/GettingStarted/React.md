# Getting started as a React developer

RSpace uses [TypeScript](https://www.typescriptlang.org/) for type checking and
[Biome](https://biomejs.dev/) for frontend formatting and linting. Run frontend commands from the
repository root; the scripts enter `src/main/webapp/ui`.

| # | Check | Command or convention |
| --- | --- | --- |
| 1. | Type checking | Type new and modified code with TypeScript. Run `pnpm tsc` for the whole frontend. |
| 2. | Formatting and linting | Run `pnpm lint` to check and `pnpm lint:fix` to apply fixes. Biome covers JavaScript, TypeScript, JSX/TSX, JSON, and CSS, but does not format Markdown. |
| 3. | CI | `pnpm lint:ci` rejects Biome info, warning, and error diagnostics. Type checking runs separately. |
| 4. | Package types | Prefer the types shipped by a dependency. When it has none, add the corresponding `@types` package if available. |

## API authentication

The frontend calls two APIs, each with its own token path. Do not mix them.

### API v1

`useOauthToken` and `useOauthTokenQuery` get a UI token from `/userform/ajax/inventoryOauthToken` with the signed-in
browser session and cache it in session storage. The route refuses to mint a token while operating as another user or
when the account's login is disabled.

Send native `fetch` calls that carry this token through `fetchWithUiToken` from
`modules/common/utils/fetchWithUiToken`. Send Axios calls through `common/axios`, including `axios.create` instances.
Both recover a rejected token on same-origin `/api/` requests:

| # | Step | Behaviour |
| --- | --- | --- |
| 1. | Check | A 401 can also mean a refused permission, so `/api/v1/userDetails/whoami` first confirms that the token itself is invalid. A rejected `whoami` request skips this check. |
| 2. | Public documents | Public document views reload their URL to restore the anonymous session. |
| 3. | Replace | A replacement comes from the legacy route. It is checked against `whoami` before it is stored, so a disabled account cannot loop on reloads. A refused token request, a login page or a response without a token navigates to `/login`. Any other failure leaves the original error with the caller. |
| 4. | Retry | For the same user, a GET retries once. Later requests whose hook or query still holds the old token send the stored replacement instead, when both tokens belong to the same session and the stored one is newer. Writes never replay. |
| 5. | Identity change | A replacement for another identity reloads the tab to discard the previous user's caches and UI state. |

Concurrent failures share one recovery when they carry the same token.

### API v2

Call REST API v2 through the generated Orval operations, which share one transport (`apiV2Fetch` and its header and
binary variants). Protected operations send a bearer token from the page's `SessionTokenProvider`. Public operations
send one when the provider can supply it and otherwise read anonymously. Session operations, including token creation,
use the browser session. The provider mints session-bound tokens from `POST /api/v2/oauth/tokens` and keeps them in
memory. Starting or ending Operate As invalidates them. Do not use `fetchWithUiToken`, `common/axios` or
the v1 token hooks for v2 calls. See [REST API v2 frontend client](../RestApiV2FrontendClient.md) for recovery,
session handlers and anonymous public reads.

## React page fonts

`src/main/webapp/ui/src/fonts.ts` loads the pinned `@fontsource-variable/roboto`
weight axis in normal and italic and exports the shared font family. The shared
MUI theme imports this module, so every theme consumer loads the font, including
standalone pages and the Ketcher dialog. Entrypoints do not need font imports.
The variable package ships only WOFF2 files and names its family `Roboto Variable`,
with legacy `Roboto` and system fonts as fallbacks.

`StyledTinyMceEditor` also injects the processed Fontsource CSS into its iframe
through `content_style`, using the shared font family. Iframes cannot inherit
font-face definitions from the parent document.


## Editor setup

### VS Code

Install the Biome extension `biomejs.biome`. The workspace `.vscode/settings.json` selects Biome
as the formatter and enables fix-all and organize-imports on save. Manual formatting shortcuts are:

| # | Platform | Shortcut |
| --- | --- | --- |
| 1. | Windows | `Shift + Alt + F` |
| 2. | macOS | `Shift + Option + F` |
| 3. | Ubuntu | `Ctrl + Shift + I` |

The optional `eg2.vscode-npm-script` and `christian-kohler.npm-intellisense` extensions help with
package scripts and imports. VSCodium is an alternative editor without Microsoft's telemetry.

### WebStorm / IntelliJ

Use the built-in TypeScript inspections. Install the Biome plugin through
`File > Settings/Preferences > Plugins`, then enable "Run Biome on save" or use
"Reformat with Biome".

### Other editors

Connect the editor's Language Server Protocol support to `biome lsp-proxy`, or run
`pnpm lint` and `pnpm lint:fix` in a terminal.

## Shared app shell

`src/main/webapp/newApp/index.jsp` supplies the servlet context through `#app`'s escaped
`data-context-path` attribute. The production router in
`src/main/webapp/ui/src/modules/common/app/router.tsx` uses it as the basepath. Routes and generated
navigation URLs retain the deployment prefix; the empty servlet context uses `/`.

Both app-shell and legacy help actions use `showLighthouse()`. It opens the widget when its methods
are ready, or opens the documentation website when the external widget is unavailable. Keep help
actions available while it loads.

## Application routes

Production routes live under `src/main/webapp/ui/src/routes/`. Keep page components, search
validators, reusable helpers, tests, and stories in their feature modules. Route files contain route
declarations and private route-specific helpers. The root `__root.tsx` owns AppShell, root metadata,
scripts, and the root not-found page.

RSpace requires directories for URL segments and the default `index.tsx` and `route.tsx` filenames.
Only the route-directory root may contain `__root.tsx`. `pnpm lint:routes` runs `@ls-lint/ls-lint`
and a scoped Biome filename check; both `pnpm lint` and `pnpm lint:ci` include it. Flat dotted route
filenames, dotted directories, `page.tsx`, `layout.tsx`, and helper files in this directory fail the
check. Biome also rejects dotted source filenames beyond ls-lint's extension-depth coverage.

After adding, moving, or renaming a route, run `pnpm serve` or `pnpm build` from the repository root.
The Vite router plugin generates `src/main/webapp/ui/src/routeTree.gen.ts` and updates route
declarations. Review and commit both with the route change. Do not edit the generated tree by hand;
Biome excludes it. CI rebuilds the frontend and fails if the committed tree or declarations drift.
The committed output lets standalone `pnpm tsc`, unit tests, Browser Mode, and Storybook run without
a generator watcher.

RSpace's application Vite configuration enables automatic route splitting and places the router
plugin before React. Keep feature components outside route files so route declarations stay small.
Use `pnpm build:stats` to inspect bundle changes when changing shared imports. Unit tests disable the
router plugin; Browser Mode and Storybook use separate configurations without route generation or
splitting. All consume the committed route declarations.

Use the official [file-based routing guide](https://tanstack.com/router/latest/docs/framework/react/routing/file-based-routing)
for route trees, nesting, index routes, and parameter syntax. Consult
[type safety](https://tanstack.com/router/latest/docs/framework/react/guide/type-safety) for typed route
hooks and navigation, and [automatic code splitting](https://tanstack.com/router/latest/docs/framework/react/guide/automatic-code-splitting)
for eager and lazy route behavior.

## Shared frontend helpers

The [frontend README](../../../src/main/webapp/ui/README.md#shared-frontend-helpers) lists reusable
layout, query, API-header, and test helpers. For isolated route-aware tests, reuse `renderInRouter`
from the [shared helper table](../../../src/main/webapp/ui/README.md#shared-frontend-helpers).

For non-route components, keep heavy optional features behind their existing lazy imports and
inspect `pnpm build:stats` after changing shared imports. See React's
[`lazy` reference](https://react.dev/reference/react/lazy) for the framework API.
